# CLOUD_LOCAL_ARCHITECTURE

Status: DESIGN ONLY. Nothing in this document is implemented yet.

## 1. Starting point (verified)

- The Android app calls AI providers directly. Keys are stored encrypted on the device.
- A "Custom / apna backend (OpenAI-compatible)" provider already exists, and docs/BACKEND_PROXY.md shows a small proxy. So the app can already talk to a backend that speaks the OpenAI chat format.
- Local models already run on the phone with LiteRT-LM, with no bridge needed.

## 2. Important limit

A model installed on a phone cannot run inside a cloud server. A bridge only lets the cloud ask the phone to run it, and works only while the phone is online, awake and allows a background connection. Android battery rules make this best effort. For a phone-only assistant a bridge adds little; it pays off when other clients (web, a second device) want to use the owner's phone models. Cloud fallback must therefore always work.

## 3. Target architecture

    Jarvis app (Android)                     Cloud backend
    - local models (LiteRT-LM)  <--- WSS ---  Gateway API (OpenAI-compatible /v1)
    - bridge client (outbound)  --- WSS --->  Device registry + bridge hub
                                              Router (local device or cloud provider)
                                              Usage ledger + budgets
                                              Provider keys (server environment only)

- The device opens an outbound TLS WebSocket to the backend. No port is opened on the phone, so nothing is exposed publicly.
- Auth: short pairing code to register a device, then a per-device token that can be rotated and revoked.

## 4. Bridge messages (proposal)

| Direction | Message | Purpose |
|---|---|---|
| device to server | hello {device_id, app_version, models[], ram_mb, battery, charging} | register and report capability |
| server to device | infer {request_id, model, messages, max_tokens, deadline_ms} | run on the phone |
| device to server | chunk / done / error {request_id, ...} | streamed result |
| server to device | cancel {request_id} | stop a request |
| both | ping / pong | liveness; server marks the device offline on timeout |

Routing rule: use the device only if it is online, has the model, its battery and RAM allow, the user opted in, and the first token arrives within a deadline (for example 8 seconds). Otherwise fall back to a cloud provider. A cancelled or timed-out request must be cancelled on the device.

## 5. Model distribution

A signed manifest lists model id, version, URL, size and SHA-256. The app verifies the SHA-256 before a downloaded file is renamed into place (this also closes finding S-03 in SECURITY_AUDIT.md).

## 6. Proposed database (new; no existing database was found)

| Table | Main columns |
|---|---|
| users | id, email, created_at |
| devices | id, user_id, name, token_hash, last_seen, online, app_version |
| model_registry / model_versions | model id, version, url, size, sha256, license |
| provider_settings | user_id, provider, enabled, secret reference (not the secret itself) |
| api_usage | user_id, ts, provider, model, route (local/cloud), tokens_in, tokens_out, est_cost, ok |
| conversations, memory | only if the user opts in to sync |
| voice_settings | user_id, voice, speed |
| agent_tasks, execution_logs | task id, status, steps, timestamps |
| routing_log, error_reports | request id, route chosen, reason, error class |

Rules: migrations with a backup first, never drop existing tables, secrets only in server environment variables.

## 6b. Security requirements

TLS only; hashed device tokens; per-user rate limits and budgets; request size limits; no keys or tokens in logs; revoke a device instantly; audit log of every device registration.

## 7. Delivery phases

1. Gateway: OpenAI-compatible proxy with auth, rate limit, usage ledger (extends docs/BACKEND_PROXY.md).
2. Device pairing and registry.
3. WebSocket bridge and routing with cloud fallback.
4. Signed model manifest and verified downloads.

## 8. Open questions for the owner

See the questions list in the project chat (hosting, language, whether a second client exists).
