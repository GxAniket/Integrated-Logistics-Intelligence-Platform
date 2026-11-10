# ShipTrack Pro — Integrated Logistics Intelligence Platform

ShipTrack Pro is a logistics management application for creating and managing shipments, tracking delivery progress, and viewing operational analytics. It uses a React and Vite frontend, a Spring Boot REST API, and PostgreSQL.

## Affiliation and Permission Notice

This project was developed as part of an Infosys Springboard internship. It is an internship project, not an official Infosys product or service. Any use, copying, modification, or redistribution must follow the applicable internship and intellectual-property terms and requires permission from the relevant rights holder.

## Features

- JWT-based registration and sign-in, with role-based access control.
- Role-specific workspaces for customers, business clients, logistics operators, support agents, and administrators.
- Shipment creation, assignment, updates, cancellation, tracking, and status history.
- Live shipment location and location history.
- Route calculation, delivery ETA, proof of delivery, and shipment forecasts.
- Notifications and shipment and delivery-performance analytics.
- OpenStreetMap geocoding and OSRM routing services for map-related features.

Administrator and Support Agent accounts cannot self-register. They must be provisioned by an existing administrator; the first administrator must be provisioned by the system operator.

## Technology

- **Frontend:** React 19, Vite 8, React Router, Leaflet
- **Backend:** Java 25, Spring Boot 4, Spring Security, Spring Data JPA
- **Authentication:** JWT and BCrypt password hashing
- **Database:** PostgreSQL
- **Deployment:** Vercel (frontend) and Render (backend and PostgreSQL)

## Repository Layout

```text
.
├── Frontend/          # React + Vite application
├── backend/           # Spring Boot REST API
│   ├── src/main/java/ # Controllers, services, entities, security
│   └── src/main/resources/
└── README.md
```

## Run Locally

### Requirements

- Node.js and npm
- Java 25
- A running PostgreSQL database

### 1. Start the backend

Create a local PostgreSQL database, then configure the connection in your shell. Do not commit credentials or put production secrets in this file.

PowerShell example:

```powershell
$env:SPRING_DATASOURCE_URL = "jdbc:postgresql://localhost:5432/shiptrack_db"
$env:SPRING_DATASOURCE_USERNAME = "your-local-database-user"
$env:SPRING_DATASOURCE_PASSWORD = "your-local-database-password"
cd backend
.\mvnw.cmd spring-boot:run
```

The API runs on `http://localhost:8080` by default. The server port can be set with `PORT`.

### 2. Start the frontend

In a separate terminal:

```powershell
cd Frontend
npm install
```

Create `Frontend/.env.local` with:

```dotenv
VITE_API_BASE_URL=http://localhost:8080
```

Then run:

```powershell
npm run dev
```

Vite prints the local frontend URL in the terminal. For production builds, run `npm run build`; the generated site is written to `Frontend/dist`.

## Environment Configuration

### Backend

Configure these environment variables in the backend runtime:

| Variable | Purpose |
| --- | --- |
| `SPRING_DATASOURCE_URL` | PostgreSQL JDBC URL, such as `jdbc:postgresql://host:5432/database` |
| `SPRING_DATASOURCE_USERNAME` | PostgreSQL username |
| `SPRING_DATASOURCE_PASSWORD` | PostgreSQL password; keep it secret |
| `PORT` | Port assigned by the hosting platform; defaults to `8080` locally |

The backend Dockerfile builds and runs the service with Java 25 and uses the hosting platform's `PORT` value when provided.

### Frontend

Set `VITE_API_BASE_URL` to the backend base URL for the Vercel deployment. `VITE_API_URL` is also accepted for compatibility. Do not include a trailing slash. The application falls back to the deployed Render API in production and `http://localhost:8080` in development when neither variable is set.

For Vercel, use `Frontend` as the project root, `npm run build` as the build command, and `dist` as the output directory. `Frontend/vercel.json` rewrites app routes to the SPA entry point.

## API Overview

Unless noted as public, API routes require a valid JWT in the `Authorization: Bearer <token>` header. Public authentication routes are:

| Method | Endpoint | Purpose |
| --- | --- | --- |
| `POST` | `/api/auth/register` | Register a Customer, Business Client, or Logistics Operator |
| `POST` | `/api/auth/login` | Sign in and receive a JWT |
| `POST` | `/api/auth/forgot-password` | Request a password-recovery code |
| `POST` | `/api/auth/reset-password` | Reset a password using the recovery code |

The API also exposes these route groups:

| Route group | Examples |
| --- | --- |
| Users | `GET /api/users/me`, `GET /api/users`, `GET /api/users/operators`, `GET /api/users/customers` |
| Shipments | `GET/POST /api/shipments`, `GET/PUT /api/shipments/{id}`, `GET /api/shipments/track/{trackingNumber}` |
| Shipment operations | `PATCH /api/shipments/{id}/status`, `/assign`, `/cancel`; `GET /api/shipments/{id}/history` |
| Live tracking | `PATCH /api/shipments/{id}/location`, `GET /api/shipments/{id}/location`, `/location-history`, `/tracking` |
| Routes | `/api/routes` — calculate and retrieve shipment routes |
| ETA and delivery | `/api/shipments/{id}/eta`, `/api/shipments/{id}/pod` |
| Forecasts | `/api/forecasts` — generate and retrieve shipment forecasts |
| Analytics | `/api/analytics/shipments`, `/api/analytics/delivery-performance` |
| Notifications | `/api/notifications`, `PATCH /api/notifications/read-all` |

Access to protected endpoints is further restricted by role-based permissions in Spring Security and method-level authorization.

## Deployment

- **Frontend:** [Vercel deployment](https://integrated-logistics-intelligence-p.vercel.app/login)
- **Backend API:** [Render service](https://shiptrack-backend-r684.onrender.com)
- **Source:** [GitHub repository](https://github.com/GxAniket/Integrated-Logistics-Intelligence-Platform)

When deploying, make sure the Vercel API URL points to the Render backend and the backend's datasource variables point to the existing PostgreSQL database. Never put database passwords or JWT secrets in frontend variables, source control, or public logs.
