package com.shiptrack.service;

import com.shiptrack.entity.Forecast;
import com.shiptrack.entity.Route;
import com.shiptrack.entity.Shipment;
import com.shiptrack.dto.ShipmentResponse;
import com.shiptrack.enums.ShipmentStatus;
import com.shiptrack.exception.ResourceNotFoundException;
import com.shiptrack.repository.ForecastRepository;
import com.shiptrack.repository.RouteRepository;
import com.shiptrack.repository.ShipmentRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class ForecastService {

    private final ShipmentRepository shipmentRepository;
    private final RouteRepository routeRepository;
    private final ForecastRepository forecastRepository;
    private final ShipmentService shipmentService;

    public ForecastService(
            ShipmentRepository shipmentRepository,
            RouteRepository routeRepository,
            ForecastRepository forecastRepository,
            ShipmentService shipmentService) {
        this.shipmentRepository = shipmentRepository;
        this.routeRepository = routeRepository;
        this.forecastRepository = forecastRepository;
        this.shipmentService = shipmentService;
    }

    @Transactional
    public Forecast generateForecast(Long shipmentId) {

        shipmentService.getShipmentById(shipmentId);
        Shipment shipment = shipmentRepository.findById(shipmentId)
                .orElseThrow(() ->
                        new ResourceNotFoundException(
                                "Shipment not found with ID: " + shipmentId));

        Route route = routeRepository.findByShipmentId(shipmentId)
                .orElse(null);

        if (shipment.getStatus() == ShipmentStatus.DELIVERED
                || shipment.getStatus() == ShipmentStatus.CANCELLED
                || shipment.getStatus() == ShipmentStatus.FAILED_DELIVERY) {
            throw new IllegalArgumentException(
                    "A forecast can only be generated for an active shipment.");
        }

        if (route == null
                || route.getEstimatedDurationMinutes() == null
                || route.getEstimatedDurationMinutes() <= 0) {
            throw new IllegalArgumentException(
                    "A saved route with a valid estimated duration is required to generate a forecast.");
        }

        Forecast forecast = new Forecast();

        forecast.setShipment(shipment);
        forecast.setPredictedDeliveryTime(
                LocalDateTime.now().plusMinutes(route.getEstimatedDurationMinutes()));
        forecast.setPredictedStatus(shipment.getStatus());

        return forecastRepository.save(forecast);
    }

    public List<Forecast> getForecastsForShipment(Long shipmentId) {
        shipmentService.getShipmentById(shipmentId);
        return forecastRepository
                .findByShipmentIdOrderByCreatedAtDesc(shipmentId);
    }

    public List<Forecast> listAll() {
        List<Long> shipmentIds = shipmentService.getAllShipments().stream()
                .map(ShipmentResponse::getId)
                .toList();
        if (shipmentIds.isEmpty()) {
            return List.of();
        }
        return forecastRepository.findByShipmentIdInOrderByCreatedAtDesc(shipmentIds);
    }
}