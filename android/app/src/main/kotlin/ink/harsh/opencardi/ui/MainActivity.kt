@file:OptIn(
	androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class,
	androidx.compose.material3.ExperimentalMaterial3Api::class,
	androidx.compose.foundation.layout.ExperimentalLayoutApi::class,
)

package ink.harsh.opencardi.ui

import android.Manifest
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.RenderEffect as AndroidRenderEffect
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.animation.animateBounds
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.BluetoothConnected
import androidx.compose.material.icons.filled.Brightness6
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.SliderState
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.ToggleButtonColors
import androidx.compose.material3.ToggleButtonShapes
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.key
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.LookaheadScope
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ink.harsh.opencardi.ble.CardiController
import ink.harsh.opencardi.ble.CardiService
import ink.harsh.opencardi.ble.CardiState
import ink.harsh.opencardi.ble.Connection
import ink.harsh.opencardi.ble.Favorite
import ink.harsh.opencardi.ble.Rgb
import ink.harsh.opencardi.protocol.MicModes
import ink.harsh.opencardi.protocol.Patterns
import ink.harsh.opencardi.protocol.Zones
import kotlinx.coroutines.launch
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * One screen, no scrolling. Zones are a swipeable carousel across the top half;
 * anything that cannot fit opens as a sheet.
 */
class MainActivity : ComponentActivity() {

	private val requestPermissions =
		registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { granted ->
			if (granted[Manifest.permission.BLUETOOTH_CONNECT] == true) {
				CardiService.start(this)
			}
		}

	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)
		// targetSdk 35+ draws edge to edge whether we ask or not.
		enableEdgeToEdge()
		CardiController.init(this)

		setContent {
			val state by CardiController.state.collectAsStateWithLifecycle()
			val accent by animateColorAsState(
				Color(state.color.r, state.color.g, state.color.b),
				label = "accent",
			)
			OpenCardiTheme(accent) {
				Screen(
					state = state,
					accent = accent,
					onConnectToggle = {
						if (state.isConnected) CardiService.stop(this)
						// Bluetooth only. POST_NOTIFICATIONS was bundled in here and
						// made Connect look like it wanted to send you
						// notifications; the foreground service runs either way,
						// its notification just stays hidden if undenied.
						else requestPermissions.launch(
							arrayOf(
								Manifest.permission.BLUETOOTH_SCAN,
								Manifest.permission.BLUETOOTH_CONNECT,
							)
						)
					},
				)
			}
		}
	}
}

private val PALETTE = listOf(
	Rgb(255, 0, 0), Rgb(255, 120, 0), Rgb(255, 220, 0), Rgb(0, 255, 60),
	Rgb(0, 220, 255), Rgb(40, 80, 255), Rgb(150, 0, 255), Rgb(255, 255, 255),
)

private enum class Sheet { NONE, PATTERN, VOICE, CUSTOM_COLOR, ABOUT }

