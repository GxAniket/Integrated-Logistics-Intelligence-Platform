package com.shiptrack;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.shiptrack.dto.*;
import com.shiptrack.entity.ProofOfDelivery;
import com.shiptrack.entity.Route;
import com.shiptrack.entity.Shipment;
import com.shiptrack.entity.User;
import com.shiptrack.enums.Role;
import com.shiptrack.enums.ShipmentStatus;
import com.shiptrack.repository.ProofOfDeliveryRepository;
import com.shiptrack.repository.RouteRepository;
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

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@Transactional
public class Milestone3EndToEndTest {

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
    private ProofOfDeliveryRepository podRepository;

    @Autowired
    private JwtUtils jwtUtils;

    private String runId;
    private User businessClientUser;
    private User otherBusinessClientUser;
    private User assignedOperator;
    private User unassignedOperator;
    private User customerUser;
    private User adminUser;

    private String clientToken;
    private String otherClientToken;
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

        businessClientUser = registerUser("Biz " + runId, "biz3-" + runId + "@shiptrack.com", Role.BUSINESS_CLIENT);
        otherBusinessClientUser = registerUser("Other Biz " + runId, "otherbiz3-" + runId + "@shiptrack.com", Role.BUSINESS_CLIENT);
        assignedOperator = registerUser("Op Assigned " + runId, "opassigned3-" + runId + "@shiptrack.com", Role.LOGISTICS_OPERATOR);
        unassignedOperator = registerUser("Op Other " + runId, "opother3-" + runId + "@shiptrack.com", Role.LOGISTICS_OPERATOR);
        customerUser = registerUser("Customer " + runId, "cust3-" + runId + "@shiptrack.com", Role.CUSTOMER);
        adminUser = registerUser("Admin " + runId, "admin3-" + runId + "@shiptrack.com", Role.ADMINISTRATOR);

