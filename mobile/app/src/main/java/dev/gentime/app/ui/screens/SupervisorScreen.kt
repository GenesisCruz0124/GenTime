package dev.gentime.app.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import dev.gentime.app.data.Supa
import dev.gentime.app.data.model.Alert
import dev.gentime.app.data.model.DailyRecord
import dev.gentime.app.data.model.LeaveRequest
import dev.gentime.app.data.model.Profile
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Order
import kotlinx.coroutines.launch
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.time.LocalDate

@Composable
fun SupervisorScreen() {
    val scope = rememberCoroutineScope()
    var names by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var today by remember { mutableStateOf<List<DailyRecord>>(emptyList()) }
    var alerts by remember { mutableStateOf<List<Alert>>(emptyList()) }
    var leaves by remember { mutableStateOf<List<LeaveRequest>>(emptyList()) }

    suspend fun reload() {
        names = runCatching {
            Supa.client.postgrest["profiles"].select().decodeList<Profile>()
                .associate { it.id to it.fullName }
        }.getOrDefault(emptyMap())
        today = runCatching {
            Supa.client.postgrest["daily_records"].select {
                filter { eq("work_date", LocalDate.now().toString()) }
            }.decodeList<DailyRecord>()
        }.getOrDefault(emptyList())
        alerts = runCatching {
            Supa.client.postgrest["alerts"].select {
                filter { exact("acknowledged_at", null) }
                order("created_at", Order.DESCENDING)
            }.decodeList<Alert>()
        }.getOrDefault(emptyList())
        leaves = runCatching {
            Supa.client.postgrest["leave_requests"].select {
                filter { eq("status", "pending") }
            }.decodeList<LeaveRequest>()
        }.getOrDefault(emptyList())
    }

    LaunchedEffect(Unit) { reload() }

    fun ack(a: Alert) = scope.launch {
        val uid = Supa.client.auth.currentUserOrNull()?.id
        runCatching {
            Supa.client.postgrest["alerts"].update(
                buildJsonObject {
                    put("acknowledged_by", uid)
                    put("acknowledged_at", java.time.Instant.now().toString())
                },
            ) { filter { eq("id", a.id) } }
        }
        reload()
    }

    fun decide(l: LeaveRequest, status: String) = scope.launch {
        val uid = Supa.client.auth.currentUserOrNull()?.id
        runCatching {
            Supa.client.postgrest["leave_requests"].update(
                buildJsonObject {
                    put("status", status)
                    put("decided_by", uid)
                    put("decided_at", java.time.Instant.now().toString())
                },
            ) { filter { eq("id", l.id!!) } }
        }
        reload()
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
        Text("Team Today", style = MaterialTheme.typography.headlineSmall)
        today.forEach { r ->
            Card(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
                Row(Modifier.padding(12.dp).fillMaxWidth()) {
                    Text(names[r.profileId] ?: r.profileId.take(8), Modifier.weight(1f))
                    Text(r.status.replace('_', ' ') +
                        if (r.minutesLate > 0) " (+${r.minutesLate}m)" else "")
                }
            }
        }

        Spacer(Modifier.height(16.dp))
        Text("Alerts", style = MaterialTheme.typography.titleMedium)
        if (alerts.isEmpty()) Text("None", style = MaterialTheme.typography.bodySmall)
        alerts.forEach { a ->
            Card(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
                Row(Modifier.padding(12.dp).fillMaxWidth()) {
                    Column(Modifier.weight(1f)) {
                        Text(a.alertType.replace('_', ' '), style = MaterialTheme.typography.titleSmall)
                        Text(names[a.profileId] ?: a.profileId.take(8), style = MaterialTheme.typography.bodySmall)
                    }
                    OutlinedButton(onClick = { ack(a) }) { Text("Ack") }
                }
            }
        }

        Spacer(Modifier.height(16.dp))
        Text("Leave Approvals", style = MaterialTheme.typography.titleMedium)
        if (leaves.isEmpty()) Text("None pending", style = MaterialTheme.typography.bodySmall)
        leaves.forEach { l ->
            Card(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
                Column(Modifier.padding(12.dp)) {
                    Text("${names[l.profileId] ?: ""} · ${l.leaveType}", style = MaterialTheme.typography.titleSmall)
                    Text("${l.dateFrom} → ${l.dateTo}", style = MaterialTheme.typography.bodySmall)
                    Row(Modifier.padding(top = 6.dp)) {
                        Button(onClick = { decide(l, "approved") }) { Text("Approve") }
                        Spacer(Modifier.height(0.dp))
                        OutlinedButton(
                            onClick = { decide(l, "rejected") },
                            modifier = Modifier.padding(start = 8.dp),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFDC2626)),
                        ) { Text("Reject") }
                    }
                }
            }
        }
    }
}
