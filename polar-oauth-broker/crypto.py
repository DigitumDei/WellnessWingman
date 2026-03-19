"""AES-256-GCM encryption for OAuth session tokens."""

import os
import base64
from cryptography.hazmat.primitives.ciphers.aead import AESGCM


def encrypt(plaintext: bytes, key: bytes) -> dict:
    """Encrypt plaintext with AES-256-GCM.

    Returns dict with base64-encoded ciphertext, nonce, and tag.
    """
    nonce = os.urandom(12)
    aesgcm = AESGCM(key)
    # AESGCM.encrypt appends the 16-byte tag to the ciphertext
    ct_with_tag = aesgcm.encrypt(nonce, plaintext, None)
    ciphertext = ct_with_tag[:-16]
    tag = ct_with_tag[-16:]
    return {
        "ciphertext": base64.b64encode(ciphertext).decode(),
        "nonce": base64.b64encode(nonce).decode(),
        "tag": base64.b64encode(tag).decode(),
    }


def decrypt(ciphertext_b64: str, nonce_b64: str, tag_b64: str, key: bytes) -> bytes:
    """Decrypt AES-256-GCM ciphertext.

    Raises ValueError on authentication failure.
    """
    ciphertext = base64.b64decode(ciphertext_b64)
    nonce = base64.b64decode(nonce_b64)
    tag = base64.b64decode(tag_b64)
    aesgcm = AESGCM(key)
    # AESGCM.decrypt expects ciphertext + tag concatenated
    return aesgcm.decrypt(nonce, ciphertext + tag, None)
