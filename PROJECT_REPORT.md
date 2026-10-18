# ShipTrack Pro — Project Report

## 1. Project overview

ShipTrack Pro is a role-based shipment management and logistics tracking application. It combines a React single-page application with a Spring Boot REST API and PostgreSQL persistence. Business users create and manage shipments; operators update progress and delivery locations; customers and business users can follow shipments they are allowed to access; and administrators and support agents have broader operational views.

This report describes the behavior implemented in the current source. It distinguishes the route-based forecast from the ETA calculation and calls out data that is entered by users versus obtained from mapping services.

## 2. Technology and structure

- **Frontend:** React, Vite, React Router, Leaflet/React Leaflet.
- **Backend:** Java 17, Spring Boot, Spring MVC, Spring Data JPA, Spring Security.
- **Database:** PostgreSQL for the running application; H2 is included for backend tests.
- **Authentication:** stateless JWT authentication; passwords are encoded with BCrypt.
- **Mapping and routing:** OpenStreetMap Nominatim for address geocoding and OSRM for driving-route distance, duration, and geometry.
- **Main source areas:** `Frontend/src/` contains pages, shared components, and API access; `backend/src/main/java/com/shiptrack/` contains controllers, DTOs, entities, repositories, security, and services.

## 3. User roles and application areas

The frontend separates routes by role:

| Role | Main capabilities and screens |
|---|---|
| Customer | Dashboard, active shipments, shipment history, tracking, notifications, and tracking insights |
| Business client | Dashboard, create/manage shipments, shipment history, package information, tracking, delivery performance, delay analysis, logistics overview, customer activity, reports, and notifications |
| Logistics operator | Dashboard, assigned-shipment tracking, live delivery/location updates, driver tracking, route management, ETA and delays, and proof of delivery |
| Support agent | Support dashboard and support navigation |
| Administrator | Administration dashboard and privileged user management |

The backend also checks roles and shipment ownership/assignment. The frontend's role-based route guard is a navigation aid; API authorization is enforced separately on the server.

## 4. Shipment lifecycle

### Create and assign

An authenticated business client can create shipments for their own account. An administrator can create a shipment on behalf of a business client. A shipment can optionally be linked to a customer and a logistics operator; the backend validates that each assigned account has the expected role.

New shipments receive a tracking number in the `STP-<year>-<sequence>` format and begin in `CREATED`. Sender/receiver contact and address details, optional reference and package description, optional weight, and account associations are persisted.

### Status progression

The supported status transitions are:

`CREATED → PICKED_UP → IN_TRANSIT → OUT_FOR_DELIVERY → DELIVERED`

From `OUT_FOR_DELIVERY`, an operator or administrator can record `FAILED_DELIVERY`; a failed delivery can return to `OUT_FOR_DELIVERY`. The backend rejects other status transitions. A business client who owns the shipment or an administrator can cancel a shipment that is not already delivered or cancelled.

Every status change is recorded with its timestamp, remarks, and updating user. The business client, linked customer, and assigned operator receive an in-app notification for status events.

### Main shipment API

| Method and path | Purpose |
|---|---|
| `POST /api/shipments` | Create a shipment |
| `GET /api/shipments` | List shipments visible to the current user |
| `GET /api/shipments/{id}` | Get a visible shipment |
| `GET /api/shipments/track/{trackingNumber}` | Find a shipment by tracking number, subject to access checks |
| `PUT /api/shipments/{id}` | Edit shipment details and assignments |
| `PATCH /api/shipments/{id}/status` | Advance status and add a history event |
| `PATCH /api/shipments/{id}/cancel` | Cancel an eligible shipment |
| `GET /api/shipments/{id}/history` | Read status history |

All API endpoints require authentication unless explicitly exposed as auth endpoints. Shipment reads are filtered or checked against role, ownership, customer association, or operator assignment as appropriate.

## 5. How shipment tracking works

Tracking combines a current shipment status, an event history, and manually recorded coordinates:

1. The frontend looks up the shipment using its tracking number or loads it from the authenticated user's shipment list.
2. It requests the shipment's status history, current location, location history, and live tracking details. Business tracking can also load its saved route, delivery forecast, and a calculated ETA.
3. An assigned logistics operator (or administrator) submits a latitude and longitude update. The backend validates coordinate ranges, checks permission and shipment status, then saves a timestamped location point and optional location name.
4. The current-location endpoint returns the most recently recorded point; the location-history endpoint returns prior points in newest-first order. The live-tracking response combines status, shipment addresses, assigned operator, current point, and location history.
5. The UI presents status/location events in time order and may show the saved route on a map.

