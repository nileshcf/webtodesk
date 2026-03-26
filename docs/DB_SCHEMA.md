# WebToDesk — Database Schema Documentation

This document describes every table, collection, and key-value store used by WebToDesk.

---

## Database Distribution

| Store | Engine | Service | Hosting (Dev) | Purpose |
| --- | --- | --- | --- | --- |
| PostgreSQL | 15+ | user-service | Neon (cloud) | Users, profiles, roles |
| MongoDB | 6+ | conversion-service | MongoDB Atlas | Conversion projects |
| Redis | 7+ | user-service | Upstash (cloud) | JWT refresh token blacklist |

---

## 1. PostgreSQL — User Service

Schema is auto-managed by Hibernate JPA (`ddl-auto: update` in dev, `validate` in prod).

> ⚠️ **No Flyway/Liquibase migrations exist.** Schema changes are managed entirely through JPA entity annotations. This is a risk for production — see IMPROVEMENTS.md.

---

### 1.1 `users` Table

**Purpose**: Core user identity table. Stores credentials and account metadata.

**JPA Entity**: `com.example.user_service.entities.User`

| Column | Type | Nullable | Default | Description |
| --- | --- | --- | --- | --- |
| `id` | `VARCHAR(255)` (UUID) | No | Auto-generated (UUID strategy) | Primary key |
| `username` | `VARCHAR(255)` | No | — | Display name, unique across all users |
| `email` | `VARCHAR(255)` | No | — | Login identifier, unique across all users |
| `password` | `VARCHAR(255)` | No | — | BCrypt-hashed password |
| `email_verified` | `BOOLEAN` | No | `false` | Whether email has been verified (⚠️ verification flow not implemented) |
| `created_at` | `TIMESTAMP` | Yes | Auto (`@CreationTimestamp`) | Account creation time |
| `updated_at` | `TIMESTAMP` | Yes | Auto (`@UpdateTimestamp`) | Last modification time |

**Constraints**:

- `PK` on `id`
- `UNIQUE` on `username`
- `UNIQUE` on `email`

**Indexes** (auto-created by unique constraints):

- Unique index on `username`
- Unique index on `email`

**Notes**:

- UUID is generated as a `String` by JPA `GenerationType.UUID`, stored as `VARCHAR(255)`
- Password is hashed with `BCryptPasswordEncoder` (default strength 10)
- The `profile` field is a `@OneToOne(mappedBy = "user", cascade = CascadeType.ALL)` relationship to `user_profiles`
- The `roles` field is an `@ElementCollection` mapped to a separate `user_roles` table

---

### 1.2 `user_profiles` Table

**Purpose**: Extended user profile information. One-to-one with `users`.

**JPA Entity**: `com.example.user_service.entities.UserProfile`

| Column | Type | Nullable | Default | Description |
| --- | --- | --- | --- | --- |
| `id` | `VARCHAR(255)` (UUID) | No | Auto-generated (UUID strategy) | Primary key |
| `user_id` | `VARCHAR(255)` | No | — | Foreign key to `users.id`, unique |
| `name` | `VARCHAR(255)` | Yes | `null` | User's display/full name |
| `phone_number` | `BIGINT` | Yes | `null` | Phone number (stored as long integer) |
| `avatar_url` | `VARCHAR(255)` | Yes | `null` | URL to user's avatar image |

**Constraints**:

- `PK` on `id`
- `UNIQUE` on `user_id`
- `FK` on `user_id` → `users.id`

**Relationships**:

- `@OneToOne` with `@JoinColumn(name = "user_id", unique = true)` → `User`
- Cascade: Profile is created/saved/deleted with its parent `User` entity via `CascadeType.ALL` on the `User.profile` side

**Notes**:

- Profile is created during user registration with only `phoneNumber` populated initially
- `name` and `avatarUrl` are set later via the `PUT /user/me` endpoint
- ⚠️ No `@CreationTimestamp` / `@UpdateTimestamp` on this entity

---

### 1.3 `user_roles` Table (Collection Table)

**Purpose**: Stores role assignments for users. Element collection (not a standalone entity).

**JPA Mapping**: `@ElementCollection` on `User.roles` field

| Column | Type | Nullable | Default | Description |
| --- | --- | --- | --- | --- |
| `user_id` | `VARCHAR(255)` | No | — | Foreign key to `users.id` |
| `role` | `VARCHAR(255)` | No | — | Role name as enum string |

**Constraints**:

- `FK` on `user_id` → `users.id`

**Enum Values** (`com.example.user_service.enums.Roles`):

| Value | Description |
| --- | --- |
| `ROLE_USER` | Default role assigned on registration |
| `ROLE_ADMIN` | Admin role (⚠️ no admin endpoints or UI exist yet) |
| `ROLE_MODERATOR` | Moderator role (⚠️ no moderator logic exists yet) |

**Notes**:

