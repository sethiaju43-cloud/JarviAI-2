# FINAL_CHANGELOG

Status: IN PROGRESS. This lists what was delivered so far. "Delivered" means source was written and handed to the owner; build and phone results are tracked in TEST_RESULTS.md.

## Stage 1: usage dashboard and identity
- UsageStats.kt: counts local and online requests, failures, tokens (estimate); 30-day history for most counters (online call counts by provider are kept 7 days).
- DashboardActivity.kt (new screen), entry button in SettingsActivity.kt, registered in AndroidManifest.xml.
- Prompts.kt: when asked who made it, Jarvi answers "hacker ai" (online and local prompts).

## Stage 2: mood and tone
- MoodTone.kt (new), MoodToneTest.kt (new); Brain.kt adds a tone hint to the prompt.

## Stage 3: memory and voice commands
- SelfCommands.kt (new), SelfCommandsTest.kt (new): forget, correct, recall, repeat, slower, faster, normal speed.
- VoiceHelper.kt: speech speed setting. Brain.kt: runs these commands without an AI call.

## Stage 4A: cost control
- Budget.kt, BudgetLogic.kt, BudgetLogicTest.kt (new): daily call limit, 30-day estimated spend limit, 80% alert, local-only when exceeded.
- ModelRouter.kt: budget gate and duplicate request guard. UsageStats.kt: duplicate counter. DashboardActivity.kt: budget section.

## Security fixes
- UrlGuard.kt, UrlGuardTest.kt (new); ModelDownloadService.kt: the HuggingFace token is sent only to real HuggingFace hosts (finding S-01).
- ApiKeyStore.kt: the old plaintext copy of the Gemini key is removed once the encrypted copy exists (S-02).

## Documentation (docs/)
AI_MODEL_INVENTORY, MODEL_LICENSE_REPORT, OFFLINE_AI_COMPATIBILITY, API_PROVIDER_INVENTORY, SECURITY_AUDIT, COMPLETE_PROJECT_INVENTORY, CLOUD_LOCAL_ARCHITECTURE, API_COST_OPTIMIZATION, TEST_RESULTS, DEPLOYMENT_GUIDE, IMPLEMENTATION_PROGRESS, FINAL_CHANGELOG. Not yet written: MAYA_SOURCE_ANALYSIS, MAYA_FEATURE_MAPPING.

## Not done yet
Wake word, local VAD, echo cancellation, offline neural TTS, speaker recognition, model checksum verification, cloud backend and device bridge, image generation, real hardware tests.