        clientToken = jwtUtils.generateToken(businessClientUser.getId(), businessClientUser.getEmail(), businessClientUser.getRole().name());
        otherClientToken = jwtUtils.generateToken(otherBusinessClientUser.getId(), otherBusinessClientUser.getEmail(), otherBusinessClientUser.getRole().name());
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
        req.setPhoneNumber("9123456780");
        UserDto dto = role == Role.ADMINISTRATOR || role == Role.SUPPORT_AGENT
                ? userService.createPrivilegedUser(req)
                : userService.registerUser(req);
        return userRepository.findById(dto.getId()).orElseThrow();
    }

    private Long createShipment(ShipmentStatus initialStatus) {
        ShipmentRequest req = new ShipmentRequest();
        req.setCustomerId(customerUser.getId());
        req.setAssignedOperatorId(assignedOperator.getId());
        req.setSenderName("Consignor Pvt Ltd");
        req.setSenderPhone("111222333");
        req.setSenderAddress("Sector 18, Gurugram, Haryana");
        req.setReceiverName("Consignee Stores");
        req.setReceiverPhone("444555666");
        req.setReceiverAddress("Connaught Place, New Delhi");
        req.setPackageDescription("Medical Equipment");
        req.setPackageWeightKg(BigDecimal.valueOf(15.0));

        try {
            String resp = mockMvc.perform(post("/api/shipments")
                            .header("Authorization", "Bearer " + clientToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(req)))
                    .andExpect(status().isOk())
                    .andReturn().getResponse().getContentAsString();
            com.fasterxml.jackson.databind.JsonNode created = objectMapper.readTree(resp);
            Long createdId = created.get("id").asLong();

            if (initialStatus != ShipmentStatus.CREATED) {
                for (ShipmentStatus nextStatus : new ShipmentStatus[]{
                        ShipmentStatus.PICKED_UP,
                        ShipmentStatus.IN_TRANSIT,
                        ShipmentStatus.OUT_FOR_DELIVERY,
                        ShipmentStatus.DELIVERED}) {
                    StatusUpdateRequest statusReq = new StatusUpdateRequest();
                    statusReq.setStatus(nextStatus);
                    mockMvc.perform(patch("/api/shipments/" + createdId + "/status")
                                    .header("Authorization", "Bearer " + assignedOperatorToken)
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(objectMapper.writeValueAsString(statusReq)))
                            .andExpect(status().isOk());
                    if (nextStatus == initialStatus) {
                        break;
                    }
                }
            }

            return createdId;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    @DisplayName("M3-01: Digital delivery confirmation & POD creation with delivery status consistency")
    void testProofOfDeliveryCreationAndStatusConsistency() throws Exception {
        Long shipmentId = createShipment(ShipmentStatus.OUT_FOR_DELIVERY);

        ProofOfDeliveryRequest podReq = new ProofOfDeliveryRequest();
        podReq.setDeliveryStatus(ShipmentStatus.DELIVERED);
        podReq.setSignature(validPngDataUrl());
        podReq.setRemarks("Proof of delivery confirmed: Delivered to store manager Rahul");

        // 1. Assigned operator submits POD: 201 Created
        mockMvc.perform(post("/api/shipments/" + shipmentId + "/pod")
                        .header("Authorization", "Bearer " + assignedOperatorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(podReq)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.shipmentId").value(shipmentId))
                .andExpect(jsonPath("$.deliveryStatus").value("DELIVERED"))
                .andExpect(jsonPath("$.signature").value(podReq.getSignature()))
                .andExpect(jsonPath("$.deliveredByUserId").value(assignedOperator.getId()));

        // 2. Verify Delivery Status Consistency: Shipment status in DB is now DELIVERED
        Shipment updatedShipment = shipmentRepository.findById(shipmentId).orElseThrow();
        assertEquals(ShipmentStatus.DELIVERED, updatedShipment.getStatus());

        // 3. Verify Status History Consistency: Delivered entry exists
        mockMvc.perform(get("/api/shipments/" + shipmentId + "/history")
                        .header("Authorization", "Bearer " + clientToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[-1].status").value("DELIVERED"))
                .andExpect(jsonPath("$[-1].remarks").value(org.hamcrest.Matchers.containsString("Proof of delivery confirmed")));

        // 4. Duplicate POD creation rejected: 400 Bad Request
        mockMvc.perform(post("/api/shipments/" + shipmentId + "/pod")
                        .header("Authorization", "Bearer " + assignedOperatorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(podReq)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("M3-02: POD creation authorization and signature validation checks")
    void testPodAuthorizationAndSignatureValidation() throws Exception {
        Long shipmentId = createShipment(ShipmentStatus.OUT_FOR_DELIVERY);

        ProofOfDeliveryRequest podReq = new ProofOfDeliveryRequest();
        podReq.setSignature("ValidSignatureSample");

        // 1. Unassigned operator cannot create POD: 403 Forbidden
        mockMvc.perform(post("/api/shipments/" + shipmentId + "/pod")
                        .header("Authorization", "Bearer " + unassignedOperatorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(podReq)))
                .andExpect(status().isForbidden());

        // 2. Customer cannot create POD: 403 Forbidden
        mockMvc.perform(post("/api/shipments/" + shipmentId + "/pod")
                        .header("Authorization", "Bearer " + customerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(podReq)))
                .andExpect(status().isForbidden());

        // 3. Blank or null signature rejected: 400 Bad Request
        ProofOfDeliveryRequest emptySigReq = new ProofOfDeliveryRequest();
        emptySigReq.setSignature("   ");
        mockMvc.perform(post("/api/shipments/" + shipmentId + "/pod")
                        .header("Authorization", "Bearer " + assignedOperatorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(emptySigReq)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("M3-03: POD retrieval and ownership visibility rules")
    void testPodRetrievalVisibility() throws Exception {
        Long shipmentId = createShipment(ShipmentStatus.DELIVERED);

        ProofOfDeliveryRequest podReq = new ProofOfDeliveryRequest();
        podReq.setSignature(validPngDataUrl());
        podReq.setDeliveryStatus(ShipmentStatus.DELIVERED);
        podReq.setRemarks("Received in good order");

        mockMvc.perform(post("/api/shipments/" + shipmentId + "/pod")
                        .header("Authorization", "Bearer " + assignedOperatorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(podReq)))
                .andExpect(status().isCreated());

        // 1. Business client owner can view POD: 200 OK
        mockMvc.perform(get("/api/shipments/" + shipmentId + "/pod")
                        .header("Authorization", "Bearer " + clientToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.signature").value(validPngDataUrl()));

        // 2. Assigned customer can view POD: 200 OK
        mockMvc.perform(get("/api/shipments/" + shipmentId + "/pod")
                        .header("Authorization", "Bearer " + customerToken))
                .andExpect(status().isOk());

        // 3. Other business client CANNOT view POD: 403 Forbidden
        mockMvc.perform(get("/api/shipments/" + shipmentId + "/pod")
                        .header("Authorization", "Bearer " + otherClientToken))
                .andExpect(status().isForbidden());

        // 4. Administrator can view POD: 200 OK
        mockMvc.perform(get("/api/shipments/" + shipmentId + "/pod")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("M3-04: Shipment Analytics and Delivery Performance Reports")
    void testShipmentAnalyticsAndDeliveryPerformance() throws Exception {
        Long shipmentId = createShipment(ShipmentStatus.OUT_FOR_DELIVERY);

        // 1. GET /api/analytics/shipments: 200 OK
        mockMvc.perform(get("/api/analytics/shipments")
                        .header("Authorization", "Bearer " + clientToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalShipments").value(org.hamcrest.Matchers.greaterThanOrEqualTo(1)))
                .andExpect(jsonPath("$.statusBreakdown").isMap());

        // 2. GET /api/analytics/delivery-performance: 200 OK
        mockMvc.perform(get("/api/analytics/delivery-performance")
                        .header("Authorization", "Bearer " + clientToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalShipments").isNumber())
                .andExpect(jsonPath("$.deliveries").isArray());
    }

    @Test
    @DisplayName("M3-05: Existing database state verification (Shipment 14, POD 1, Route 1, Location 1)")
    void testExistingDatabaseStateIntegrity() throws Exception {
        // Verify Shipment 14 from database
        Optional<Shipment> ship14Opt = shipmentRepository.findById(14L);
        if (ship14Opt.isPresent()) {
            Shipment ship14 = ship14Opt.get();
            assertEquals("STP-2026-00006", ship14.getTrackingNumber());
            assertEquals(ShipmentStatus.PICKED_UP, ship14.getStatus());
            assertNotNull(ship14.getAssignedOperator());
            assertEquals(59L, ship14.getAssignedOperator().getId());
            assertEquals(32L, ship14.getBusinessClient().getId());
        }

        // Verify existing ProofOfDelivery ID 1
        Optional<ProofOfDelivery> podOpt = podRepository.findById(1L);
        if (podOpt.isPresent()) {
            ProofOfDelivery pod = podOpt.get();
            assertEquals(10L, pod.getShipment().getId());
            assertEquals(59L, pod.getDeliveredBy().getId());
            assertEquals(ShipmentStatus.DELIVERED, pod.getDeliveryStatus());
            assertEquals("16936", pod.getSignature());
        }

        // Verify existing Route ID 1
        Optional<Route> routeOpt = routeRepository.findById(1L);
        if (routeOpt.isPresent()) {
            Route route = routeOpt.get();
            assertEquals(10L, route.getShipment().getId());
            assertEquals("Dehradun", route.getOrigin());
            assertEquals("Delhi", route.getDestination());
        }
    }

    private String validPngDataUrl() throws IOException {
        BufferedImage image = new BufferedImage(4, 4, BufferedImage.TYPE_INT_ARGB);
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ImageIO.write(image, "png", output);
        return "data:image/png;base64," + Base64.getEncoder().encodeToString(output.toByteArray());
    }
}
