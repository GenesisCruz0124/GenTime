import { useEffect, useRef, useState } from "react";
import maplibregl from "maplibre-gl";
import { supabase } from "../lib/supabase";
import type { LocationPing, Profile, Site } from "../lib/types";

const MAP_STYLE =
  import.meta.env.VITE_MAP_STYLE ?? "https://tiles.openfreemap.org/styles/liberty";
const MANILA_TZ = "Asia/Manila";

// Build a GeoJSON circle polygon (metres radius) for a site geofence.
function circle(lng: number, lat: number, radiusM: number, points = 48) {
  const coords: [number, number][] = [];
  const dLat = radiusM / 111_320;
  const dLng = radiusM / (111_320 * Math.cos((lat * Math.PI) / 180));
  for (let i = 0; i <= points; i++) {
    const a = (i / points) * 2 * Math.PI;
    coords.push([lng + dLng * Math.cos(a), lat + dLat * Math.sin(a)]);
  }
  return coords;
}

const timeManila = (iso: string) =>
  new Date(iso).toLocaleString("en-PH", {
    timeZone: MANILA_TZ, month: "short", day: "numeric",
    hour: "numeric", minute: "2-digit",
  });

export default function LiveMap() {
  const container = useRef<HTMLDivElement>(null);
  const map = useRef<maplibregl.Map | null>(null);
  const markers = useRef<Map<string, maplibregl.Marker>>(new Map());
  const sitesRef = useRef<Site[]>([]);
  const peopleRef = useRef<Map<string, Profile>>(new Map());
  const [ready, setReady] = useState(false);
  const [count, setCount] = useState(0);

  const insideAnySite = (lat: number, lng: number) =>
    sitesRef.current.some((s) => {
      const dLat = (lat - s.lat) * 111_320;
      const dLng = (lng - s.lng) * 111_320 * Math.cos((lat * Math.PI) / 180);
      return Math.hypot(dLat, dLng) <= s.radius_m;
    });

  const popupHtml = (name: string, code: string, inside: boolean, color: string, at: string) =>
    `<strong>${name}</strong><br/>${code}<br/>` +
    `<span style="color:${color}">${inside ? "Inside geofence" : "Outside geofence"}</span><br/>` +
    `<small>${timeManila(at)}</small>`;

  useEffect(() => {
    if (!container.current) return;
    const m = new maplibregl.Map({
      container: container.current,
      style: MAP_STYLE,
      center: [121.0245, 14.5547],
      zoom: 11,
    });
    map.current = m;
    m.on("load", async () => {
      const { data: sites } = await supabase.from("sites").select("*");
      sitesRef.current = (sites as Site[]) ?? [];
      sitesRef.current.forEach((s) => {
        m.addSource(`site-${s.id}`, {
          type: "geojson",
          data: {
            type: "Feature",
            geometry: { type: "Polygon", coordinates: [circle(s.lng, s.lat, s.radius_m)] },
            properties: {},
          },
        });
        m.addLayer({
          id: `site-fill-${s.id}`, type: "fill", source: `site-${s.id}`,
          paint: { "fill-color": "#2563eb", "fill-opacity": 0.1 },
        });
        m.addLayer({
          id: `site-line-${s.id}`, type: "line", source: `site-${s.id}`,
          paint: { "line-color": "#2563eb", "line-width": 2 },
        });
      });
      setReady(true);
    });
    return () => m.remove();
  }, []);

  const upsertMarker = (p: LocationPing) => {
    if (!map.current) return;
    const inside = insideAnySite(p.lat, p.lng);
    const color = inside ? "#16a34a" : "#dc2626";
    const person = peopleRef.current.get(p.profile_id);
    const name = person?.full_name ?? "Employee";
    const code = person?.employee_code ?? "";
    const popup = () =>
      new maplibregl.Popup({ offset: 16 }).setHTML(popupHtml(name, code, inside, color, p.pinged_at));

    const existing = markers.current.get(p.profile_id);
    if (existing) {
      existing.setLngLat([p.lng, p.lat]);
      const dot = existing.getElement().querySelector<HTMLElement>(".gt-dot");
      if (dot) dot.style.background = color;
      existing.setPopup(popup());
      return;
    }

    const el = document.createElement("div");
    el.style.cssText = "display:flex;flex-direction:column;align-items:center;cursor:pointer";
    const dot = document.createElement("div");
    dot.className = "gt-dot";
    dot.style.cssText =
      `width:16px;height:16px;border-radius:50%;border:2px solid white;box-shadow:0 0 4px rgba(0,0,0,.4);background:${color}`;
    const label = document.createElement("div");
    label.textContent = name.split(" ")[0];
    label.style.cssText =
      "margin-top:3px;font-size:11px;font-weight:600;color:#0f172a;background:rgba(255,255,255,.85);" +
      "padding:1px 6px;border-radius:8px;white-space:nowrap;box-shadow:0 1px 2px rgba(0,0,0,.15)";
    el.appendChild(dot);
    el.appendChild(label);

    const marker = new maplibregl.Marker({ element: el })
      .setLngLat([p.lng, p.lat])
      .setPopup(popup())
      .addTo(map.current);
    markers.current.set(p.profile_id, marker);
    setCount(markers.current.size);
  };

  useEffect(() => {
    if (!ready) return;
    (async () => {
      // Names (RLS scopes to the caller's team / all for admin).
      const { data: people } = await supabase.from("profiles").select("*");
      peopleRef.current = new Map(
        ((people as Profile[]) ?? []).map((p) => [p.id, p]),
      );

      // Latest known position per employee — no time filter, so people show
      // even when live tracking isn't currently running.
      const { data } = await supabase
        .from("location_pings")
        .select("*")
        .order("pinged_at", { ascending: false })
        .limit(500);
      const seen = new Set<string>();
      (data as LocationPing[] | null)?.forEach((p) => {
        if (!seen.has(p.profile_id)) {
          seen.add(p.profile_id);
          upsertMarker(p);
        }
      });
    })();

    const channel = supabase
      .channel("live-pings")
      .on(
        "postgres_changes",
        { event: "INSERT", schema: "public", table: "location_pings" },
        (payload) => upsertMarker(payload.new as LocationPing),
      )
      .subscribe();
    return () => { supabase.removeChannel(channel); };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [ready]);

  return (
    <div className="flex h-full flex-col">
      <div className="mb-4 flex items-baseline justify-between">
        <h1 className="text-2xl font-semibold">Live Map</h1>
        <span className="text-sm text-slate-500">{count} employee{count === 1 ? "" : "s"} shown</span>
      </div>
      <div ref={container} className="flex-1 overflow-hidden rounded-xl border border-slate-200" />
      <p className="mt-2 text-xs text-slate-400">
        Each employee's latest known location — green = inside a site geofence,
        red = outside. Positions update live while employees are on the clock.
      </p>
    </div>
  );
}
