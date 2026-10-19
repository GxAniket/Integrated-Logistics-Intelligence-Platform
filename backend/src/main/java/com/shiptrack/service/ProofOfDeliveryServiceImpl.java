package com.shiptrack.service;

import com.shiptrack.dto.ProofOfDeliveryRequest;
import com.shiptrack.dto.ProofOfDeliveryResponse;
import com.shiptrack.entity.ProofOfDelivery;
import com.shiptrack.entity.Shipment;
import com.shiptrack.entity.User;
import com.shiptrack.entity.ShipmentStatusHistory;
import com.shiptrack.enums.Role;
import com.shiptrack.enums.ShipmentStatus;
import com.shiptrack.exception.ResourceNotFoundException;
import com.shiptrack.repository.ProofOfDeliveryRepository;
import com.shiptrack.repository.ShipmentRepository;
import com.shiptrack.repository.ShipmentStatusHistoryRepository;
import com.shiptrack.repository.UserRepository;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
public class ProofOfDeliveryServiceImpl implements ProofOfDeliveryService {

    private final ProofOfDeliveryRepository podRepository;
    private final ShipmentRepository shipmentRepository;
    private final UserRepository userRepository;
    private final ShipmentStatusHistoryRepository historyRepository;

    public ProofOfDeliveryServiceImpl(ProofOfDeliveryRepository podRepository,
                                      ShipmentRepository shipmentRepository,
                                      UserRepository userRepository,
                                      ShipmentStatusHistoryRepository historyRepository) {
        this.podRepository = podRepository;
        this.shipmentRepository = shipmentRepository;
        this.userRepository = userRepository;
        this.historyRepository = historyRepository;
    }

    private User getCurrentUser() {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("Logged-in user not found with email: " + email));
    }

    private Shipment getShipment(Long shipmentId) {
        return shipmentRepository.findById(shipmentId)
                .orElseThrow(() -> new ResourceNotFoundException("Shipment not found with ID: " + shipmentId));
    }

    private void checkVisibility(Shipment shipment, User user) {
        if (user.getRole() == Role.ADMINISTRATOR || user.getRole() == Role.SUPPORT_AGENT) return;

        if (user.getRole() == Role.BUSINESS_CLIENT
                && shipment.getBusinessClient() != null
                && shipment.getBusinessClient().getId().equals(user.getId())) return;

        if (user.getRole() == Role.CUSTOMER
                && shipment.getCustomer() != null
                && shipment.getCustomer().getId().equals(user.getId())) return;

        if (user.getRole() == Role.LOGISTICS_OPERATOR
                && shipment.getAssignedOperator() != null
                && shipment.getAssignedOperator().getId().equals(user.getId())) return;

        throw new AccessDeniedException("You do not have permission to view this shipment's proof of delivery.");
    }

    private void checkCreatePermission(Shipment shipment, User user) {
        if (user.getRole() == Role.ADMINISTRATOR) return;

        if (user.getRole() == Role.LOGISTICS_OPERATOR
                && shipment.getAssignedOperator() != null
                && shipment.getAssignedOperator().getId().equals(user.getId())) return;

        throw new AccessDeniedException("Only the assigned logistics operator or an administrator can create proof of delivery.");
    }

    @Override
    @Transactional
    public ProofOfDeliveryResponse createProofOfDelivery(Long shipmentId, ProofOfDeliveryRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("POD request is required.");
        }

        User currentUser = getCurrentUser();
        Shipment shipment = getShipment(shipmentId);
        checkCreatePermission(shipment, currentUser);

        if (shipment.getStatus() == ShipmentStatus.CANCELLED) {
            throw new IllegalArgumentException("Cannot create proof of delivery for a cancelled shipment.");
        }

        if (shipment.getStatus() == ShipmentStatus.CREATED) {
            throw new IllegalArgumentException("Shipment must be in transit or out for delivery before proof of delivery can be created.");
        }

        if (podRepository.existsByShipmentId(shipmentId)) {
            throw new IllegalArgumentException("Proof of delivery already exists for shipment ID: " + shipmentId);
        }

        if (request.getSignature() == null || request.getSignature().trim().isEmpty()) {
            throw new IllegalArgumentException("Signature is required to create proof of delivery.");
        }

        // Synchronize shipment status to DELIVERED and record history entry if not already marked DELIVERED
        if (shipment.getStatus() != ShipmentStatus.DELIVERED) {
            shipment.setStatus(ShipmentStatus.DELIVERED);
            shipmentRepository.save(shipment);

            ShipmentStatusHistory history = new ShipmentStatusHistory();
            history.setShipment(shipment);
            history.setStatus(ShipmentStatus.DELIVERED);
            history.setRemarks("Delivered. Proof of delivery confirmed.");
            history.setUpdatedBy(currentUser);
            historyRepository.save(history);
        }

        if (request.getDeliveryStatus() != null && request.getDeliveryStatus() != ShipmentStatus.DELIVERED) {
            throw new IllegalArgumentException("POD delivery status must be DELIVERED.");
        }

        ProofOfDelivery pod = new ProofOfDelivery();
        pod.setShipment(shipment);
        pod.setReceiverName(shipment.getReceiverName());
        pod.setReceiverPhone(shipment.getReceiverPhone());
        pod.setReceiverAddress(shipment.getReceiverAddress());
        pod.setDeliveredAt(request.getDeliveredAt() != null ? request.getDeliveredAt() : LocalDateTime.now());
        pod.setDeliveryStatus(ShipmentStatus.DELIVERED);
        pod.setSignature(request.getSignature().trim());
        pod.setRemarks(normalize(request.getRemarks()));
        pod.setDeliveredBy(currentUser);

        return mapToResponse(podRepository.save(pod));
    }

    @Override
    @Transactional(readOnly = true)
    public ProofOfDeliveryResponse getProofOfDelivery(Long shipmentId) {
        User currentUser = getCurrentUser();
        Shipment shipment = getShipment(shipmentId);
        checkVisibility(shipment, currentUser);

        ProofOfDelivery pod = podRepository.findByShipmentId(shipmentId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Proof of delivery not found for shipment ID: " + shipmentId));

        return mapToResponse(pod);
    }

    private String normalize(String value) {
        if (value == null) return null;
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private ProofOfDeliveryResponse mapToResponse(ProofOfDelivery pod) {
        ProofOfDeliveryResponse response = new ProofOfDeliveryResponse();
        Shipment shipment = pod.getShipment();
        User deliveredBy = pod.getDeliveredBy();

        response.setId(pod.getId());
        response.setShipmentId(shipment.getId());
        response.setTrackingNumber(shipment.getTrackingNumber());
        response.setReceiverName(pod.getReceiverName());
        response.setReceiverPhone(pod.getReceiverPhone());
        response.setReceiverAddress(pod.getReceiverAddress());
        response.setDeliveredAt(pod.getDeliveredAt());
        response.setDeliveryStatus(pod.getDeliveryStatus());
        response.setSignature(pod.getSignature());
        response.setRemarks(pod.getRemarks());
        response.setDeliveredByUserId(deliveredBy.getId());
        response.setDeliveredByUserName(deliveredBy.getFullName());
        response.setCreatedAt(pod.getCreatedAt());
        response.setUpdatedAt(pod.getUpdatedAt());
        return response;
    }
}
