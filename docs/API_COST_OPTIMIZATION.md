# API_COST_OPTIMIZATION

Status legend: EXISTING = was already in Jarvis (seen in source/README); ADDED = delivered in this project (source delivered, owner builds and tests); NOT DONE.

## 1. Gateway

Every AI request passes through ModelRouter.complete() (ModelRouter.kt), which uses RouterCore for ordering and fallback. Online and local backends are wrapped by CountingBackend (UsageStats.kt), so every request is counted in one place.

## 2. Measures

| Measure | Status | Where |
|---|---|---|
| Local-first / local-only / online / auto route modes | EXISTING | RouteModes, RoutePlanner, Prefs.routeMode |
| Provider fallback chain, cooldown on rate limit/quota, retry | EXISTING | RouterCore.kt, ModelChain, HealthTracker |
| Fast model first for small chat; strong model for hard tasks | EXISTING (partial "quality vs cost") | ProviderRegistry roles / smart mode |
| Phone commands (torch, volume, alarm, call...) done by rules with no AI call | EXISTING | Brain.ruleCommand, OfflineCommands |
| "How many API calls?" answered locally | EXISTING | UsageStats.isUsageQuestion |
| App list sent to the model only when the task needs it | EXISTING | Brain.askSmartInner |
| Context trimming for the local model | EXISTING | ContextTrimmer |
| Avoid local model when battery low / phone hot | EXISTING | ModelRouter.avoidLocal |
| Local vs cloud counting, tokens estimate, dashboard | ADDED | UsageStats.kt, DashboardActivity.kt |
| Daily online-call limit and 30-day estimated spend limit; 80% alert; at 100% local only or a clear message | ADDED | Budget.kt, BudgetLogic.kt, ModelRouter.complete |
| Duplicate request guard (same request within 6 seconds returns the previous answer) | ADDED | ModelRouter.complete, BudgetLogic.dupKey |
| Response cache | NOT DONE (deliberate) | see section 4 |
| Per-provider real price table | NOT DONE | |
| Client-side rate limiting before sending | NOT DONE (providers' 429 answers are handled) | |
| Explicit "Low cost" and "Quality first" modes | NOT DONE (only partial via roles) | |

## 3. Measured versus estimated

- MEASURED: number of online requests, local answers, failures, duplicates stopped.
- ESTIMATED: tokens (text length / 4, rougher for Hindi) and money (tokens x the rate the user sets). The dashboard labels these as estimates. "Calls avoided" equals the number of local answers and is an estimate, because without a local model some of those requests might never have been sent.
- Local inference is not free: it uses CPU, RAM, storage and battery.

## 4. Why there is no response cache

Each prompt contains the current time, memory lines and a mood hint, so an exact repeat is rare and a cache would rarely hit. A cache of conversational answers also risks stale facts and keeping private content. A safe cache would need: single-turn stateless questions only, no memory context, short expiry, in memory only, cleared on "forget". It can be added later if measurements show repeated stateless questions.

## 5. Next steps

1. Per-provider price table (owner supplies the providers actually used).
2. Optional Low cost / Quality first modes.
3. Server-side budgets once the backend exists.
