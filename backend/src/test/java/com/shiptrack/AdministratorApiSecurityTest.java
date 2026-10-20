package com.shiptrack;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.shiptrack.dto.LoginRequest;
import com.shiptrack.dto.RegisterRequest;
import com.shiptrack.dto.ShipmentRequest;
import com.shiptrack.dto.UserDto;
import com.shiptrack.entity.Notification;
import com.shiptrack.entity.User;
import com.shiptrack.enums.NotificationType;
import com.shiptrack.enums.Role;
import com.shiptrack.repository.NotificationRepository;
import com.shiptrack.repository.UserRepository;
import com.shiptrack.security.JwtAuthenticationFilter;
import com.shiptrack.security.JwtUtils;
import com.shiptrack.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@Transactional
class AdministratorApiSecurityTest {

    @Autowired
    private WebApplicationContext webApplicationContext;

    @Autowired
    private JwtAuthenticationFilter jwtAuthenticationFilter;

    @Autowired
    private UserService userService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private NotificationRepository notificationRepository;

    @Autowired
    private JwtUtils jwtUtils;

    @Autowired
    private ObjectMapper objectMapper;

    private MockMvc mockMvc;
    private User administrator;
    private User customer;
    private String administratorToken;
    private String customerToken;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext)
                .addFilter(jwtAuthenticationFilter)
                .build();

        administrator = registerUser(Role.ADMINISTRATOR);
        customer = registerUser(Role.CUSTOMER);
        administratorToken = tokenFor(administrator);
        customerToken = tokenFor(customer);
    }

    @Test
    void userListIsAdministratorOnlyAndOmitsPasswordHashes() throws Exception {
        mockMvc.perform(get("/api/users")
                        .header("Authorization", "Bearer " + customerToken))
                .andExpect(status().isForbidden());

        long databaseUserCount = userRepository.count();
        String response = mockMvc.perform(get("/api/users")
                        .header("Authorization", "Bearer " + administratorToken))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        assertEquals(databaseUserCount, objectMapper.readTree(response).size());
        assertFalse(response.contains("passwordHash"));
        assertFalse(response.contains("password_hash"));
    }

    @Test
    void roleCollectionsAreScopedToBusinessClientsAndReturnSafeDtos() throws Exception {
        User businessClient = registerUser(Role.BUSINESS_CLIENT);
        User operator = registerUser(Role.LOGISTICS_OPERATOR);
        String businessToken = tokenFor(businessClient);

        for (String path : new String[]{"/api/users/operators", "/api/users/customers"}) {
            mockMvc.perform(get(path)
                            .header("Authorization", "Bearer " + customerToken))
                    .andExpect(status().isForbidden());
        }
        mockMvc.perform(get("/api/users/" + administrator.getId())
                        .header("Authorization", "Bearer " + customerToken))
                .andExpect(status().isForbidden());

        String operators = mockMvc.perform(get("/api/users/operators")
                        .header("Authorization", "Bearer " + businessToken))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        var operatorRows = objectMapper.readTree(operators);
        assertTrue(operatorRows.isArray());
        assertTrue(java.util.stream.StreamSupport.stream(operatorRows.spliterator(), false)
                .allMatch(user -> user.get("role").asText().equals("LOGISTICS_OPERATOR")));
        assertTrue(java.util.stream.StreamSupport.stream(operatorRows.spliterator(), false)
                .anyMatch(user -> user.get("id").asLong() == operator.getId()));
        assertFalse(operators.contains("passwordHash"));

        String customers = mockMvc.perform(get("/api/users/customers")
                        .header("Authorization", "Bearer " + businessToken))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        var customerRows = objectMapper.readTree(customers);
        assertTrue(customerRows.isArray());
        assertTrue(java.util.stream.StreamSupport.stream(customerRows.spliterator(), false)
                .allMatch(user -> user.get("role").asText().equals("CUSTOMER")));
        assertFalse(customers.contains("passwordHash"));
    }

    @Test
    void shipmentListsAndRoutesRespectCustomerOwnership() throws Exception {
        User businessClient = registerUser(Role.BUSINESS_CLIENT);
        User otherCustomer = registerUser(Role.CUSTOMER);
        String businessToken = tokenFor(businessClient);

        ShipmentRequest request = new ShipmentRequest();
        request.setCustomerId(customer.getId());
        request.setSenderName("Sender");
        request.setSenderPhone("1111111111");
        request.setSenderAddress("Origin");
        request.setReceiverName("Receiver");
        request.setReceiverPhone("2222222222");
        request.setReceiverAddress("Destination");

        MvcResult created = mockMvc.perform(post("/api/shipments")
                        .header("Authorization", "Bearer " + businessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.customerId").value(customer.getId()))
                .andReturn();
        long shipmentId = objectMapper.readTree(created.getResponse().getContentAsString())
                .get("id").asLong();

        mockMvc.perform(get("/api/shipments")
                        .header("Authorization", "Bearer " + customerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(shipmentId));
        mockMvc.perform(get("/api/shipments")
                        .header("Authorization", "Bearer " + tokenFor(otherCustomer)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
        mockMvc.perform(get("/api/shipments/" + shipmentId)
                        .header("Authorization", "Bearer " + tokenFor(otherCustomer)))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/routes/shipment/" + shipmentId)
                        .header("Authorization", "Bearer " + customerToken))
                .andExpect(status().isNoContent());
        mockMvc.perform(get("/api/routes/shipment/" + shipmentId)
                        .header("Authorization", "Bearer " + tokenFor(otherCustomer)))
                .andExpect(status().isForbidden());
    }

    @Test
    void administratorLoginShipmentsAndAnalyticsEndpointsWork() throws Exception {
        LoginRequest loginRequest = new LoginRequest();
        loginRequest.setEmail(administrator.getEmail());
        loginRequest.setPassword("password123");
        String loginResponse = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("ADMINISTRATOR"))
                .andExpect(jsonPath("$.token").isNotEmpty())
                .andReturn()
                .getResponse()
                .getContentAsString();
        String authenticatedToken = objectMapper.readTree(loginResponse).get("token").asText();

        mockMvc.perform(get("/api/shipments")
                        .header("Authorization", "Bearer " + authenticatedToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());

        mockMvc.perform(get("/api/analytics/shipments")
                        .param("from", "2000-01-01")
                        .param("to", "2100-12-31")
                        .header("Authorization", "Bearer " + authenticatedToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.from").value("2000-01-01"))
                .andExpect(jsonPath("$.to").value("2100-12-31"))
                .andExpect(jsonPath("$.totalShipments").isNumber())
                .andExpect(jsonPath("$.statusBreakdown").isMap());

        mockMvc.perform(get("/api/analytics/delivery-performance")
                        .param("from", "2000-01-01")
                        .param("to", "2100-12-31")
                        .header("Authorization", "Bearer " + authenticatedToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.from").value("2000-01-01"))
                .andExpect(jsonPath("$.to").value("2100-12-31"))
                .andExpect(jsonPath("$.measuredDeliveries").isNumber())
                .andExpect(jsonPath("$.onTimeRatePercent").isNumber());

        mockMvc.perform(get("/api/analytics/shipments")
                        .header("Authorization", "Bearer " + customerToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void publicRegistrationCannotCreateAdministrator() throws Exception {
        RegisterRequest request = registrationRequest("public-admin", Role.ADMINISTRATOR);

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());

        assertFalse(userRepository.existsByEmail(request.getEmail()));
    }

    @Test
    void privilegedRegistrationRequiresAdministratorAndAllowsOnlyPrivilegedRoles() throws Exception {
        RegisterRequest supportRequest = registrationRequest("created-support", Role.SUPPORT_AGENT);

        mockMvc.perform(post("/api/users/privileged")
                        .header("Authorization", "Bearer " + customerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(supportRequest)))
                .andExpect(status().isForbidden());
        assertFalse(userRepository.existsByEmail(supportRequest.getEmail()));

        mockMvc.perform(post("/api/users/privileged")
                        .header("Authorization", "Bearer " + administratorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(supportRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("SUPPORT_AGENT"))
                .andExpect(jsonPath("$.passwordHash").doesNotExist());

        RegisterRequest customerRequest = registrationRequest("invalid-privileged", Role.CUSTOMER);
        mockMvc.perform(post("/api/users/privileged")
                        .header("Authorization", "Bearer " + administratorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(customerRequest)))
                .andExpect(status().isBadRequest());
        assertFalse(userRepository.existsByEmail(customerRequest.getEmail()));
    }

    @Test
    void notificationsAreUserScopedAndMarkAllReadDoesNotAffectOtherUsers() throws Exception {
        Notification administratorNotification = createNotification(administrator);
        createNotification(customer);

        mockMvc.perform(get("/api/notifications")
                        .header("Authorization", "Bearer " + administratorToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(administratorNotification.getId()))
                .andExpect(jsonPath("$[0].read").value(false))
                .andExpect(jsonPath("$[0].passwordHash").doesNotExist());

        mockMvc.perform(patch("/api/notifications/read-all")
                        .header("Authorization", "Bearer " + administratorToken))
                .andExpect(status().isNoContent());

        assertTrue(notificationRepository.findByUserIdOrderByCreatedAtDesc(administrator.getId())
                .get(0).isRead());
        assertFalse(notificationRepository.findByUserIdOrderByCreatedAtDesc(customer.getId())
                .get(0).isRead());
    }

    private User registerUser(Role role) {
        RegisterRequest request = registrationRequest(role.name().toLowerCase(), role);
        UserDto dto = userService.registerUser(request);
        return userRepository.findById(dto.getId()).orElseThrow();
    }

    private RegisterRequest registrationRequest(String prefix, Role role) {
        RegisterRequest request = new RegisterRequest();
        request.setFullName(prefix + " account");
        request.setEmail(prefix + "-" + java.util.UUID.randomUUID() + "@shiptrack.com");
        request.setPassword("password123");
        request.setConfirmPassword("password123");
        request.setRole(role);
        request.setPhoneNumber("1234567890");
        return request;
    }

    private String tokenFor(User user) {
        return jwtUtils.generateToken(user.getId(), user.getEmail(), user.getRole().name());
    }

    private Notification createNotification(User user) {
        Notification notification = new Notification();
        notification.setUser(user);
        notification.setType(NotificationType.SYSTEM);
        notification.setTitle("System notice");
        notification.setMessage("A system update is available.");
        return notificationRepository.save(notification);
    }
}