@Composable
private fun Screen(state: CardiState, accent: Color, onConnectToggle: () -> Unit) {
	var sheet by remember { mutableStateOf(Sheet.NONE) }
	var selectedShape by remember { mutableStateOf(SELECTABLE_SHAPES.first()) }
	val zones = remember { Zones.list.filterNot { it.k4Only } }

	// Master off drains all colour from the screen rather than dimming it, so
	// "the lights are off" is unmistakable at a glance.
	val saturation by animateFloatAsState(
		if (state.masterOn) 1f else 0f,
		label = "saturation",
	)

	Scaffold(containerColor = MaterialTheme.colorScheme.background) { inner ->
		Column(
			modifier = Modifier
				.fillMaxSize()
				.padding(inner)
				.graphicsLayer {
					renderEffect = if (saturation < 0.999f) {
						AndroidRenderEffect.createColorFilterEffect(
							ColorMatrixColorFilter(
								ColorMatrix().apply { setSaturation(saturation) }
							)
						).asComposeRenderEffect()
					} else {
						null
					}
				},
			horizontalAlignment = Alignment.CenterHorizontally,
		) {
			ZoneArea(
				zones = zones,
				state = state,
				selectedShape = selectedShape,
				onPick = { favorite ->
					// A fresh shape per pick, so selection never repeats itself.
					selectedShape = SELECTABLE_SHAPES.random()
					CardiController.applyFavorite(favorite)
				},
				onPin = { favorite, alreadyPinned ->
					CardiController.toggleFavorite(favorite)
					// Pinning a colour is also choosing it. Unpinning is not —
					// it should not yank the lights off whatever is active.
					if (!alreadyPinned) {
						selectedShape = SELECTABLE_SHAPES.random()
						CardiController.applyFavorite(favorite)
					}
				},
				onCustomColor = { sheet = Sheet.CUSTOM_COLOR },
				modifier = Modifier.weight(1f),
			)

			BrightnessRow(state.brightness, accent)

			Spacer(Modifier.height(20.dp))

			ControlRow(
				state = state,
				onPatterns = { sheet = Sheet.PATTERN },
				onVoice = { sheet = Sheet.VOICE },
			)

			Spacer(Modifier.height(16.dp))

			BottomBar(state, onConnectToggle) { sheet = Sheet.ABOUT }

			Spacer(Modifier.height(12.dp))
		}
	}

	if (sheet != Sheet.NONE) {
		val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
		// Cap the height so an expanded sheet never reaches the status bar, and
		// pad the bottom past the gesture bar so the last row is never clipped.
		val maxSheetHeight = (LocalConfiguration.current.screenHeightDp * 0.82f).dp
		val navBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
		ModalBottomSheet(
			onDismissRequest = { sheet = Sheet.NONE },
			sheetState = sheetState,
			containerColor = MaterialTheme.colorScheme.surfaceContainer,
			// We reserve the gesture-bar space ourselves below; letting the
			// sheet also consume it made the measured inset come back zero.
			contentWindowInsets = { WindowInsets(0) },
			// The stock handle carries its own tonal surface, which reads as a
			// stray lighter block against our tinted container. Just the bar.
			dragHandle = {
				Box(
					Modifier
						.fillMaxWidth()
						.padding(vertical = 14.dp),
					contentAlignment = Alignment.Center,
				) {
					Box(
						Modifier
							.width(36.dp)
							.height(4.dp)
							.clip(CircleShape)
							.background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f))
					)
				}
			},
		) {
			Box(
				modifier = Modifier
					.fillMaxWidth()
					.heightIn(max = maxSheetHeight)
					.padding(bottom = navBottom + 40.dp),
			) {
			Column(
				modifier = Modifier
					.fillMaxWidth()
					.verticalScroll(rememberScrollState())
					.padding(horizontal = 24.dp),
				verticalArrangement = Arrangement.spacedBy(16.dp),
			) {
				when (sheet) {
					Sheet.PATTERN -> PatternSheet(state) { sheet = Sheet.NONE }
					Sheet.VOICE -> VoiceSheet(state) { sheet = Sheet.NONE }
					Sheet.CUSTOM_COLOR -> CustomColorSheet(state.brightness) { sheet = Sheet.NONE }
					Sheet.ABOUT -> AboutSheet()
					Sheet.NONE -> Unit
				}
			}
			}
		}
	}
}

/**
 * Everything above the brightness slider is one swipe surface for changing zone,
 * but only the zone name travels — the swatches are a fixed overlay, so the
 * colours you are aiming at never slide out from under your thumb.
 *
 * The pager's own gesture handling is off; an outer [draggable] drives it
 * instead, which is what lets a swipe that starts on a swatch still page. The
 * swatches keep their taps because a drag is never a click.
 */
