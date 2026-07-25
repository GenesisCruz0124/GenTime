import type { ReactNode } from "react";
import { NavLink } from "react-router-dom";
import { useAuth } from "../auth/AuthContext";

const linkClass = ({ isActive }: { isActive: boolean }) =>
  `block rounded-lg px-3 py-2 text-sm font-medium ${
    isActive ? "bg-brand text-white" : "text-slate-600 hover:bg-slate-100"
  }`;

export default function Layout({ children }: { children: ReactNode }) {
  const { profile, signOut } = useAuth();
  const isAdmin = profile?.role === "admin";

  return (
    <div className="flex h-full">
      <aside className="flex w-56 shrink-0 flex-col border-r border-slate-200 bg-white p-3">
        <div className="mb-4 px-2">
          <div className="text-lg font-bold text-brand">GenTime</div>
          <div className="text-xs text-slate-400 capitalize">{profile?.role}</div>
        </div>
        <nav className="flex-1 space-y-1">
          <NavLink to="/map" className={linkClass}>Live Map</NavLink>
          <NavLink to="/today" className={linkClass}>Today</NavLink>
          <NavLink to="/attendance" className={linkClass}>Attendance</NavLink>
          <NavLink to="/leave" className={linkClass}>Leave Approvals</NavLink>
          <NavLink to="/alerts" className={linkClass}>Alerts</NavLink>
          <NavLink to="/files" className={linkClass}>201 Files</NavLink>
          {isAdmin && (
            <NavLink to="/manage" className={linkClass}>Employees &amp; Sites</NavLink>
          )}
          {isAdmin && (
            <NavLink to="/payroll-settings" className={linkClass}>Payroll Settings</NavLink>
          )}
          <NavLink to="/reports" className={linkClass}>Reports</NavLink>
        </nav>
        <div className="border-t border-slate-200 pt-3">
          <div className="px-2 text-sm font-medium">{profile?.full_name}</div>
          <button
            onClick={signOut}
            className="mt-2 w-full rounded-lg px-3 py-2 text-left text-sm text-red-600 hover:bg-red-50"
          >
            Sign out
          </button>
        </div>
      </aside>
      <main className="flex-1 overflow-auto p-6">{children}</main>
    </div>
  );
}
