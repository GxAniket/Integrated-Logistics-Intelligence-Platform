package com.shiptrack;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.shiptrack.dto.RegisterRequest;
import com.shiptrack.entity.Route;
import com.shiptrack.entity.Shipment;
import com.shiptrack.entity.ShipmentStatusHistory;
import com.shiptrack.entity.User;
import com.shiptrack.enums.Role;
import com.shiptrack.enums.ShipmentStatus;
import com.shiptrack.repository.ProofOfDeliveryRepository;
import com.shiptrack.repository.RouteRepository;
import com.shiptrack.repository.ShipmentRepository;
import com.shiptrack.repository.ShipmentStatusHistoryRepository;
import com.shiptrack.repository.UserRepository;
import com.shiptrack.security.JwtUtils;
import com.shiptrack.service.MapsService;
import com.shiptrack.service.UserService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ShipmentReportingApiIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ShipmentRepository shipmentRepository;

    @Autowired
    private RouteRepository routeRepository;

    @Autowired
    private ShipmentStatusHistoryRepository historyRepository;

    @Autowired
    private ProofOfDeliveryRepository proofOfDeliveryRepository;

    @Autowired
    private JwtUtils jwtUtils;

    @Autowired
    private UserService userService;

    @MockitoBean
    private MapsService mapsService;

    @Test
    void protectedEndpointsRequireAuthentication() throws Exception {
        mockMvc.perform(get("/api/shipments"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void invalidLoginCredentialsReturnUnauthorized() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"missing-user@example.invalid","password":"invalid"}
                                """))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Invalid email or password."));
    }

    @Test
    void administratorCanLogInAndAccessAdminDashboardApisWhileOtherRolesCannot()
            throws Exception {
        String email = "bootstrapped-admin-" + UUID.randomUUID() + "@shiptrack.test";
        String password = "secure-admin-password";
        RegisterRequest adminRequest = new RegisterRequest();
        adminRequest.setFullName("Bootstrapped Administrator");
        adminRequest.setEmail(email);
        adminRequest.setPassword(password);
        adminRequest.setRole(Role.ADMINISTRATOR);
        userService.createPrivilegedUser(adminRequest);

        JsonNode login = objectMapper.readTree(
                mockMvc.perform(post("/api/auth/login")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(Map.of(
                                        "email", email,
                                        "password", password))))
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$.role").value(Role.ADMINISTRATOR.name()))
                        .andReturn().getResponse().getContentAsString());
        String administratorToken = "Bearer " + login.get("token").asText();

        mockMvc.perform(get("/api/users")
                        .header("Authorization", administratorToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].role").exists());

        mockMvc.perform(get("/api/analytics")
                        .header("Authorization", administratorToken))
                .andExpect(status().isOk());

        ApiUser businessClient = registerAndLogin(
                "admin-dashboard-access-business-client",
                Role.BUSINESS_CLIENT);
        mockMvc.perform(get("/api/users")
                        .header("Authorization", businessClient.bearerToken()))
                .andExpect(status().isForbidden());
    }

    @Test
    void registrationLoginShipmentAssignmentUpdateLifecycleAndHistoryWorkEndToEnd()
            throws Exception {
        ApiUser businessClient = registerAndLogin("e2e-business", Role.BUSINESS_CLIENT);
        ApiUser operator = registerAndLogin("e2e-operator", Role.LOGISTICS_OPERATOR);

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "fullName", "Unauthorized Admin",
                                "email", "e2e-admin-" + UUID.randomUUID() + "@shiptrack.test",
                                "password", "test-password",
                                "role", Role.ADMINISTRATOR.name()))))
                .andExpect(status().isBadRequest());

        Map<String, Object> shipmentRequest = shipmentRequest(
                operator.id(), "E2E original origin");
        JsonNode created = objectMapper.readTree(mockMvc.perform(post("/api/shipments")
                        .header("Authorization", businessClient.bearerToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(shipmentRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(ShipmentStatus.CREATED.name()))
                .andReturn().getResponse().getContentAsString());
        long shipmentId = created.get("id").asLong();

        mockMvc.perform(get("/api/shipments")
                        .header("Authorization", operator.bearerToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(shipmentId))
                .andExpect(jsonPath("$[0].assignedOperatorId").value(operator.id()));

        mockMvc.perform(get("/api/shipments")
                        .header("Authorization", businessClient.bearerToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].trackingNumber")
                        .value(created.get("trackingNumber").asText()));

        Map<String, Object> editRequest = shipmentRequest(
                operator.id(), "E2E updated origin");
        mockMvc.perform(put("/api/shipments/{id}", shipmentId)
                        .header("Authorization", businessClient.bearerToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(editRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.senderAddress").value("E2E updated origin"));

        for (ShipmentStatus nextStatus : List.of(
                ShipmentStatus.PICKED_UP,
                ShipmentStatus.IN_TRANSIT,
                ShipmentStatus.OUT_FOR_DELIVERY,
                ShipmentStatus.DELIVERED)) {
            mockMvc.perform(patch("/api/shipments/{id}/status", shipmentId)
                            .header("Authorization", operator.bearerToken())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(Map.of(
                                    "status", nextStatus.name(),
                                    "remarks", "End-to-end " + nextStatus.name()))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value(nextStatus.name()));
        }

        mockMvc.perform(get("/api/shipments/{id}/history", shipmentId)
                        .header("Authorization", businessClient.bearerToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(5))
                .andExpect(jsonPath("$[0].status").value(ShipmentStatus.CREATED.name()))
                .andExpect(jsonPath("$[4].status").value(ShipmentStatus.DELIVERED.name()));

        mockMvc.perform(patch("/api/shipments/{id}/status", shipmentId)
                        .header("Authorization", operator.bearerToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"status":"CREATED","remarks":"Invalid backward transition"}
                                """))
                .andExpect(status().isBadRequest());

        mockMvc.perform(patch("/api/shipments/{id}/cancel", shipmentId)
                        .header("Authorization", businessClient.bearerToken()))
                .andExpect(status().isBadRequest());

        User admin = user("e2e-admin", Role.ADMINISTRATOR);
        String adminToken = "Bearer " + jwtUtils.generateToken(
                admin.getId(), admin.getEmail(), admin.getRole().name());
        mockMvc.perform(post("/api/users/privileged")
                        .header("Authorization", adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "fullName", "E2E Support Agent",
                                "email", "e2e-support-" + UUID.randomUUID() + "@shiptrack.test",
                                "password", "test-password",
                                "role", Role.SUPPORT_AGENT.name()))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.role").value(Role.SUPPORT_AGENT.name()));
    }

    @Test
    void allRolesCanUseTheirPersistedShipmentAndTrackingWorkflowsEndToEnd()
            throws Exception {
        ApiUser businessClient = registerAndLogin("role-matrix-business", Role.BUSINESS_CLIENT);
        ApiUser operator = registerAndLogin("role-matrix-operator", Role.LOGISTICS_OPERATOR);
        ApiUser customer = registerAndLogin("role-matrix-customer", Role.CUSTOMER);
        User adminUser = user("role-matrix-admin", Role.ADMINISTRATOR);
        User supportUser = user("role-matrix-support", Role.SUPPORT_AGENT);

        String adminToken = "Bearer " + jwtUtils.generateToken(
                adminUser.getId(), adminUser.getEmail(), adminUser.getRole().name());
        String supportToken = "Bearer " + jwtUtils.generateToken(
                supportUser.getId(), supportUser.getEmail(), supportUser.getRole().name());

        Map<String, Object> request = new java.util.HashMap<>(
                shipmentRequest(operator.id(), "Role matrix origin"));
        request.put("customerId", customer.id());

        JsonNode created = objectMapper.readTree(mockMvc.perform(post("/api/shipments")
                        .header("Authorization", businessClient.bearerToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(ShipmentStatus.CREATED.name()))
                .andExpect(jsonPath("$.customerId").value(customer.id()))
                .andExpect(jsonPath("$.assignedOperatorId").value(operator.id()))
                .andReturn().getResponse().getContentAsString());
        long shipmentId = created.get("id").asLong();
        String trackingNumber = created.get("trackingNumber").asText();

        when(mapsService.geocode("Role matrix origin"))
                .thenReturn(new MapsService.Coordinates(17.3850, 78.4867));
        when(mapsService.geocode("E2E destination"))
                .thenReturn(new MapsService.Coordinates(17.3860, 78.4875));
        when(mapsService.route(any(MapsService.Coordinates.class),
                any(MapsService.Coordinates.class)))
                .thenReturn(new MapsService.RouteData(
                        1200,
                        90,
                        "{\"type\":\"LineString\",\"coordinates\":[[78.4867,17.385],[78.4875,17.386]]}"));

        mockMvc.perform(post("/api/routes/shipment/{shipmentId}/calculate", shipmentId)
                        .header("Authorization", businessClient.bearerToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.shipmentId").value(shipmentId))
                .andExpect(jsonPath("$.estimatedDurationMinutes").value(2));

        mockMvc.perform(post("/api/forecasts/generate")
                        .param("shipmentId", String.valueOf(shipmentId))
                        .header("Authorization", businessClient.bearerToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.shipment.id").value(shipmentId))
                .andExpect(jsonPath("$.predictedDeliveryTime").isNotEmpty())
                .andExpect(jsonPath("$.confidence").value(0.75));

        mockMvc.perform(patch("/api/shipments/{id}/location", shipmentId)
                        .header("Authorization", operator.bearerToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"latitude":17.385,"longitude":78.4867,"locationName":"Test origin"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.latitude").value(17.385))
                .andExpect(jsonPath("$.longitude").value(78.4867));

        for (ShipmentStatus nextStatus : List.of(
                ShipmentStatus.PICKED_UP,
                ShipmentStatus.IN_TRANSIT,
                ShipmentStatus.OUT_FOR_DELIVERY)) {
            mockMvc.perform(patch("/api/shipments/{id}/status", shipmentId)
                            .header("Authorization", operator.bearerToken())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(Map.of(
                                    "status", nextStatus.name(),
                                    "remarks", "Role matrix " + nextStatus.name()))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value(nextStatus.name()));
        }

        String[] roleTokens = {
                businessClient.bearerToken(),
                customer.bearerToken(),
                operator.bearerToken(),
                supportToken,
                adminToken
        };

        for (String roleToken : roleTokens) {
            mockMvc.perform(get("/api/users/me")
                            .header("Authorization", roleToken))
                    .andExpect(status().isOk());
            mockMvc.perform(get("/api/shipments")
                            .header("Authorization", roleToken))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[0].trackingNumber").value(trackingNumber));
            mockMvc.perform(get("/api/shipments/track/{trackingNumber}", trackingNumber)
                            .header("Authorization", roleToken))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(shipmentId));
            mockMvc.perform(get("/api/shipments/{id}/tracking", shipmentId)
                            .header("Authorization", roleToken))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.currentLocation.latitude").value(17.385));
            mockMvc.perform(get("/api/shipments/{id}/location-history", shipmentId)
                            .header("Authorization", roleToken))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.length()").value(1));
            mockMvc.perform(get("/api/shipments/{id}/history", shipmentId)
                            .header("Authorization", roleToken))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.length()").value(4));
            mockMvc.perform(get("/api/routes/shipment/{shipmentId}", shipmentId)
                            .header("Authorization", roleToken))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.estimatedDurationMinutes").value(2));
            mockMvc.perform(get("/api/forecasts/shipment/{shipmentId}", shipmentId)
                            .header("Authorization", roleToken))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.length()").value(1));
            mockMvc.perform(get("/api/forecasts")
                            .header("Authorization", roleToken))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.length()").value(1));
            mockMvc.perform(get("/api/analytics")
                            .header("Authorization", roleToken))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.totalShipments").value(1))
                    .andExpect(jsonPath("$.inTransitShipments").value(1));
            mockMvc.perform(get("/api/reports/performance")
                            .header("Authorization", roleToken))
                    .andExpect(status().isOk());
            mockMvc.perform(get("/api/notifications")
                            .header("Authorization", roleToken))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.length()").value(org.hamcrest.Matchers.greaterThan(0)));
        }

        mockMvc.perform(post("/api/shipments/{id}/eta", shipmentId)
                        .header("Authorization", operator.bearerToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"destination":{"latitude":17.386,"longitude":78.4875}}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.shipmentId").value(shipmentId))
                .andExpect(jsonPath("$.expectedCompletionTime").isNotEmpty());

        mockMvc.perform(post("/api/shipments/{id}/pod", shipmentId)
                        .header("Authorization", operator.bearerToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                podRequest(validPngDataUrl()))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.deliveryStatus").value("DELIVERED"));

        mockMvc.perform(get("/api/shipments/{id}/pod", shipmentId)
                        .header("Authorization", businessClient.bearerToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.shipmentId").value(shipmentId));
    }

    @Test
    void analyticsReportsAndPodConfirmationUsePersistedShipmentData() throws Exception {
        User businessClient = user("analytics-business", Role.BUSINESS_CLIENT);
        User operator = user("analytics-operator", Role.LOGISTICS_OPERATOR);
        User admin = user("analytics-admin", Role.ADMINISTRATOR);
        String bearerToken = "Bearer " + jwtUtils.generateToken(
                operator.getId(), operator.getEmail(), operator.getRole().name());
        String adminToken = "Bearer " + jwtUtils.generateToken(
                admin.getId(), admin.getEmail(), admin.getRole().name());

        mockMvc.perform(get("/api/users")
                        .header("Authorization", adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").exists());

        mockMvc.perform(get("/api/users")
                        .header("Authorization", bearerToken))
                .andExpect(status().isForbidden());

        Shipment activeShipment = shipment(
                businessClient, operator, ShipmentStatus.OUT_FOR_DELIVERY);
        Shipment deliveredShipment = shipment(
                businessClient, operator, ShipmentStatus.DELIVERED);
        history(deliveredShipment, operator, ShipmentStatus.DELIVERED);
        shipmentRepository.flush();

        String date = LocalDate.now().toString();
        mockMvc.perform(get("/api/analytics")
                        .param("from", date)
                        .param("to", date)
                        .header("Authorization", bearerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalShipments").value(2))
                .andExpect(jsonPath("$.deliveredShipments").value(1))
                .andExpect(jsonPath("$.inTransitShipments").value(1));

        mockMvc.perform(get("/api/reports/performance")
                        .param("from", date)
                        .param("to", date)
                        .header("Authorization", bearerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.completedDeliveries").value(1))
                .andExpect(jsonPath("$.onTimeDeliveries").value(1))
                .andExpect(jsonPath("$.records[0].trackingNumber")
                        .value(deliveredShipment.getTrackingNumber()));

        mockMvc.perform(get("/api/analytics")
                        .param("from", "2026-10-08")
                        .param("to", "2026-10-07")
                        .header("Authorization", bearerToken))
                .andExpect(status().isBadRequest());

        Map<String, Object> invalidPod = podRequest("data:image/png;base64,invalid");
        mockMvc.perform(post("/api/shipments/{id}/pod", activeShipment.getId())
                        .header("Authorization", bearerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalidPod)))
                .andExpect(status().isBadRequest());
        org.junit.jupiter.api.Assertions.assertEquals(
                ShipmentStatus.OUT_FOR_DELIVERY,
                shipmentRepository.findById(activeShipment.getId()).orElseThrow().getStatus());

        mockMvc.perform(post("/api/shipments/{id}/pod", activeShipment.getId())
                        .header("Authorization", bearerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                podRequest(validPngDataUrl()))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.deliveryStatus").value("DELIVERED"))
                .andExpect(jsonPath("$.signatureVerified").value(true))
                .andExpect(jsonPath("$.trackingNumber")
                        .value(activeShipment.getTrackingNumber()));

        org.junit.jupiter.api.Assertions.assertEquals(
                ShipmentStatus.DELIVERED,
                shipmentRepository.findById(activeShipment.getId()).orElseThrow().getStatus());
        org.junit.jupiter.api.Assertions.assertTrue(
                proofOfDeliveryRepository.existsByShipmentId(activeShipment.getId()));
    }

    @Test
    void assignedOperatorCanReadPersistedRouteGeometryOnlyForAssignedShipment()
            throws Exception {
        User businessClient = user("route-business", Role.BUSINESS_CLIENT);
        User assignedOperator = user("route-assigned-operator", Role.LOGISTICS_OPERATOR);
        User otherOperator = user("route-other-operator", Role.LOGISTICS_OPERATOR);
        Shipment assignedShipment = shipment(
                businessClient, assignedOperator, ShipmentStatus.IN_TRANSIT);
        Shipment otherShipment = shipment(
                businessClient, otherOperator, ShipmentStatus.IN_TRANSIT);
        Shipment withoutRoute = shipment(
                businessClient, assignedOperator, ShipmentStatus.CREATED);
        String persistedGeometry =
                "{\"type\":\"LineString\",\"coordinates\":[[78.4867,17.3850],[78.4875,17.3860]]}";
        routeRepository.saveAndFlush(route(assignedShipment, assignedOperator, persistedGeometry));
        routeRepository.saveAndFlush(route(otherShipment, otherOperator, persistedGeometry));

        String assignedToken = "Bearer " + jwtUtils.generateToken(
                assignedOperator.getId(),
                assignedOperator.getEmail(),
                assignedOperator.getRole().name());
        String otherToken = "Bearer " + jwtUtils.generateToken(
                otherOperator.getId(),
                otherOperator.getEmail(),
                otherOperator.getRole().name());

        mockMvc.perform(get("/api/routes/shipment/{shipmentId}", assignedShipment.getId())
                        .header("Authorization", assignedToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.shipmentId").value(assignedShipment.getId()))
                .andExpect(jsonPath("$.geometry").value(persistedGeometry));

        mockMvc.perform(get("/api/routes/shipment/{shipmentId}", assignedShipment.getId())
                        .header("Authorization", otherToken))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/routes/shipment/{shipmentId}", withoutRoute.getId())
                        .header("Authorization", assignedToken))
                .andExpect(status().isNotFound());
    }

    @Test
    void assignedOperatorCanGenerateAndPersistOsrmRouteForActiveShipment()
            throws Exception {
        User businessClient = user("route-generation-business", Role.BUSINESS_CLIENT);
        User assignedOperator = user("route-generation-operator", Role.LOGISTICS_OPERATOR);
        User otherOperator = user("route-generation-other", Role.LOGISTICS_OPERATOR);
        Shipment activeShipment = shipment(
                businessClient, assignedOperator, ShipmentStatus.IN_TRANSIT);
        Shipment unassignedShipment = shipment(
                businessClient, otherOperator, ShipmentStatus.IN_TRANSIT);
        Shipment inactiveShipment = shipment(
                businessClient, assignedOperator, ShipmentStatus.CREATED);
        String persistedGeometry =
                "{\"type\":\"LineString\",\"coordinates\":[[78.4867,17.3850],[78.4875,17.3860]]}";
        when(mapsService.geocode("Test origin"))
                .thenReturn(new MapsService.Coordinates(17.3850, 78.4867));
        when(mapsService.geocode("Test destination"))
                .thenReturn(new MapsService.Coordinates(17.3860, 78.4875));
        when(mapsService.route(
                new MapsService.Coordinates(17.3850, 78.4867),
                new MapsService.Coordinates(17.3860, 78.4875)))
                .thenReturn(new MapsService.RouteData(1200, 90, persistedGeometry));

        String assignedToken = "Bearer " + jwtUtils.generateToken(
                assignedOperator.getId(),
                assignedOperator.getEmail(),
                assignedOperator.getRole().name());

        mockMvc.perform(post("/api/routes/shipment/{shipmentId}/calculate",
                        activeShipment.getId())
                        .header("Authorization", assignedToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.shipmentId").value(activeShipment.getId()))
                .andExpect(jsonPath("$.geometry").value(persistedGeometry));

        org.junit.jupiter.api.Assertions.assertEquals(
                persistedGeometry,
                routeRepository.findFirstByShipmentIdOrderByUpdatedAtDescIdDesc(activeShipment.getId())
                        .orElseThrow()
                        .getGeometry());

        mockMvc.perform(post("/api/routes/shipment/{shipmentId}/calculate",
                        unassignedShipment.getId())
                        .header("Authorization", assignedToken))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/routes/shipment/{shipmentId}/calculate",
                        inactiveShipment.getId())
                        .header("Authorization", assignedToken))
                .andExpect(status().isForbidden());
    }

    private User user(String prefix, Role role) {
        User user = new User();
        user.setFullName(prefix);
        user.setEmail(prefix + "-" + UUID.randomUUID() + "@shiptrack.test");
        user.setPasswordHash("test-password-hash");
        user.setRole(role);
        return userRepository.saveAndFlush(user);
    }

    private ApiUser registerAndLogin(String prefix, Role role) throws Exception {
        String email = prefix + "-" + UUID.randomUUID() + "@shiptrack.test";
        String password = "test-password";
        String registration = objectMapper.writeValueAsString(Map.of(
                "fullName", prefix,
                "email", email,
                "password", password,
                "confirmPassword", password,
                "role", role.name()));

        JsonNode registered = objectMapper.readTree(mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registration))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());

        JsonNode login = objectMapper.readTree(mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "email", email,
                                "password", password))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value(role.name()))
                .andReturn().getResponse().getContentAsString());

        return new ApiUser(
                registered.get("id").asLong(),
                "Bearer " + login.get("token").asText());
    }

    private Map<String, Object> shipmentRequest(long operatorId, String origin) {
        return Map.of(
                "referenceId", "E2E-" + UUID.randomUUID(),
                "senderName", "E2E Sender",
                "senderPhone", "1111111111",
                "senderAddress", origin,
                "receiverName", "E2E Receiver",
                "receiverPhone", "2222222222",
                "receiverAddress", "E2E destination",
                "packageDescription", "Test parcel",
                "packageWeightKg", 2.5,
                "assignedOperatorId", operatorId);
    }

    private record ApiUser(long id, String bearerToken) {}

    private Shipment shipment(
            User businessClient,
            User operator,
            ShipmentStatus status) {
        Shipment shipment = new Shipment();
        shipment.setTrackingNumber("STP-TEST-" + UUID.randomUUID().toString().replace("-", "").substring(0, 12));
        shipment.setBusinessClient(businessClient);
        shipment.setAssignedOperator(operator);
        shipment.setSenderName("Test Sender");
        shipment.setSenderPhone("1111111111");
        shipment.setSenderAddress("Test origin");
        shipment.setReceiverName("Test Receiver");
        shipment.setReceiverPhone("2222222222");
        shipment.setReceiverAddress("Test destination");
        shipment.setStatus(status);
        return shipmentRepository.saveAndFlush(shipment);
    }

    private Route route(
            Shipment shipment,
            User operator,
            String geometry) {
        Route route = new Route();
        route.setShipment(shipment);
        route.setAssignedOperator(operator);
        route.setOrigin(shipment.getSenderAddress());
        route.setDestination(shipment.getReceiverAddress());
        route.setOriginLatitude(17.3850);
        route.setOriginLongitude(78.4867);
        route.setDestinationLatitude(17.3860);
        route.setDestinationLongitude(78.4875);
        route.setGeometry(geometry);
        return route;
    }

    private void history(
            Shipment shipment,
            User operator,
            ShipmentStatus status) {
        ShipmentStatusHistory history = new ShipmentStatusHistory();
        history.setShipment(shipment);
        history.setUpdatedBy(operator);
        history.setStatus(status);
        history.setRemarks("Integration test delivery record");
        history.setCreatedAt(LocalDateTime.now());
        historyRepository.saveAndFlush(history);
    }

    private Map<String, Object> podRequest(String signature) {
        return Map.of(
                "deliveredAt", LocalDateTime.now().minusMinutes(1).toString(),
                "deliveryStatus", "DELIVERED",
                "signature", signature,
                "remarks", "Integration test confirmation");
    }

    private String validPngDataUrl() throws Exception {
        BufferedImage image = new BufferedImage(8, 8, BufferedImage.TYPE_INT_ARGB);
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ImageIO.write(image, "png", output);
        return "data:image/png;base64," + Base64.getEncoder().encodeToString(output.toByteArray());
    }
}
