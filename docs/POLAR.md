# Polar Integration

## Overview

WellnessWingman integrates with Polar AccessLink through an OAuth broker that keeps the Polar client secret off-device. The current implementation covers:

- Android OAuth connect/disconnect flow
- broker-backed session redemption and refresh
- shared Polar API client and DTO mapping
- persisted sync checkpoints and metric storage
- foreground refresh orchestration
- Android periodic background sync through WorkManager
- summary-facing Polar insight generation

This document reflects the post-March-2026 implementation state.

## Components

### Android app

- launches the Polar consent flow in a browser/custom tab
- receives `wellnesswingman://oauth/result` deep links
- stores pending OAuth state so redemption can survive process death
- stores Polar tokens through the app settings repository
- schedules a periodic `PolarSyncWorker` on Android

### Shared code

- `PolarOAuthRepository`: redeem and refresh token flows
- `PolarApiClient`: AccessLink requests and DTO mapping
- `PolarSyncRepository`: SQLDelight-backed persistence for metrics and checkpoints
- `PolarSyncOrchestrator`: refresh logic and sync coordination
- `PolarInsightService`: transforms synced data into summary-friendly context

### Broker backend

`polar-oauth-broker/` contains:

- `main.py`: callback, redeem, refresh, and assetlinks routes
- `crypto.py`: token encryption helpers
- `infra/`: Terraform for Cloud Function, Firestore, Secret Manager, and IAM

## OAuth Flow

1. User taps connect in Polar settings.
2. Android opens the Polar authorization URL.
3. Polar redirects to the broker callback.
4. The broker exchanges the code for tokens, encrypts them, and stores a short-lived session.
5. The broker redirects back to `wellnesswingman://oauth/result?...`.
6. `MainActivity` delivers the pending result.
7. `PolarOAuthRepository` redeems the one-time session and persists the tokens locally.

If the app process dies before redemption completes, `WellnessWingmanApp` retries redemption on the next launch while the broker session is still valid.

## Local Configuration

Add these to `local.properties`:

```properties
polar.client.id=your-polar-client-id
polar.broker.base.url=https://your-cloud-function-url
```

These values become Android `BuildConfig` fields and are injected into `PolarOAuthConfig`.

## Backend Deployment

```bash
cd polar-oauth-broker/infra
cp terraform.tfvars.example terraform.tfvars
terraform init
terraform apply
```

After the first apply, upload real secret values:

```bash
echo -n "real-client-secret" | gcloud secrets versions add polar-client-secret --data-file=-
python3 -c "import os; print(os.urandom(32).hex())" | gcloud secrets versions add polar-oauth-session-key --data-file=-
```

## Sync Behavior

Current implementation:

- Polar data is stored in dedicated SQLDelight tables, not in `TrackedEntry`
- checkpoint state is tracked independently for each sync domain
- the app performs a foreground refresh when the latest successful sync is stale
- Android also schedules a periodic background refresh every 12 hours when connected to the network

Token renewal and recovery:

- [Polar's v4 access tokens last 12 hours](https://www.polar.com/polar-api-v4/#token-endpoint). Sync renews them before expiry and retries an API request once after a 401.
- Overlapping refresh calls share the renewed credentials. Once a refresh request starts, it has up to 30 seconds to save replacement tokens even if its calling sync is cancelled; cancellation then propagates normally.
- A late refresh response cannot restore a disconnected account or clear credentials from a newly connected session. Network failures, server failures, and malformed errors preserve the connection for a later retry. Only an explicit OAuth `invalid_grant` on HTTP 400/401 clears the rejected session.
- Background sync requests WorkManager retries for partial failures. Automatic sync skips only when every metric has a recent successful checkpoint without a recorded failure.
- Refresh diagnostics omit response bodies and exception details that could contain credentials. Tokens continue to use the existing platform settings storage (encrypted preferences on Android).

A revoked or expired refresh token still requires reconnecting in settings. Protecting an in-flight refresh from coroutine cancellation does not guarantee recovery if the OS kills the process or the network loses the response after Polar issues replacement credentials.

Non-Android platforms do not currently ship an equivalent background scheduler in this repo.

## AccessLink Endpoint Notes

Implemented or actively used today:

- `activity/list` with `samples`
- `sleeps` with sleep-related feature flags
- `training-sessions/list`
- `nightly-recharge-results`
- `user/account-data`

The current architecture already accounts for the fact that some endpoints support broader date windows while others require day-by-day sync behavior.

## What Changed In March 2026

- `#118`: initial broker-backed Polar OAuth flow
- `#119`: shared Polar API client and DTO mapping
- `#120`: sync persistence and refresh orchestration
- `#121`: Polar insight bridge and follow-up day-detail support

Earlier documentation that said there was no sync orchestrator or no Android background sync is now outdated.

## Verification Checklist

- connect from Polar settings and complete the browser redirect
- confirm the app reports a connected state
- confirm sync data persists across app restarts
- verify `PolarSyncWorker` is scheduled after app launch
- verify disconnect clears local Polar token state
- interrupt a sync during token renewal, then verify the next sync can use the replacement credentials
- temporarily interrupt network access and verify background sync retries after connectivity returns
