# WebToDesk — Subscription & Billing

> ⚠️ **STATUS: NOT YET IMPLEMENTED**
>
> This document describes the **planned** subscription and billing architecture for WebToDesk. No payment gateway integration, subscription entities, webhook handlers, or plan enforcement logic currently exists in the codebase.

---

## Table of Contents

1. [Plans Offered](#1-plans-offered)
2. [Payment Gateway Integration](#2-payment-gateway-integration)
3. [Webhook Events](#3-webhook-events)
4. [Trial Logic](#4-trial-logic)
5. [Plan Upgrade / Downgrade](#5-plan-upgrade--downgrade)
6. [Failed Payment Handling & Grace Period](#6-failed-payment-handling--grace-period)
7. [Refund Policy](#7-refund-policy)
8. [Implementation Roadmap](#8-implementation-roadmap)

---

## 1. Plans Offered

### Planned Tier Structure

| Feature | Free | Basic | Pro | Enterprise |
| --- | --- | --- | --- | --- |
| **Price** | $0/mo | $9/mo | $29/mo | Custom |
| **Conversions / month** | 2 | 10 | 50 | Unlimited |
| **Server-side .exe builds** | No | Yes | Yes | Yes |
| **Custom icon upload** | No | Yes | Yes | Yes |
| **Screenshot protection** | Yes | Yes | Yes | Yes |
| **DevTools blocking** | Yes | Yes | Yes | Yes |
| **Cross-platform builds** | Windows only | Win + Mac | Win + Mac + Linux | All + custom |
| **Version management** | No | Yes | Yes | Yes |
| **Priority support** | No | No | Yes | Yes (dedicated) |
| **White-label branding** | No | No | No | Yes |
| **API access** | No | No | Yes | Yes |
| **Team members** | 1 | 1 | 5 | Unlimited |

### Current State

- All users can create unlimited conversion projects (no enforcement)
- All features are available to all users (no gating)
- No plan concept exists in the data model
- The `Roles` enum has `ROLE_USER`, `ROLE_ADMIN`, `ROLE_MODERATOR` but these are not tied to subscription tiers

---

## 2. Payment Gateway Integration

### Recommended: Stripe (Primary) + Razorpay (India)

#### Stripe Integration Overview (Planned)

| Component | Purpose |
| --- | --- |
| **Stripe Checkout** | Hosted payment page for initial subscription |
| **Stripe Customer Portal** | Self-service plan management, billing history, payment method updates |
| **Stripe Webhooks** | Server-side event processing for payment lifecycle |
| **Stripe Billing** | Recurring subscription management with automatic invoicing |

#### Required New Components

| Component | Location | Description |
| --- | --- | --- |
| `payment-service` | New microservice | Handles Stripe API calls, webhook processing, plan management |
| `Subscription` entity | PostgreSQL (user-service) or payment-service DB | Stores plan, status, Stripe IDs, billing period |
| `PaymentEvent` entity | payment-service DB | Audit log of all payment events |
| `POST /payment/checkout` | payment-service | Creates Stripe Checkout session |
| `POST /payment/webhook` | payment-service | Receives Stripe webhook events |
| `GET /payment/subscription` | payment-service | Returns current user's subscription details |
| `POST /payment/portal` | payment-service | Creates Stripe Customer Portal session |

#### Environment Variables Required

| Variable | Description |
| --- | --- |
| `STRIPE_SECRET_KEY` | Stripe API secret key |
| `STRIPE_PUBLISHABLE_KEY` | Stripe publishable key (frontend) |
| `STRIPE_WEBHOOK_SECRET` | Webhook endpoint signing secret |
| `STRIPE_PRICE_ID_BASIC` | Stripe Price ID for Basic plan |
| `STRIPE_PRICE_ID_PRO` | Stripe Price ID for Pro plan |

---

## 3. Webhook Events

### Events to Handle (Planned)

| Stripe Event | Action |
| --- | --- |
| `checkout.session.completed` | Create subscription record, activate plan |
| `invoice.payment_succeeded` | Extend billing period, reset monthly usage counters |
| `invoice.payment_failed` | Mark subscription as `PAST_DUE`, send notification email |
| `customer.subscription.updated` | Handle plan upgrades/downgrades |
| `customer.subscription.deleted` | Mark subscription as `CANCELLED`, downgrade to Free |
| `customer.subscription.trial_will_end` | Send trial expiry reminder (3 days before) |

### Webhook Security

- All webhooks must verify the `Stripe-Signature` header using `STRIPE_WEBHOOK_SECRET`
- Webhook endpoint must be publicly accessible (not behind JWT auth)
- Idempotency: process each event ID only once (store processed event IDs)

---

## 4. Trial Logic

### Planned Trial Flow

| Aspect | Value |
| --- | --- |
| **Trial duration** | 14 days |
| **Trial plan** | Pro (full features) |
| **Credit card required** | No (for initial trial) |
| **Auto-convert after trial** | Downgrade to Free (no auto-charge) |
| **Trial per user** | One trial per email address |

### Trial Lifecycle

1. User registers → `subscription.status = TRIALING`, `trialEndsAt = now + 14 days`
2. Day 11 → Send "trial ending soon" email
3. Day 14 → Trial expires
   - If user has subscribed to a paid plan → continue with paid plan
   - If no payment → downgrade to Free tier, restrict features

---

## 5. Plan Upgrade / Downgrade

### Upgrade (e.g., Basic → Pro)

- **Effective**: Immediately
- **Billing**: Prorated — charge the difference for remaining billing period
- **Usage limits**: Immediately updated to new plan limits
- **Implementation**: Stripe handles proration automatically with `subscription.update()`

### Downgrade (e.g., Pro → Basic)

- **Effective**: At end of current billing period (not immediate)
- **Billing**: No refund for current period; new rate applies at next renewal
- **Usage limits**: Remain at current plan until period ends
- **Excess projects**: If user has more projects than new plan allows, existing projects are preserved but new creation is blocked
- **Implementation**: Stripe `subscription.update()` with `proration_behavior: 'none'` and `billing_cycle_anchor: 'unchanged'`

---

## 6. Failed Payment Handling & Grace Period

### Flow

| Day | Action |
| --- | --- |
| Day 0 | Payment fails → Stripe retries automatically (Smart Retries) |
| Day 0 | Subscription status → `PAST_DUE` |
| Day 0 | Email: "Payment failed — please update your payment method" |
| Day 3 | Stripe retry #2 |
| Day 3 | Email: "Second payment attempt failed" |
| Day 5 | Stripe retry #3 |
| Day 7 | **Grace period ends** |
| Day 7 | If still unpaid → subscription status → `CANCELLED` |
| Day 7 | User downgraded to Free tier |
| Day 7 | Email: "Your subscription has been cancelled" |

### During Grace Period (PAST_DUE)

- User retains full access to their current plan features
- A banner is shown in the dashboard: "Payment failed — update your payment method"
- User can update payment method via Stripe Customer Portal
- New conversions may be blocked (configurable)

---

## 7. Refund Policy

### Planned Policy

| Scenario | Refund |
| --- | --- |
| Cancellation within first 7 days | Full refund |
| Cancellation after 7 days | No refund (access until end of billing period) |
| Service outage > 24 hours | Prorated credit |
| Duplicate charge | Full refund |
| Unauthorized charge | Full refund |

### Refund Process

1. User requests refund via support email or admin panel
2. Admin reviews request and applies refund through Stripe Dashboard or API
3. Stripe processes refund to original payment method (5-10 business days)
4. Subscription status updated accordingly

---

## 8. Implementation Roadmap

### Phase 1 — Data Model (Estimated: S effort)

- [ ] Create `Subscription` entity in PostgreSQL

```java
@Entity
@Table(name = "subscriptions")
public class Subscription {
    @Id @GeneratedValue(strategy = GenerationType.UUID)
    private String id;
    
    @OneToOne
    @JoinColumn(name = "user_id", unique = true)
    private User user;
    
    @Enumerated(EnumType.STRING)
    private PlanType plan;           // FREE, BASIC, PRO, ENTERPRISE
    
    @Enumerated(EnumType.STRING)
    private SubStatus status;        // TRIALING, ACTIVE, PAST_DUE, CANCELLED
    
    private String stripeCustomerId;
    private String stripeSubscriptionId;
    private Instant currentPeriodStart;
    private Instant currentPeriodEnd;
    private Instant trialEndsAt;
    private int conversionsUsedThisMonth;
    
    @CreationTimestamp
    private LocalDateTime createdAt;
    @UpdateTimestamp
    private LocalDateTime updatedAt;
}
```

- [ ] Create `PlanType` and `SubStatus` enums
- [ ] Add `subscription` relationship to `User` entity

### Phase 2 — Payment Service (Estimated: L effort)

- [ ] Create `payment-service` Maven module
- [ ] Integrate Stripe Java SDK
- [ ] Implement checkout session creation
- [ ] Implement webhook endpoint with signature verification
- [ ] Implement Customer Portal session creation
- [ ] Add gateway route for `/payment/**`

### Phase 3 — Plan Enforcement (Estimated: M effort)

- [ ] Add plan check middleware to conversion-service
- [ ] Implement monthly usage counter reset (scheduler or on webhook)
- [ ] Add plan-gated feature flags
- [ ] Return plan info in user profile response

### Phase 4 — Frontend (Estimated: M effort)

- [ ] Pricing page with plan comparison
- [ ] Checkout flow integration
- [ ] Dashboard billing section
- [ ] Usage meter / limits display
- [ ] Upgrade/downgrade prompts
- [ ] Payment failure banners
