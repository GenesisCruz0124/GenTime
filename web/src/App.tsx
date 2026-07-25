import { Navigate, Route, Routes } from "react-router-dom";
import { useAuth } from "./auth/AuthContext";
import Layout from "./components/Layout";
import Login from "./pages/Login";
import LiveMap from "./pages/LiveMap";
import Today from "./pages/Today";
import Attendance from "./pages/Attendance";
import LeaveApprovals from "./pages/LeaveApprovals";
import Alerts from "./pages/Alerts";
import EmployeesSites from "./pages/EmployeesSites";
import EmployeeFiles from "./pages/EmployeeFiles";
import PayrollSettings from "./pages/PayrollSettings";
import Reports from "./pages/Reports";

export default function App() {
  const { session, profile, loading } = useAuth();

  if (loading) {
    return (
      <div className="grid h-full place-items-center text-slate-500">Loading…</div>
    );
  }

  if (!session) return <Login />;

  // Web is supervisor/admin only.
  if (profile && profile.role === "employee") {
    return (
      <div className="grid h-full place-items-center p-6 text-center">
        <div>
          <h1 className="text-xl font-semibold">Access restricted</h1>
          <p className="mt-2 text-slate-500">
            The web dashboard is for supervisors and administrators. Please use
            the GenTime mobile app.
          </p>
        </div>
      </div>
    );
  }

  const isAdmin = profile?.role === "admin";

  return (
    <Layout>
      <Routes>
        <Route path="/" element={<Navigate to="/today" replace />} />
        <Route path="/map" element={<LiveMap />} />
        <Route path="/today" element={<Today />} />
        <Route path="/attendance" element={<Attendance />} />
        <Route path="/leave" element={<LeaveApprovals />} />
        <Route path="/alerts" element={<Alerts />} />
        <Route path="/files" element={<EmployeeFiles />} />
        {isAdmin && <Route path="/manage" element={<EmployeesSites />} />}
        {isAdmin && <Route path="/payroll-settings" element={<PayrollSettings />} />}
        <Route path="/reports" element={<Reports />} />
        <Route path="*" element={<Navigate to="/today" replace />} />
      </Routes>
    </Layout>
  );
}
