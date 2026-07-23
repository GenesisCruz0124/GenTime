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
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.gentime.app.data.AttendanceRepository
import dev.gentime.app.data.model.DailyRecord

@Composable
fun AttendanceScreen(repo: AttendanceRepository) {
    var records by remember { mutableStateOf<List<DailyRecord>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        records = runCatching { repo.myRecords() }.getOrDefault(emptyList())
        loading = false
    }

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text("My Attendance", style = MaterialTheme.typography.headlineSmall)
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
