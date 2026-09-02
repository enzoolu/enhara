package br.com.enhara.api.notes;

import br.com.enhara.api.notes.application.VehicleNoteService;
import br.com.enhara.api.notes.domain.VehicleNote;
import br.com.enhara.api.shared.api.ApiModels.CreateVehicleRequest;
import br.com.enhara.api.shared.api.ApiModels.TelemetryRequest;
import br.com.enhara.api.shared.api.ApiModels.VehicleNoteRequest;
import br.com.enhara.api.statistics.application.VehicleStatisticsService;
import br.com.enhara.api.telemetry.application.TelemetryService;
import br.com.enhara.api.telemetry.domain.TelemetrySample;
import br.com.enhara.api.trips.domain.Trip;
import br.com.enhara.api.trips.domain.TripMetrics;
import br.com.enhara.api.trips.infrastructure.TripRepository;
import br.com.enhara.api.vehicle.application.VehicleService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class VehicleNotesAndStatisticsIntegrationTest {
    @Autowired VehicleService vehicles;
    @Autowired VehicleNoteService notes;
    @Autowired VehicleStatisticsService statistics;
    @Autowired TelemetryService telemetry;
    @Autowired TripRepository tripRepository;

    @Test
    void noteIsPersistedAndCanBeCompletedAndReopened() {
        var vehicle = createVehicle("8AGZZZ377VT004270", "TST1A20");
        var request = new VehicleNoteRequest("Trocar filtro", "Confirmar peça no manual",
                VehicleNote.Category.MAINTENANCE, Instant.now().minusSeconds(60));

        var created = notes.create(vehicle.getId(), request);

        assertThat(notes.list(vehicle.getId(), false)).singleElement().satisfies(note -> {
            assertThat(note.getTitle()).isEqualTo("Trocar filtro");
            assertThat(note.isOverdue(Instant.now())).isTrue();
        });
        notes.complete(vehicle.getId(), created.getId());
        assertThat(notes.list(vehicle.getId(), false)).isEmpty();
        assertThat(notes.list(vehicle.getId(), true)).singleElement()
                .extracting(VehicleNote::getStatus).isEqualTo(VehicleNote.Status.COMPLETED);
        notes.reopen(vehicle.getId(), created.getId());
        assertThat(notes.list(vehicle.getId(), false)).hasSize(1);
    }

    @Test
    void statisticsUseRecordedTelemetryAndCompletedTripsWithoutInventingConsumption() {
        var vehicle = createVehicle("8AGZZZ377VT004271", "TST1A21");
        var trip = new Trip(vehicle.getId(), Instant.now().minusSeconds(120));
        trip.finish(Instant.now(), new TripMetrics(4.2, 48, 72, 0, 0, 0, 100));
        tripRepository.save(trip);
        telemetry.ingest(vehicle.getId(), sample(Instant.now(), 72));

        var result = statistics.get(vehicle.getId());

        assertThat(result.distanceTrackedKm()).isEqualTo(4.2);
        assertThat(result.maxRecordedSpeedKph()).isEqualTo(72);
        assertThat(result.averageConsumptionKmPerLiter()).isNull();
        assertThat(result.consumptionAvailability().name()).isEqualTo("INSUFFICIENT_DATA");
        assertThat(result.completedTrips()).isEqualTo(1);
    }

    private br.com.enhara.api.vehicle.domain.Vehicle createVehicle(String vin, String plate) {
        return vehicles.create(new CreateVehicleRequest("Carro estatísticas", vin,
                "Demo", "Stats", 2026, plate, 0));
    }

    private TelemetryRequest sample(Instant recordedAt, double speedKph) {
        return new TelemetryRequest(recordedAt, speedKph, 2200, 90.0, 42.0, 25.0,
                13.8, 70.0, null, null, TelemetrySample.Source.SIMULATED_OBD);
    }
}
