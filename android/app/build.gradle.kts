plugins {
	alias(libs.plugins.android.application)
	alias(libs.plugins.kotlin.compose)
}

kotlin {
	jvmToolchain(17)

	compilerOptions {
		// The expressive components are all experimental for now; opting in
		// here keeps the annotation off every composable in the file.
		freeCompilerArgs.addAll(
			"-opt-in=androidx.compose.material3.ExperimentalMaterial3ExpressiveApi",
			"-opt-in=androidx.compose.material3.ExperimentalMaterial3Api",
			"-opt-in=androidx.compose.foundation.layout.ExperimentalLayoutApi",
		)
	}
}

android {
	namespace = "ink.harsh.opencardi"
	compileSdk = 37

	defaultConfig {
		applicationId = "ink.harsh.opencardi"
		// API 33 lets us use only the modern BluetoothGatt.writeCharacteristic
		// overload and the runtime BLUETOOTH_CONNECT/SCAN permission model —
		// no legacy branches. Android Auto needs a recent phone regardless.
		minSdk = 33
		targetSdk = 37
		versionCode = 10
		versionName = "0.9.1"
	}

	signingConfigs {
		create("release") {
			// Local dev key. Android Auto refuses to list debug-signed,
			// debuggable packages, so even sideloaded testing needs a real
			// release build.
			storeFile = file("../keystore/opencardi.jks")
			storePassword = "opencardi"
			keyAlias = "opencardi"
			keyPassword = "opencardi"
		}
	}

	buildTypes {
		release {
			isMinifyEnabled = false
			signingConfig = signingConfigs.getByName("release")
		}
	}

	buildFeatures {
		compose = true
		// The about sheet reports the running version.
		buildConfig = true
	}
}

dependencies {
	implementation(project(":protocol"))

	implementation(libs.androidx.car.app)
	implementation(libs.androidx.car.app.projected)

	implementation(libs.androidx.core.ktx)
	implementation(libs.androidx.lifecycle.runtime.ktx)
	implementation(libs.androidx.lifecycle.service)
	implementation(libs.androidx.activity.compose)

	implementation(platform(libs.androidx.compose.bom))
	implementation(libs.androidx.compose.material3)
	implementation(libs.androidx.graphics.shapes)
	implementation(libs.androidx.compose.material.icons)
	implementation(libs.androidx.compose.ui.tooling.preview)
	debugImplementation(libs.androidx.compose.ui.tooling)
}
