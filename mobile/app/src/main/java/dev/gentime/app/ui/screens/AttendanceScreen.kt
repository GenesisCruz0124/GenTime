package dev.gentime.app.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.CardDefaults
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.gentime.app.data.AttendanceRepository
import dev.gentime.app.data.TodayPunch
import dev.gentime.app.data.model.DailyRecord

@Composable
fun AttendanceScreen(repo: AttendanceRepository) {
    var records by remember { mutableStateOf<List<DailyRecord>>(emptyList()) }
    var today by remember { mutableStateOf<List<TodayPunch>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        today = runCatching { repo.todayPunches() }.getOrDefault(emptyList())
        records = runCatching { repo.myRecords() }.getOrDefault(emptyList())
        loading = false
    }

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text("My Attendance", style = MaterialTheme.typography.headlineSmall)

        TodayCard(today)
        Spacer(Modifier.height(8.dp))

        if (loading) {
            Text("Loading…", Modifier.padding(top = 16.dp))
        } else if (records.isEmpty()) {
            Text("No records yet.", Modifier.padding(top = 16.dp))
        } else {
            LazyColumn(Modifier.padding(top = 12.dp)) {
                items(records) { r -> RecordRow(r) }
            }
        }
    }
}

@Composable
private fun TodayCard(punches: List<TodayPunch>) {
    val checkIn = punches.firstOrNull { it.type == "check_in" }
    val checkOut = punches.lastOrNull { it.type == "check_out" }
    Card(
        Modifier.fillMaxWidth().padding(top = 12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
        ),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text("Today", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(4.dp))
            if (punches.isEmpty()) {
                Text("No check-in yet today.", style = MaterialTheme.typography.bodyMedium)
            } else {
                Text(
                    "Check in: ${checkIn?.timeLocal ?: "—"}",
                    style = MaterialTheme.typography.bodyLarge,
                )
                Text(
                    "Check out: ${checkOut?.timeLocal ?: "—"}",
                    style = MaterialTheme.typography.bodyLarge,
                )
                if (punches.any { !it.synced }) {
                    Text(
                        "Pending sync",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        }
    }
}

@Composable
private fun RecordRow(r: DailyRecord) {
    Card(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Column(Modifier.padding(12.dp)) {
            Text(r.workDate, style = MaterialTheme.typography.titleSmall)
            Text(
                buildString {
                    append(r.status.replace('_', ' ').replaceFirstChar { it.uppercase() })
                    if (r.minutesLate > 0) append(" · ${r.minutesLate}m late")
                    r.minutesWorked?.let { append(" · ${it / 60}h ${it % 60}m") }
                },
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}
