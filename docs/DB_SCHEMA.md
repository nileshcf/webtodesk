# WebToDesk — Database Schema Documentation

This document describes every table, collection, and key-value store used by WebToDesk.

---

## Database Distribution

| Store | Engine | Service | Hosting (Dev) | Purpose |
| --- | --- | --- | --- | --- |
| PostgreSQL | 15+ | user-service | Neon (cloud) | Users, profiles, roles, license metadata |
| MongoDB | 6+ | conversion-service | MongoDB Atlas | Conversion projects, feature configs, build flags |
| Redis | 7+ | user-service | Upstash (cloud) | JWT refresh token blacklist, license cache |

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

### 2.1 `conversion_projects` Collection

**Purpose**: Core conversion project data with licensing and feature configuration.

**Java Entity**: `com.example.conversion_service.entities.ConversionProject`

| Field | Type | Nullable | Description |
| --- | --- | --- | --- |
| `id` | `String` (UUID) | No | Primary key |
| `projectName` | `String` | No | Human-readable project name |
| `websiteUrl` | `String` | No | Target website URL |
| `appTitle` | `String` | No | Desktop app title |
| `iconFile` | `String` | Yes | Icon filename/path |
| `currentVersion` | `String` | No | Current app version |
| `status` | `Enum` | No | DRAFT, READY, BUILDING, FAILED, LICENSE_EXPIRED |
| `createdBy` | `String` | Yes | User ID who created project |
| `createdAt` | `Date` | No | Creation timestamp |
| `updatedAt` | `Date` | No | Last modification timestamp |
| `tier` | `Enum` | No | TRIAL, STARTER, PRO, LIFETIME |
| `licenseExpiresAt` | `Date` | Yes | License expiry date |
| `buildCount` | `Integer` | No | Number of builds used |
| `maxBuilds` | `Integer` | No | Maximum builds allowed |
| `activeAppsCount` | `Integer` | No | Number of active apps |
| `buildFlags` | `Object` | Yes | OS-specific build configuration |
| `moduleRegistry` | `Object` | Yes | Enabled/disabled modules |
| `licenseMetadata` | `Object` | Yes | License persistence data |
| `featureConfig` | `Object` | Yes | Complete feature configuration |
| `buildError` | `String` | Yes | Build error message |
| `downloadUrl` | `String` | Yes | Download URL for completed build |
| `r2Key` | `String` | Yes | Cloudflare R2 storage key |
| `buildArtifactPath` | `String` | Yes | Local build artifact path |

### 2.2 Feature Configuration Schema

**Purpose**: Detailed feature configuration for licensing and module system.

**Embedded in `conversion_projects` as `featureConfig` field:**

```javascript
{
  "tier": "TRIAL|STARTER|PRO|LIFETIME",
  "buildFlags": {
    "targetOS": "WINDOWS|LINUX|MACOS",
    "priority": "NORMAL|PRIORITY",
    "fileType": "WINDOWS_EXE|WINDOWS_MSI|LINUX_APPIMAGE|LINUX_DEB|LINUX_RPM|MACOS_DMG|MACOS_ZIP",
    "crossPlatform": boolean,
    "osFileMappings": {
      "WINDOWS": "WINDOWS_EXE",
      "LINUX": "LINUX_APPIMAGE",
      "MACOS": "MACOS_DMG"
    }
  },
  "modules": {
    "splashScreen": {
      "logoUrl": "string",
      "showOurLogo": boolean,
      "durationMs": number
    },
    "titleBar": {
      "enabled": boolean,
      "text": "string",
      "style": "default|hidden|hiddenInset"
    },
    "domainLock": {
      "allowedDomains": ["string"],
      "blockedDomains": ["string"],
      "blockMessage": "string"
    },
    "fileDownload": boolean,
    "watermark": {
      "text": "string",
      "imageUrl": "string",
      "position": "top-left|top-right|bottom-left|bottom-right|center",
      "opacity": number,
      "color": "string",
      "fontSize": number,
      "useOurBranding": boolean
    },
    "expiry": {
      "expiresAt": "ISO8601 string",
      "lockMessage": "string",
      "upgradeUrl": "string"
    },
    "screenCaptureProtection": boolean,
    "keyBindings": [
      {
        "accelerator": "string",
        "action": "string",
        "ipcChannel": "string"
      }
    ],
    "offlineCache": {
      "strategy": "cache-first|network-first|stale-while-revalidate",
      "maxAgeSeconds": number,
      "maxSizeMb": number
    },
    "autoUpdate": {
      "feedUrl": "string",
      "silent": boolean,
      "autoInstall": boolean
    },
    "nativeNotifications": boolean,
    "systemTray": {
      "tooltip": "string",
      "contextMenu": [
        {
          "label": "string",
          "action": "string",
          "ipcChannel": "string"
        }
      ]
    },
    "darkLightSync": boolean,
    "clipboardIntegration": {
      "allowRead": boolean,
      "allowWrite": boolean
    },
    "windowPolish": {
      "blur": boolean,
      "alwaysOnTop": boolean,
      "frame": boolean,
      "opacity": number,
      "vibrancy": "string"
    },
    "rightClickDisable": {
      "disable": boolean,
      "customMenuItems": [
        {
          "label": "string",
          "action": "string",
          "ipcChannel": "string"
        }
      ]
    },
    "fileSystemAccess": {
      "allowedPaths": ["string"],
      "mode": "read|read-write"
    },
    "globalHotkeys": [
      {
        "accelerator": "string",
        "action": "string",
        "ipcChannel": "string"
      }
    ]
  }
}
```

