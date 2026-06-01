# Web Search Provider Fallback Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use `superpowers:executing-plans` to implement this plan task-by-task. It will decide whether each batch should run in parallel or serial subagent mode and will pass only task-local context to each subagent. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add configurable web search provider fallback order with provider-level circuit breaking and safe system configuration.

**Architecture:** Keep `ConfigurableWebSearchChannel` as the single search channel bean, but move its internals from single-provider dispatch to ordered provider fallback. Add provider-level configuration accessors and an independent search health registry that mirrors AI routing circuit breaker semantics.

**Tech Stack:** Java 21, Spring Boot, Hutool, OkHttp, PostgreSQL SQL migrations, JUnit 5, Mockito.

---

### Task 1: Runtime Configuration Accessors

**Files:**
- Modify: `backend/src/test/java/com/codingx/chat/application/service/support/RuntimeSettingServiceTest.java`
- Modify: `backend/src/main/java/com/codingx/chat/application/service/support/RuntimeSettingService.java`

- [x] **Step 1: Write failing tests**

Add tests for default provider order, configured provider order, provider-level base URL/API key, sensitive provider key decryption, and search fallback threshold/open duration accessors.

- [x] **Step 2: Verify red state**

Run: `cd backend && mvn -Dtest=RuntimeSettingServiceTest#webSearchProviderOrderFallsBackToDomesticDefault+webSearchProviderOrderReadsConfiguredValue+webSearchProviderConfigReadsProviderScopedValues+webSearchProviderConfigIgnoresLegacySingleProvider+webSearchCircuitBreakerReadsConfiguredValues test`

Expected: compilation/test failure because new methods do not exist.

- [x] **Step 3: Implement accessors**

Add `webSearchProviderOrder()`, `webSearchProviderBaseUrl(provider)`, `webSearchProviderApiKey(provider)`, `webSearchFailureThreshold()`, and `webSearchOpenDurationMs()`.

- [x] **Step 4: Verify green state**

Run the same targeted Maven test command and expect pass.

### Task 2: Search Provider Circuit Breaker

**Files:**
- Create: `backend/src/main/java/com/codingx/chat/infrastructure/search/SearchProviderHealthRegistry.java`
- Create: `backend/src/test/java/com/codingx/chat/infrastructure/search/SearchProviderHealthRegistryTest.java`

- [x] **Step 1: Write failing tests**

Copy the AI provider health scenarios with search provider names: threshold trip, success reset, half-open single probe, half-open failure reopen.

- [x] **Step 2: Verify red state**

Run: `cd backend && mvn -Dtest=SearchProviderHealthRegistryTest test`

Expected: compilation failure because class does not exist.

- [x] **Step 3: Implement registry**

Implement the same CLOSED/OPEN/HALF_OPEN state machine with search-specific comments and method names.

- [x] **Step 4: Verify green state**

Run the same targeted test and expect pass.

### Task 3: Provider Fallback Channel

**Files:**
- Modify: `backend/src/test/java/com/codingx/chat/infrastructure/search/ConfigurableWebSearchChannelTest.java`
- Modify: `backend/src/main/java/com/codingx/chat/infrastructure/search/ConfigurableWebSearchChannel.java`

- [x] **Step 1: Write failing tests**

Add tests for ordered fallback, circuit breaker skip, SerpApi parsing, Exa parsing, Bing HTML parsing, DuckDuckGo HTML parsing, and legacy config removal.

- [x] **Step 2: Verify red state**

Run: `cd backend && mvn -Dtest=ConfigurableWebSearchChannelTest test`

Expected: failures because channel still uses a single provider and lacks new providers.

- [x] **Step 3: Implement fallback**

Refactor channel internals to iterate `runtimeSettingService.webSearchProviderOrder()`, resolve provider config, skip unhealthy/config-missing providers, call provider-specific request builders/parsers, mark success/failure, and log provider attempts without secrets.

- [x] **Step 4: Verify green state**

Run the same test and expect pass.

### Task 4: Database And Feature Docs

**Files:**
- Create: `backend/src/main/resources/db/migration/V20260530_210000__add_web_search_provider_fallback_settings.sql`
- Modify: `backend/src/main/resources/db/init.sql`
- Create: `docs/features/chat/web-search-provider-fallback.md`
- Modify: `docs/features/index.md`
- Modify: `docs/superpowers/memory/chat/web-search-module-card.md`
- Modify: `docs/superpowers/memory/chat/web-search-runtime-contract.md`

- [x] **Step 1: Write migration**

Seed provider order, circuit breaker settings, provider base URLs, and blank sensitive API key slots. Do not write user-provided key values.

- [x] **Step 2: Update init baseline**

Mirror the same `setting` seed rows in `init.sql`, because this repo keeps setting data seeds there while `schema.sql` owns table structure.

- [x] **Step 3: Update feature docs**

Document usage, provider order, key safety, logs, and verification commands.

### Task 5: Verification And Commit

**Files:**
- All files touched above.

- [x] **Step 1: Run focused backend tests**

Run: `cd backend && mvn -Dtest=RuntimeSettingServiceTest,SearchProviderHealthRegistryTest,ConfigurableWebSearchChannelTest test`

- [x] **Step 2: Run backend compile**

Run: `cd backend && mvn compile`

- [x] **Step 3: Run backend test suite**

Run: `cd backend && mvn test`

- [x] **Step 4: Review diff and ensure no secrets**

Run: `git diff --check` and search for the provided keys.

- [x] **Step 5: Commit only this task's files**

Stage backend/docs files touched by this task only and commit with Chinese message: `feat(search): 支持联网搜索提供方顺序与故障切换`。
