# Polar Integration

## Overview
WellnessWingman integrates with [Polar](https://www.polar.com/) fitness devices via Polar's AccessLink API. Authentication uses OAuth 2.0 with a server-side broker that keeps the client secret off the mobile device.

## Architecture

```mermaid
sequenceDiagram
    participant User
    participant App as Android App
    participant Browser as Chrome Custom Tab
    participant Polar as Polar Auth
    participant Broker as Cloud Function
    participant Firestore

    User->>App: Tap "Connect Polar"
    App->>Browser: Open Polar authorization URL
    Browser->>Polar: User consents
    Polar->>Broker: GET /oauth/callback?code=...&state=...
    Broker->>Polar: Exchange code for tokens (Basic auth)
    Polar-->>Broker: access_token, refresh_token
    Broker->>Broker: Encrypt tokens (AES-256-GCM)
    Broker->>Firestore: Store encrypted session
    Broker-->>Browser: Redirect wellnesswingman://oauth/result?session=<id>
    Browser->>App: Deep link → MainActivity.onNewIntent
    App->>App: PendingOAuthResultStore.deliver()
    App->>Broker: POST /oauth/redeem {session_id}
    Broker->>Firestore: Read & mark redeemed
    Broker->>Broker: Decrypt tokens
    Broker-->>App: {tokens}
    App->>App: Store in EncryptedSharedPreferences
    App->>User: UI shows "Connected"
```

## Components

### Backend: `polar-oauth-broker/`

A Python Cloud Function (Google Cloud Functions gen2) with four routes:

| Route | Method | Purpose |
|-------|--------|---------|
| `/oauth/callback` | GET | Polar redirects here after user consent. Exchanges authorization code for tokens using Basic auth (Polar v4), encrypts tokens with AES-256-GCM, stores in Firestore, redirects to app via custom scheme. |
| `/oauth/redeem` | POST | App sends `{session_id}`. One-time pickup: decrypts tokens, returns JSON, marks session redeemed. Returns 410 on replay. |
| `/oauth/refresh` | POST | Stateless proxy: app sends `{refresh_token}`, broker forwards to Polar with client credentials, returns new tokens. No Firestore involved. |
| `/.well-known/assetlinks.json` | GET | Placeholder for future App Links verification. |

**Infrastructure** is managed with Terraform (`polar-oauth-broker/infra/`):
- Cloud Function gen2 (Python 3.12, 256MB, min-instances=0)
- Firestore with TTL policy on `oauth_sessions` (10-minute expiry)
- Secret Manager for `polar-client-secret` and `polar-oauth-session-key`
- Dedicated service account with `datastore.user` + `secretmanager.secretAccessor`
- Public invocation IAM (Polar must reach the callback)

### Android Client

**Token storage:** `EncryptedSharedPreferences` via `AppSettingsRepository` methods (`getPolarAccessToken`, `setPolarRefreshToken`, etc.). All Polar keys use the `polar_` prefix.

**Deep link flow:**
1. `MainActivity` has `singleTask` launch mode and an intent-filter for `wellnesswingman://oauth/result`
2. `handleOAuthDeepLink()` extracts `session` and `state` parameters
3. Delivers to `PendingOAuthResultStore` (in-memory `StateFlow` singleton)
4. `PolarSettingsViewModel` observes the store and auto-triggers session redemption

**Browser launch:** `OAuthBrowserLauncher` is an `expect`/`actual` composable. Android uses `CustomTabsIntent`, desktop is a no-op.

## Configuration

### Local development

Add to `local.properties` (see `local.properties.example`):

```properties
polar.client.id=your-polar-client-id
polar.broker.base.url=https://your-cloud-function-url
```

These become `BuildConfig.POLAR_CLIENT_ID` and `BuildConfig.POLAR_BROKER_BASE_URL`, provided to `PolarOAuthConfig` via Koin in `WellnessWingmanApp`.

### Backend deployment

```bash
cd polar-oauth-broker/infra
cp terraform.tfvars.example terraform.tfvars
# Edit terraform.tfvars with real values
terraform init
terraform apply
```

After initial apply, update the secret values:

```bash
echo -n "real-client-secret" | gcloud secrets versions add polar-client-secret --data-file=-
python3 -c "import os; print(os.urandom(32).hex())" | gcloud secrets versions add polar-oauth-session-key --data-file=-
```

## Verification

1. **Backend curl test:** Deploy function → open Polar auth URL in browser → consent → grab `session_id` from redirect → `curl -X POST <url>/oauth/redeem -H 'Content-Type: application/json' -d '{"session_id":"<id>"}'` → tokens returned → repeat → 410 Gone
2. **Migration test:** Install current app (plain prefs), then install updated build → verify settings preserved in encrypted prefs
3. **Deep link test:** `adb shell am start -a android.intent.action.VIEW -d "wellnesswingman://oauth/result?session=test&state=abc"` → verify `PendingOAuthResultStore` receives it
4. **End-to-end:** Tap Connect → Chrome Custom Tab → Polar consent → redirect → app shows "Connected" with user ID
5. **Disconnect:** Tap Disconnect → tokens cleared → UI shows "Connect" button

## Scope Boundaries (Milestone 1)

Not included in the initial implementation:
- No sync orchestrator / WorkManager / Polar data fetching
- No App Links verification (custom scheme only)
- No iOS support
- No Cloud Armor / rate limiting
- No `/start` endpoint (state generated on-device)
