# CLI Browser Auth Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use `superpowers:executing-plans` to implement this plan task-by-task. It will decide whether each batch should run in parallel or serial subagent mode and will pass only task-local context to each subagent. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build browser-based CLI login with loopback callback, device-code fallback, and readable authentication failures for CodingX TUI.

**Architecture:** Add a backend CLI auth application service behind `/api/auth/cli`, a user frontend `/cli-login` authorization page, and CLI auth services that save tokens through `CliConfigStore`. Keep `/api/chat/stream` unchanged except for error readability and keep `codingx` as the TUI-first product entry.

**Tech Stack:** Java 21, Spring Boot 3.4, Sa-Token, Hutool, React 19, Vite Plus, Tailwind theme variables, Java JDK `HttpServer`, JUnit 5, Vitest.

---

## File Structure

- Create `backend/src/main/java/com/codingx/auth/application/service/CliAuthApplicationService.java` for authorization-code and device-code workflows.
- Create `backend/src/main/java/com/codingx/auth/application/service/CliAuthStateStore.java` and an in-memory implementation for short-lived CLI auth state.
- Create backend request/response records under `backend/src/main/java/com/codingx/auth/interfaces/request` and `backend/src/main/java/com/codingx/auth/interfaces/response`.
- Modify `backend/src/main/java/com/codingx/auth/interfaces/controller/AuthController.java` or add `CliAuthController.java` under the same package for `/api/auth/cli`.
- Create tests under `backend/src/test/java/com/codingx/auth/application/service` and `backend/src/test/java/com/codingx/auth/interfaces/controller`.
- Create `frontend/user/src/api/cliAuthApi.ts`, `frontend/user/src/views/CliLoginView.tsx`, and `frontend/user/tests/views/CliLoginView.test.tsx`.
- Modify `frontend/user/src/App.tsx` and `frontend/user/src/api/authApi.ts` only as needed to route and reuse auth helpers.
- Create `cli/src/main/java/com/codingx/cli/auth/CliAuthService.java`, `LoopbackCallbackServer.java`, request/response records, and tests under `cli/src/test/java/com/codingx/cli/auth`.
- Modify `cli/src/main/java/com/codingx/cli/command/CliCommandRunner.java`, `cli/src/main/java/com/codingx/cli/CodingXCli.java`, and `cli/src/main/java/com/codingx/cli/backend/BackendChatEventSource.java`.
- Update `docs/features/agent/java-cli-terminal-mvp.md` and `docs/features/index.md` if required by existing feature index conventions.

## Task 1: Backend CLI Auth Tests

- [ ] Write failing service tests for loopback redirect validation, authorization-code exchange, one-time consumption, PKCE mismatch, device pending, device approval, and device token exchange.
- [ ] Run backend targeted tests and confirm they fail because `CliAuthApplicationService` and related contracts do not exist.

## Task 2: Backend CLI Auth Implementation

- [ ] Implement request/response records with field comments.
- [ ] Implement `CliAuthStateStore` and in-memory TTL storage with synchronized cleanup.
- [ ] Implement `CliAuthApplicationService` using `AuthSessionGateway`, `UserRepository`, and `UserViewService`.
- [ ] Implement `/api/auth/cli/*` Controller endpoints with protocol-only logic.
- [ ] Run targeted backend tests until green.

## Task 3: Frontend CLI Login Tests

- [ ] Write failing Vitest tests for `/cli-login` loopback mode, device mode, existing authenticated session, error rendering, and route isolation from normal chat.
- [ ] Run targeted frontend tests and confirm they fail for missing page/API.

## Task 4: Frontend CLI Login Implementation

- [ ] Implement `CliAuthApi` with centralized response parsing and backend message preservation.
- [ ] Implement `CliLoginView` as a full-page authorization surface with dark/light theme support and no native browser dialogs.
- [ ] Wire `App.tsx` so `/cli-login` renders the authorization page before the normal app shell.
- [ ] Run targeted frontend tests until green.

## Task 5: CLI Auth Tests

- [ ] Write failing CLI tests for loopback callback state handling, token exchange config write, device-code polling, command runner `auth login --device`, and stream 401 JSON message parsing.
- [ ] Run targeted CLI tests and confirm they fail for missing auth services and command paths.

## Task 6: CLI Auth Implementation

- [ ] Implement `LoopbackCallbackServer` using JDK `HttpServer`, bound only to `127.0.0.1`.
- [ ] Implement `CliAuthService` with browser-login URL generation, PKCE challenge, token exchange, device-code polling, and config persistence.
- [ ] Extend `CliCommandRunner` with `auth login` and `auth login --device` while keeping current TUI-only task restrictions.
- [ ] Update `CodingXCli` dependency assembly.
- [ ] Update `BackendChatEventSource` `Accept` header and error parsing.
- [ ] Run targeted CLI tests until green.

## Task 7: Documentation and Verification

- [ ] Update feature docs with CLI browser login usage, fallback device-code usage, and troubleshooting for invalid token.
- [ ] Run `mvn compile` and `mvn test` in `backend`.
- [ ] Run `mvn test` and `mvn package` in `cli`.
- [ ] Run `npm run test:run` and `npm run build` in `frontend/user`.
- [ ] Use CDP to open `/cli-login`, capture screenshot evidence to `logs/`, and verify dark/light readability.
- [ ] Run `git status --short` and stage only files touched by this feature, excluding unrelated dirty governance and SkillsView files.

