package ink.harsh.opencardi.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Air
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Autorenew
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.ui.graphics.vector.ImageVector
import ink.harsh.opencardi.protocol.Pattern

/**
 * A glyph per pattern family so the grid reads at a glance rather than as a
 * wall of similar text. Grouped by how the light behaves, not by colour.
 */
fun patternIcon(pattern: Pattern): ImageVector = when {
	pattern.label.contains("strobe", true) || pattern.label.contains("flash", true) ->
		Icons.Filled.Bolt
	pattern.label.contains("breathing", true) -> Icons.Filled.Air
	pattern.label.contains("pulse", true) -> Icons.Filled.GraphicEq
	pattern.label.contains("sweep", true) || pattern.label.contains("cycle", true) ->
		Icons.Filled.Autorenew
	else -> Icons.Filled.AutoAwesome
}
