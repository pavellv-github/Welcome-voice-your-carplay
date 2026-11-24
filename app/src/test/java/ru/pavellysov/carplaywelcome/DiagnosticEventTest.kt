package ru.pavellysov.carplaywelcome

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DiagnosticEventTest {
    @Test
    fun everyDiagnosticEventHasItsOwnSound() {
        assertEquals(DiagnosticEvent.values().size, DiagnosticEvent.values().map { it.sound }.distinct().size)
        assertTrue(DiagnosticEvent.values().any { it.severity == Severity.CRITICAL })
    }
}
