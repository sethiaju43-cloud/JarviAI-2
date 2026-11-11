# COMPLETE_PROJECT_INVENTORY

Date: 2026-11-10. Subject: JarvisApp-main (state after the stages in IMPLEMENTATION_PROGRESS.md). All numbers were measured from the files.

## 1. Overview

- Native Android app, Kotlin, package com.jarvis.app, name "JARVI AI".
- versionName 2.0, versionCode 2, minSdk 26, ABI filter arm64-v8a only (app/build.gradle.kts).
- 71 Kotlin source files, 14,840 lines in app/src/main/java/com/jarvis/app/.
- Resources: 21 drawables, 8 layouts, 4 values files, 2 xml files (accessibility_config.xml, network_security_config.xml).
- Repo top level: .github/, app/, docs/, build.gradle.kts, settings.gradle.kts, gradle.properties, README.md.
- No backend, no server database, no Dockerfile in the repository. Local data lives in encrypted SharedPreferences and files under filesDir (models in filesDir/models).

## 2. Dependencies (app/build.gradle.kts)

| Library | Version | Used for |
|---|---|---|
| androidx core-ktx, appcompat, constraintlayout, recyclerview | 1.13.1 / 1.7.0 / 2.1.4 / 1.3.2 | UI |
| material | 1.12.0 | UI |
| okhttp, okhttp-dnsoverhttps | 4.12.0 | network |
| vosk-android + jna | 0.3.75 / 5.18.1 | offline speech recognition |
| kotlinx-coroutines-core | 1.9.0 | concurrency |
| litertlm-android | 0.16.1 | local LLM runtime |
| junit, org.json (tests) | 4.13.2 / 20240303 | unit tests |

## 3. Code areas

| Area | Main files |
|---|---|
| Model routing and online providers | RouterCore.kt, ModelRouter.kt, Providers.kt, ProvidersActivity.kt, OnlineParsers.kt, OnlineStream.kt, TaskClassifier.kt |
| Local LLM and model manager | LocalLlm.kt, LocalModelCatalog.kt, ModelFit.kt, DeviceInfo.kt, ModelDownloadService.kt, ModelsActivity.kt |
| Reasoning, answers, checks | Brain.kt, Prompts.kt, SelfCheck.kt, FreshMind.kt |
| Memory and history | MemoryStore.kt, MemoryLogic.kt, ChatStore.kt, PastChats.kt, MemoryActivity.kt, HistoryActivity.kt |
| Voice | VoiceInput.kt, OfflineVoice.kt, VoskModels.kt, VoiceHelper.kt, GeminiVoice.kt, BargeIn.kt, VoiceHints.kt |
| Phone control | CommandHandler.kt, PhoneActions.kt, JarvisAccessibilityService.kt, OfflineCommands.kt, ActionWords.kt |
| Files and search | Attachments.kt, PdfReader.kt, SearchService.kt |
| UI, themes, assistant character | ChatActivity.kt, SettingsActivity.kt, DesignActivity.kt, FloatingService.kt, AppTheme.kt, Skin.kt, ThemeBackgroundView.kt, MatrixRainView.kt, PetAnimator.kt, UiKit.kt, PermissionsActivity.kt |
| Security and network | ApiKeyStore.kt, Net.kt, UrlGuard.kt |
| Added in this project | UsageStats.kt, DashboardActivity.kt, MoodTone.kt, SelfCommands.kt, Budget.kt, BudgetLogic.kt, UrlGuard.kt |

Largest files: Brain.kt 934 lines, FloatingService.kt 876, Providers.kt 848, CommandHandler.kt 789, ThemeBackgroundView.kt 776, ChatActivity.kt 709.

## 3b. Not listed in detail

The remaining small files were counted but not described one by one here. A per-file listing can be generated from the repository at any time (wc -l on the source folder).

## 4. Manifest

- Permissions: INTERNET, ACCESS_NETWORK_STATE, FOREGROUND_SERVICE (+ DATA_SYNC, SPECIAL_USE, MICROPHONE), QUERY_ALL_PACKAGES, RECORD_AUDIO, CAMERA, SYSTEM_ALERT_WINDOW, POST_NOTIFICATIONS, RECEIVE_BOOT_COMPLETED, CALL_PHONE, READ_CONTACTS, SEND_SMS, SET_ALARM.
- Services: ModelDownloadService (dataSync), FloatingService (specialUse|microphone), JarvisAccessibilityService.
- Receiver: BootReceiver (exported).
- Activities: Main, Settings, Permissions, Chat, Memory, History, Models, Providers, VoiceStudio, Design, Dashboard.
- Network: usesCleartextTraffic=false, networkSecurityConfig, allowBackup=false.

## 5. Tests and CI

- Unit test files (app/src/test/java/com/jarvis/app/): ActionWordsTest, BudgetLogicTest, LogicTests, MatrixRainMathTest, ModelAutoTest, ModelChainTest, MoodToneTest, OnlineStreamTest, RouterTests, SelfCommandsTest, UrlGuardTest, UsageStatsTest, VoiceHintsTest.
- A stray file named ModelAutoTest.k (not .kt) exists in the test folder; it is not compiled. Check whether it is a leftover.
- CI: .github/workflows/build.yml on push to main/master or manual run: JDK 17, Gradle 8.7, runs unit tests (continue-on-error), uploads Test-Report, builds the debug APK, uploads Jarvis-APK, writes a PASS/FAIL summary.
- Only a debug APK is built; no release signing is configured.

## 6. Existing docs

docs/BACKEND_PROXY.md: example Node.js proxy for the "Custom / apna backend" provider (stated in the file as not tested).

## 7. Gaps against the original brief

No backend, database, device bridge, wake word, local neural TTS, speaker recognition, image generation, or response cache exist. See IMPLEMENTATION_PROGRESS.md.