### 2.3 License Metadata Schema

**Purpose**: License persistence and upgrade tracking.

**Embedded in `conversion_projects` as `licenseMetadata` field:**

```javascript
{
  "licenseId": "UUID string",
  "issuedAt": "ISO8601 string",
  "lastValidatedAt": "ISO8601 string",
  "migrationHistory": [
    {
      "fromVersion": "string",
      "toVersion": "string",
      "migratedAt": "ISO8601 string",
      "successful": boolean,
      "metadataPreserved": ["string"]
    }
  ]
}
```

**Indexes**:

- Default `_id` index
- ⚠️ No custom index on `createdBy` — queries by user email (`findByCreatedByOrderByCreatedAtDesc`) would benefit from a compound index on `{createdBy: 1, createdAt: -1}`

**Query Methods**:

| Method | Query | Sort |
| --- | --- | --- |

---

## 3. Redis — User Service

**Purpose**: JWT refresh token blacklisting and license caching.

**Data Structure**: Key-value pairs with TTL.

### 3.1 Token Blacklist

**Key Pattern**: `blacklist:refresh:{tokenId}`

**Value**: Token metadata (JSON string)

**TTL**: 30 days (matches refresh token expiry)

**Example**:

```redis
SET blacklist:refresh:abc123 "{\"userId\":\"user-456\",\"revokedAt\":\"2023-12-01T10:00:00Z\",\"expiry\":\"2023-12-31T23:59:59Z\"}" EX 2592000
```

### 3.2 License Cache

**Key Pattern**: `license:{userId}`

**Value**: License information (JSON string)

**TTL**: 1 hour (refreshed on each access)

**Example**:

```redis
SET license:user-456 "{\"tier\":\"PRO\",\"expiresAt\":\"2025-12-31T23:59:59Z\",\"buildsUsed\":\"45\",\"buildsAllowed\":\"3000\"}" EX 3600
```

**Usage**:

- **Blacklist**: When user logs out, refresh token is added to blacklist to prevent reuse
- **License Cache**: Cache license validation results to reduce database load
- **Rate Limiting**: Track build attempts and API usage per user

**Redis Commands Used**:

- `SET key value EX ttl` — Set with expiration
- `GET key` — Retrieve cached data
- `DEL key` — Remove from blacklist or cache
- `EXISTS key` — Check if token is blacklisted
- `INCR counter` — Track usage metrics

---

## 4. Database Relationships

### 4.1 User ↔ Conversion Projects

**Relationship**: One-to-Many

- **User** (`users.id`) ← **ConversionProject** (`conversion_projects.createdBy`)
- **Cascading**: Deleting a user should cascade to their conversion projects (handled at application level)
- **Queries**: Most conversion project queries are filtered by `createdBy`

### 4.2 License ↔ Projects

**Relationship**: One-to-Many

- **License** (embedded in user profile) ← **ConversionProject** (`conversion_projects.tier`, `licenseExpiresAt`)
- **Validation**: License tier and expiry validated before build operations
- **Upgrades**: License changes propagate to all user projects

---

## 5. Licensing System Schema

### 5.1 License Tiers

**Enum**: `LicenseTier`

