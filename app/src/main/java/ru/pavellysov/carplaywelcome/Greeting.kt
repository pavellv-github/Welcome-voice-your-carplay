package ru.pavellysov.carplaywelcome

import java.time.LocalTime

enum class Sound(val rawName: String) {
    MORNING("greeting_morning"),
    DAY("greeting_day"),
    EVENING("greeting_evening"),
    NIGHT("greeting_night"),
    RESTORED("connection_restored")
}

fun greetingFor(time: LocalTime): Sound = when {
    time >= LocalTime.of(6, 0) && time <= LocalTime.of(12, 0) -> Sound.MORNING
    time >= LocalTime.of(12, 1) && time <= LocalTime.of(17, 0) -> Sound.DAY
    time >= LocalTime.of(17, 1) && time <= LocalTime.of(23, 0) -> Sound.EVENING
    else -> Sound.NIGHT
}
