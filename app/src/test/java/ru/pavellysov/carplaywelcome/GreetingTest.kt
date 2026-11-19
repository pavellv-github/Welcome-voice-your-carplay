package ru.pavellysov.carplaywelcome

import java.time.LocalTime
import kotlin.test.Test
import kotlin.test.assertEquals

class GreetingTest {
    @Test fun `selects agreed time boundaries`() {
        assertEquals(Sound.MORNING, greetingFor(LocalTime.of(6, 0)))
        assertEquals(Sound.MORNING, greetingFor(LocalTime.of(12, 0)))
        assertEquals(Sound.DAY, greetingFor(LocalTime.of(12, 1)))
        assertEquals(Sound.EVENING, greetingFor(LocalTime.of(17, 1)))
        assertEquals(Sound.NIGHT, greetingFor(LocalTime.of(23, 1)))
        assertEquals(Sound.NIGHT, greetingFor(LocalTime.of(5, 59)))
    }
}
