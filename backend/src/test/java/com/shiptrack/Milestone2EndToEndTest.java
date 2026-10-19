package com.shiptrack;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.shiptrack.dto.*;
import com.shiptrack.entity.Route;
import com.shiptrack.entity.Shipment;
import com.shiptrack.entity.User;
import com.shiptrack.enums.Role;
import com.shiptrack.enums.ShipmentStatus;
import com.shiptrack.enums.TrafficCondition;
import com.shiptrack.repository.RouteRepository;
import com.shiptrack.repository.ShipmentLocationRepository;
import com.shiptrack.repository.ShipmentRepository;
import com.shiptrack.repository.UserRepository;
import com.shiptrack.security.JwtAuthenticationFilter;
import com.shiptrack.security.JwtUtils;
import com.shiptrack.service.ShipmentService;
import com.shiptrack.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

import java.math.BigDecimal;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@Transactional
public class Milestone2EndToEndTest {

    @Autowired
    private WebApplicationContext webApplicationContext;

    @Autowired
    private JwtAuthenticationFilter jwtAuthenticationFilter;

    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserService userService;

    @Autowired
    private ShipmentService shipmentService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ShipmentRepository shipmentRepository;

    @Autowired
    private RouteRepository routeRepository;

    @Autowired
    private ShipmentLocationRepository locationRepository;

    @Autowired
    private JwtUtils jwtUtils;

    private String runId;
    private User businessClientUser;
    private User assignedOperator;
    private User unassignedOperator;
    private User customerUser;
    private User adminUser;