| Tier | Description | Build Limits | Duration | Queue Priority |
| --- | --- | --- | --- | --- |
| `TRIAL` | Free trial tier | 4 builds total (2 apps × 1 update) | 30 days | Normal |
| `STARTER` | Basic paid tier | 120 builds (10/month) | 1 year | Priority |
| `PRO` | Professional tier | 3,000 builds (50/month) | 5 years | Priority |
| `LIFETIME` | Lifetime access | Unlimited (fair use) | Unlimited | Priority |

### 5.2 Build Flags Schema

**Embedded in `conversion_projects` as `buildFlags` field:**

```javascript
{
  "targetOS": "WINDOWS|LINUX|MACOS",
  "priority": "NORMAL|PRIORITY",
  "fileType": "WINDOWS_EXE|WINDOWS_MSI|LINUX_APPIMAGE|LINUX_DEB|LINUX_RPM|MACOS_DMG|MACOS_ZIP",
  "crossPlatform": boolean,
  "osFileMappings": {
    "WINDOWS": "WINDOWS_EXE",
    "LINUX": "LINUX_APPIMAGE",
    "MACOS": "MACOS_DMG"
  }
}
```

### 5.3 Module Registry Schema

**Embedded in `conversion_projects` as `moduleRegistry` field:**

```javascript
{
  "enabled": ["splash-screen", "file-download", "domain-lock"],
  "disabled": ["screen-capture-protection", "key-bindings"],
  "config": {
    "splash-screen": { /* module-specific config */ },
    "file-download": { /* module-specific config */ }
  }
}
```

---

## 6. Performance Considerations

### 6.1 Recommended Indexes

**MongoDB Indexes for `conversion_projects`:**

```javascript
// Compound index for user project queries
db.conversionprojects.createIndex({ "createdBy": 1, "createdAt": -1 })

// Index for license expiry tracking
db.conversionprojects.createIndex({ "licenseExpiresAt": 1 })

// Index for build queue routing
db.conversionprojects.createIndex({ "buildFlags.targetOS": 1 })
db.conversionprojects.createIndex({ "buildFlags.priority": 1 })

// Index for tier-based queries
db.conversionprojects.createIndex({ "tier": 1 })

// Index for status tracking
db.conversionprojects.createIndex({ "status": 1 })
```

### 6.2 Query Optimization

**Frequent Query Patterns:**

1. **User Projects**: `find({createdBy: userId}).sort({createdAt: -1})`
2. **License Validation**: `find({createdBy: userId, tier: {$ne: null}, licenseExpiresAt: {$gt: new Date()}})`
3. **Build Queue**: `find({status: "BUILDING", "buildFlags.priority": "PRIORITY"})`
4. **Expiry Monitoring**: `find({licenseExpiresAt: {$lte: new Date(Date.now() + 7*24*60*60*1000)}})`

### 6.3 Caching Strategy

**Redis Cache Keys:**

- `license:{userId}` — User license information (1 hour TTL)
- `build-queue:stats` — Queue statistics (5 minutes TTL)
- `feature-config:{projectId}` — Project feature configuration (30 minutes TTL)
- `tier-limits:{tier}` — Tier configuration (24 hours TTL)

---

## 7. Data Migration

### 7.1 License System Migration

**Migration Steps:**

1. Add new fields to existing `conversion_projects` documents
2. Set default `tier: "TRIAL"` for all existing projects
3. Calculate `licenseExpiresAt` based on creation date + 30 days
4. Set `buildCount: 0` and `maxBuilds: 4` for trial users
5. Initialize empty `featureConfig`, `buildFlags`, and `licenseMetadata` objects

**MongoDB Migration Script:**

```javascript
db.conversionprojects.updateMany(
  { tier: { $exists: false } },
  {
    $set: {
      tier: "TRIAL",
      licenseExpiresAt: new Date(Date.now() + 30*24*60*60*1000),
      buildCount: 0,
      maxBuilds: 4,
      activeAppsCount: 0,
      buildFlags: {
        targetOS: "WINDOWS",
        priority: "NORMAL",
        fileType: "WINDOWS_EXE",
        crossPlatform: false,
        osFileMappings: {}
      },
      moduleRegistry: {
        enabled: ["splash-screen", "file-download"],
        disabled: [],
        config: {}
      },
      licenseMetadata: {
        licenseId: UUID().toString(),
        issuedAt: new Date(),
        lastValidatedAt: new Date(),
        migrationHistory: []
      },
      featureConfig: {
        tier: "TRIAL",
        buildFlags: {
          targetOS: "WINDOWS",
          priority: "NORMAL",
          fileType: "WINDOWS_EXE",
          crossPlatform: false,
          osFileMappings: {}
        },
        modules: {}
      }
    }
  }
);
```

