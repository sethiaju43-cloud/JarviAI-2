# SECURITY_AUDIT

Date: 2026-11-10. Scope: static review of the Jarvis source (JarvisApp-main). Engineering review, not a penetration test.

## 1. What was reviewed

API key storage, key transport, logging, network configuration, AndroidManifest (permissions, exported components), model downloader, chat/memory storage, screen-reading privacy, confirmations for sensitive actions.

## 2. What was NOT reviewed

- Dynamic testing on a device (no device available).
- Known-vulnerability scan of dependencies (LiteRT-LM, Vosk, OkHttp, AndroidX): no internet access in the analysis environment.
- The backend: it does not exist yet (see CLOUD_LOCAL_ARCHITECTURE once written).
- The Maya archive beyond what is in the API audit workbook (manifest permissions, URL list, key slots). The audit reports no plaintext secret values.

## 3. Verified good practices

| Area | Evidence |
|---|---|
| API keys encrypted at rest | ApiKeyStore.kt: Android Keystore AES-256-GCM, key alias "jarvis_api_key" |
| Keys not placed in URLs | Providers.kt, GeminiVoice.kt use headers (x-goog-api-key, Authorization: Bearer) |
| HTTPS only | res/xml/network_security_config.xml: cleartextTrafficPermitted="false"; manifest usesCleartextTraffic="false" |
| No app data backup | manifest allowBackup="false" |
| Chat history and memory encrypted | ChatStore.kt, Brain.kt, MemoryStore.kt use ApiKeyStore.seal/open |
| Errors redacted before logging | OnlineParsers.redact() used in Log calls (ChatActivity, FloatingService, ModelRouter, downloader) |
| Sensitive text not saved to memory | SensitiveFilter in MemoryLogic.kt (passwords, OTP, card, Aadhaar, long numbers, email) |
| Password fields not read from screen | JarvisAccessibilityService.collectText skips isPassword nodes |
| Private apps not read | JarvisAccessibilityService.isPrivateApp (bank, upi, wallet and a prefix list) |
| Confirmation before SMS / call / WhatsApp / Send | Brain.kt confirmQuestion and confirmSendClick; setting confirm_send defaults to true |
| Microphone only on user action | Settings text states this; no always-listening service exists |
| Components not exported | all activities and services exported="false"; only the launcher activity and BootReceiver are exported |
| "Wipe all" removes secrets | ApiKeyStore.wipeAll, used by Settings |

## 4. Findings

| ID | Severity | Finding | Evidence | Status |
|---|---|---|---|---|
| S-01 | Medium | HuggingFace token could be sent to a non-HuggingFace server. The downloader added the token when the URL merely contained the text "huggingface.co" (for example https://evil.com/?x=huggingface.co). Custom model URLs are user-entered. | ModelDownloadService.kt (two places) | FIXED in this stage: UrlGuard.isHuggingFace checks the real HTTPS host. Unit tests added. Pending owner build. |
| S-02 | Medium | The legacy plaintext copy of the Gemini key was never deleted. removeOldCopy() existed but had no caller. | ApiKeyStore.kt | FIXED in this stage: getKey() now calls removeOldCopy() after the encrypted copy is stored. |
| S-03 | Medium | Downloaded model files are not integrity-checked. No SHA-256 or signature check exists anywhere in the code. A tampered or corrupted model would be loaded. | grep for sha256 / MessageDigest / checksum: no results | OPEN. Fix: store an official SHA-256 for each catalog model and verify before the .part file is renamed; for custom URLs show the computed hash and let the user confirm. Needs the official hashes from the model host. |
| S-04 | Medium (privacy) | Screen text is sent to cloud AI by default. screenToAi defaults to true. Mitigations exist (password fields skipped, private-app filter, a settings toggle) but the private-app filter is name based and cannot know every sensitive app. | Prefs.kt line: screenToAi default true | OPEN, owner decision. Option: ask once at first use, or default to off. |
| S-05 | Low | If the Android Keystore is unavailable, setKey() falls back to storing the key in private SharedPreferences, and seal() returns data unencrypted. Done on purpose so the app keeps working, but those devices store secrets unencrypted (still in the app-private folder). | ApiKeyStore.kt | OPEN (accepted trade-off). Consider warning the user in Settings when the fallback is active. |
| S-06 | Low | redact() masks only runs of 20 or more letters, digits, "_" and "-". Shorter keys or keys with other characters would not be masked. Mitigated because keys are not logged on purpose. | OnlineParsers.kt | OPEN |
| S-07 | Low | BootReceiver is exported. It checks the action and starts only if the user enabled auto start and overlay permission. Setting exported="false" is expected to work for the system boot broadcast but must be tested on a device before changing. | AndroidManifest.xml, BootReceiver.kt | OPEN (test first) |
| S-08 | Info | Broad permissions are declared: QUERY_ALL_PACKAGES (restricted by Google Play policy), SEND_SMS, CALL_PHONE, READ_CONTACTS, RECORD_AUDIO, CAMERA, SYSTEM_ALERT_WINDOW, plus an accessibility service. All are used by real features; Play Store review may ask for a justification. No location permission is requested. | AndroidManifest.xml | NOTE |
| S-09 | Info | No minify / shrink settings were found in app/build.gradle.kts, so release builds are not obfuscated. API keys are not stored in the APK, so this is not a key leak. | app/build.gradle.kts | NOTE |

## 5. Requirements for the future cloud bridge (not built yet)

1. Device registration with a per-device secret; tokens short lived and revocable.
2. TLS only; no local inference port open to the internet.
3. Model downloads verified by SHA-256 (closes S-03 for the bridge too).
4. Server-side rate limits and per-user budgets; secrets only in server environment variables, never in the repository or app.
5. No Maya servers, keys or hosted model files are used anywhere (see MODEL_LICENSE_REPORT.md).

## 6. Recommended next order

1. Owner builds and tests S-01 and S-02 fixes.
2. S-03 checksum verification (needs official hashes).
3. S-04 decision on the screen-to-AI default.
4. S-07 after an on-device boot test.
