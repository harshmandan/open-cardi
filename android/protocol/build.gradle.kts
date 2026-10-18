plugins {
	alias(libs.plugins.kotlin.jvm)
}

kotlin {
	jvmToolchain(17)
}

dependencies {
	testImplementation(libs.kotlin.test)
	// Test-only: reads the shared ../../protocol/vectors.json contract.
	testImplementation(libs.kotlinx.serialization.json)
}

tasks.test {
	useJUnitPlatform()
}
