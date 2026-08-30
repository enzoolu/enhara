package br.com.enhara.api.config;

import br.com.enhara.api.vehicle.domain.Vehicle;
import br.com.enhara.api.vehicle.infrastructure.VehicleRepository;
import br.com.enhara.api.simulator.application.SimulationService;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class DemoDataConfiguration {
    @Bean
    @ConditionalOnProperty(name = "enhara.demo.seed", havingValue = "true")
    ApplicationRunner seedDemoVehicle(VehicleRepository vehicles, SimulationService simulation,
                                      @Value("${enhara.demo.auto-start-simulation:false}") boolean autoStart) {
        return ignored -> {
            Vehicle vehicle = vehicles.findAll().stream().findFirst().orElseGet(() -> vehicles.save(
                    new Vehicle("Enhara Demo Car", "9BWZZZ377VT004251", "Demo Motors", "Prototype 01", 2026,
                            "EHR2A26", 12_450)));
            if (autoStart) {
                simulation.start(vehicle.getId());
            }
        };
    }
}
