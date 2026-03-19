"""Polar OAuth broker — Cloud Function with 4 routes.

Handles the server-side OAuth token exchange so the Polar client secret
never touches the mobile device.
"""

import json
import os
import uuid
from datetime import datetime

import functions_framework
import requests as http_requests
from flask import Request, jsonify, redirect
from google.cloud import firestore, secretmanager

import crypto

# ---------------------------------------------------------------------------
# Lazy-loaded singletons
# ---------------------------------------------------------------------------
_firestore_client = None
_secrets = {}


def _get_firestore() -> firestore.Client:
    global _firestore_client
    if _firestore_client is None:
        _firestore_client = firestore.Client()
    return _firestore_client


def _get_secret(secret_id: str) -> str:
    """Fetch a secret from Secret Manager (cached per cold start)."""
    if secret_id not in _secrets:
        client = secretmanager.SecretManagerServiceClient()
        project = os.environ["GCP_PROJECT"]
        name = f"projects/{project}/secrets/{secret_id}/versions/latest"
        response = client.access_secret_version(request={"name": name})
        _secrets[secret_id] = response.payload.data.decode("utf-8")
    return _secrets[secret_id]


# ---------------------------------------------------------------------------
# Route helpers
# ---------------------------------------------------------------------------

POLAR_TOKEN_URL = "https://auth.polar.com/oauth/token"
REDIRECT_SCHEME = "wellnesswingman"


def _oauth_callback(request: Request):
    """Handle Polar's OAuth redirect: exchange code for tokens, store encrypted in Firestore."""
    code = request.args.get("code")
    state = request.args.get("state")
    error = request.args.get("error")

    if error:
        return redirect(
            f"{REDIRECT_SCHEME}://oauth/result?error={error}&state={state or ''}"
        )

    if not code or not state:
        return jsonify({"error": "Missing code or state parameter"}), 400

    # Exchange authorization code for tokens (Polar v4 uses Basic auth)
    client_id = os.environ["POLAR_CLIENT_ID"]
    client_secret = _get_secret("polar-client-secret")

    token_response = http_requests.post(
        POLAR_TOKEN_URL,
        data={
            "grant_type": "authorization_code",
            "code": code,
        },
        auth=(client_id, client_secret),
        headers={"Accept": "application/json"},
        timeout=15,
    )

    if not token_response.ok:
        return redirect(
            f"{REDIRECT_SCHEME}://oauth/result?error=token_exchange_failed&state={state}"
        )

    tokens = token_response.json()

    # Encrypt token JSON with session key
    session_key = _get_secret("polar-oauth-session-key").encode("utf-8")[:32]
    encrypted = crypto.encrypt(json.dumps(tokens).encode("utf-8"), session_key)

    # Store in Firestore with TTL
    session_id = uuid.uuid4().hex
    db = _get_firestore()
    db.collection("oauth_sessions").document(session_id).set(
        {
            "ciphertext": encrypted["ciphertext"],
            "nonce": encrypted["nonce"],
            "tag": encrypted["tag"],
            "state": state,
            "created_at": datetime.utcnow(),
            "redeemed": False,
        }
    )

    return redirect(
        f"{REDIRECT_SCHEME}://oauth/result?session={session_id}&state={state}"
    )


def _oauth_redeem(request: Request):
    """One-time token pickup: decrypt and return tokens, mark session redeemed."""
    body = request.get_json(silent=True)
    if not body or "session_id" not in body:
        return jsonify({"error": "Missing session_id"}), 400

    session_id = body["session_id"]
    db = _get_firestore()
    doc_ref = db.collection("oauth_sessions").document(session_id)

    # Transactional read-and-mark to prevent replay
    @firestore.transactional
    def redeem_in_transaction(transaction):
        snapshot = doc_ref.get(transaction=transaction)
        if not snapshot.exists:
            return None, 404
        data = snapshot.to_dict()
        if data.get("redeemed"):
            return None, 410
        transaction.update(doc_ref, {"redeemed": True})
        return data, 200

    transaction = db.transaction()
    data, status = redeem_in_transaction(transaction)

    if status == 404:
        return jsonify({"error": "Session not found"}), 404
    if status == 410:
        return jsonify({"error": "Session already redeemed"}), 410

    # Decrypt tokens
    session_key = _get_secret("polar-oauth-session-key").encode("utf-8")[:32]
    try:
        plaintext = crypto.decrypt(
            data["ciphertext"], data["nonce"], data["tag"], session_key
        )
        tokens = json.loads(plaintext)
    except Exception:
        return jsonify({"error": "Decryption failed"}), 500

    return jsonify({"tokens": tokens, "state": data.get("state", "")}), 200


def _oauth_refresh(request: Request):
    """Stateless proxy: forward refresh_token to Polar, return new tokens."""
    body = request.get_json(silent=True)
    if not body or "refresh_token" not in body:
        return jsonify({"error": "Missing refresh_token"}), 400

    client_id = os.environ["POLAR_CLIENT_ID"]
    client_secret = _get_secret("polar-client-secret")

    token_response = http_requests.post(
        POLAR_TOKEN_URL,
        data={
            "grant_type": "refresh_token",
            "refresh_token": body["refresh_token"],
        },
        auth=(client_id, client_secret),
        headers={"Accept": "application/json"},
        timeout=15,
    )

    if not token_response.ok:
        return (
            jsonify({"error": "Refresh failed", "detail": token_response.text}),
            token_response.status_code,
        )

    return jsonify(token_response.json()), 200


def _asset_links(request: Request):
    """Placeholder for Digital Asset Links (returns empty array for now)."""
    return jsonify([]), 200


# ---------------------------------------------------------------------------
# Entry point
# ---------------------------------------------------------------------------

_ROUTES = {
    "/oauth/callback": ("GET", _oauth_callback),
    "/oauth/redeem": ("POST", _oauth_redeem),
    "/oauth/refresh": ("POST", _oauth_refresh),
    "/.well-known/assetlinks.json": ("GET", _asset_links),
}


@functions_framework.http
def handler(request: Request):
    """Single Cloud Function entry point that dispatches by path."""
    path = request.path.rstrip("/") or "/"

    # Handle preflight CORS for POST routes
    if request.method == "OPTIONS":
        headers = {
            "Access-Control-Allow-Origin": "*",
            "Access-Control-Allow-Methods": "GET, POST, OPTIONS",
            "Access-Control-Allow-Headers": "Content-Type",
            "Access-Control-Max-Age": "3600",
        }
        return ("", 204, headers)

    route = _ROUTES.get(path)
    if route is None:
        return jsonify({"error": "Not found"}), 404

    expected_method, handler_fn = route
    if request.method != expected_method:
        return jsonify({"error": "Method not allowed"}), 405

    response = handler_fn(request)

    # Add CORS headers to all responses
    if isinstance(response, tuple):
        body, status = response[0], response[1]
        headers = response[2] if len(response) > 2 else {}
        if isinstance(headers, dict):
            headers["Access-Control-Allow-Origin"] = "*"
            return body, status, headers
        return body, status
    else:
        # redirect or Response object
        return response