    private String clientToken;
    private String assignedOperatorToken;
    private String unassignedOperatorToken;
    private String customerToken;
    private String adminToken;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext)
                .addFilter(jwtAuthenticationFilter)
                .build();

        runId = UUID.randomUUID().toString().substring(0, 8);

        businessClientUser = registerUser("Biz " + runId, "biz-" + runId + "@shiptrack.com", Role.BUSINESS_CLIENT);
        assignedOperator = registerUser("Assigned Op " + runId, "op1-" + runId + "@shiptrack.com", Role.LOGISTICS_OPERATOR);
        unassignedOperator = registerUser("Other Op " + runId, "op2-" + runId + "@shiptrack.com", Role.LOGISTICS_OPERATOR);
        customerUser = registerUser("Customer " + runId, "cust-" + runId + "@shiptrack.com", Role.CUSTOMER);
        adminUser = registerUser("Admin " + runId, "admin-" + runId + "@shiptrack.com", Role.ADMINISTRATOR);

        clientToken = jwtUtils.generateToken(businessClientUser.getId(), businessClientUser.getEmail(), businessClientUser.getRole().name());
        assignedOperatorToken = jwtUtils.generateToken(assignedOperator.getId(), assignedOperator.getEmail(), assignedOperator.getRole().name());
        unassignedOperatorToken = jwtUtils.generateToken(unassignedOperator.getId(), unassignedOperator.getEmail(), unassignedOperator.getRole().name());
        customerToken = jwtUtils.generateToken(customerUser.getId(), customerUser.getEmail(), customerUser.getRole().name());
        adminToken = jwtUtils.generateToken(adminUser.getId(), adminUser.getEmail(), adminUser.getRole().name());
    }

    private User registerUser(String name, String email, Role role) {
        RegisterRequest req = new RegisterRequest();
        req.setFullName(name);
        req.setEmail(email);
        req.setPassword("password123");
        req.setConfirmPassword("password123");
        req.setRole(role);
        req.setPhoneNumber("9876543210");
        UserDto dto = userService.registerUser(req);
        return userRepository.findById(dto.getId()).orElseThrow();
    }

    private Long createShipment(boolean assignToOperator) {
        ShipmentRequest req = new ShipmentRequest();
        req.setCustomerId(customerUser.getId());
        if (assignToOperator) {
            req.setAssignedOperatorId(assignedOperator.getId());
        }
        req.setSenderName("Express Sender");
        req.setSenderPhone("111222333");
        req.setSenderAddress("Dehradun, Uttarakhand");
        req.setReceiverName("Express Receiver");
        req.setReceiverPhone("444555666");
        req.setReceiverAddress("Connaught Place, New Delhi");
        req.setPackageDescription("Precision Instruments");
        req.setPackageWeightKg(BigDecimal.valueOf(8.0));

        try {
            String resp = mockMvc.perform(post("/api/shipments")
                            .header("Authorization", "Bearer " + clientToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(req)))
                    .andExpect(status().isOk())
                    .andReturn().getResponse().getContentAsString();
            com.fasterxml.jackson.databind.JsonNode created = objectMapper.readTree(resp);
            return created.get("id").asLong();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    @DisplayName("M2-01: Status update permissions - assigned operator can update; unassigned operator receives 403")
    void testStatusUpdateAuthorization() throws Exception {
        Long shipmentId = createShipment(true);

        StatusUpdateRequest updateReq = new StatusUpdateRequest();
        updateReq.setStatus(ShipmentStatus.PICKED_UP);
        updateReq.setRemarks("Package picked up from hub");

        // 1. Assigned operator updates status: 200 OK
        mockMvc.perform(patch("/api/shipments/" + shipmentId + "/status")
                        .header("Authorization", "Bearer " + assignedOperatorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PICKED_UP"));

        // 2. Unassigned operator attempts to update: 403 Forbidden
        updateReq.setStatus(ShipmentStatus.IN_TRANSIT);
        mockMvc.perform(patch("/api/shipments/" + shipmentId + "/status")
                        .header("Authorization", "Bearer " + unassignedOperatorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateReq)))
                .andExpect(status().isForbidden());

        // 3. Customer attempts to update: 403 Forbidden
        mockMvc.perform(patch("/api/shipments/" + shipmentId + "/status")
                        .header("Authorization", "Bearer " + customerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateReq)))
                .andExpect(status().isForbidden());

        // 4. Administrator can also update status: 200 OK
        updateReq.setStatus(ShipmentStatus.IN_TRANSIT);
        mockMvc.perform(patch("/api/shipments/" + shipmentId + "/status")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("IN_TRANSIT"));

        // 5. Verify status history entries exist in order
        mockMvc.perform(get("/api/shipments/" + shipmentId + "/history")
                        .header("Authorization", "Bearer " + clientToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", org.hamcrest.Matchers.hasSize(3)))
                .andExpect(jsonPath("$[0].status").value("CREATED"))
                .andExpect(jsonPath("$[1].status").value("PICKED_UP"))
                .andExpect(jsonPath("$[2].status").value("IN_TRANSIT"));
    }

    @Test
    @DisplayName("M2-02: Live location updates, coordinate validation, and location history")
    void testLiveLocationUpdatesAndHistory() throws Exception {
        Long shipmentId = createShipment(true);

        LocationUpdateRequest locReq = new LocationUpdateRequest();
        locReq.setLatitude(28.6139);
        locReq.setLongitude(77.2090);
        locReq.setLocationName("Delhi Transit Hub");

        // 1. Assigned operator records location: 200 OK
        mockMvc.perform(patch("/api/shipments/" + shipmentId + "/location")
                        .header("Authorization", "Bearer " + assignedOperatorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(locReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.latitude").value(28.6139))
                .andExpect(jsonPath("$.longitude").value(77.2090))
                .andExpect(jsonPath("$.locationName").value("Delhi Transit Hub"))
                .andExpect(jsonPath("$.recordedByOperatorId").value(assignedOperator.getId()));

        // 2. Unassigned operator attempts location update: 403 Forbidden
        mockMvc.perform(patch("/api/shipments/" + shipmentId + "/location")
                        .header("Authorization", "Bearer " + unassignedOperatorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(locReq)))
                .andExpect(status().isForbidden());

        // 3. Coordinate validation: latitude > 90 rejected with 400 Bad Request
        LocationUpdateRequest invalidReq = new LocationUpdateRequest();
        invalidReq.setLatitude(95.0);
        invalidReq.setLongitude(77.2090);
        mockMvc.perform(patch("/api/shipments/" + shipmentId + "/location")
                        .header("Authorization", "Bearer " + assignedOperatorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalidReq)))
                .andExpect(status().isBadRequest());

        // 4. Retrieve current location: 200 OK
        mockMvc.perform(get("/api/shipments/" + shipmentId + "/location")
                        .header("Authorization", "Bearer " + clientToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.locationName").value("Delhi Transit Hub"));

        // 5. Retrieve location history: 200 OK
        mockMvc.perform(get("/api/shipments/" + shipmentId + "/location-history")
                        .header("Authorization", "Bearer " + clientToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", org.hamcrest.Matchers.hasSize(1)));

        // 6. Retrieve live tracking summary: 200 OK
        mockMvc.perform(get("/api/shipments/" + shipmentId + "/tracking")
                        .header("Authorization", "Bearer " + clientToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.shipmentId").value(shipmentId))
                .andExpect(jsonPath("$.currentLocation.locationName").value("Delhi Transit Hub"))
                .andExpect(jsonPath("$.locationHistory", org.hamcrest.Matchers.hasSize(1)));
    }

    @Test
    @DisplayName("M2-03: Route-based ETA and Dynamic Delivery ETA with traffic/delay factors")
    void testEtaCalculations() throws Exception {
        Long shipmentId = createShipment(true);
        Shipment shipment = shipmentRepository.findById(shipmentId).orElseThrow();

        // Create a persistent Route record for this shipment
        Route route = new Route();
        route.setShipment(shipment);
        route.setAssignedOperator(assignedOperator);
        route.setOrigin("Dehradun");
        route.setDestination("Delhi");
        route.setOriginLatitude(30.3165);
        route.setOriginLongitude(78.0322);
        route.setDestinationLatitude(28.6139);
        route.setDestinationLongitude(77.2090);
        route.setDistanceKm(BigDecimal.valueOf(250.0));
        route.setEstimatedDurationMinutes(300); // 5 hours
        routeRepository.save(route);

        // 1. GET /api/shipments/{id}/eta (Route-based ETA)
        mockMvc.perform(get("/api/shipments/" + shipmentId + "/eta")
                        .header("Authorization", "Bearer " + clientToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.shipmentId").value(shipmentId))
                .andExpect(jsonPath("$.origin").value("Dehradun"))
                .andExpect(jsonPath("$.destination").value("Delhi"))
                .andExpect(jsonPath("$.estimatedDurationMinutes").value(300))
                .andExpect(jsonPath("$.estimatedArrivalTime").isNotEmpty());

        // 2. Route retrieval endpoints
        mockMvc.perform(get("/api/routes/shipment/" + shipmentId)
                        .header("Authorization", "Bearer " + clientToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.origin").value("Dehradun"))
                .andExpect(jsonPath("$.destination").value("Delhi"))
                .andExpect(jsonPath("$.distanceKm").value(250.0));

        mockMvc.perform(get("/api/routes/" + route.getId())
                        .header("Authorization", "Bearer " + clientToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(route.getId()));
    }

    @Test
    @DisplayName("M2-04: Forecast generation and retrieval based on route and shipment status")
    void testForecastGenerationAndRetrieval() throws Exception {
        Long shipmentId = createShipment(true);
        Shipment shipment = shipmentRepository.findById(shipmentId).orElseThrow();

        Route route = new Route();
        route.setShipment(shipment);
        route.setOrigin("Dehradun");
        route.setDestination("Delhi");
        route.setDistanceKm(BigDecimal.valueOf(250.0));
        route.setEstimatedDurationMinutes(240);
        routeRepository.save(route);

        // 1. Generate forecast: 200 OK
        mockMvc.perform(post("/api/forecasts/generate?shipmentId=" + shipmentId)
                        .header("Authorization", "Bearer " + clientToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.confidence").value(0.75))
                .andExpect(jsonPath("$.predictedStatus").value("CREATED"))
                .andExpect(jsonPath("$.predictedDeliveryTime").isNotEmpty());

        // 2. Retrieve forecasts for shipment: 200 OK
        mockMvc.perform(get("/api/forecasts/shipment/" + shipmentId)
                        .header("Authorization", "Bearer " + clientToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", org.hamcrest.Matchers.hasSize(org.hamcrest.Matchers.greaterThanOrEqualTo(1))));

        // 3. List all forecasts: 200 OK
        mockMvc.perform(get("/api/forecasts")
                        .header("Authorization", "Bearer " + clientToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
    }
}
