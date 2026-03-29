# WebToDesk — API Reference

Complete endpoint documentation for the WebToDesk API. All requests go through the **API Gateway** at `http://localhost:8080`.

---

## Table of Contents

1. [Authentication](#1-authentication)
   - [Register](#11-register)
   - [Login](#12-login)
   - [Refresh Token](#13-refresh-token)
   - [Logout](#14-logout)
2. [User Profile](#2-user-profile)
   - [Get My Profile](#21-get-my-profile)
   - [Update My Profile](#22-update-my-profile)
3. [Conversions](#3-conversions)
   - [Create Conversion](#31-create-conversion)
   - [List Conversions](#32-list-conversions)
   - [Get Conversion by ID](#33-get-conversion-by-id)
   - [Update Conversion](#34-update-conversion)
   - [Delete Conversion](#35-delete-conversion)
   - [Generate Electron Project](#36-generate-electron-project)
   - [Build Endpoints (Implemented)](#37-build-endpoints-implemented)
4. [License Management](#4-license-management)
   - [Get Current License](#41-get-current-license)
   - [Get License Dashboard](#42-get-license-dashboard)
   - [Validate License](#43-validate-license)
   - [Get Upgrade Options](#44-get-upgrade-options)
   - [Initiate License Upgrade](#45-initiate-license-upgrade)
   - [Complete License Upgrade](#46-complete-license-upgrade)
   - [Check Feature Availability](#47-check-feature-availability)
   - [Get License Restrictions](#48-get-license-restrictions)
   - [Refresh License Cache](#49-refresh-license-cache)

> ⚠️ **Scope Note**: Version-management APIs are deferred in the current implementation and are not documented as active endpoints here.

---

## Authentication Notes

- **Token type**: JWT Bearer tokens
- **Access token expiry**: 15 minutes (900,000 ms)
- **Refresh token expiry**: 30 days (2,592,000,000 ms)
- **Header format**: `Authorization: Bearer <access_token>`
- Endpoints marked **Auth: No** do not require the Authorization header
- Endpoints marked **Auth: Yes** return `401 Unauthorized` if the token is missing, expired, or invalid

---

## 1. Authentication

Base path: `/user/auth` (gateway strips `/user` prefix → service receives `/auth`)

---

### 1.1 Register

Creates a new user account with `ROLE_USER`.

**`POST /user/auth/register`**

**Auth**: No

**Request Body**:

| Field | Type | Required | Validation | Description |
| --- | --- | --- | --- | --- |
| `email` | `string` | Yes | `@NotBlank`, `@Email` | User's email address |
| `password` | `string` | Yes | `@NotBlank`, `@Size(min=6)` | Password (min 6 characters) |
| `username` | `string` | Yes | `@NotBlank` | Unique display name |
| `phoneNumber` | `number` | No | — | Phone number (stored as long) |

**Success Response** — `200 OK`:

```json
{
  "message": "User registered successfully",
  "email": "user@example.com",
  "userId": "a1b2c3d4-e5f6-7890-abcd-ef1234567890",
  "createdAt": "2025-01-15T10:30:00"
}
```

**Error Responses**:

| Status | Error Code | Condition |
| --- | --- | --- |
| `400` | `BAD_REQUEST` | Email already taken |
| `400` | `VALIDATION_FAILED` | Missing/invalid fields |
| `500` | `INTERNAL_SERVER_ERROR` | Unexpected error |

**Example**:

```bash
curl -X POST http://localhost:8080/user/auth/register \
  -H "Content-Type: application/json" \
  -d '{
    "email": "john@example.com",
    "password": "secret123",
    "username": "johndoe",
    "phoneNumber": 9876543210
  }'
```

---

### 1.2 Login

Authenticates user and returns JWT access + refresh tokens.

**`POST /user/auth/login`**

**Auth**: No

**Request Body**:

| Field | Type | Required | Validation | Description |
| --- | --- | --- | --- | --- |
| `email` | `string` | Yes | `@NotBlank`, `@Email` | Registered email |
| `password` | `string` | Yes | `@NotBlank` | Account password |

**Success Response** — `200 OK`:

```json
{
  "accessToken": "eyJhbGciOiJIUzI1NiIs...",
  "refreshToken": "eyJhbGciOiJIUzI1NiIs...",
  "tokenType": "Bearer",
  "expiresIn": 900,
  "userId": "a1b2c3d4-e5f6-7890-abcd-ef1234567890",
  "email": "john@example.com",
  "roles": ["ROLE_USER"]
}
```

**Response Fields**:

| Field | Type | Description |
| --- | --- | --- |
| `accessToken` | `string` | JWT for API authentication (15 min expiry) |
| `refreshToken` | `string` | JWT for token renewal (30 day expiry) |
| `tokenType` | `string` | Always `"Bearer"` |
| `expiresIn` | `number` | Access token TTL in **seconds** |
| `userId` | `string` | UUID of the authenticated user |
| `email` | `string` | User's email |
| `roles` | `string[]` | Role names (e.g., `["ROLE_USER"]`) |

**Access Token Claims**:

| Claim | Type | Description |
| --- | --- | --- |
| `sub` | `string` | User email |
| `userId` | `string` | User UUID |
| `roles` | `string[]` | Role names |
| `email` | `string` | User email |
| `username` | `string` | Display name |
| `type` | `string` | Always `"access"` |
| `iat` | `number` | Issued at (epoch seconds) |
| `exp` | `number` | Expiration (epoch seconds) |

**Error Responses**:

| Status | Error Code | Condition |
| --- | --- | --- |
| `401` | `INVALID_CREDENTIALS` | Wrong email or password |
| `400` | `VALIDATION_FAILED` | Missing/invalid fields |

**Example**:

```bash
curl -X POST http://localhost:8080/user/auth/login \
  -H "Content-Type: application/json" \
  -d '{
    "email": "john@example.com",
    "password": "secret123"
  }'
```

---

### 1.3 Refresh Token

Exchanges a valid refresh token for a new access token. Does **not** rotate the refresh token.

**`POST /user/auth/refresh`**

**Auth**: No (uses refresh token in body, not Authorization header)

**Request Body**:

| Field | Type | Required | Validation | Description |
| --- | --- | --- | --- | --- |
| `refreshToken` | `string` | Yes | `@NotBlank` | Current refresh token |

**Success Response** — `200 OK`:

```json
{
  "accessToken": "eyJhbGciOiJIUzI1NiIs...",
  "tokenType": "Bearer",
  "expiresIn": 900
}
```

**Error Responses**:

| Status | Error Code | Condition |
| --- | --- | --- |
| `400` | `BAD_REQUEST` | Token expired, invalid, or blacklisted |
| `400` | `VALIDATION_FAILED` | Missing refresh token |

**Example**:

```bash
curl -X POST http://localhost:8080/user/auth/refresh \
  -H "Content-Type: application/json" \
  -d '{
    "refreshToken": "eyJhbGciOiJIUzI1NiIs..."
  }'
```

---

### 1.4 Logout

Blacklists the refresh token in Redis so it can no longer be used for token refresh.

**`POST /user/auth/logout`**

**Auth**: Yes (access token required in Authorization header)

**Headers**:

| Header | Required | Description |
| --- | --- | --- |
| `Authorization` | Yes | `Bearer <access_token>` |

**Request Body**:

| Field | Type | Required | Validation | Description |
| --- | --- | --- | --- | --- |
| `refreshToken` | `string` | Yes | `@NotBlank` | Refresh token to invalidate |

**Success Response** — `200 OK`:

```json
{
  "message": "Logged out successfully"
}
```

**Error Responses**:

| Status | Error Code | Condition |
| --- | --- | --- |
| `401` | — | Missing or invalid access token (gateway level) |
| `400` | `BAD_REQUEST` | Invalid or expired refresh token |

**Example**:

```bash
curl -X POST http://localhost:8080/user/auth/logout \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer eyJhbGciOiJIUzI1NiIs..." \
  -d '{
    "refreshToken": "eyJhbGciOiJIUzI1NiIs..."
  }'
```

---

## 2. User Profile

Base path: `/user/me` (gateway strips `/user` prefix → service receives `/me`)

---

### 2.1 Get My Profile

Returns the authenticated user's full profile.

**`GET /user/me`**

**Auth**: Yes

**Success Response** — `200 OK`:

```json
{
  "userId": "a1b2c3d4-e5f6-7890-abcd-ef1234567890",
  "email": "john@example.com",
  "username": "johndoe",
  "name": "John Doe",
  "phoneNumber": 9876543210,
  "avatarUrl": "https://example.com/avatar.jpg",
  "roles": ["ROLE_USER"],
  "emailVerified": false,
  "createdAt": "2025-01-15T10:30:00Z",
  "updatedAt": "2025-01-15T12:00:00Z"
}
```

**Error Responses**:

| Status | Error Code | Condition |
| --- | --- | --- |
| `401` | — | Not authenticated |
| `400` | `BAD_REQUEST` | User not found (should not happen for authenticated user) |

**Example**:

```bash
curl http://localhost:8080/user/me \
  -H "Authorization: Bearer eyJhbGciOiJIUzI1NiIs..."
```

---

### 2.2 Update My Profile

Partially updates the authenticated user's profile. Only provided fields are updated.

**`PUT /user/me`**

**Auth**: Yes

**Request Body** (all fields optional):

| Field | Type | Required | Validation | Description |
| --- | --- | --- | --- | --- |
| `username` | `string` | No | Must be unique | New display name |
| `name` | `string` | No | — | Full/display name |
| `phoneNumber` | `number` | No | — | Phone number |
| `avatarUrl` | `string` | No | — | Avatar image URL |

**Success Response** — `200 OK`:

```json
{
  "userId": "a1b2c3d4-e5f6-7890-abcd-ef1234567890",
  "email": "john@example.com",
  "username": "johnnew",
  "name": "John Doe",
  "phoneNumber": 9876543210,
  "avatarUrl": "https://example.com/new-avatar.jpg",
  "roles": ["ROLE_USER"],
  "emailVerified": false,
  "createdAt": "2025-01-15T10:30:00Z",
  "updatedAt": "2025-01-16T08:00:00Z"
}
```

**Error Responses**:

| Status | Error Code | Condition |
| --- | --- | --- |
| `401` | — | Not authenticated |
| `400` | `BAD_REQUEST` | Username already taken by another user |

**Example**:

```bash
curl -X PUT http://localhost:8080/user/me \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer eyJhbGciOiJIUzI1NiIs..." \
  -d '{
    "username": "johnnew",
    "name": "John Doe"
  }'
```

---

## 3. Conversions

Base path: `/conversion/conversions` (gateway strips `/conversion` prefix → service receives `/conversions`)

---

### 3.1 Create Conversion

Creates a new website-to-desktop conversion project.

**`POST /conversion/conversions`**

**Auth**: Yes

**Request Body**:

| Field | Type | Required | Validation | Description |
| --- | --- | --- | --- | --- |
| `projectName` | `string` | Yes | `@NotBlank` | Project identifier (will be sanitized to lowercase alphanumeric + hyphens) |
| `websiteUrl` | `string` | Yes | `@NotBlank`, `@URL` | Target website URL |
| `appTitle` | `string` | Yes | `@NotBlank` | Desktop app display title |
| `iconFile` | `string` | No | — | Icon filename (default: `"icon.ico"`) |

**Success Response** — `200 OK`:

```json
{
  "id": "6789abcd1234ef5678901234",
  "projectName": "my-cool-app",
  "websiteUrl": "https://example.com",
  "appTitle": "My Cool App",
  "iconFile": "icon.ico",
  "currentVersion": "1.0.0",
  "status": "DRAFT",
  "createdBy": "john@example.com",
  "createdAt": "2025-01-15T10:30:00Z",
  "updatedAt": "2025-01-15T10:30:00Z"
}
```

**Error Responses**:

| Status | Error Code | Condition |
| --- | --- | --- |
| `401` | — | Not authenticated |
| `400` | `VALIDATION_FAILED` | Missing/invalid fields |

**Example**:

```bash
curl -X POST http://localhost:8080/conversion/conversions \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer eyJhbGciOiJIUzI1NiIs..." \
  -d '{
    "projectName": "My Cool App",
    "websiteUrl": "https://example.com",
    "appTitle": "My Cool App",
    "iconFile": "custom-icon.ico"
  }'
```

---

### 3.2 List Conversions

Returns all conversion projects created by the authenticated user, sorted by creation date (newest first).

**`GET /conversion/conversions`**

**Auth**: Yes

**Success Response** — `200 OK`:

```json
[
  {
    "id": "6789abcd1234ef5678901234",
    "projectName": "my-cool-app",
    "websiteUrl": "https://example.com",
    "appTitle": "My Cool App",
    "iconFile": "icon.ico",
    "currentVersion": "1.0.0",
    "status": "READY",
    "createdBy": "john@example.com",
    "createdAt": "2025-01-15T10:30:00Z",
    "updatedAt": "2025-01-15T11:00:00Z"
  }
]
```

**Example**:

```bash
curl http://localhost:8080/conversion/conversions \
  -H "Authorization: Bearer eyJhbGciOiJIUzI1NiIs..."
```

---

### 3.3 Get Conversion by ID

Returns a single conversion project by its ID.

**`GET /conversion/conversions/{id}`**

**Auth**: Yes

**Path Parameters**:

| Parameter | Type | Description |
| --- | --- | --- |
| `id` | `string` | MongoDB document ID |

**Success Response** — `200 OK`:

```json
{
  "id": "6789abcd1234ef5678901234",
  "projectName": "my-cool-app",
  "websiteUrl": "https://example.com",
  "appTitle": "My Cool App",
  "iconFile": "icon.ico",
  "currentVersion": "1.0.0",
  "status": "DRAFT",
  "createdBy": "john@example.com",
  "createdAt": "2025-01-15T10:30:00Z",
  "updatedAt": "2025-01-15T10:30:00Z"
}
```

**Error Responses**:

| Status | Error Code | Condition |
| --- | --- | --- |
| `401` | — | Not authenticated |
| `400` | `BAD_REQUEST` | Project not found |

> ⚠️ **Security Gap**: No ownership check — any authenticated user can access any project by ID.

**Example**:

```bash
curl http://localhost:8080/conversion/conversions/6789abcd1234ef5678901234 \
  -H "Authorization: Bearer eyJhbGciOiJIUzI1NiIs..."
```

---

### 3.4 Update Conversion

Partially updates a conversion project. Only provided fields are updated.

**`PUT /conversion/conversions/{id}`**

**Auth**: Yes

**Path Parameters**:

| Parameter | Type | Description |
| --- | --- | --- |
| `id` | `string` | MongoDB document ID |

**Request Body** (all fields optional):

| Field | Type | Required | Description |
| --- | --- | --- | --- |
| `projectName` | `string` | No | New project name (will be sanitized) |
| `websiteUrl` | `string` | No | New target URL |
| `appTitle` | `string` | No | New display title |
| `iconFile` | `string` | No | New icon filename |
| `currentVersion` | `string` | No | New version string |

**Success Response** — `200 OK`: Same shape as Create response with updated values.

**Error Responses**:

| Status | Error Code | Condition |
| --- | --- | --- |
| `401` | — | Not authenticated |
| `400` | `BAD_REQUEST` | Project not found |

> ⚠️ **Security Gap**: No ownership check — any authenticated user can update any project.

**Example**:

```bash
curl -X PUT http://localhost:8080/conversion/conversions/6789abcd1234ef5678901234 \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer eyJhbGciOiJIUzI1NiIs..." \
  -d '{
    "appTitle": "Updated App Title",
    "currentVersion": "1.1.0"
  }'
```

---

### 3.5 Delete Conversion

Permanently deletes a conversion project.

**`DELETE /conversion/conversions/{id}`**

**Auth**: Yes

**Path Parameters**:

| Parameter | Type | Description |
| --- | --- | --- |
| `id` | `string` | MongoDB document ID |

**Success Response** — `204 No Content` (empty body)

**Error Responses**:

| Status | Error Code | Condition |
| --- | --- | --- |
| `401` | — | Not authenticated |
| `400` | `BAD_REQUEST` | Project not found |

> ⚠️ **Security Gap**: No ownership check — any authenticated user can delete any project.
> ⚠️ **No soft delete**: Projects are permanently removed from MongoDB.

**Example**:

```bash
curl -X DELETE http://localhost:8080/conversion/conversions/6789abcd1234ef5678901234 \
  -H "Authorization: Bearer eyJhbGciOiJIUzI1NiIs..."
```

---

### 3.6 Generate Electron Project

Generates the complete Electron project source files for a conversion project. Returns file contents as a JSON map (filename → content string).

**`POST /conversion/conversions/{id}/generate`**

**Auth**: Yes

**Path Parameters**:

| Parameter | Type | Description |
| --- | --- | --- |
| `id` | `string` | MongoDB document ID |

**Success Response** — `200 OK`:

```json
{
  "projectName": "my-cool-app",
  "appTitle": "My Cool App",
  "websiteUrl": "https://example.com",
  "files": {
    "config.js": "// Generated by WebToDesk Conversion Service\nmodule.exports = {\n  projectName: 'my-cool-app',\n  ...\n};",
    "main.js": "const { app, BrowserWindow, globalShortcut, ipcMain, shell } = require('electron');\n...",
    "preload.js": "const { contextBridge, ipcRenderer } = require('electron');\n...",
    "package.json": "{\n  \"name\": \"my-cool-app\",\n  \"version\": \"1.0.0\",\n  ...\n}"
  }
}
```

**Generated Files**:

| File | Purpose |
| --- | --- |
| `config.js` | Project configuration (name, version, URL, icon) |
| `main.js` | Electron main process: window creation, screenshot protection shortcuts, DevTools blocking |
| `preload.js` | Context bridge + screenshot protection UI overlay (10-second blackout with countdown) |
| `package.json` | NPM package with electron + electron-builder dependencies and build config |

**Side Effect**: Sets the project's `status` to `READY` in MongoDB.

**Error Responses**:

| Status | Error Code | Condition |
| --- | --- | --- |
| `401` | — | Not authenticated |
| `400` | `BAD_REQUEST` | Project not found |

> ⚠️ **Security Gap**: No ownership check.

**Example**:

```bash
curl -X POST http://localhost:8080/conversion/conversions/6789abcd1234ef5678901234/generate \
  -H "Authorization: Bearer eyJhbGciOiJIUzI1NiIs..."
```

---

### 3.7 Build Endpoints (Implemented)

The codebase currently exposes build APIs through both legacy conversion routes and dedicated build routes.

| Endpoint | Auth | Description |
| --- | --- | --- |
| `POST /conversion/conversions/{id}/build` | Yes | Trigger async build for a conversion project |
| `GET /conversion/conversions/{id}/build/status` | Yes | Poll build status |
| `GET /conversion/conversions/{id}/build/stream` | Yes | SSE progress stream |
| `GET /conversion/conversions/{id}/build/download` | Yes | Redirect to artifact URL |
| `POST /conversion/build/trigger` | Yes | Trigger build by request body (`projectId`) |
| `GET /conversion/build/status/{projectId}` | Yes | Poll build status |
| `GET /conversion/build/progress/{projectId}` | Yes | SSE progress stream |
| `GET /conversion/build/queue/status` | Yes | Queue metrics |
| `POST /conversion/build/retry/{projectId}` | Yes | Retry build |
| `POST /conversion/build/cancel/{projectId}` | Yes | Cancel request (currently stub response) |
| `GET /conversion/build/file-types/{targetOS}` | Yes | Supported package types per OS |
| `POST /conversion/build/validate-config` | Yes | Build config validation (currently stub response) |
| `GET /conversion/build/metrics` | Yes | User build metrics |
| `GET /conversion/build/metrics/{projectId}` | Yes | Project build metrics |
| `GET /conversion/build/history/{projectId}` | Yes | Build history |
| `GET /conversion/build/modules?tier=<TIER>` | Yes | Module availability by tier (`TRIAL`, `STARTER`, `PRO`, `LIFETIME`) |
| `GET /conversion/build/download/{projectId}` | Yes | Redirect to artifact URL |
| `GET /conversion/build/logs/{projectId}` | Yes | Last known build log summary |

> ℹ️ Cross-platform trigger/version-management endpoints are not active in the current backend API surface.

---

## Error Response Shape

All error responses follow the standard `ErrorResponse` format:

```json
{
  "error": "ERROR_CODE",
  "message": "Human-readable description",
  "status": 400,
  "timestamp": "2025-01-15T10:30:00Z"
}
```

| Field | Type | Description |
| --- | --- | --- |
| `error` | `string` | Machine-readable error code |
| `message` | `string` | Human-readable error description |
| `status` | `number` | HTTP status code |
| `timestamp` | `string` | ISO-8601 timestamp of the error |

### Error Codes Reference

| Code | HTTP Status | Source |
| --- | --- | --- |
| `INVALID_CREDENTIALS` | 401 | Bad email or password |
| `USER_NOT_FOUND` | 404 | User email not in database |
| `ACCOUNT_DISABLED` | 403 | User account is disabled |
| `VALIDATION_FAILED` | 400 | Request body validation failed |
| `BAD_REQUEST` | 400 | Generic runtime error |
| `INTERNAL_SERVER_ERROR` | 500 | Unexpected exception |

---

## 4. License Management

Base path: `/conversion/license` (gateway strips `/conversion` prefix → service receives `/license`)

> ⚠️ Payment provider integration is currently deferred; upgrade endpoints are available for workflow/testing but should be treated as non-final billing behavior.

---

### 4.1 Get Current License

Retrieves the current license information for the authenticated user.

**`GET /conversion/license/current`**

**Auth**: Yes

**Success Response** — `200 OK`:

```json
{
  "licenseId": "uuid-string",
  "tier": "TRIAL|STARTER|PRO|LIFETIME",
  "expiresAt": "2025-12-31T23:59:59Z",
  "buildsUsed": 45,
  "buildsAllowed": 3000,
  "activeAppsCount": 3,
  "features": {
    "splashScreen": true,
    "fileDownload": true,
    "screenCaptureProtection": true,
    "watermark": true,
    "keyBindings": true,
    "offlineCache": true,
    "autoUpdate": true,
    "notifications": true,
    "systemTray": true,
    "darkLightSync": true,
    "clipboardIntegration": true,
    "windowPolish": true,
    "rightClickDisable": true,
    "fileSystemAccess": true,
    "globalHotkeys": true
  },
  "restrictions": {
    "maxProjects": 10,
    "maxBuildsPerMonth": 50,
    "maxFileSize": 100,
    "allowedOS": ["WINDOWS", "LINUX", "MACOS"]
  }
}
```

**Error Responses**:

| Status | Error Code | Condition |
| --- | --- | --- |
| `401` | `UNAUTHORIZED` | Invalid or expired token |
| `403` | `LICENSE_EXPIRED` | License has expired |
| `404` | `LICENSE_NOT_FOUND` | No license found for user |

---

### 4.2 Get License Dashboard

Retrieves dashboard statistics and usage information.

**`GET /conversion/license/dashboard`**

**Auth**: Yes

**Success Response** — `200 OK`:

```json
{
  "currentTier": "PRO",
  "expiresAt": "2025-12-31T23:59:59Z",
  "daysUntilExpiry": 45,
  "buildsUsed": 45,
  "buildsAllowed": 3000,
  "buildsRemaining": 2955,
  "activeProjects": 3,
  "maxProjects": 10,
  "usage": {
    "buildsThisMonth": 12,
    "buildsLastMonth": 8,
    "totalBuilds": 45,
    "averageBuildTime": "2m 30s",
    "successRate": 0.95
  },
  "upgradeOptions": [
    {
      "fromTier": "PRO",
      "toTier": "LIFETIME",
      "price": 299,
      "currency": "USD",
      "benefits": [
        "Unlimited builds",
        "Lifetime access",
        "Priority support",
        "All future features"
      ]
    }
  ],
  "featureUsage": {
    "splashScreen": { "used": true, "lastUsed": "2025-01-15T10:30:00Z" },
    "fileDownload": { "used": true, "lastUsed": "2025-01-14T15:20:00Z" },
    "screenCaptureProtection": { "used": false, "lastUsed": null }
  }
}
```

---

### 4.3 Validate License

Validates if a specific feature or operation is allowed under current license.

**`POST /conversion/license/validate`**

**Auth**: Yes

**Request Body**:

| Field | Type | Required | Description |
| --- | --- | --- | --- |
| `featureId` | `string` | Yes | Feature identifier to validate |
| `operation` | `string` | No | Specific operation (e.g., "build", "download") |
| `targetOS` | `string` | No | Target OS for build operations |

**Success Response** — `200 OK`:

```json
{
  "valid": true,
  "featureId": "screenCaptureProtection",
  "tier": "PRO",
  "expiresAt": "2025-12-31T23:59:59Z",
  "restrictions": {
    "maxUsage": 100,
    "currentUsage": 45,
    "resetDate": "2025-02-01T00:00:00Z"
  },
  "message": "Feature is available"
}
```

**Error Responses**:

| Status | Error Code | Condition |
| --- | --- | --- |
| `403` | `FEATURE_NOT_AVAILABLE` | Feature not available for current tier |
| `403` | `LICENSE_EXPIRED` | License has expired |
| `429` | `RATE_LIMIT_EXCEEDED` | Feature usage limit exceeded |

---

### 4.4 Get Upgrade Options

Retrieves available upgrade options for current license.

**`GET /conversion/license/upgrade-options`**

**Auth**: Yes

**Success Response** — `200 OK`:

```json
{
  "currentTier": "STARTER",
  "availableUpgrades": [
    {
      "fromTier": "STARTER",
      "toTier": "PRO",
      "price": 29,
      "currency": "USD",
      "billingCycle": "monthly",
      "discount": 0,
      "benefits": [
        "3,000 builds (50/month)",
        "5 year duration",
        "Priority support",
        "Advanced features",
        "Cross-platform builds"
      ],
      "migrationPath": "automatic",
      "preservesData": true
    },
    {
      "fromTier": "STARTER",
      "toTier": "LIFETIME",
      "price": 299,
      "currency": "USD",
      "billingCycle": "once",
      "discount": 10,
      "benefits": [
        "Unlimited builds",
        "Lifetime access",
        "All features",
        "Priority support"
      ],
      "migrationPath": "automatic",
      "preservesData": true
    }
  ]
}
```

---

### 4.5 Initiate License Upgrade

Starts the license upgrade process.

**`POST /conversion/license/upgrade`**

**Auth**: Yes

**Request Body**:

| Field | Type | Required | Description |
| --- | --- | --- | --- |
| `tier` | `string` | Yes | Target license tier |
| `billingCycle` | `string` | Yes | Billing cycle |

**Success Response** — `200 OK`:

```json
{
  "upgradeId": "uuid-string",
  "status": "PENDING_PAYMENT",
  "fromTier": "STARTER",
  "toTier": "PRO",
  "price": 29,
  "currency": "USD",
  "paymentUrl": "https://payment-provider.com/pay/uuid-string",
  "expiresAt": "2025-01-15T11:00:00Z",
  "estimatedProcessingTime": "2-5 minutes"
}
```

---

### 4.6 Complete License Upgrade

Completes an upgrade session.

**`POST /conversion/license/upgrade/complete`**

**Auth**: Yes

**Request Body**:

| Field | Type | Required | Description |
| --- | --- | --- | --- |
| `sessionId` | `string` | Yes | Upgrade session ID |

**Success Response** — `200 OK`:

```json
{
  "upgradeId": "uuid-string",
  "status": "COMPLETED",
  "newTier": "PRO",
  "previousTier": "STARTER",
  "upgradedAt": "2025-01-15T10:45:00Z",
  "expiresAt": "2030-01-15T23:59:59Z",
  "buildsAllowed": 3000,
  "featuresUnlocked": [
    "screenCaptureProtection",
    "customWatermark",
    "keyBindings",
    "offlineCache"
  ],
  "migrationSummary": {
    "projectsMigrated": 3,
    "dataPreserved": true,
    "downtime": "30 seconds"
  }
}
```

---

### 4.7 Check Feature Availability

Checks if a specific feature is available for the current license tier.

**`GET /conversion/license/features/{featureId}/availability`**

**Auth**: Yes

**Path Parameters**:

| Parameter | Type | Description |
| --- | --- | --- |
| `featureId` | `string` | Feature identifier |

**Success Response** — `200 OK`:

```json
{
  "featureId": "screenCaptureProtection",
  "available": true,
  "tier": "PRO",
  "description": "Prevent screenshots and screen recordings",
  "restrictions": {
    "maxUsage": null,
    "requiresPayment": false,
    "availableInTrial": false
  },
  "configuration": {
    "supportedOS": ["WINDOWS", "LINUX", "MACOS"],
    "requiresRestart": false,
    "userConfigurable": true
  }
}
```

---

### 4.8 Get License Restrictions

Retrieves all current license restrictions and limits.

**`GET /conversion/license/restrictions`**

**Auth**: Yes

**Success Response** — `200 OK`:

```json
{
  "tier": "PRO",
  "expiresAt": "2025-12-31T23:59:59Z",
  "restrictions": {
    "projects": {
      "maxActive": 10,
      "current": 3,
      "remaining": 7
    },
    "builds": {
      "maxPerMonth": 50,
      "usedThisMonth": 12,
      "remaining": 38,
      "totalUsed": 45
    },
    "storage": {
      "maxFileSize": 100,
      "unit": "MB"
    },
    "features": {
      "restrictedInTrial": [
        "screenCaptureProtection",
        "customWatermark",
        "keyBindings"
      ],
      "premiumOnly": [
        "fileSystemAccess",
        "globalHotkeys"
      ]
    },
    "os": {
      "supported": ["WINDOWS", "LINUX", "MACOS"],
      "crossPlatformBuilds": true
    },
    "api": {
      "rateLimitPerMinute": 100,
      "concurrentBuilds": 3
    }
  }
}
```

---

### 4.9 Refresh License Cache

Refreshes the license cache for the current user.

**`POST /conversion/license/refresh`**

**Auth**: Yes

**Success Response** — `200 OK`:

```json
{
  "message": "License cache refreshed",
  "previousCacheExpiry": "2025-01-15T10:30:00Z",
  "newCacheExpiry": "2025-01-15T11:30:00Z",
  "changes": {
    "tierUpdated": false,
    "featuresUpdated": false,
    "restrictionsUpdated": false
  }
}
```

---
| `/user/**` | `lb://user-service` | `/user` stripped (StripPrefix=1) | 8081 |
| `/conversion/**` | `lb://conversion-service` | `/conversion` stripped (StripPrefix=1) | 8082 |

**Forwarded Headers** (added by `HeaderForwardingFilter` for authenticated requests):

| Header | Source | Description |
| --- | --- | --- |
| `X-User-Id` | JWT `userId` claim | UUID of the authenticated user |
| `X-User-Email` | JWT `sub` claim | Email of the authenticated user |
| `X-User-Roles` | JWT `roles` claim | Stringified roles list |