@Composable
private fun ZoneArea(
	zones: List<ink.harsh.opencardi.protocol.Zone>,
	state: CardiState,
	selectedShape: androidx.graphics.shapes.RoundedPolygon,
	onPick: (Favorite) -> Unit,
	onPin: (Favorite, alreadyPinned: Boolean) -> Unit,
	onCustomColor: () -> Unit,
	modifier: Modifier = Modifier,
) {
	val startPage = remember { zones.indexOfFirst { it.id == state.zoneId }.coerceAtLeast(0) }
	val pagerState = rememberPagerState(initialPage = startPage) { zones.size }
	val scope = rememberCoroutineScope()

	LaunchedEffect(pagerState.currentPage) {
		zones.getOrNull(pagerState.currentPage)?.let { CardiController.selectZone(it.id) }
	}

	Box(
		modifier = modifier
			.fillMaxWidth()
			// Read the drag here and just move the page. Feeding a PagerState
			// from an outer modifier (draggable or scrollable) did not survive
			// the child swatches' own pointer handling; a plain drag detector
			// does, and the pager is only animating a title anyway.
			.pointerInput(zones.size) {
				var travelled = 0f
				val threshold = 56.dp.toPx()
				detectHorizontalDragGestures(
					onDragStart = { travelled = 0f },
					onDragEnd = {
						val target = when {
							travelled <= -threshold -> pagerState.currentPage + 1
							travelled >= threshold -> pagerState.currentPage - 1
							else -> pagerState.currentPage
						}.coerceIn(0, zones.lastIndex)
						scope.launch { pagerState.animateScrollToPage(target) }
					},
				) { _, delta -> travelled += delta }
			},
		contentAlignment = Alignment.Center,
	) {
		Column(horizontalAlignment = Alignment.CenterHorizontally) {
			// Only this pager moves; the swatches below are its sibling.
			HorizontalPager(
				state = pagerState,
				userScrollEnabled = false,
				modifier = Modifier.fillMaxWidth(),
			) { page ->
				Column(
					modifier = Modifier.fillMaxWidth(),
					horizontalAlignment = Alignment.CenterHorizontally,
				) {
					Text(
						zones[page].label,
						style = MaterialTheme.typography.displaySmall,
						fontWeight = FontWeight.ExtraBold,
						textAlign = TextAlign.Center,
					)
					Text(
						state.patternIndex?.let { Patterns.list.getOrNull(it)?.label }
							?: "Solid color",
						style = MaterialTheme.typography.bodyMedium,
						color = MaterialTheme.colorScheme.onSurfaceVariant,
					)
				}
			}

			Spacer(Modifier.height(40.dp))

			SwatchGrid(
				state = state,
				selectedShape = selectedShape,
				onPick = onPick,
				onPin = onPin,
				onCustomColor = onCustomColor,
			)
		}
	}
}

/** Pinned colours first, then the stock palette, then a custom-colour button. */
@Composable
private fun SwatchGrid(
	state: CardiState,
	selectedShape: androidx.graphics.shapes.RoundedPolygon,
	onPick: (Favorite) -> Unit,
	onPin: (Favorite, alreadyPinned: Boolean) -> Unit,
	onCustomColor: () -> Unit,
	modifier: Modifier = Modifier,
) {
	val pinned = state.favorites
	val seen = (pinned + state.customColors).map { it.color }.toSet()
	// Starred first, then anything mixed in the picker, then the stock palette.
	val entries = pinned +
		state.customColors.filterNot { it in pinned } +
		PALETTE.filterNot { it in seen }.map { Favorite(it, state.brightness) }

	// Pinning reorders the grid. LookaheadScope + animateBounds lets each
	// swatch slide to its new slot instead of teleporting; the key() is what
	// preserves a swatch's identity across the reorder so it has a slot to
	// slide *from*.
	LookaheadScope {
	FlowRow(
		horizontalArrangement = Arrangement.spacedBy(14.dp, Alignment.CenterHorizontally),
		verticalArrangement = Arrangement.spacedBy(14.dp),
		maxItemsInEachRow = 4,
		modifier = modifier
			.fillMaxWidth()
			.padding(horizontal = 24.dp),
	) {
		entries.forEach { entry ->
			val isPinned = entry in pinned
			val isSelected = state.color == entry.color && state.patternIndex == null
			key(entry.color) {
			Swatch(
				rgb = entry.color,
				selected = isSelected,
				pinned = isPinned,
				// Only worth stating when it differs from full. The live value
				// wins on the active swatch so dimming shows up where you are
				// looking, not just on pinned colours.
				brightnessLabel = when {
					isSelected && state.brightness != 100 -> state.brightness
					isPinned && entry.brightness != 100 -> entry.brightness
					else -> null
				},
				selectedShape = selectedShape,
				onClick = { onPick(entry) },
				onLongClick = { onPin(entry, isPinned) },
				modifier = Modifier.animateBounds(this@LookaheadScope),
			)
			}
		}

		Box(
			modifier = Modifier
				.size(64.dp)
				.clip(CircleShape),
			contentAlignment = Alignment.Center,
		) {
			ToggleButton(
				checked = false,
				onCheckedChange = { onCustomColor() },
				shapes = ToggleButtonShapes(CircleShape, CircleShape, CircleShape),
				colors = tileColors(),
				contentPadding = PaddingValues(0.dp),
				modifier = Modifier.size(64.dp),
			) {
				Icon(Icons.Filled.Add, contentDescription = "Custom color")
			}
		}
	}
	}
}

