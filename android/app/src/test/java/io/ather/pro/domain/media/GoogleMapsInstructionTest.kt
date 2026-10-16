package io.ather.pro.domain.media

import org.junit.Assert.*
import org.junit.Test

/** Synthetic and redacted phone-observed notification text. */
class GoogleMapsInstructionTest {
    private fun parse(vararg fields: String) = GoogleMapsInstruction.parse(fields.toList())

    @Test fun formatsMetricTurnsAndPreservesDecimalDistance() {
        assertEquals("200M LEFT", parse("200 m", "Turn left onto Example Road").label)
        assertEquals("1.2KM RIGHT", parse("1.2 km", "Turn right").label)
        assertEquals("90M LEFT", parse("0.09 km", "Turn left").label)
        assertEquals("1.2KM LEFT", parse("1,2\u00a0km", "Turn left").label)
    }
    @Test fun keepsImperialUnitsRatherThanInventingConversions() {
        assertEquals("500FT RIGHT", parse("500 ft", "Turn right").label)
        assertEquals("1MI LEFT", parse("1 mi", "Turn left").label)
    }
    @Test fun handlesQualifiedTurnsAndRoundaboutExit() {
        assertEquals("50M SLEFT", parse("50 m", "Slight left").label)
        assertEquals("50M HRIGHT", parse("50 m", "Turn sharply right").label)
        assertEquals("200M KLEFT", parse("200 m", "Keep left").label)
        assertEquals("200M EXIT2", parse("200 m", "At the roundabout, take the 2nd exit").label)
        assertEquals("200M UTURN", parse("200 m", "Make a U-turn").label)
    }
    @Test fun ignoresEtaDistanceAndAmbiguousThousandsSeparators() {
        assertEquals("TURN LEFT", parse("2.3 km · 7 min", "Turn left").label)
        assertEquals("TURN LEFT", parse("2.3 km remaining", "Turn left").label)
        assertEquals("TURN LEFT", parse("1,000 m", "Turn left").label)
        assertEquals("TURN LEFT", parse("200 m", "Turn left", "500 m").label)
    }
    @Test fun removesLongDistanceWithoutTruncatingTheAction() {
        assertEquals("TURN RIGHT", parse("12345 km", "Turn right").label)
        assertEquals("GO STRAIGHT", parse("Continue straight").label)
    }
    @Test fun headTowardKeepsItsMeaningWithoutPublishingOrParsingTheRoadName() {
        listOf("Head toward Example Road", "Head towards Example Road",
            "Head toward Turn Left Road", "Head toward Roundabout Take the 2nd Exit Road").forEach {
            assertEquals(GoogleMapsInstruction("HEAD TOWARD", true), parse(it))
        }
        assertEquals("HEAD TOWARD", parse("200 m", "Head toward Example Road").label)
    }
    @Test fun incompleteNegatedAndConflictingHeadingsStayUnsupported() {
        listOf(arrayOf("Head toward"), arrayOf("Head toward   "),
            arrayOf("Do not head toward Example Road"),
            arrayOf("Rerouting", "Head toward Example Road"),
            arrayOf("Head toward Example Road", "Turn left"),
            arrayOf("Head toward Example Road then turn right"),
            arrayOf("Head toward Example Road and turn right")).forEach { fields ->
            assertEquals(GoogleMapsInstruction("CHECK MAP", false), parse(*fields))
        }
    }
    @Test fun neverInfersTurnsFromStreetNamesOrArrivalSide() {
        listOf("Left Bank Road", "Destination will be on your right", "Head north", "Arrived", "").forEach {
            assertFalse(parse(it).supported)
            assertEquals("CHECK MAP", parse(it).label)
        }
    }
    @Test fun contradictoryOrMultiStepInstructionsAreUnsupported() {
        listOf(arrayOf("Turn left then turn right"), arrayOf("Turn left", "Turn right"),
            arrayOf("Do not turn left"), arrayOf("Rerouting", "Turn left"),
            arrayOf("Don’t turn left"), arrayOf("Never turn right"),
            arrayOf("GPS signal lost", "Turn right")).forEach { fields ->
            assertEquals(GoogleMapsInstruction("CHECK MAP", false), parse(*fields))
        }
    }
    @Test fun oversizedAndUnrecognizedLanguageInputsAreUnsupported() {
        assertFalse(parse("Turn left " + "x".repeat(501)).supported)
        assertFalse(parse("200 m", "Tournez à gauche").supported)
        assertFalse(parse("one", "two", "three", "Turn left").supported)
    }
    @Test fun everySupportedActionFitsTheChosenDisplayBudget() {
        listOf("Turn left", "Turn right", "Slight left", "Sharp right", "Keep right",
            "Continue straight", "Make a U turn", "At the roundabout, take the 20th exit",
            "Head toward Example Road").forEach { action ->
            listOf("", "50 m", "1.2 km", "12345 km").forEach { distance ->
                val result = parse(distance, action)
                assertTrue(result.supported)
                assertTrue(result.label.length in 1..11)
                assertTrue(result.label.matches(Regex("[A-Z0-9 .]+")))
            }
        }
    }
}