**Important distinction:** the inspected implementation records location updates through an API request. It does not establish an automatic GPS device feed or continuous background tracking loop. “Live” therefore means the latest location submitted to the backend, not guaranteed real-time device telemetry.

Location/tracking APIs:

- `PATCH /api/shipments/{id}/location`
- `GET /api/shipments/{id}/location`
- `GET /api/shipments/{id}/location-history`
- `GET /api/shipments/{id}/tracking`

Location updates are blocked once a shipment is delivered or cancelled. Reads require the caller to be associated with the shipment or have an administrative/support role; updates are limited to the assigned operator or an administrator.

## 6. How a route is calculated

Route calculation uses the shipment's sender and receiver addresses:

1. Nominatim geocodes each address into latitude/longitude.
2. OSRM's driving profile calculates a road route between those coordinates.
3. The backend saves the origin and destination addresses and coordinates, distance in kilometres, estimated duration in minutes, and returned GeoJSON route geometry. Existing route data for the shipment is updated rather than duplicated.

Relevant APIs:

- `POST /api/routes/shipment/{shipmentId}/calculate`
- `GET /api/routes/shipment/{shipmentId}`
- `GET /api/routes/{id}`

Route calculation is available to business clients for their own shipments, administrators, and operators for shipments assigned to them that are actively being tracked. It depends on the external geocoding/routing services being reachable.

## 7. How the ETA is calculated

The on-demand ETA endpoint is `POST /api/shipments/{id}/eta`. It computes a response for the request; the `DeliveryEtaService` does not persist that ETA as a shipment record.

### Inputs and origin/destination

- **Origin:** the latest saved shipment location, when one exists. Otherwise, the saved route's origin coordinates are used.
- **Destination:** destination coordinates in the request. If omitted, the service uses saved route destination coordinates.
- **Base duration:** OSRM's driving-route duration from the selected origin to destination.
- **Traffic:** a traffic condition supplied in the request; if omitted, it defaults to `MODERATE`.
- **Weather and route-change delays:** non-negative hours supplied in the request; each defaults to zero. The service does not fetch weather data or infer a route-change delay itself.

If the service cannot obtain an origin or destination coordinate, it rejects the calculation rather than returning a fabricated estimate.

### Calculation

Let `R` be the OSRM route duration in minutes, `M` the traffic multiplier, `W` the requested weather delay in hours, and `C` the requested route-change delay in hours:

```text
traffic delay (minutes) = R × (M − 1)
weather delay (minutes) = W × 60
route-change delay (minutes) = C × 60
predicted delay (hours) = (traffic delay + weather delay + route-change delay) ÷ 60
expected completion = calculation time + R minutes + all predicted delay minutes
```

The traffic multipliers are:

| Traffic condition | Multiplier |
|---|---:|
| CLEAR | 1.00 |
| LIGHT | 1.05 |
| MODERATE | 1.15 |
| HEAVY | 1.35 |
| SEVERE | 1.60 |

For example, a 120-minute base route at `HEAVY` traffic adds `120 × (1.35 − 1) = 42` minutes of traffic delay. Weather and route-change values, if provided, are added to that delay.

The response includes shipment ID and tracking number, distance, base route hours, each delay component, total predicted delay, expected completion time, and the traffic condition. The backend sets `delayAlert` when the predicted delay is **strictly greater than 0.5 hours** (30 minutes).

The operator ETA screen separately groups positive ETA delays as at risk below 1.5 hours and high risk at or above 1.5 hours. It also counts `FAILED_DELIVERY` shipments as delivery exceptions. These UI categories are not the same as the backend's 30-minute `delayAlert` threshold.

## 8. Forecasts are separate from live ETA

The forecast API (`POST /api/forecasts/generate?shipmentId=...`) saves a forecast record:

- If the shipment has a route with a positive estimated duration, it predicts `now + route duration` with confidence `0.75`.
- Without that route duration, it predicts `now + 1 day` with confidence `0.40`.
- A delivered shipment is forecast as delivered now with confidence `0.99`.
- An `OUT_FOR_DELIVERY` shipment uses `now + 4 hours` and at least `0.70` confidence.
- The saved forecast also records the shipment's current status as its predicted status.

Forecasts can be listed per shipment through `GET /api/forecasts/shipment/{shipmentId}` or listed across the current user's visible shipments with `GET /api/forecasts`. This is a simple route/status-based heuristic, not the on-demand ETA formula and not a machine-learning model.

