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
fun CarLayout(state: CardiState, palette: List<Favorite>) {
	val zones = Zones.list.filterNot { it.k4Only }
	val index = zones.indexOfFirst { it.id == state.zoneId }.coerceAtLeast(0)

	Surface(
		color = MaterialTheme.colorScheme.background,
		contentColor = MaterialTheme.colorScheme.onBackground,
		modifier = Modifier.fillMaxSize(),
	) {
	Row(
		modifier = Modifier
			.fillMaxSize()
			.padding(horizontal = 20.dp, vertical = 12.dp),
		verticalAlignment = Alignment.CenterVertically,
		horizontalArrangement = Arrangement.spacedBy(18.dp),
	) {
		// Power: the largest target, always in the same place.
		ToggleButton(
			checked = state.masterOn,
			onCheckedChange = { CardiController.setMaster(it) },
			shapes = ToggleButtonShapes(CircleShape, CircleShape, CircleShape),
			contentPadding = PaddingValues(0.dp),
			modifier = Modifier.size(104.dp),
		) {
			Icon(
				Icons.Filled.PowerSettingsNew,
				contentDescription = "Master power",
				modifier = Modifier.size(40.dp),
			)
		}

		Column(
			modifier = Modifier.weight(1f),
			verticalArrangement = Arrangement.spacedBy(12.dp),
		) {
			// Zone as a stepper, not a swipe: a swipe on a head unit competes
			// with the host's own gestures.
			Row(
				verticalAlignment = Alignment.CenterVertically,
				horizontalArrangement = Arrangement.spacedBy(16.dp),
				modifier = Modifier.fillMaxWidth(),
			) {
				CarStep(Icons.Filled.ChevronLeft, "Previous zone") {
					CardiController.selectZone(zones[(index - 1 + zones.size) % zones.size].id)
				}
				Text(
					zones[index].label,
					style = MaterialTheme.typography.titleLarge,
					fontWeight = FontWeight.ExtraBold,
					textAlign = TextAlign.Center,
					color = MaterialTheme.colorScheme.onBackground,
					modifier = Modifier.weight(1f),
				)
				CarStep(Icons.Filled.ChevronRight, "Next zone") {
					CardiController.selectZone(zones[(index + 1) % zones.size].id)
				}
			}

			Row(
				horizontalArrangement = Arrangement.spacedBy(14.dp, Alignment.CenterHorizontally),
				modifier = Modifier.fillMaxWidth(),
			) {
				palette.take(6).forEach { favorite ->
					val fill = Color(favorite.color.r, favorite.color.g, favorite.color.b)
					ToggleButton(
						checked = state.color == favorite.color,
						onCheckedChange = { CardiController.applyFavorite(favorite) },
						shapes = ToggleButtonShapes(CircleShape, CircleShape, CircleShape),
						colors = ToggleButtonColors(
							containerColor = fill,
							contentColor = fill,
							disabledContainerColor = fill,
							disabledContentColor = fill,
							checkedContainerColor = fill,
							checkedContentColor = fill,
						),
						contentPadding = PaddingValues(0.dp),
						modifier = Modifier.size(60.dp),
					) {}
				}
			}

			// Stepped brightness rather than a slider: dragging a thin track is
			// the one thing you cannot do on a car screen without looking.
			Row(
				horizontalArrangement = Arrangement.spacedBy(10.dp),
				modifier = Modifier.fillMaxWidth(),
			) {
				listOf(10, 25, 50, 75, 100).forEach { pct ->
					ToggleButton(
						checked = state.brightness == pct,
						onCheckedChange = { CardiController.setBrightness(pct) },
						shapes = squircleShapes(),
						colors = tileColors(),
						contentPadding = PaddingValues(0.dp),
						modifier = Modifier
							.weight(1f)
							.height(50.dp),
					) {
						Text("$pct%", style = MaterialTheme.typography.titleMedium)
					}
				}
			}
		}
	}
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
