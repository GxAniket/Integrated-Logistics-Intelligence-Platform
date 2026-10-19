package com.shiptrack;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.shiptrack.dto.*;
import com.shiptrack.entity.Shipment;
import com.shiptrack.entity.User;
import com.shiptrack.enums.Role;
import com.shiptrack.enums.ShipmentStatus;
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
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@Transactional
public class Milestone1EndToEndTest {

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
    private JwtUtils jwtUtils;

    private String runId;
    private User customerUser;
    private User businessClientUser;
    private User otherBusinessClientUser;
    private User operatorUser;
    private User supportUser;
    private User adminUser;

    private String customerToken;
    private String businessClientToken;
    private String otherBusinessClientToken;
    private String operatorToken;
    private String supportToken;
    private String adminToken;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext)
                .addFilter(jwtAuthenticationFilter)
                .build();

        runId = UUID.randomUUID().toString().substring(0, 8);

        customerUser = registerTestUser("Customer " + runId, "customer-" + runId + "@shiptrack.com", Role.CUSTOMER);
        businessClientUser = registerTestUser("Business " + runId, "biz-" + runId + "@shiptrack.com", Role.BUSINESS_CLIENT);
        otherBusinessClientUser = registerTestUser("Other Biz " + runId, "otherbiz-" + runId + "@shiptrack.com", Role.BUSINESS_CLIENT);
        operatorUser = registerTestUser("Operator " + runId, "operator-" + runId + "@shiptrack.com", Role.LOGISTICS_OPERATOR);
        supportUser = registerTestUser("Support " + runId, "support-" + runId + "@shiptrack.com", Role.SUPPORT_AGENT);
        adminUser = registerTestUser("Admin " + runId, "admin-" + runId + "@shiptrack.com", Role.ADMINISTRATOR);

        customerToken = jwtUtils.generateToken(customerUser.getId(), customerUser.getEmail(), customerUser.getRole().name());
        businessClientToken = jwtUtils.generateToken(businessClientUser.getId(), businessClientUser.getEmail(), businessClientUser.getRole().name());
        otherBusinessClientToken = jwtUtils.generateToken(otherBusinessClientUser.getId(), otherBusinessClientUser.getEmail(), otherBusinessClientUser.getRole().name());
        operatorToken = jwtUtils.generateToken(operatorUser.getId(), operatorUser.getEmail(), operatorUser.getRole().name());
        supportToken = jwtUtils.generateToken(supportUser.getId(), supportUser.getEmail(), supportUser.getRole().name());
        adminToken = jwtUtils.generateToken(adminUser.getId(), adminUser.getEmail(), adminUser.getRole().name());
    }

    private User registerTestUser(String name, String email, Role role) {
        RegisterRequest req = new RegisterRequest();
        req.setFullName(name);
        req.setEmail(email);
        req.setPassword("password123");
        req.setConfirmPassword("password123");
        req.setRole(role);
        req.setPhoneNumber("9998887766");
        UserDto dto = role == Role.ADMINISTRATOR || role == Role.SUPPORT_AGENT
                ? userService.createPrivilegedUser(req)
                : userService.registerUser(req);
        return userRepository.findById(dto.getId()).orElseThrow();
    }

    @Test
    @DisplayName("M1-01: User provisioning assigns business register IDs only to business clients")
    void testRegistrationForAllRoles() throws Exception {
        // Business Client gets auto-generated registerId
        assertNotNull(businessClientUser.getRegisterId());
        assertTrue(businessClientUser.getRegisterId().startsWith("BUS-"));
        assertEquals(12, businessClientUser.getRegisterId().length());

        // Other roles do not get registerId
        assertNull(customerUser.getRegisterId());
        assertNull(operatorUser.getRegisterId());
        assertNull(supportUser.getRegisterId());
        assertNull(adminUser.getRegisterId());
    }

    @Test
    @DisplayName("M1-02: User login and JWT generation, validation, and claims")
    void testLoginAndJwtGeneration() throws Exception {
        LoginRequest loginRequest = new LoginRequest();
        loginRequest.setEmail(businessClientUser.getEmail());
        loginRequest.setPassword("password123");

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isNotEmpty())
                .andExpect(jsonPath("$.email").value(businessClientUser.getEmail()))
                .andExpect(jsonPath("$.role").value("BUSINESS_CLIENT"));

        // Validate JWT utility functions
        assertTrue(jwtUtils.validateToken(businessClientToken));
        assertEquals(businessClientUser.getEmail(), jwtUtils.getEmailFromJwt(businessClientToken));
        assertEquals("BUSINESS_CLIENT", jwtUtils.getRoleFromJwt(businessClientToken));
        assertEquals(businessClientUser.getId(), jwtUtils.getIdFromJwt(businessClientToken));
    }

    @Test
    @DisplayName("M1-03: GET /api/users/me returns authenticated user profile")
    void testGetCurrentUserProfile() throws Exception {
        mockMvc.perform(get("/api/users/me")
                        .header("Authorization", "Bearer " + businessClientToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(businessClientUser.getId()))
                .andExpect(jsonPath("$.email").value(businessClientUser.getEmail()))
                .andExpect(jsonPath("$.role").value("BUSINESS_CLIENT"))
                .andExpect(jsonPath("$.registerId").value(businessClientUser.getRegisterId()));
    }

    @Test
    @DisplayName("M1-04: RBAC on shipment creation - business client & admin allowed; customer & operator forbidden")
    void testRbacOnShipmentCreation() throws Exception {
        ShipmentRequest validRequest = new ShipmentRequest();
        validRequest.setSenderName("Alpha Corp");
        validRequest.setSenderPhone("111222333");
        validRequest.setSenderAddress("Sector 62, Noida, UP");
        validRequest.setReceiverName("Beta LLC");
        validRequest.setReceiverPhone("444555666");
        validRequest.setReceiverAddress("Connaught Place, New Delhi");
        validRequest.setPackageDescription("Electronics");
        validRequest.setPackageWeightKg(BigDecimal.valueOf(5.25));

        // Business Client creates shipment: 200 OK
        mockMvc.perform(post("/api/shipments")
                        .header("Authorization", "Bearer " + businessClientToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.trackingNumber").value(org.hamcrest.Matchers.startsWith("STP-")))
                .andExpect(jsonPath("$.status").value("CREATED"))
                .andExpect(jsonPath("$.businessClientId").value(businessClientUser.getId()));

        // Customer attempts to create shipment: 403 Forbidden
        mockMvc.perform(post("/api/shipments")
                        .header("Authorization", "Bearer " + customerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validRequest)))
                .andExpect(status().isForbidden());

        // Logistics Operator attempts to create shipment: 403 Forbidden
        mockMvc.perform(post("/api/shipments")
                        .header("Authorization", "Bearer " + operatorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validRequest)))
                .andExpect(status().isForbidden());

        // Administrator creates shipment with businessClientId: 200 OK
        validRequest.setBusinessClientId(businessClientUser.getId());
        mockMvc.perform(post("/api/shipments")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.businessClientId").value(businessClientUser.getId()));
    }

    @Test
    @DisplayName("M1-05: Shipment ownership and visibility rules for customer and business client")
    void testShipmentVisibilityRules() throws Exception {
        // Create shipment for businessClientUser assigned to customerUser
        ShipmentRequest req = new ShipmentRequest();
        req.setCustomerId(customerUser.getId());
        req.setSenderName("Origin Corp");
        req.setSenderPhone("111");
        req.setSenderAddress("Address A");
        req.setReceiverName("Dest Corp");
        req.setReceiverPhone("222");
        req.setReceiverAddress("Address B");

        String createResp = mockMvc.perform(post("/api/shipments")
                        .header("Authorization", "Bearer " + businessClientToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        com.fasterxml.jackson.databind.JsonNode createdNode = objectMapper.readTree(createResp);
        Long shipmentId = createdNode.get("id").asLong();
        String trackingNumber = createdNode.get("trackingNumber").asText();

        // 1. Owning business client can view: 200 OK
        mockMvc.perform(get("/api/shipments/" + shipmentId)
                        .header("Authorization", "Bearer " + businessClientToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(shipmentId));

        // 2. Assigned customer can view: 200 OK
        mockMvc.perform(get("/api/shipments/" + shipmentId)
                        .header("Authorization", "Bearer " + customerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(shipmentId));

        // 3. Other business client CANNOT view: 403 Forbidden
        mockMvc.perform(get("/api/shipments/" + shipmentId)
                        .header("Authorization", "Bearer " + otherBusinessClientToken))
                .andExpect(status().isForbidden());

        // 4. Track by tracking number enforces same visibility
        mockMvc.perform(get("/api/shipments/track/" + trackingNumber)
                        .header("Authorization", "Bearer " + businessClientToken))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/shipments/track/" + trackingNumber)
                        .header("Authorization", "Bearer " + otherBusinessClientToken))
                .andExpect(status().isForbidden());

        // 5. Support agent and Administrator can view any shipment: 200 OK
        mockMvc.perform(get("/api/shipments/" + shipmentId)
                        .header("Authorization", "Bearer " + supportToken))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/shipments/" + shipmentId)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("M1-06: Operator assignment via PATCH /api/shipments/{id}/assign and operator preservation on update")
    void testOperatorAssignmentAndPreservation() throws Exception {
        // Create unassigned shipment
        ShipmentRequest req = new ShipmentRequest();
        req.setSenderName("Supplier");
        req.setSenderPhone("111");
        req.setSenderAddress("Warehouse A");
        req.setReceiverName("Buyer");
        req.setReceiverPhone("222");
        req.setReceiverAddress("Store B");

        String createResp = mockMvc.perform(post("/api/shipments")
                        .header("Authorization", "Bearer " + businessClientToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        com.fasterxml.jackson.databind.JsonNode createdNode = objectMapper.readTree(createResp);
        Long shipmentId = createdNode.get("id").asLong();
        assertTrue(createdNode.get("assignedOperatorId").isNull());

        // Assign operator via PATCH /api/shipments/{id}/assign
        AssignOperatorRequest assignReq = new AssignOperatorRequest(operatorUser.getId());
        mockMvc.perform(patch("/api/shipments/" + shipmentId + "/assign")
                        .header("Authorization", "Bearer " + businessClientToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(assignReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.assignedOperatorId").value(operatorUser.getId()))
                .andExpect(jsonPath("$.assignedOperatorName").value(operatorUser.getFullName()));

        // Verify status history recorded the assignment
        mockMvc.perform(get("/api/shipments/" + shipmentId + "/history")
                        .header("Authorization", "Bearer " + businessClientToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[1].remarks").value(org.hamcrest.Matchers.containsString("Assigned to logistics operator")));

        // Update shipment details WITHOUT specifying assignedOperatorId
        ShipmentRequest updateReq = new ShipmentRequest();
        updateReq.setPackageDescription("Updated fragile contents");

        mockMvc.perform(put("/api/shipments/" + shipmentId)
                        .header("Authorization", "Bearer " + businessClientToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.packageDescription").value("Updated fragile contents"))
                // Operator MUST BE PRESERVED, not nullified!
                .andExpect(jsonPath("$.assignedOperatorId").value(operatorUser.getId()));
    }

    @Test
    @DisplayName("M1-07: Shipment cancellation and status history logging")
    void testShipmentCancellation() throws Exception {
        ShipmentRequest req = new ShipmentRequest();
        req.setSenderName("Canceller Co");
        req.setSenderPhone("111");
        req.setSenderAddress("Warehouse");
        req.setReceiverName("Buyer");
        req.setReceiverPhone("222");
        req.setReceiverAddress("Store");

        String createResp = mockMvc.perform(post("/api/shipments")
                        .header("Authorization", "Bearer " + businessClientToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        com.fasterxml.jackson.databind.JsonNode createdNode = objectMapper.readTree(createResp);
        Long shipmentId = createdNode.get("id").asLong();

        // Cancel shipment: 200 OK
        mockMvc.perform(patch("/api/shipments/" + shipmentId + "/cancel")
                        .header("Authorization", "Bearer " + businessClientToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));

        // Check history: should have CREATED and CANCELLED
        mockMvc.perform(get("/api/shipments/" + shipmentId + "/history")
                        .header("Authorization", "Bearer " + businessClientToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", org.hamcrest.Matchers.hasSize(2)))
                .andExpect(jsonPath("$[1].status").value("CANCELLED"));
    }
}