@Composable
private fun BrightnessRow(pct: Int, accent: Color) {
	Row(
		verticalAlignment = Alignment.CenterVertically,
		modifier = Modifier
			.fillMaxWidth()
			.padding(horizontal = 24.dp),
	) {
		Icon(
			Icons.Filled.Brightness6,
			contentDescription = null,
			tint = accent,
			modifier = Modifier.size(20.dp),
		)
		Spacer(Modifier.width(12.dp))
		Slider(
			value = pct.toFloat(),
			onValueChange = { CardiController.setBrightness(it.roundToInt()) },
			valueRange = 0f..100f,
			// 20 stops of 5% — the slider snaps to these, and the track draws
			// a tick for each one.
			steps = 19,
			track = { WavyTrack(it) },
			modifier = Modifier.weight(1f),
		)
		Spacer(Modifier.width(12.dp))
		Text(
			"$pct%",
			style = MaterialTheme.typography.labelLarge,
			color = MaterialTheme.colorScheme.onSurfaceVariant,
			modifier = Modifier.width(44.dp),
			textAlign = TextAlign.End,
		)
	}
}

@Composable
private fun ControlRow(state: CardiState, onPatterns: () -> Unit, onVoice: () -> Unit) {
	val pattern = state.patternIndex?.let { Patterns.list.getOrNull(it) }

	Row(
		horizontalArrangement = Arrangement.spacedBy(18.dp, Alignment.CenterHorizontally),
		verticalAlignment = Alignment.CenterVertically,
		modifier = Modifier.fillMaxWidth(),
	) {
		// The icon becomes the active pattern's own glyph, and the tile fills
		// with the accent — so the button says what is running.
		SquareAction(
			icon = pattern?.let { patternIcon(it) } ?: Icons.Filled.Tune,
			description = "Patterns",
			selected = pattern != null,
			onClick = onPatterns,
		)

		ToggleButton(
			checked = state.masterOn,
			onCheckedChange = { CardiController.setMaster(it) },
			shapes = ToggleButtonShapes(CircleShape, CircleShape, CircleShape),
			contentPadding = PaddingValues(0.dp),
			modifier = Modifier.size(112.dp),
		) {
			Icon(
				Icons.Filled.PowerSettingsNew,
				contentDescription = "Master power",
				modifier = Modifier.size(38.dp),
			)
		}

		SquareAction(
			icon = if (state.micMode != null) Icons.Filled.GraphicEq else Icons.Filled.Mic,
			description = "Voice",
			selected = state.micMode != null,
			onClick = onVoice,
		)
	}
}

@Composable
private fun SquareAction(
	icon: androidx.compose.ui.graphics.vector.ImageVector,
	description: String,
	selected: Boolean,
	onClick: () -> Unit,
) {
	ToggleButton(
		checked = selected,
		onCheckedChange = { onClick() },
		shapes = squircleShapes(),
		colors = tileColors(),
		contentPadding = PaddingValues(0.dp),
		modifier = Modifier.size(88.dp),
	) {
		Icon(icon, contentDescription = description, modifier = Modifier.size(26.dp))
	}
}

