package dev.gentime.app.ui.screens

import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.gentime.app.data.Supa
import dev.gentime.app.data.model.LeaveRequest
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Order
import kotlinx.coroutines.launch

private val LEAVE_TYPES = listOf("vacation", "sick", "emergency", "unpaid", "other")

@Composable
fun LeaveScreen() {
    val scope = rememberCoroutineScope()
    var type by remember { mutableStateOf("vacation") }
    var from by remember { mutableStateOf("") }
    var to by remember { mutableStateOf("") }
    var reason by remember { mutableStateOf("") }
    var requests by remember { mutableStateOf<List<LeaveRequest>>(emptyList()) }
    var message by remember { mutableStateOf<String?>(null) }

    suspend fun load() {
        val uid = Supa.client.auth.currentUserOrNull()?.id ?: return
        requests = runCatching {
            Supa.client.postgrest["leave_requests"].select {
                filter { eq("profile_id", uid) }
                order("created_at", Order.DESCENDING)
            }.decodeList<LeaveRequest>()
        }.getOrDefault(emptyList())
    }

    androidx.compose.runtime.LaunchedEffect(Unit) { load() }

    fun submit() {
        scope.launch {
            val uid = Supa.client.auth.currentUserOrNull()?.id ?: return@launch
            try {
                Supa.client.postgrest["leave_requests"].insert(
                    LeaveRequest(
                        profileId = uid, leaveType = type,
                        dateFrom = from, dateTo = to,
                        reason = reason.ifBlank { null },
                    ),
                )
                message = "Leave filed"
                from = ""; to = ""; reason = ""
                load()
            } catch (e: Exception) {
                message = e.message ?: "Failed to file leave"
            }
        }
    }

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text("File Leave", style = MaterialTheme.typography.headlineSmall)

        Row(Modifier.horizontalScroll(rememberScrollState()).padding(vertical = 8.dp)) {
            LEAVE_TYPES.forEach { t ->
                FilterChip(
                    selected = type == t,
                    onClick = { type = t },
                    label = { Text(t) },
                    modifier = Modifier.padding(end = 6.dp),
                )
            }
        }
        OutlinedTextField(from, { from = it }, label = { Text("From (YYYY-MM-DD)") },
            singleLine = true, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(to, { to = it }, label = { Text("To (YYYY-MM-DD)") },
            singleLine = true, modifier = Modifier.fillMaxWidth().padding(top = 8.dp))
        OutlinedTextField(reason, { reason = it }, label = { Text("Reason (optional)") },
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp))
        Button(onClick = { submit() },
            enabled = from.isNotBlank() && to.isNotBlank(),
            modifier = Modifier.fillMaxWidth().padding(top = 12.dp)) { Text("Submit") }

        message?.let { Text(it, Modifier.padding(top = 8.dp)) }

        Spacer(Modifier.height(16.dp))
        Text("My Requests", style = MaterialTheme.typography.titleMedium)
        LazyColumn(Modifier.padding(top = 8.dp)) {
            items(requests) { r ->
                Card(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                    Column(Modifier.padding(12.dp)) {
                        Text("${r.leaveType} · ${r.status}", style = MaterialTheme.typography.titleSmall)
                        Text("${r.dateFrom} → ${r.dateTo}", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }
}
