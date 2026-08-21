@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package ink.harsh.opencardi.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.BluetoothConnected
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.ToggleButtonColors
import androidx.compose.material3.ToggleButtonShapes
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import ink.harsh.opencardi.ble.CardiController
import ink.harsh.opencardi.ble.CardiState
import ink.harsh.opencardi.ble.Favorite
import ink.harsh.opencardi.protocol.Patterns
import ink.harsh.opencardi.protocol.Zones
import kotlin.math.roundToInt

/**
 * The wide layout, used on a head unit and on a phone held sideways.
 *
 * Four columns divided by rules: controls, zones, colours, brightness. Nothing
 * stretches to fill — sizes are fixed and the columns are centred, because a
 * control inflated to whatever height is left reads as a slab rather than a
 * button.
 */
@Composable
fun CarLayout(
	state: CardiState,
	palette: List<Favorite>,
	accent: Color,
	onPatterns: () -> Unit,
	onVoice: () -> Unit,
	onConnect: () -> Unit,
) {
	val zones = Zones.list.filterNot { it.k4Only }
	val pattern = state.patternIndex?.let { Patterns.list.getOrNull(it) }
	val colors = palette.take(9)

	Surface(
		color = MaterialTheme.colorScheme.background,
		contentColor = MaterialTheme.colorScheme.onBackground,
		modifier = Modifier.fillMaxSize(),
	) {
		Row(
			modifier = Modifier
				.fillMaxSize()
				.windowInsetsPadding(WindowInsets.safeDrawing)
				.padding(horizontal = 18.dp, vertical = 14.dp),
			verticalAlignment = Alignment.CenterVertically,
			horizontalArrangement = Arrangement.spacedBy(18.dp),
		) {
			// 1 — controls
			Column(
				verticalArrangement = Arrangement.spacedBy(14.dp),
				horizontalAlignment = Alignment.CenterHorizontally,
			) {
				ToggleButton(
					checked = state.masterOn,
					onCheckedChange = { CardiController.setMaster(it) },
					shapes = ToggleButtonShapes(CircleShape, CircleShape, CircleShape),
					contentPadding = PaddingValues(0.dp),
					modifier = Modifier.size(58.dp),
				) {
					Icon(
						Icons.Filled.PowerSettingsNew,
						contentDescription = "Master power",
						modifier = Modifier.size(24.dp),
					)
				}
				RoundControl(
					icon = pattern?.let { patternIcon(it) } ?: Icons.Filled.Tune,
					description = "Patterns",
					selected = pattern != null,
					onClick = onPatterns,
				)
				RoundControl(
					icon = if (state.micMode != null) Icons.Filled.GraphicEq else Icons.Filled.Mic,
					description = "Voice",
					selected = state.micMode != null,
					onClick = onVoice,
				)
				RoundControl(
					icon = if (state.isConnected) Icons.Filled.BluetoothConnected
					else Icons.Filled.Bluetooth,
					description = if (state.isConnected) "Disconnect" else "Connect",
					selected = state.isConnected,
					onClick = onConnect,
				)
			}

			CarRule()

			// 2 — zones, flat buttons that outline in the accent when active
			Column(
				verticalArrangement = Arrangement.spacedBy(12.dp),
				modifier = Modifier.width(150.dp),
			) {
				zones.forEach { zone ->
					val selected = state.zoneId == zone.id
					ToggleButton(
						checked = selected,
						onCheckedChange = { CardiController.selectZone(zone.id) },
						shapes = pillShapes(),
						colors = ToggleButtonColors(
							containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
							contentColor = MaterialTheme.colorScheme.onSurface,
							disabledContainerColor = MaterialTheme.colorScheme.surfaceContainer,
							disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
							checkedContainerColor = accent.copy(alpha = 0.14f),
							checkedContentColor = accent,
						),
						border = if (selected)
							androidx.compose.foundation.BorderStroke(1.5.dp, accent) else null,
						modifier = Modifier
							.fillMaxWidth()
							.height(54.dp),
					) {
						Text(
							zone.label,
							style = MaterialTheme.typography.titleSmall,
							fontWeight = FontWeight.SemiBold,
							maxLines = 1,
						)
					}
				}
			}

			CarRule()

			// 3 — colours, 3x3
			Column(
				verticalArrangement = Arrangement.spacedBy(12.dp),
				horizontalAlignment = Alignment.CenterHorizontally,
				modifier = Modifier.weight(1f),
			) {
				colors.chunked(3).forEach { row ->
					Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
						row.forEach { favorite ->
							val fill =
								Color(favorite.color.r, favorite.color.g, favorite.color.b)
							ToggleButton(
								checked = state.color == favorite.color,
								onCheckedChange = { CardiController.applyFavorite(favorite) },
								shapes = ToggleButtonShapes(
									CircleShape,
									CircleShape,
									CircleShape,
								),
								colors = ToggleButtonColors(
									containerColor = fill,
									contentColor = fill,
									disabledContainerColor = fill,
									disabledContentColor = fill,
									checkedContainerColor = fill,
									checkedContentColor = fill,
								),
								contentPadding = PaddingValues(0.dp),
								modifier = Modifier.size(52.dp),
							) {}
						}
						repeat(3 - row.size) { Spacer(Modifier.size(52.dp)) }
					}
				}
			}

			CarRule()

			// 4 — brightness
			Column(
				horizontalAlignment = Alignment.CenterHorizontally,
				verticalArrangement = Arrangement.spacedBy(10.dp),
			) {
				Icon(
					Icons.Filled.LightMode,
					contentDescription = null,
					tint = MaterialTheme.colorScheme.onSurfaceVariant,
					modifier = Modifier.size(22.dp),
				)
				VerticalSlider(
					value = state.brightness.toFloat(),
					onValueChange = { CardiController.setBrightness(it.roundToInt()) },
					valueRange = 0f..100f,
					steps = 19,
					trackWidth = 46.dp,
					trackHeight = 190.dp,
					accent = accent,
				)
				Text(
					"${state.brightness}%",
					style = MaterialTheme.typography.titleSmall,
					fontWeight = FontWeight.SemiBold,
				)
			}
		}
	}
}

@Composable
private fun CarRule() {
	VerticalDivider(
		modifier = Modifier
			.fillMaxHeight()
			.padding(vertical = 6.dp),
		color = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f),
	)
}

@Composable
private fun RoundControl(
	icon: ImageVector,
	description: String,
	selected: Boolean,
	onClick: () -> Unit,
) {
	ToggleButton(
		checked = selected,
		onCheckedChange = { onClick() },
		shapes = ToggleButtonShapes(CircleShape, CircleShape, CircleShape),
		colors = tileColors(),
		contentPadding = PaddingValues(0.dp),
		modifier = Modifier.size(58.dp),
	) {
		Icon(icon, contentDescription = description, modifier = Modifier.size(22.dp))
	}
}