/** Connects or disconnects on the spot — no sheet, no second confirmation. */
@Composable
private fun BottomBar(state: CardiState, onToggle: () -> Unit, onAbout: () -> Unit) {
	Row(
		modifier = Modifier
			.fillMaxWidth()
			.padding(horizontal = 24.dp),
		horizontalArrangement = Arrangement.spacedBy(10.dp),
		verticalAlignment = Alignment.CenterVertically,
	) {
		ToggleButton(
			checked = false,
			onCheckedChange = { onAbout() },
			shapes = ToggleButtonShapes(CircleShape, CircleShape, CircleShape),
			colors = tileColors(),
			contentPadding = PaddingValues(0.dp),
			modifier = Modifier.size(44.dp),
		) {
			Icon(
				Icons.Filled.Info,
				contentDescription = "About",
				modifier = Modifier.size(18.dp),
			)
		}

		ToggleButton(
			checked = state.isConnected,
			onCheckedChange = { onToggle() },
			shapes = pillShapes(),
			colors = tileColors(),
		) {
			Icon(
				if (state.isConnected) Icons.Filled.BluetoothConnected else Icons.Filled.Bluetooth,
				contentDescription = null,
				modifier = Modifier.size(18.dp),
			)
			Spacer(Modifier.width(8.dp))
			Text(
				when (state.connection) {
					Connection.CONNECTED -> state.deviceName ?: "Connected"
					Connection.CONNECTING -> "Connecting…"
					Connection.SCANNING -> "Scanning…"
					Connection.DISCONNECTED -> "Connect"
				},
				style = MaterialTheme.typography.labelLarge,
				maxLines = 1,
			)
		}
	}
}

// --- sheets ----------------------------------------------------------------

@Composable
private fun SheetTitle(text: String) {
	Text(text, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
}

@Composable
private fun GroupLabel(text: String) {
	Text(
		text,
		style = MaterialTheme.typography.labelLarge,
		color = MaterialTheme.colorScheme.onSurfaceVariant,
	)
}

/** Grouped by how the light behaves, so 24 options stay scannable. */
private val PATTERN_GROUPS: List<Pair<String, List<ink.harsh.opencardi.protocol.Pattern>>> by lazy {
	listOf(
		"Sweeps" to Patterns.list.filter {
			it.label.contains("sweep", true) || it.label.contains("cycle", true)
		},
		"Breathing" to Patterns.list.filter { it.label.contains("breathing", true) },
		"Pulses" to Patterns.list.filter { it.label.contains("pulse", true) },
		"Strobes" to Patterns.list.filter { it.strobe },
	).map { (name, list) -> name to list.distinctBy { it.index } }
}

@Composable
private fun PatternSheet(state: CardiState, onDone: () -> Unit) {
	SheetTitle("Pattern")
	Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
		ToggleButton(
			checked = state.patternIndex == null,
			onCheckedChange = {
				CardiController.clearPattern()
				onDone()
			},
			shapes = pillShapes(),
			colors = tileColors(),
			modifier = Modifier
				.fillMaxWidth()
				.height(52.dp),
		) {
			Text("No pattern", style = MaterialTheme.typography.labelLarge)
		}

		PATTERN_GROUPS.forEach { (group, patterns) ->
			if (patterns.isEmpty()) return@forEach
			GroupLabel(group)
			FlowRow(
				horizontalArrangement = Arrangement.spacedBy(8.dp),
				verticalArrangement = Arrangement.spacedBy(8.dp),
			) {
				patterns.forEach { pattern ->
					ToggleButton(
						checked = state.patternIndex == pattern.index,
						onCheckedChange = {
							CardiController.setPattern(pattern.index)
							onDone()
						},
						shapes = pillShapes(),
						colors = tileColors(),
						contentPadding = PaddingValues(horizontal = 14.dp),
					) {
						Icon(
							patternIcon(pattern),
							contentDescription = null,
							modifier = Modifier.size(16.dp),
						)
						Spacer(Modifier.width(6.dp))
						Text(pattern.label, style = MaterialTheme.typography.labelMedium)
					}
				}
			}
		}
	}
}

@Composable
private fun VoiceSheet(state: CardiState, onDone: () -> Unit) {
	SheetTitle("Voice")
	ToggleButton(
		checked = state.micMode == null,
		onCheckedChange = {
			CardiController.setMic(null)
			onDone()
		},
		shapes = pillShapes(),
		colors = tileColors(),
		modifier = Modifier
			.fillMaxWidth()
			.height(52.dp),
	) {
		Text("Off", style = MaterialTheme.typography.labelLarge)
	}
	MicModes.list.forEach { mode ->
		ToggleButton(
			checked = state.micMode == mode.id,
			onCheckedChange = {
				CardiController.setMic(mode.id)
				onDone()
			},
			shapes = pillShapes(),
			colors = tileColors(),
			modifier = Modifier
				.fillMaxWidth()
				.height(56.dp),
		) {
			Text(mode.label, style = MaterialTheme.typography.titleMedium)
			Spacer(Modifier.width(10.dp))
			Text(
				mode.subtitle,
				style = MaterialTheme.typography.bodySmall,
				color = MaterialTheme.colorScheme.onSurfaceVariant,
			)
		}
	}
}

