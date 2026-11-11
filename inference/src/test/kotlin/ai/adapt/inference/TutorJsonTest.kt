package ai.adapt.inference

import org.junit.Assert.*
import org.junit.Test

class TutorJsonTest {
    private val valid="""{"version":1,"title":"CPU","explanation":"XNNPACK targets CPU.","exercise":"Name a metric."}"""
    @Test fun parsesTeachingDocument() { assertEquals("CPU",TutorJson.parse(valid).title) }
    @Test fun rejectsTrailingTextAndMultipleDocuments() {
        assertThrows(IllegalArgumentException::class.java) { TutorJson.parse(valid+" extra") }
        assertThrows(IllegalArgumentException::class.java) { TutorJson.parse(valid+valid) }
    }
    @Test fun refusesMasteryActionsAndUnknownFields() {
        assertThrows(IllegalArgumentException::class.java) { TutorJson.parse(valid.dropLast(1)+""", "mastery":1}""") }
    }
    @Test fun refusesCoercedVersionAndOversizedFields() {
        assertThrows(IllegalArgumentException::class.java) { TutorJson.parse(valid.replace("\"version\":1","\"version\":\"1\"")) }
        assertThrows(IllegalArgumentException::class.java) { TutorJson.parse(valid.replace("CPU","x".repeat(121))) }
    }
    @Test fun refusesJsonExtensionsAndDuplicateFields() {
        listOf(valid.replace('"','\''),valid.replace("\"version\":1","\"version\":1.0"),valid.replace("\"version\":1","\"version\":1,\"version\":1"),valid.dropLast(1)+",}",valid.replace("CPU","bad\ncontrol")).forEach { value ->
            assertThrows(IllegalArgumentException::class.java) { TutorJson.parse(value) }
        }
    }
    @Test fun supportsEscapedUnicodeAndRejectsUnpairedSurrogates() {
        assertEquals("🙂",TutorJson.parse(valid.replace("CPU","\\uD83D\\uDE42")).title)
        assertThrows(IllegalArgumentException::class.java) { TutorJson.parse(valid.replace("CPU","\\uD83D")) }
    }
}
