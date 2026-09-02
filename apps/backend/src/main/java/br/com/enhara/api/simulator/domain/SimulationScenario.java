package br.com.enhara.api.simulator.domain;

public enum SimulationScenario {
    NORMAL,
    OVERHEAT,
    LOW_VOLTAGE,
    MISFIRE,
    /** Compatibilidade temporária com clientes do CP1. */
    LOW_BATTERY;

    public boolean isLowVoltage() {
        return this == LOW_VOLTAGE || this == LOW_BATTERY;
    }
}
