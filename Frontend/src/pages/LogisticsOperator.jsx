import { useEffect, useMemo, useState } from "react";
import { apiRequest } from "../api";
import ShipmentCard from "../components/ShipmentCard";
import "./LogisticsOperator.css";

// Shipment list and summary counts are loaded from the backend.
// The remaining dashboard sections below still contain demo data and are
// labelled as such until their APIs are integrated.

const mockLiveDeliveries = [
  {
    trackingNumber: "SHP-10241",
    driver: "Driver #A12",
    lastLocationUpdate: "12:04 PM",
    status: "Moving towards destination",
  },
  {
    trackingNumber: "SHP-10247",
    driver: "Driver #A07",
    lastLocationUpdate: "12:01 PM",
    status: "Stopped - possible traffic",
  },
];

const mockDrivers = [
  {
    driverId: "A12",
    name: "Driver #A12",
    assignedShipments: 3,
    lastKnownLocation: "NH16, near Vijayawada",
    status: "Active",
  },
  {
    driverId: "A07",
    name: "Driver #A07",
    assignedShipments: 2,
    lastKnownLocation: "Chennai Bypass Road",
    status: "Active",
  },
  {
    driverId: "A19",
    name: "Driver #A19",
    assignedShipments: 1,
    lastKnownLocation: "Depot - Bengaluru",
    status: "Idle",
  },
];

const mockRoutes = [
  {
    routeId: "RT-501",
    origin: "Hyderabad Hub",
    destination: "Vijayawada Local Station",
    distance: "275 km",
    trafficCondition: "Moderate",
    optimizationNote: "Optimized route suggested (saves ~18 min)",
  },
  {
    routeId: "RT-502",
    origin: "Chennai Sorting Facility",
    destination: "Bengaluru - Customer Address",
    distance: "346 km",
    trafficCondition: "Heavy",
    optimizationNote: "Alternate route available",
  },
];

const mockEtaMonitoring = [
  {
    trackingNumber: "SHP-10234",
    originalEta: "Today, 5:45 PM",
    currentEta: "Today, 6:30 PM",
    delayStatus: "Minor delay predicted",
  },
  {
    trackingNumber: "SHP-10198",
    originalEta: "Today, 8:00 PM",
    currentEta: "Tomorrow, 11:00 AM",
    delayStatus: "Significant delay",
  },
];

const mockProofOfDelivery = [
  {
    trackingNumber: "SHP-10255",
    digitalSignature: "Captured",
    deliveryPhoto: "Captured",
    confirmation: "Confirmed by recipient",
    verification: "Verified",
    evidenceStorage: "Stored (mock reference: EVID-4471)",
  },
];

function LogisticsOperator() {
  const [statusFilter, setStatusFilter] = useState("All");
  const [shipments, setShipments] = useState([]);