@Composable
private fun CustomColorSheet(startBrightness: Int, onDone: () -> Unit) {
	var hue by remember { mutableFloatStateOf(0f) }
	var saturation by remember { mutableFloatStateOf(1f) }
	var brightness by remember { mutableIntStateOf(startBrightness) }
	val rgb = remember(hue, saturation) { hsvToRgb(hue, saturation) }
	val swatch = Color(rgb.r, rgb.g, rgb.b)
	// Follows the track: full brightness is the pure colour, low brightness
	// washes toward white, so the button always shows the shade at the thumb.
	val preview = lerp(Color.White, swatch, brightness / 100f)
	val previewInk = if (preview.luminance() > 0.5f) Color.Black.copy(alpha = 0.8f) else Color.White

	SheetTitle("Custom color")

	Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
		ColorWheel(
			hue = hue,
			saturation = saturation,
			onChange = { h, s -> hue = h; saturation = s },
		)
	}

	GroupLabel("Brightness · $brightness%")
	// The ramp is the point of this control: the track shows white through to
	// the picked colour, so the thumb sits on the shade it will actually set.
	// Keeps the thumb gap and snap ticks the stock track would give.
	Slider(
		value = brightness.toFloat(),
		onValueChange = { brightness = it.roundToInt() },
		valueRange = 0f..100f,
		steps = 19,
		colors = SliderDefaults.colors(thumbColor = swatch),
		track = { BrightnessTrack(it, swatch) },
	)

	ToggleButton(
		checked = false,
		onCheckedChange = {
			// "Add" puts it in the grid and selects it. It does not star it —
			// favouriting stays a separate, deliberate long-press.
			val favorite = Favorite(rgb, brightness)
			CardiController.addCustomColor(favorite)
			CardiController.applyFavorite(favorite)
			onDone()
		},
		shapes = pillShapes(),
		// The button *is* the preview — it carries the colour you picked, so a
		// separate swatch blob has nothing left to say.
		colors = ToggleButtonColors(
			containerColor = preview,
			contentColor = previewInk,
			disabledContainerColor = preview,
			disabledContentColor = previewInk,
			checkedContainerColor = preview,
			checkedContentColor = previewInk,
		),
		modifier = Modifier
			.fillMaxWidth()
			.height(64.dp),
	) {
		Text(
			"Add color",
			style = MaterialTheme.typography.titleMedium,
			fontWeight = FontWeight.Bold,
		)
	}
}

/** Readable ink over a swatch, by perceived luminance. */
private fun inkOn(rgb: Rgb): Color {
	val luminance = (0.299 * rgb.r + 0.587 * rgb.g + 0.114 * rgb.b) / 255.0
	return if (luminance > 0.55) Color.Black.copy(alpha = 0.75f) else Color.White
}

/** A plain painted bar — the value is carried by the thumb, not by a fill. */
@Composable
private fun GradientTrack(brush: Brush) {
	Canvas(
		Modifier
			.fillMaxWidth()
			.height(24.dp)
	) {
		val h = 14.dp.toPx()
		drawRoundRect(
			brush = brush,
			topLeft = Offset(0f, (size.height - h) / 2f),
			size = androidx.compose.ui.geometry.Size(size.width, h),
			cornerRadius = CornerRadius(h / 2f),
		)
	}
}

/**
 * White through to the chosen colour, with the snap ticks carried on the
 * inactive half. The gradient spans the whole track, not just the filled part,
 * so a given position always shows the same shade.
 */
