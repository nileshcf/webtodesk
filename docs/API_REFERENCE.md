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

## Gateway Routing Summary

| Incoming Path | Routed To | Strip Prefix | Service Port |
| --- | --- | --- | --- |
| `/user/**` | `lb://user-service` | `/user` stripped (StripPrefix=1) | 8081 |
| `/conversion/**` | `lb://conversion-service` | `/conversion` stripped (StripPrefix=1) | 8082 |

**Forwarded Headers** (added by `HeaderForwardingFilter` for authenticated requests):

| Header | Source | Description |
| --- | --- | --- |
| `X-User-Id` | JWT `userId` claim | UUID of the authenticated user |
| `X-User-Email` | JWT `sub` claim | Email of the authenticated user |
| `X-User-Roles` | JWT `roles` claim | Stringified roles list |
