package com.shiptrack;

import com.shiptrack.entity.Forecast;
import com.shiptrack.entity.Route;
import com.shiptrack.entity.Shipment;
import com.shiptrack.enums.ShipmentStatus;
import com.shiptrack.repository.ForecastRepository;
import com.shiptrack.repository.RouteRepository;
import com.shiptrack.repository.ShipmentRepository;
import com.shiptrack.service.ForecastService;
import com.shiptrack.service.ShipmentService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ForecastServiceTest {

    @Mock
    private ShipmentRepository shipmentRepository;

    @Mock
    private RouteRepository routeRepository;

    @Mock
    private ForecastRepository forecastRepository;

    @Mock
    private ShipmentService shipmentService;

    @InjectMocks
    private ForecastService forecastService;

    @Test
    void doesNotCreateForecastWhenRouteDurationIsUnavailable() {
        Shipment shipment = shipment(10L, ShipmentStatus.IN_TRANSIT);
        when(shipmentRepository.findById(10L)).thenReturn(Optional.of(shipment));
        when(routeRepository.findFirstByShipmentIdOrderByUpdatedAtDescIdDesc(10L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> forecastService.generateForecast(10L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("saved route with a valid estimated duration");

        verify(forecastRepository, never()).save(any(Forecast.class));
    }

    @Test
    void doesNotCreateForecastWhenRouteDurationIsInvalid() {
        Shipment shipment = shipment(10L, ShipmentStatus.IN_TRANSIT);
        Route route = new Route();
        route.setEstimatedDurationMinutes(0);

        when(shipmentRepository.findById(10L)).thenReturn(Optional.of(shipment));
        when(routeRepository.findFirstByShipmentIdOrderByUpdatedAtDescIdDesc(10L)).thenReturn(Optional.of(route));

        assertThatThrownBy(() -> forecastService.generateForecast(10L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("saved route with a valid estimated duration");

        verify(forecastRepository, never()).save(any(Forecast.class));
    }

    @Test
    void createsRouteBasedForecastWithConfidenceScore() {
        Shipment shipment = shipment(10L, ShipmentStatus.IN_TRANSIT);
        Route route = new Route();
        route.setEstimatedDurationMinutes(90);

        when(shipmentRepository.findById(10L)).thenReturn(Optional.of(shipment));
        when(routeRepository.findFirstByShipmentIdOrderByUpdatedAtDescIdDesc(10L)).thenReturn(Optional.of(route));
        when(forecastRepository.save(any(Forecast.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        LocalDateTime startedAt = LocalDateTime.now();
        Forecast forecast = forecastService.generateForecast(10L);

        assertThat(forecast.getPredictedDeliveryTime())
                .isBetween(startedAt.plusMinutes(89), startedAt.plusMinutes(91));
        assertThat(forecast.getConfidence()).isEqualTo(0.75);
        verify(forecastRepository).save(forecast);
    }

    private static Shipment shipment(Long id, ShipmentStatus status) {
        Shipment shipment = new Shipment();
        shipment.setId(id);
        shipment.setStatus(status);
        return shipment;
    }
}