@Composable
private fun BrightnessTrack(sliderState: SliderState, color: Color) {
	val range = sliderState.valueRange.endInclusive - sliderState.valueRange.start
	val fraction =
		if (range <= 0f) 0f
		else ((sliderState.value - sliderState.valueRange.start) / range).coerceIn(0f, 1f)
	val stepCount = sliderState.steps

	Canvas(
		Modifier
			.fillMaxWidth()
			.height(26.dp)
	) {
		val h = 16.dp.toPx()
		val top = (size.height - h) / 2f
		val radius = CornerRadius(h / 2f)
		val gap = 5.dp.toPx()
		val thumbX = size.width * fraction

		val inactiveStart = (thumbX + gap).coerceAtMost(size.width)
		if (inactiveStart < size.width) {
			drawRoundRect(
				color = color.copy(alpha = 0.22f),
				topLeft = Offset(inactiveStart, top),
				size = Size(size.width - inactiveStart, h),
				cornerRadius = radius,
			)
		}

		val activeEnd = (thumbX - gap).coerceAtLeast(0f)
		if (activeEnd > 0f) {
			drawRoundRect(
				brush = Brush.horizontalGradient(
					colors = listOf(Color.White, color),
					startX = 0f,
					endX = size.width,
				),
				topLeft = Offset(0f, top),
				size = Size(activeEnd, h),
				cornerRadius = radius,
			)
		}

		if (stepCount > 0) {
			val stops = stepCount + 1
			for (i in 1 until stops) {
				val x = size.width * (i.toFloat() / stops)
				if (x > inactiveStart) {
                                        drawCircle(
						color = color.copy(alpha = 0.8f),
						radius = 2.dp.toPx(),
						center = Offset(x, size.height / 2f),
					)
				}
			}
		}
	}
}

private fun hsvToRgb(hue: Float, saturation: Float): Rgb {
	val c = android.graphics.Color.HSVToColor(floatArrayOf(hue, saturation, 1f))
	return Rgb(
		android.graphics.Color.red(c),
		android.graphics.Color.green(c),
		android.graphics.Color.blue(c),
	)
}

// --- wavy slider track ------------------------------------------------------

/**
 * Active half is a sine wave that flattens as it meets the thumb; the inactive
 * half is a plain line carrying a tick for every snap position. Both stop clear
 * of the thumb so nothing crosses it.
 */
@Composable
private fun WavyTrack(sliderState: SliderState, thick: Boolean = false) {
	val active = MaterialTheme.colorScheme.primary
	val inactive = MaterialTheme.colorScheme.surfaceContainerHighest
	val range = sliderState.valueRange.endInclusive - sliderState.valueRange.start
	val fraction =
		if (range <= 0f) 0f
		else ((sliderState.value - sliderState.valueRange.start) / range).coerceIn(0f, 1f)
	val stepCount = sliderState.steps

	Canvas(
		Modifier
			.fillMaxWidth()
			.height(if (thick) 40.dp else 28.dp)
	) {
		val midY = size.height / 2f
		val widthPx = (if (thick) 12.dp else 8.dp).toPx()
		val gap = widthPx * 1.2f
		val thumbX = size.width * fraction

		val inactiveStart = (thumbX + gap).coerceAtMost(size.width)
		if (inactiveStart < size.width) {
			drawLine(
				color = inactive,
				start = Offset(inactiveStart, midY),
				end = Offset(size.width, midY),
				strokeWidth = widthPx,
				cap = StrokeCap.Round,
			)
		}

		if (stepCount > 0) {
			val stops = stepCount + 1
			for (i in 0..stops) {
				val x = size.width * (i.toFloat() / stops)
				if (x > inactiveStart) {
					drawCircle(
						color = active.copy(alpha = 0.35f),
						radius = widthPx * 0.18f,
						center = Offset(x, midY),
					)
				}
			}
		}

		val activeEnd = thumbX - gap
		if (activeEnd > 1f) {
			val amplitude = (if (thick) 9.dp else 6.dp).toPx()
			val wavelength = (if (thick) 64.dp else 48.dp).toPx()
			val path = Path().apply {
				moveTo(0f, midY)
				var x = 0f
				while (x <= activeEnd) {
					// Damp near the thumb so the wave arrives flat.
					val damp = ((activeEnd - x) / wavelength).coerceIn(0f, 1f)
					lineTo(
						x,
						midY + sin(x / wavelength * 2f * Math.PI.toFloat()) * amplitude * damp,
					)
					x += 2f
				}
				lineTo(activeEnd, midY)
			}
			drawPath(path, color = active, style = Stroke(width = widthPx, cap = StrokeCap.Round))
		}
	}
}