---

## 8. API Reference Updates

### 8.1 New License Endpoints

| Method | Path | Auth | Description |
|--------|------|------|-------------|
| `GET` | `/conversion/license/current` | Yes | Get current license information |
| `GET` | `/conversion/license/dashboard` | Yes | Get license dashboard with usage stats |
| `POST` | `/conversion/license/validate` | Yes | Validate license for specific operation |
| `GET` | `/conversion/license/upgrade-options` | Yes | Get available upgrade options |
| `POST` | `/conversion/license/upgrade` | Yes | Initiate license upgrade |
| `POST` | `/conversion/license/upgrade/complete` | Yes | Complete license upgrade after payment |
| `GET` | `/conversion/license/features/{featureId}/availability` | Yes | Check if feature is available |
| `GET` | `/conversion/license/restrictions` | Yes | Get license restrictions |
| `POST` | `/conversion/license/refresh` | Yes | Refresh license cache |

### 8.2 New Build Endpoints

| Method | Path | Auth | Description |
|--------|------|------|-------------|
| `POST` | `/conversion/build/trigger` | Yes | Trigger single platform build |
| `POST` | `/conversion/build/cross-platform` | Yes | Trigger cross-platform build |
| `GET` | `/conversion/build/status/{projectId}` | Yes | Get build status |
| `GET` | `/conversion/build/progress/{projectId}` | Yes | Subscribe to build progress (SSE) |
| `GET` | `/conversion/build/queue/status` | Yes | Get queue status |
| `GET` | `/conversion/build/metrics` | Yes | Get build metrics |
| `POST` | `/conversion/build/cancel/{projectId}` | Yes | Cancel build |
| `POST` | `/conversion/build/retry/{projectId}` | Yes | Retry failed build |
| `GET` | `/conversion/build/file-types/{targetOS}` | Yes | Get available file types for OS |
| `POST` | `/conversion/build/validate-config` | Yes | Validate build configuration |
| `GET` | `/conversion/build/history/{projectId}` | Yes | Get build history |
| `GET` | `/conversion/build/logs/{projectId}` | Yes | Get build logs |

### 8.3 New Version Management Endpoints

| Method | Path | Auth | Description |
|--------|------|------|-------------|
| `GET` | `/conversion/versions/history/{projectId}` | Yes | Get version history |
| `GET` | `/conversion/versions/updates/{projectId}` | Yes | Get available updates |
| `GET` | `/conversion/versions/upgrade-dialog/{projectId}` | Yes | Get upgrade dialog information |
| `POST` | `/conversion/versions/upgrade` | Yes | Initiate version upgrade |
| `GET` | `/conversion/versions/progress/{upgradeId}` | Yes | Get upgrade progress |
| `GET` | `/conversion/versions/progress/{upgradeId}` | Yes | Subscribe to upgrade progress (SSE) |
| `GET` | `/conversion/versions/compare/{projectId}` | Yes | Compare versions |
| `GET` | `/conversion/versions/auto-upgrade/{projectId}` | Yes | Get auto-upgrade settings |
| `PUT` | `/conversion/versions/auto-upgrade/{projectId}` | Yes | Update auto-upgrade settings |
| `GET` | `/conversion/versions/rollback/capability/{projectId}` | Yes | Get rollback capability |
| `POST` | `/conversion/versions/rollback/{projectId}` | Yes | Initiate rollback |
| `GET` | `/conversion/versions/rollback/history/{projectId}` | Yes | Get rollback history |
| `POST` | `/conversion/versions/cancel/{upgradeId}` | Yes | Cancel upgrade |
| `GET` | `/conversion/versions/changelog` | Yes | Get changelog |
| `GET` | `/conversion/versions/license-compatibility/{projectId}` | Yes | Check license compatibility |

---

## 9. Frontend Integration

### 9.1 TypeScript Types

**License Types** (`frontend/src/types/license.ts`):
- `LicenseTier` enum
- `LicenseInfo` interface
- `LicenseDashboard` interface
- `UpgradeOption` interface
- `LicenseValidationResponse` interface

**Build Types** (`frontend/src/types/build.ts`):
- `TargetOS` enum
- `FileType` enum
- `BuildPriority` enum
- `BuildFlags` interface
- `BuildConfigForm` interface
- `BuildStatusResponse` interface
- `BuildProgress` interface

**Module Types** (`frontend/src/types/modules.ts`):
- `FeatureConfig` interface
- `ModuleConfig` interface
- All module-specific configuration interfaces