## 9. Proof of delivery

An assigned operator or administrator can submit one proof-of-delivery record per shipment. It records a receiver signature as a PNG data URL, remarks, delivery time, and the submitting user. The server validates that the signature decodes to a PNG image, is no larger than 1 MB, and has no more than 4 million pixels. Delivery time cannot be in the future, and a cancelled shipment cannot be confirmed as delivered.

Creating proof of delivery changes a shipment to `DELIVERED` if needed, adds a status-history entry, and notifies the shipment's associated users. Authorized users can retrieve the record with:

- `POST /api/shipments/{id}/pod`
- `GET /api/shipments/{id}/pod`

## 10. Notifications, analytics, and reports

- **Notifications:** status changes generate in-app messages. Users can list their own notifications and mark one or all as read (`/api/notifications`).
- **Shipment analytics:** `GET /api/analytics` optionally accepts `from` and `to` dates and summarizes visible shipments by creation date and current status (total, delivered, delayed/failed delivery, in transit, created, picked up, out for delivery, cancelled).
- **Delivery performance:** `GET /api/reports/performance` accepts optional date bounds and returns delivery records and counts. Its explicit “on time” definition is a delivered shipment with no `FAILED_DELIVERY` event in status history; it does not compare actual delivery time to an originally promised schedule.
- **Business tools:** the UI also includes delivery performance, delay analysis, logistics overview, customer activity, and report pages.

## 11. Authentication and account management

- Registration supports customer, business-client, and logistics-operator accounts. Administrator and support-agent accounts are created through an administrator-only endpoint.
- Business-client registration IDs are generated by the backend in the `BUS-` plus eight-character hexadecimal format.
- Login authenticates email and password and returns a JWT containing user identity and role. Protected frontend API calls attach the stored token.
- Forgot-password creates a six-digit, hashed one-time code delivered by email. It expires after 10 minutes and is limited to five verification attempts.
- Account endpoints include current-user details, administrator user listing, and operator/customer lists where permitted.

## 12. Main data records

The persistence model includes users, shipments, status history, shipment location points, routes, forecasts, proof of delivery, notifications, and password-reset state. A shipment links to its business client and may link to a customer and assigned operator. Shipment, route, status, location, forecast, and proof-of-delivery records are handled through JPA repositories and service logic.

## 13. Running locally

The project README contains the launch commands and environment settings. In summary:

1. Configure a local PostgreSQL database; the default database is `shiptrack_db`.
2. In `backend`, set `DB_URL`, `DB_USERNAME`, and `DB_PASSWORD`, then run `.\mvnw.cmd clean spring-boot:run`. The backend listens on port `8080`.
3. In `Frontend`, run `npm install` and `npm run dev`. Vite listens on port `5173`.
4. Configure the `MAIL_*` settings to enable password-reset email delivery. `VITE_API_BASE_URL` can override the frontend's default backend URL.

Backend tests use an isolated in-memory H2 database; the application itself is configured for PostgreSQL.

## 14. Key implementation references

- Shipment lifecycle and ownership: [ShipmentServiceImpl.java](./backend/src/main/java/com/shiptrack/service/ShipmentServiceImpl.java)
- Route/geocoding/routing: [RouteController.java](./backend/src/main/java/com/shiptrack/controller/RouteController.java), [MapsService.java](./backend/src/main/java/com/shiptrack/service/MapsService.java)
- ETA formula: [DeliveryEtaService.java](./backend/src/main/java/com/shiptrack/service/DeliveryEtaService.java), [TrafficCondition.java](./backend/src/main/java/com/shiptrack/enums/TrafficCondition.java)
- Location tracking: [LiveTrackingServiceImpl.java](./backend/src/main/java/com/shiptrack/service/LiveTrackingServiceImpl.java)
- Forecast heuristic: [ForecastService.java](./backend/src/main/java/com/shiptrack/service/ForecastService.java)
- Proof of delivery: [ProofOfDeliveryServiceImpl.java](./backend/src/main/java/com/shiptrack/service/ProofOfDeliveryServiceImpl.java)
- Frontend route map: [App.jsx](./Frontend/src/App.jsx)
- Business tracking UI: [BusinessTracking.jsx](./Frontend/src/pages/BusinessTracking.jsx)
- Operator ETA/delay UI: [OperatorETADelays.jsx](./Frontend/src/pages/OperatorETADelays.jsx)
