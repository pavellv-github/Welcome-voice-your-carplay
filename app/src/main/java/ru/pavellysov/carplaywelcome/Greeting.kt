package ru.pavellysov.carplaywelcome

import java.time.LocalTime

enum class Sound(val rawName: String) {
    MORNING("greeting_morning"),
    DAY("greeting_day"),
    EVENING("greeting_evening"),
    NIGHT("greeting_night"),
    RESTORED("connection_restored"),
    VEHICLE_WELCOME("vehicle_welcome"),
    DIAGNOSTICS_START("diagnostics_start"),
    DIAGNOSTICS_OK("diagnostics_ok"),
    DIAGNOSTICS_ERRORS("diagnostics_errors"),
    DIAGNOSTICS_MULTIPLE_ERRORS("diagnostics_multiple_errors"),
    DIAGNOSTICS_COMPLETE("diagnostics_complete"),
    ADAPTER_UNAVAILABLE("adapter_unavailable"),
    ADAPTER_CONNECTION_LOST("adapter_connection_lost"),
    ADAPTER_CONNECTION_RESTORED("adapter_connection_restored"),
    ENGINE_OVERHEAT("engine_overheat"),
    ENGINE_TEMPERATURE_HIGH("engine_temperature_high"),
    ENGINE_NOT_WARM("engine_not_warm"),
    COOLANT_SENSOR_FAULT("coolant_sensor_fault"),
    ENGINE_RPM_HIGH("engine_rpm_high"),
    ENGINE_MISFIRE("engine_misfire"),
    ENGINE_MISFIRE_CRITICAL("engine_misfire_critical"),
    ENGINE_IDLE_UNSTABLE("engine_idle_unstable"),
    FUEL_LEAN("fuel_lean"),
    FUEL_RICH("fuel_rich"),
    FUEL_LONG_TRIM("fuel_long_trim"),
    FUEL_SHORT_TRIM("fuel_short_trim"),
    FUEL_SYSTEM_FAULT("fuel_system_fault"),
    FUEL_INJECTOR_FAULT("fuel_injector_fault"),
    INTAKE_MAF_FAULT("intake_maf_fault"),
    INTAKE_MANIFOLD_PRESSURE("intake_manifold_pressure"),
    INTAKE_MAP_FAULT("intake_map_fault"),
    INTAKE_AIR_TEMPERATURE("intake_air_temperature"),
    THROTTLE_FAULT("throttle_fault"),
    ENGINE_LOAD_HIGH("engine_load_high"),
    IGNITION_COIL_FAULT("ignition_coil_fault"),
    OXYGEN_SENSOR_FAULT("oxygen_sensor_fault"),
    OXYGEN_SENSOR_HEATER_FAULT("oxygen_sensor_heater_fault"),
    CATALYST_EFFICIENCY("catalyst_efficiency"),
    EVAP_FAULT("evap_fault"),
    VOLTAGE_LOW("voltage_low"),
    VOLTAGE_HIGH("voltage_high"),
    VOLTAGE_CHARGE_LOW("voltage_charge_low")
}

fun greetingFor(time: LocalTime): Sound = when {
    time >= LocalTime.of(6, 0) && time <= LocalTime.of(12, 0) -> Sound.MORNING
    time >= LocalTime.of(12, 1) && time <= LocalTime.of(17, 0) -> Sound.DAY
    time >= LocalTime.of(17, 1) && time <= LocalTime.of(23, 0) -> Sound.EVENING
    else -> Sound.NIGHT
}
