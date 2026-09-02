package br.com.enhara.api.simulator.domain;

/**
 * Catálogo deliberadamente pequeno de PIDs SAE J1979 usados pela simulação.
 * A presença no catálogo não implica suporte: cada perfil declara sua capability.
 */
public enum ObdPid {
    CALCULATED_ENGINE_LOAD("CALCULATED_ENGINE_LOAD", "01", "04", "%"),
    ENGINE_COOLANT_TEMPERATURE("ENGINE_COOLANT_TEMPERATURE", "01", "05", "°C"),
    SHORT_TERM_FUEL_TRIM_BANK_1("SHORT_TERM_FUEL_TRIM_BANK_1", "01", "06", "%"),
    LONG_TERM_FUEL_TRIM_BANK_1("LONG_TERM_FUEL_TRIM_BANK_1", "01", "07", "%"),
    INTAKE_MANIFOLD_ABSOLUTE_PRESSURE("INTAKE_MANIFOLD_ABSOLUTE_PRESSURE", "01", "0B", "kPa"),
    ENGINE_SPEED("ENGINE_SPEED", "01", "0C", "rpm"),
    VEHICLE_SPEED("VEHICLE_SPEED", "01", "0D", "km/h"),
    INTAKE_AIR_TEMPERATURE("INTAKE_AIR_TEMPERATURE", "01", "0F", "°C"),
    MAF_AIR_FLOW_RATE("MAF_AIR_FLOW_RATE", "01", "10", "g/s"),
    THROTTLE_POSITION("THROTTLE_POSITION", "01", "11", "%"),
    OXYGEN_SENSOR_OUTPUT_VOLTAGE_B1S1("OXYGEN_SENSOR_OUTPUT_VOLTAGE_B1S1", "01", "14", "V"),
    FUEL_LEVEL_INPUT("FUEL_LEVEL_INPUT", "01", "2F", "%"),
    BAROMETRIC_PRESSURE("BAROMETRIC_PRESSURE", "01", "33", "kPa"),
    CONTROL_MODULE_VOLTAGE("CONTROL_MODULE_VOLTAGE", "01", "42", "V"),
    COMMANDED_EQUIVALENCE_RATIO("COMMANDED_EQUIVALENCE_RATIO", "01", "44", "lambda"),
    ENGINE_OIL_TEMPERATURE("ENGINE_OIL_TEMPERATURE", "01", "5C", "°C"),
    VEHICLE_IDENTIFICATION_NUMBER("VEHICLE_IDENTIFICATION_NUMBER", "09", "02", null);

    private final String key;
    private final String service;
    private final String pid;
    private final String unit;

    ObdPid(String key, String service, String pid, String unit) {
        this.key = key;
        this.service = service;
        this.pid = pid;
        this.unit = unit;
    }

    public String key() { return key; }
    public String service() { return service; }
    public String pid() { return pid; }
    public String unit() { return unit; }
    public String address() { return service + "-" + pid; }
}
