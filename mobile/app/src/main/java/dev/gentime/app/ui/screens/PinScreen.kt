package dev.gentime.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

/**
 * Set a new PIN: enter 4–6 digits, then re-enter to confirm. Calls [onCreate]
 * with the confirmed PIN. [onSignOut] lets the user bail back to email sign-in.
 */
@Composable
fun SetPinScreen(onCreate: (String) -> Unit, onSignOut: () -> Unit) {
    var first by remember { mutableStateOf("") }
    var entry by remember { mutableStateOf("") }
    var confirming by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    // In the confirm step, submit automatically once the re-entry matches the
    // first PIN's length.
    LaunchedEffect(entry, confirming) {
        if (confirming && entry.length == first.length) {
            if (entry == first) {
                onCreate(entry)
            } else {
                error = "PINs didn't match. Start over."
                first = ""; entry = ""; confirming = false
            }
        }
    }

    PinScaffold(
        title = if (confirming) "Confirm your PIN" else "Create a PIN",
        subtitle = if (confirming) "Re-enter the same digits" else "Choose 4–6 digits to unlock the app",
        filled = entry.length,
        total = if (confirming) first.length else MAX_LEN,
        error = error,
        onDigit = { c ->
            error = null
            if (entry.length < (if (confirming) first.length else MAX_LEN)) entry += c
        },
        onBackspace = { entry = entry.dropLast(1) },
        footer = {
            if (!confirming) {
                TextButton(
                    onClick = {
                        if (entry.length in MIN_LEN..MAX_LEN) {
                            first = entry; entry = ""; confirming = true; error = null
                        } else {
                            error = "Enter at least $MIN_LEN digits"
                        }
                    },
                ) { Text("Continue") }
            }
            TextButton(onClick = onSignOut) { Text("Sign out") }
        },
    )
}

/**
 * Unlock an existing session. Auto-verifies once [pinLength] digits are entered.
 * [onUsePassword] signs out and returns to email/password (the forgot-PIN path).
 */
@Composable
fun UnlockScreen(
    pinLength: Int,
    verify: (String) -> Boolean,
    onUnlocked: () -> Unit,
    onUsePassword: () -> Unit,
) {
    val len = if (pinLength in MIN_LEN..MAX_LEN) pinLength else MAX_LEN
    var entry by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(entry) {
        if (entry.length == len) {
            if (verify(entry)) {
                onUnlocked()
            } else {
                error = "Incorrect PIN. Try again."
                entry = ""
            }
        }
    }

    PinScaffold(
        title = "Enter your PIN",
        subtitle = "Unlock GenTime",
        filled = entry.length,
        total = len,
        error = error,
        onDigit = { c -> error = null; if (entry.length < len) entry += c },
        onBackspace = { entry = entry.dropLast(1) },
        footer = {
            TextButton(onClick = onUsePassword) { Text("Use email & password instead") }
        },
    )
}

// ---- shared UI ----------------------------------------------------------

private const val MIN_LEN = 4
private const val MAX_LEN = 6

@Composable
private fun PinScaffold(
    title: String,
    subtitle: String,
    filled: Int,
    total: Int,
    error: String?,
    onDigit: (Char) -> Unit,
    onBackspace: () -> Unit,
    footer: @Composable () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(title, style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(6.dp))
        Text(subtitle, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center)

        Spacer(Modifier.height(28.dp))
        PinDots(filled = filled, total = total)

        Spacer(Modifier.height(12.dp))
        Text(
            error ?: " ",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
        )

        Spacer(Modifier.height(16.dp))
        Column(modifier = Modifier.widthIn(max = 320.dp).fillMaxWidth()) {
            listOf("123", "456", "789").forEach { row ->
                Row(Modifier.fillMaxWidth()) { row.forEach { c -> KeyButton(c.toString()) { onDigit(c) } } }
            }
            Row(Modifier.fillMaxWidth()) {
                Spacer(Modifier.weight(1f).aspectRatio(1f))
                KeyButton("0") { onDigit('0') }
                KeyButton("⌫") { onBackspace() }
            }
        }

        Spacer(Modifier.height(12.dp))
        footer()

        Text(
            dev.gentime.app.BuildConfig.APP_VERSION,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.outline,
            modifier = Modifier.padding(top = 8.dp),
        )
    }
}

@Composable
private fun RowScope.KeyButton(label: String, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.weight(1f).aspectRatio(1f).padding(8.dp),
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(label, style = MaterialTheme.typography.headlineSmall)
        }
    }
}

@Composable
private fun PinDots(filled: Int, total: Int) {
    Row(horizontalArrangement = Arrangement.Center) {
        repeat(total) { i ->
            Box(
                Modifier
                    .padding(horizontal = 8.dp)
                    .size(16.dp)
                    .clip(CircleShape)
                    .background(
                        if (i < filled) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.surfaceVariant,
                    ),
            )
        }
    }
}
