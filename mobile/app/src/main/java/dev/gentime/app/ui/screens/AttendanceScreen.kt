package dev.gentime.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.background
import dev.gentime.app.data.AttendanceRepository
import dev.gentime.app.data.TodayPunch
import dev.gentime.app.data.model.DailyRecord
import dev.gentime.app.ui.components.OutlineCardModifier
import dev.gentime.app.ui.components.StatusPill
import dev.gentime.app.ui.components.attendanceStatusStyle
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

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

    Column(Modifier.fillMaxSize().padding(horizontal = 20.dp)) {
        Text(
            "My Attendance",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(top = 20.dp, bottom = 16.dp),
        )

        TodayCard(today)
        Spacer(Modifier.height(20.dp))

        Text(
            "History",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 8.dp),
        )
        when {
            loading -> Text("Loading…", color = MaterialTheme.colorScheme.onSurfaceVariant)
            records.isEmpty() -> Text("No records yet.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            else -> LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(records) { r -> RecordRow(r) }
            }
        }
    }
}

@Composable
private fun TodayCard(punches: List<TodayPunch>) {
    val checkIn = punches.firstOrNull { it.type == "check_in" }
    val checkOut = punches.lastOrNull { it.type == "check_out" }
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.primaryContainer)
            .padding(20.dp),
    ) {
        Text(
            "Today",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f),
        )
        Spacer(Modifier.height(10.dp))
        if (punches.isEmpty()) {
            Text(
                "No check-in yet.",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
        } else {
            Row(Modifier.fillMaxWidth()) {
                TimeStat("Check in", checkIn?.timeLocal ?: "—", Modifier.weight(1f))
                TimeStat("Check out", checkOut?.timeLocal ?: "—", Modifier.weight(1f))
            }
            if (punches.any { !it.synced }) {
                Spacer(Modifier.height(10.dp))
                Text(
                    "Pending sync",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.6f),
                )
            }
        }
    }
}

@Composable
private fun TimeStat(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(
            label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f),
        )
        Text(
            value,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
        )
    }
}

@Composable
private fun RecordRow(r: DailyRecord) {
    Row(
        OutlineCardModifier().fillMaxWidth().padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                formatDate(r.workDate),
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
            )
            val detail = buildString {
                if (r.minutesLate > 0) append("${r.minutesLate}m late")
                r.minutesWorked?.let {
                    if (isNotEmpty()) append("  ·  ")
                    append("${it / 60}h ${it % 60}m")
                }
            }
            if (detail.isNotEmpty()) {
                Spacer(Modifier.height(2.dp))
                Text(
                    detail,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        StatusPill(attendanceStatusStyle(r.status))
    }
}

// "2026-07-24" -> "Fri, Jul 24"
private fun formatDate(iso: String): String = runCatching {
    LocalDate.parse(iso).format(DateTimeFormatter.ofPattern("EEE, MMM d", Locale.getDefault()))
}.getOrDefault(iso)
