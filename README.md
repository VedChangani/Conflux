# Conflux

### Discover. Connect. Build.

**Conflux** is a marketplace for **startup ideas, projects, MVPs, and early-stage opportunities**.

Creators can publish opportunities they want to build, collaborate on, or sell. Developers, founders, designers, and entrepreneurs can discover them, connect with creators, and take them forward.

> **Discover → Evaluate → Connect → Build / Acquire**

---

## Features

- **Post Opportunities** — Ideas, Projects, MVPs, and Startups
- **Marketplace Discovery** — Search, filters, sorting, and pagination
- **Collaborate** — Find people interested in building with you
- **Acquire** — Discover projects and startups available for acquisition
- **Save Listings** — Bookmark interesting opportunities
- **Messaging** — Communicate after connecting
- **Profiles** — Showcase skills, interests, and opportunities
- **Trust & Safety** — Report problematic listings and users

---

## Core Workflow

```text
Create Opportunity
        ↓
       Draft
        ↓
      Publish
        ↓
     Discover
        ↓
   View Listing
        ↓
   Save / Interest
        ↓
    Connection
        ↓
     Messaging
        ↓
  ┌────────────────┐
  │ Collaborate    │
  │      or        │
  │ Acquire        │
  └────────────────┘
```

Listings are published directly by their owners. Admins are used for reactive trust and safety rather than mandatory approval.

---

## Architecture

Conflux is a **modular monolith** built around a REST API.

```text
┌──────────────────────┐
│     React + Vite     │
└──────────┬───────────┘
           │
        REST / JSON
           │
           ▼
┌──────────────────────────────┐
│         Spring Boot          │
│       Modular Monolith       │
│                              │
│  Auth       Users            │
│  Listings   Connections      │
│  Messaging  Saved            │
│  Reports    Admin            │
└──────────────┬───────────────┘
               │
               ▼
        ┌─────────────┐
        │    MySQL    │
        └─────────────┘
```

### Backend Structure

```text
com.conflux
├── auth
├── user
├── listing
├── connection
├── message
├── saved
├── report
├── admin
├── common
└── config
```

---

## Tech Stack

### Backend

- Java 21
- Spring Boot
- Spring Security + JWT
- Spring Data JPA / Hibernate
- Flyway
- MySQL
- Maven

### Frontend

- React
- Vite
- React Router
- Axios
- Tailwind CSS

---

## API

Base path:

```text
/api/v1
```

### Authentication

```text
POST /auth/register
POST /auth/login
GET  /auth/me
```

### Marketplace

```text
GET /listings
GET /listings/{slug}
```

### Listing Management

```text
POST   /listings
GET    /listings/mine
GET    /listings/mine/{id}
PUT    /listings/{id}
POST   /listings/{id}/publish
DELETE /listings/{id}
```

---

## Listing Model

Every opportunity has an **asset type** and a **marketplace mode**.

**Asset Type**

```text
IDEA
PROJECT
MVP
STARTUP
```

**Marketplace Mode**

```text
COLLABORATE
ACQUIRE
```

A listing starts as `DRAFT` and can be published directly by its owner.

```text
DRAFT → PUBLISHED → ARCHIVED
             ↓
         SUSPENDED
```

Only `PUBLISHED` listings are visible in the public marketplace.

---

## Testing

The backend includes automated tests covering authentication, authorization, ownership, listing lifecycle, validation, persistence, migrations, search, filtering, pagination, and error handling.

---

## Run Locally

### Backend

```bash
cd backend
.\mvnw.cmd spring-boot:run
```

### Frontend

```bash
cd client
npm install
npm run dev
```

Create `backend/.env` from `backend/.env.example` and configure MySQL and JWT settings.

---
