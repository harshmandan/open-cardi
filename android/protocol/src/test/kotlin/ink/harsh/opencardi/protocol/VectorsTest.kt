package ink.harsh.opencardi.protocol

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * The other half of the shared contract. `protocol/vectors.json` at the repo
 * root is driven by both this and `src/lib/protocol/vectors.test.ts`, so the
 * Kotlin and TypeScript builders cannot disagree without a test going red.
 */
class VectorsTest {

	private val vectors by lazy {
		// Gradle runs tests with the module directory as the working directory.
		val file = File("../../protocol/vectors.json")
		assertTrue(file.exists(), "missing shared contract at ${file.absolutePath}")
		Json.parseToJsonElement(file.readText()).jsonArray
	}

	private fun hex(bytes: ByteArray) =
		bytes.joinToString(" ") { "%02X".format(it) }

	private fun call(fn: String, a: List<Int>): ByteArray = when (fn) {
		"setColor" -> Commands.setColor(a[0], a[1], a[2], a[3])
		"masterOn" -> Commands.masterOn()
		"masterOff" -> Commands.masterOff()
		"selectZone" -> Commands.selectZone(a[0])
		"setPattern" -> Commands.setPattern(a[0])
		"setSpeed" -> Commands.setSpeed(a[0])
		"setPatternBrightness" -> Commands.setPatternBrightness(a[0])
		"setWarmWhite" -> Commands.setWarmWhite(a[0])
		"micOn" -> Commands.micOn(a[0])
		"micOff" -> Commands.micOff()
		"musicColor" -> Commands.musicColor(a[0], a[1], a[2])
		"setAppMode" -> Commands.setAppMode(a[0])
		"fragranceOn" -> Commands.fragranceOn()
		"fragranceOff" -> Commands.fragranceOff()
		"getLightData" -> Commands.getLightData()
		else -> throw IllegalArgumentException("no Kotlin binding for '$fn'")
	}

	@Test
	fun `every vector matches`() {
		var checked = 0
		for (element in vectors) {
			val v = element.jsonObject
			val fn = v.getValue("fn").jsonPrimitive.content
			val args = v.getValue("args").jsonArray.map { it.jsonPrimitive.content.toInt() }
			val expected = v.getValue("bytes").jsonPrimitive.content

			assertEquals(expected, hex(call(fn, args)), "$fn(${args.joinToString(", ")})")
			checked++
		}
		assertTrue(checked > 0, "vectors.json parsed but produced no cases")
	}

	@Test
	fun `pattern opcodes are unique and skip the setColor opcode`() {
		val opcodes = Patterns.list.map { it.opcode }
		assertEquals(opcodes.size, opcodes.toSet().size, "duplicate pattern opcode")
		assertTrue(0x49 !in opcodes, "0x49 is setColor, not a pattern")
	}

	@Test
	fun `zone values are unique`() {
		val values = Zones.list.map { it.value }
		assertEquals(values.size, values.toSet().size, "duplicate zone value")
	}
}
