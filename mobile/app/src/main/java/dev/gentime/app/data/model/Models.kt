package dev.gentime.app.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class Profile(
    val id: String,
    @SerialName("employee_code") val employeeCode: String,
    @SerialName("full_name") val fullName: String,
    val role: String,
    @SerialName("supervisor_id") val supervisorId: String? = null,
    @SerialName("site_id") val siteId: String? = null,
    @SerialName("device_id") val deviceId: String? = null,
    @SerialName("is_active") val isActive: Boolean = true,
)

@Serializable
data class DailyRecord(
    val id: String,
    @SerialName("profile_id") val profileId: String,
    @SerialName("work_date") val workDate: String,
    @SerialName("first_in") val firstIn: String? = null,
    @SerialName("last_out") val lastOut: String? = null,
    @SerialName("minutes_worked") val minutesWorked: Int? = null,
    @SerialName("minutes_late") val minutesLate: Int = 0,
    val status: String,
)

@Serializable
enum class LeaveType {
    @SerialName("vacation") VACATION,
    @SerialName("sick") SICK,
    @SerialName("emergency") EMERGENCY,
    @SerialName("unpaid") UNPAID,
    @SerialName("other") OTHER,
}

@Serializable
data class LeaveRequest(
    val id: String? = null,
    @SerialName("profile_id") val profileId: String? = null,
    @SerialName("leave_type") val leaveType: String,
    @SerialName("date_from") val dateFrom: String,
    @SerialName("date_to") val dateTo: String,
    val reason: String? = null,
    val status: String = "pending",
)

@Serializable
data class TeamMemberToday(
    @SerialName("profile_id") val profileId: String,
    @SerialName("full_name") val fullName: String,
    val status: String,
    @SerialName("minutes_late") val minutesLate: Int = 0,
)

@Serializable
data class Alert(
    val id: String,
    @SerialName("profile_id") val profileId: String,
    @SerialName("alert_type") val alertType: String,
    @SerialName("created_at") val createdAt: String,
    @SerialName("acknowledged_at") val acknowledgedAt: String? = null,
)

/** Payload for the submit_attendance_event RPC. */
@Serializable
data class SubmitEventArgs(
    @SerialName("p_client_event_id") val clientEventId: String,
    @SerialName("p_event_type") val eventType: String,
    @SerialName("p_event_at") val eventAt: String,
    @SerialName("p_lat") val lat: Double?,
    @SerialName("p_lng") val lng: Double?,
    @SerialName("p_accuracy_m") val accuracyM: Double?,
    @SerialName("p_device_id") val deviceId: String,
    @SerialName("p_is_offline_sync") val isOfflineSync: Boolean,
)

/** Payload for inserting a location_pings row. */
@Serializable
data class PingInsert(
    @SerialName("profile_id") val profileId: String,
    val lat: Double,
    val lng: Double,
    @SerialName("accuracy_m") val accuracyM: Double?,
)
