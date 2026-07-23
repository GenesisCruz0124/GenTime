// Hand-written types mirroring the Postgres schema. In a fuller setup these
// would be generated via `supabase gen types typescript`.

export type Role = "employee" | "supervisor" | "admin";

export interface Profile {
  id: string;
  employee_code: string;
  full_name: string;
  role: Role;
  supervisor_id: string | null;
  site_id: string | null;
  device_id: string | null;
  fcm_token: string | null;
  is_active: boolean;
  created_at: string;
}

export interface Site {
  id: string;
  name: string;
  lat: number;
  lng: number;
  radius_m: number;
  created_at: string;
}

export interface Shift {
  id: string;
  profile_id: string;
  days: number[];
  start_time: string;
  end_time: string;
  grace_minutes: number;
  created_at: string;
}

export type DailyStatus =
  | "pending" | "present" | "late" | "absent" | "on_leave" | "incomplete";

export interface DailyRecord {
  id: string;
  profile_id: string;
  work_date: string;
  first_in: string | null;
  last_out: string | null;
  minutes_worked: number | null;
  minutes_late: number;
  status: DailyStatus;
  correction_note: string | null;
  corrected_by: string | null;
  computed_at: string | null;
}

export type LeaveType = "vacation" | "sick" | "emergency" | "unpaid" | "other";
export type LeaveStatus = "pending" | "approved" | "rejected" | "cancelled";

export interface LeaveRequest {
  id: string;
  profile_id: string;
  leave_type: LeaveType;
  date_from: string;
  date_to: string;
  reason: string | null;
  status: LeaveStatus;
  decided_by: string | null;
  decided_at: string | null;
  created_at: string;
}

export type AlertType =
  | "outside_geofence" | "wrong_location_checkin" | "missed_checkin" | "missed_checkout";

export interface Alert {
  id: string;
  profile_id: string;
  alert_type: AlertType;
  details: Record<string, unknown> | null;
  acknowledged_by: string | null;
  acknowledged_at: string | null;
  created_at: string;
}

export interface LocationPing {
  id: number;
  profile_id: string;
  lat: number;
  lng: number;
  accuracy_m: number | null;
  pinged_at: string;
}

export interface ReportRow {
  profile_id: string;
  employee_code: string;
  full_name: string;
  days_present: number;
  days_late: number;
  total_minutes_late: number;
  days_absent: number;
  days_on_leave: number;
  total_hours: number;
}