**Upgrade Types** (`frontend/src/types/upgrade.ts`):
- `AppVersion` interface
- `VersionUpgradeRequest` interface
- `VersionUpgradeResponse` interface
- `UpgradeDialog` interface
- `VersionHistory` interface

### 9.2 API Services

**License API** (`frontend/src/services/licenseApi.ts`):
- License management endpoints
- Upgrade flow handling
- Feature availability checking

**Build API** (`frontend/src/services/buildApi.ts`):
- Build triggering and monitoring
- Queue status tracking
- Real-time progress via SSE

**Version API** (`frontend/src/services/versionApi.ts`):
- Version upgrade management
- Rollback capabilities
- Auto-upgrade settings

### 9.3 Custom Hooks

**License Hook** (`frontend/src/hooks/useLicense.ts`):
- License state management
- Tier-based feature availability
- Upgrade flow orchestration

**Build Queue Hook** (`frontend/src/hooks/useBuildQueue.ts`):
- Real-time build monitoring
- Queue position tracking
- Cross-platform build management

---

## 10. Migration Checklist

### 10.1 Database Migration

- [ ] Add new fields to `conversion_projects` collection
- [ ] Create recommended MongoDB indexes
- [ ] Set up Redis cache keys
- [ ] Run migration script for existing projects
- [ ] Validate data integrity

### 10.2 Frontend Migration

- [ ] Add TypeScript type definitions
- [ ] Implement API service methods
- [ ] Create custom React hooks
- [ ] Update component architecture
- [ ] Add multi-step project wizard
- [ ] Implement license management UI
- [ ] Add build queue monitoring

### 10.3 Backend Migration

- [ ] Update `ConversionProject` entity
- [ ] Implement license validation service
- [ ] Add build queue routing logic
- [ ] Create new API endpoints
- [ ] Add SSE support for real-time updates
- [ ] Implement version upgrade system

### 10.4 Testing

- [ ] Unit tests for license validation
- [ ] Integration tests for build queue
- [ ] E2E tests for upgrade flow
- [ ] Performance tests for database queries
- [ ] Load tests for build queue

---

## 11. Monitoring & Observability

### 11.1 Key Metrics

**License Metrics:**
- Active licenses by tier
- License expiry rate
- Upgrade conversion rate
- Feature usage by tier

**Build Metrics:**
- Build success rate by OS
- Average build time by tier
- Queue wait times
- Cross-platform build performance

**System Metrics:**
- Database query performance
- Redis cache hit rates
- API response times
- Error rates by endpoint

### 11.2 Alerts

**Critical Alerts:**
- License expiry within 7 days
- Build queue length > 50
- Build failure rate > 10%
- Database connection failures

**Warning Alerts:**
- License expiry within 30 days
- Queue wait time > 10 minutes
- Cache hit rate < 80%
- API response time > 2 seconds

---

## 12. Summary

This comprehensive database schema documentation covers:

### 12.1 Core Components

- **PostgreSQL**: User management and authentication
- **MongoDB**: Conversion projects with licensing and feature configuration
- **Redis**: Token blacklisting and license caching

### 12.2 Licensing System

- **Four-tier model**: Trial, Starter, Pro, Lifetime
- **Build limits**: Tier-based build quotas and expiry management
- **Feature modules**: Modular architecture with tier-based availability
- **OS-specific builds**: Cross-platform support with priority queue routing

### 12.3 Frontend Integration

- **TypeScript types**: Complete type definitions for all entities
- **API services**: License, build, and version management endpoints
- **Custom hooks**: State management for license and build queue
- **Component architecture**: Multi-step wizards and real-time monitoring

### 12.4 Performance & Scalability

- **Optimized indexes**: Database performance recommendations
- **Caching strategy**: Redis-based license and feature caching
- **Real-time updates**: SSE-based build progress monitoring
- **Migration path**: Step-by-step upgrade checklist

### 12.5 Monitoring & Observability

- **Key metrics**: License usage, build performance, system health
- **Alerting**: Proactive monitoring for critical issues
- **Data integrity**: Validation and consistency checks

---

**Next Steps**:

1. Review and approve the proposed schema changes
2. Implement database migration scripts
3. Update backend services with new endpoints
4. Develop frontend components and hooks
5. Set up monitoring and alerting
6. Test end-to-end functionality
7. Deploy to production with feature flags

---

*This documentation will be updated as the licensing system implementation progresses.*