- Fetched eagerly (`FetchType.EAGER`) — roles are always loaded with the user
- Stored as `@Enumerated(EnumType.STRING)` — column contains the string name of the enum
- All new users are assigned `[ROLE_USER]` during registration
- No endpoint exists to change roles — must be done directly in the database

---

## 2. MongoDB — Conversion Service

Database name: `webtodesk_conversions` (configured in MongoDB URI)

Auditing is enabled via `@EnableMongoAuditing` in `ConversionSecurityConfig`.

---

### 2.1 `conversions` Collection

**Purpose**: Stores website-to-desktop conversion project configurations.

**Document Class**: `com.example.conversion_service.entity.ConversionProject`

| Field | Type | Required | Default | Description |
| --- | --- | --- | --- | --- |
| `_id` | `ObjectId` (String) | Yes | Auto-generated by MongoDB | Document primary key |
| `projectName` | `String` | Yes | — | Sanitized project name (lowercase, alphanumeric + hyphens) |
| `websiteUrl` | `String` | Yes | — | Target website URL to wrap in Electron |
| `appTitle` | `String` | Yes | — | Display name for the desktop application |
| `iconFile` | `String` | No | `"icon.ico"` | Icon filename for the app (e.g., `icon.ico`, `icon.png`) |
| `currentVersion` | `String` | No | `"1.0.0"` | Semantic version string for the generated app |
| `status` | `String` (enum) | Yes | `"DRAFT"` | Current state of the conversion project |
| `createdBy` | `String` | Yes | — | Email of the user who created the project (from X-User-Email header) |
| `createdAt` | `ISODate` | Yes | Auto (`@CreatedDate`) | Document creation timestamp |
| `updatedAt` | `ISODate` | Yes | Auto (`@LastModifiedDate`) | Last modification timestamp |

**Status Enum** (`ConversionProject.ConversionStatus`):

| Value | Description |
| --- | --- |
| `DRAFT` | Project created but not yet generated |
| `READY` | Electron files have been generated successfully |
| `BUILDING` | ⚠️ Defined but never used — intended for server-side build pipeline |
| `FAILED` | ⚠️ Defined but never set — intended for failed builds |

**Indexes**:

- Default `_id` index
- ⚠️ No custom index on `createdBy` — queries by user email (`findByCreatedByOrderByCreatedAtDesc`) would benefit from a compound index on `{createdBy: 1, createdAt: -1}`

**Query Methods**:

| Method | Query | Sort |
| --- | --- | --- |
| `findByCreatedByOrderByCreatedAtDesc(email)` | `{createdBy: email}` | `{createdAt: -1}` |
| `findById(id)` | `{_id: id}` | — |
| `existsById(id)` | `{_id: id}` | — |
| `deleteById(id)` | `{_id: id}` | — |

**Notes**:

- Project name is sanitized on create/update: `name.toLowerCase().replaceAll("[^a-z0-9\\-]", "-")`
- The `generate` endpoint sets status to `READY` but does not track errors (no `FAILED` path)
- ⚠️ No authorization check — any authenticated user can access/modify any project by ID (only `list` filters by user email)

---

## 3. Redis — Token Blacklist

Used by the user-service to invalidate refresh tokens on logout.

**Connection**: Upstash Redis (TLS via `rediss://` protocol in dev)

---

### 3.1 Blacklist Keys

**Key Pattern**: `blacklist:{refreshToken}`

| Attribute | Value |
| --- | --- |
| **Key** | `blacklist:` + full JWT refresh token string |
| **Value** | User's email address (for debugging/auditing) |
| **TTL** | Remaining time until the refresh token's natural expiry (milliseconds) |
| **Set by** | `AuthService.logout()` |
| **Checked by** | `AuthService.refreshToken()` |

**Lifecycle**:

1. On `POST /auth/logout`: refresh token is validated, remaining TTL calculated, key stored with that TTL
2. On `POST /auth/refresh`: before issuing new access token, checks if `blacklist:{token}` key exists
3. After TTL expires: Redis automatically deletes the key (token would have expired naturally anyway)

**Notes**:

- If the refresh token is already expired at logout time, no blacklist entry is created (unnecessary)
- Key size can be large since full JWT strings are used as keys
- ⚠️ No access token blacklisting — if an access token is compromised, it remains valid for up to 15 minutes until expiry

---

## 4. Seed Data

> ⚠️ **No seed data exists.** There are no database migration scripts, seed files, or initial data loaders.

For a new deployment:

1. **PostgreSQL**: Tables are auto-created by Hibernate on first startup (`ddl-auto: update`)
2. **MongoDB**: Collection is auto-created on first document insert
3. **Redis**: No initialization needed

### Recommended Seed Data (not yet implemented)

- Default admin user with `ROLE_ADMIN`
- Subscription plan definitions (when billing is implemented)
- System configuration entries
