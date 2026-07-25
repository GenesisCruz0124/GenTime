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
  // 201-file master fields (non-sensitive; visible to team + admin)
  position: string | null;
  date_hired: string | null;
  employment_type: EmploymentType | null;
}

export type EmploymentType =
  | "regular" | "probationary" | "contractual" | "project_based" | "part_time";

export type RateType = "monthly" | "daily" | "hourly";

// Sensitive PII + PH government IDs + compensation. Admin write; supervisor
// reads their team. 1:1 with Profile.
export interface EmployeeDetails {
  profile_id: string;
  birth_date: string | null;
  gender: string | null;
  civil_status: string | null;
  address: string | null;
  phone: string | null;
  personal_email: string | null;
  emergency_contact_name: string | null;
  emergency_contact_phone: string | null;
  emergency_contact_relation: string | null;
  sss_no: string | null;
  philhealth_no: string | null;
  pagibig_no: string | null;
  tin_no: string | null;
  pay_rate: number | null;
  rate_type: RateType | null;
  updated_at: string;
}

export type DocType =
  | "resume" | "contract" | "govt_id" | "nbi_clearance" | "sss" | "philhealth"
  | "pagibig" | "tin" | "birth_certificate" | "diploma" | "medical" | "other";

export interface EmployeeDocument {
  id: string;
  profile_id: string;
  doc_type: DocType;
  label: string | null;
  storage_path: string;
  mime_type: string | null;
  size_bytes: number | null;
  uploaded_by: string | null;
  uploaded_at: string;
}

// One row returned by the compute_payroll(profile, from, to) RPC.
export interface PayrollResult {
  profile_id: string;
  employee_code: string;
  full_name: string;
  rate_type: RateType;
  pay_rate: number;
  days_present: number;
  days_absent: number;
  gross_pay: number;
  absence_deduction: number;
  sss_ee: number;
  philhealth_ee: number;
  pagibig_ee: number;
  taxable_income: number;
  withholding_tax: number;
  total_deductions: number;
  net_pay: number;
}

export interface PayrollSettings {
  id: boolean;
  sss_ee_rate: number;
  sss_msc_floor: number;
  sss_msc_ceiling: number;
  philhealth_rate: number;
  philhealth_floor: number;
  philhealth_ceiling: number;
  pagibig_ee_rate_low: number;
  pagibig_ee_rate_high: number;
  pagibig_threshold: number;
  pagibig_base_ceiling: number;
  working_days_per_month: number;
  hours_per_day: number;
  rates_verified: boolean;
  notes: string | null;
  updated_at: string;
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
  position: string | null;
  date_hired: string | null;
  employment_type: EmploymentType | null;
  days_present: number;
  days_late: number;
  total_minutes_late: number;
  days_absent: number;
  days_on_leave: number;
  total_hours: number;
}
