@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package ink.harsh.opencardi.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.ToggleButtonColors
import androidx.compose.material3.ToggleButtonShapes
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.BluetoothConnected
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Tune
import ink.harsh.opencardi.protocol.Patterns
import ink.harsh.opencardi.ble.CardiController
import ink.harsh.opencardi.ble.CardiState
import ink.harsh.opencardi.ble.Favorite
import ink.harsh.opencardi.protocol.Zones

/**
 * What the head unit shows.
 *
 * A car display is wide, further from the eye than a phone and touched without
 * looking at it, so this is not the phone layout scaled up: no carousel, no
 * colour wheel, no bottom sheets and no sliders. Every control is a large fixed
 * target laid out left to right, and the only text is the zone name.
 */
@Composable
fun CarLayout(
	state: CardiState,
	palette: List<Favorite>,
	onPatterns: () -> Unit,
	onVoice: () -> Unit,
	onConnect: () -> Unit,
) {
	val zones = Zones.list.filterNot { it.k4Only }
	val index = zones.indexOfFirst { it.id == state.zoneId }.coerceAtLeast(0)
	val pattern = state.patternIndex?.let { Patterns.list.getOrNull(it) }
	val colors = palette.take(8)

	Surface(
		color = MaterialTheme.colorScheme.background,
		contentColor = MaterialTheme.colorScheme.onBackground,
		modifier = Modifier.fillMaxSize(),
	) {
		Row(
			modifier = Modifier
				.fillMaxSize()
				// This layout was running under the status bar and the gesture
				// bar; nothing else here matters until it stops doing that.
				.windowInsetsPadding(WindowInsets.safeDrawing)
				.padding(horizontal = 16.dp, vertical = 10.dp),
			verticalAlignment = Alignment.CenterVertically,
			horizontalArrangement = Arrangement.spacedBy(14.dp),
		) {
			// Every control in one column down the left, each taking a share of
			// the full height rather than sitting in a band.
			Column(
				modifier = Modifier.width(72.dp),
				verticalArrangement = Arrangement.spacedBy(10.dp),
			) {
				ToggleButton(
					checked = state.masterOn,
					onCheckedChange = { CardiController.setMaster(it) },
					shapes = squircleShapes(),
					contentPadding = PaddingValues(0.dp),
					modifier = Modifier.size(72.dp),
				) {
					Icon(
						Icons.Filled.PowerSettingsNew,
						contentDescription = "Master power",
						modifier = Modifier.size(30.dp),
					)
				}
				CarSide(
					icon = pattern?.let { patternIcon(it) } ?: Icons.Filled.Tune,
					description = "Patterns",
					selected = pattern != null,
					onClick = onPatterns,
					modifier = Modifier.size(72.dp),
				)
				CarSide(
					icon = if (state.micMode != null) Icons.Filled.GraphicEq else Icons.Filled.Mic,
					description = "Voice",
					selected = state.micMode != null,
					onClick = onVoice,
					modifier = Modifier.size(72.dp),
				)
				ToggleButton(
					checked = state.isConnected,
					onCheckedChange = { onConnect() },
					shapes = pillShapes(),
					colors = tileColors(),
					contentPadding = PaddingValues(0.dp),
					modifier = Modifier
						.fillMaxWidth()
						.height(40.dp),
				) {
					Icon(
						if (state.isConnected) Icons.Filled.BluetoothConnected
						else Icons.Filled.Bluetooth,
						contentDescription = if (state.isConnected) "Connected" else "Connect",
						modifier = Modifier.size(18.dp),
					)
				}
			}

			// Colours as a two-row grid that fills whatever height is left.
			Column(
				modifier = Modifier.weight(1f),
				verticalArrangement = Arrangement.spacedBy(10.dp),
			) {
				Row(
					verticalAlignment = Alignment.CenterVertically,
					horizontalArrangement = Arrangement.spacedBy(10.dp),
					modifier = Modifier
						.fillMaxWidth()
						.height(46.dp),
				) {
					CarStep(Icons.Filled.ChevronLeft, "Previous zone") {
						CardiController.selectZone(
							zones[(index - 1 + zones.size) % zones.size].id,
						)
					}
					Column(
						modifier = Modifier.weight(1f),
						horizontalAlignment = Alignment.CenterHorizontally,
					) {
						Text(
							zones[index].label,
							style = MaterialTheme.typography.titleMedium,
							fontWeight = FontWeight.ExtraBold,
							textAlign = TextAlign.Center,
							color = MaterialTheme.colorScheme.onBackground,
						)
						Text(
							pattern?.label ?: "Solid color",
							style = MaterialTheme.typography.labelSmall,
							color = MaterialTheme.colorScheme.onSurfaceVariant,
						)
					}
					CarStep(Icons.Filled.ChevronRight, "Next zone") {
						CardiController.selectZone(zones[(index + 1) % zones.size].id)
					}
				}

				colors.chunked(4).forEach { row ->
					Row(
						horizontalArrangement = Arrangement.spacedBy(10.dp),
						modifier = Modifier.fillMaxWidth(),
					) {
						row.forEach { favorite ->
							val fill =
								Color(favorite.color.r, favorite.color.g, favorite.color.b)
							ToggleButton(
								checked = state.color == favorite.color,
								onCheckedChange = { CardiController.applyFavorite(favorite) },
								// A weighted cell cannot stay a circle, so the
								// swatches are rounded tiles that fill it.
								shapes = squircleShapes(),
								colors = ToggleButtonColors(
									containerColor = fill,
									contentColor = fill,
									disabledContainerColor = fill,
									disabledContentColor = fill,
									checkedContainerColor = fill,
									checkedContentColor = fill,
								),
								contentPadding = PaddingValues(0.dp),
								modifier = Modifier
									.weight(1f)
									.height(64.dp),
							) {}
						}
						repeat(4 - row.size) { Spacer(Modifier.weight(1f)) }
					}
				}
			}

			// Brightness down the right, same trick.
			Column(
				modifier = Modifier.width(84.dp),
				verticalArrangement = Arrangement.spacedBy(8.dp),
			) {
				listOf(100, 75, 50, 25, 10).forEach { pct ->
					ToggleButton(
						checked = state.brightness == pct,
						onCheckedChange = { CardiController.setBrightness(pct) },
						shapes = squircleShapes(),
						colors = tileColors(),
						contentPadding = PaddingValues(0.dp),
						modifier = Modifier
							.fillMaxWidth()
							.height(40.dp),
					) {
						Text("$pct%", style = MaterialTheme.typography.labelLarge)
					}
				}
			}
		}
	}
}

@Composable
private fun CarSide(
	icon: ImageVector,
	description: String,
	selected: Boolean,
	onClick: () -> Unit,
	modifier: Modifier = Modifier,
) {
	ToggleButton(
		checked = selected,
		onCheckedChange = { onClick() },
		shapes = squircleShapes(),
		colors = tileColors(),
		contentPadding = PaddingValues(0.dp),
		modifier = modifier,
	) {
		Icon(icon, contentDescription = description, modifier = Modifier.size(26.dp))
	}
}

@Composable
private fun CarStep(icon: ImageVector, description: String, onClick: () -> Unit) {
	ToggleButton(
		checked = false,
		onCheckedChange = { onClick() },
		shapes = squircleShapes(),
		colors = tileColors(),
		contentPadding = PaddingValues(0.dp),
		modifier = Modifier.size(56.dp),
	) {
		Icon(icon, contentDescription = description, modifier = Modifier.size(26.dp))
	}
}
