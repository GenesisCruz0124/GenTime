package dev.gentime.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/** A status's label plus its badge foreground/background colors. */
data class StatusStyle(val label: String, val fg: Color, val bg: Color)

private val Emerald = Color(0xFF059669) to Color(0xFFD1FAE5)
private val Amber = Color(0xFFB45309) to Color(0xFFFEF3C7)
private val Red = Color(0xFFB91C1C) to Color(0xFFFEE2E2)
private val Indigo = Color(0xFF4338CA) to Color(0xFFE0E7FF)
private val Slate = Color(0xFF475569) to Color(0xFFF1F5F9)

private fun pretty(status: String) =
    status.replace('_', ' ').replaceFirstChar { it.uppercase() }

/** Daily-record status → badge style. */
fun attendanceStatusStyle(status: String): StatusStyle {
    val (fg, bg) = when (status) {
        "present" -> Emerald
        "late" -> Amber
        "absent" -> Red
        "on_leave" -> Indigo
        else -> Slate // pending / incomplete
    }
    return StatusStyle(pretty(status), fg, bg)
}

/** Leave-request status → badge style. */
fun leaveStatusStyle(status: String): StatusStyle {
    val (fg, bg) = when (status) {
        "approved" -> Emerald
        "rejected" -> Red
        "pending" -> Amber
        else -> Slate // cancelled
    }
    return StatusStyle(pretty(status), fg, bg)
}

@Composable
fun StatusPill(style: StatusStyle, modifier: Modifier = Modifier) {
    Box(
        modifier
            .clip(RoundedCornerShape(999.dp))
            .background(style.bg)
            .padding(horizontal = 10.dp, vertical = 3.dp),
    ) {
        Text(style.label, style = MaterialTheme.typography.labelMedium, color = style.fg)
    }
}

/** A clean white card: thin outline, no elevation — the app's base surface. */
@Composable
fun OutlineCardModifier(): Modifier = Modifier
    .clip(RoundedCornerShape(16.dp))
    .background(MaterialTheme.colorScheme.surface)
    .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(16.dp))

val CardPadding = PaddingValues(16.dp)
