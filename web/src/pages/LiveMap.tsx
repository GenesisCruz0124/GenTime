import { useEffect, useRef, useState } from "react";
import maplibregl from "maplibre-gl";
import { supabase } from "../lib/supabase";
import type { LocationPing, Site } from "../lib/types";

const MAP_STYLE =
  import.meta.env.VITE_MAP_STYLE ?? "https://tiles.openfreemap.org/styles/liberty";

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

export default function LiveMap() {
  const container = useRef<HTMLDivElement>(null);
  const map = useRef<maplibregl.Map | null>(null);
  const markers = useRef<Map<string, maplibregl.Marker>>(new Map());
  const sitesRef = useRef<Site[]>([]);
  const [ready, setReady] = useState(false);

  // Determine whether a ping is inside any site geofence (green vs red marker).
  const insideAnySite = (lat: number, lng: number) =>
    sitesRef.current.some((s) => {
      const dLat = (lat - s.lat) * 111_320;
      const dLng = (lng - s.lng) * 111_320 * Math.cos((lat * Math.PI) / 180);
      return Math.hypot(dLat, dLng) <= s.radius_m;
    });

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
          id: `site-fill-${s.id}`,
          type: "fill",
          source: `site-${s.id}`,
          paint: { "fill-color": "#2563eb", "fill-opacity": 0.12 },
        });
        m.addLayer({
          id: `site-line-${s.id}`,
          type: "line",
          source: `site-${s.id}`,
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
    const existing = markers.current.get(p.profile_id);
    if (existing) {
      existing.setLngLat([p.lng, p.lat]);
      (existing.getElement().firstElementChild as HTMLElement).style.background = color;
    } else {
      const el = document.createElement("div");
      const dot = document.createElement("div");
      dot.style.cssText = `width:14px;height:14px;border-radius:50%;border:2px solid white;box-shadow:0 0 4px rgba(0,0,0,.4);background:${color}`;
      el.appendChild(dot);
      const marker = new maplibregl.Marker({ element: el })
        .setLngLat([p.lng, p.lat])
        .addTo(map.current);
      markers.current.set(p.profile_id, marker);
    }
  };

  useEffect(() => {
    if (!ready) return;
    // Seed with the latest ping per employee (last 30 min).
    (async () => {
      const since = new Date(Date.now() - 30 * 60_000).toISOString();
      const { data } = await supabase
        .from("location_pings")
        .select("*")
        .gte("pinged_at", since)
        .order("pinged_at", { ascending: false });
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
      <h1 className="mb-4 text-2xl font-semibold">Live Map</h1>
      <div ref={container} className="flex-1 overflow-hidden rounded-xl border border-slate-200" />
    </div>
  );
}
