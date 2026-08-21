@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package ink.harsh.opencardi.ui

import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Code
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import ink.harsh.opencardi.BuildConfig

private const val REPO_URL = "https://github.com/harshmandan/open-cardi"

@Composable
fun AboutSheet() {
	val context = LocalContext.current

	Text("open-cardi", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
	Text(
		"Controls Cardi K3 / K4 and other ConSmart-family ambient light kits over " +
			"Bluetooth. The protocol was reverse-engineered from scratch — no ads, " +
			"no accounts, no tracking.",
		style = MaterialTheme.typography.bodyMedium,
		color = MaterialTheme.colorScheme.onSurfaceVariant,
	)

	Spacer(Modifier.height(4.dp))

	Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
		InfoRow("Version", "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
		InfoRow("Web app", "opencardi.harsh.ink")
		InfoRow("License", "MIT")
	}

	ToggleButton(
		checked = false,
		onCheckedChange = {
			context.startActivity(Intent(Intent.ACTION_VIEW, REPO_URL.toUri()))
		},
		shapes = pillShapes(),
		colors = tileColors(),
		modifier = Modifier
			.fillMaxWidth()
			.height(56.dp),
	) {
		Icon(Icons.Filled.Code, contentDescription = null, modifier = Modifier.size(18.dp))
		Spacer(Modifier.width(10.dp))
		Text("Source on GitHub", style = MaterialTheme.typography.titleMedium)
	}
}

@Composable
private fun InfoRow(label: String, value: String) {
	androidx.compose.foundation.layout.Row(
		modifier = Modifier.fillMaxWidth(),
		horizontalArrangement = Arrangement.SpaceBetween,
	) {
		Text(
			label,
			style = MaterialTheme.typography.bodyMedium,
			color = MaterialTheme.colorScheme.onSurfaceVariant,
		)
		Text(value, style = MaterialTheme.typography.bodyMedium)
	}
}
