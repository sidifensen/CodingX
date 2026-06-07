# Acceptance Criteria: CLI Browser Auth

**Spec:** `docs/superpowers/specs/2026-06-07-140205-cli-browser-auth-design.md`
**Date:** 2026-06-07
**Status:** Approved

---

## Criteria

| ID | Description | Test Type | Preconditions | Expected Result |
|----|-------------|-----------|---------------|-----------------|
| AC-001 | CLI browser login creates a loopback callback, opens `/cli-login`, exchanges a returned authorization code, and saves the returned token outside the workspace. | Logic | Mock backend returns valid CLI token and callback receives matching `state` and `code`. | `~/.codingx/cli.yml` contains the new `serverUrl` and token, and no project workspace file receives the token. |
| AC-002 | Backend CLI authorization rejects non-loopback redirect URIs. | API | Authenticated browser request submits `https://evil.example/callback` as `redirectUri`. | API returns HTTP 400 with a Chinese `ApiResponse.message`, and no authorization code is stored. |
| AC-003 | Backend CLI token exchange enforces one-time authorization codes and PKCE verifier checks. | API | A code was created with a known `state` and `codeChallenge`. | Correct `codeVerifier` returns `LoginResponse`; reusing the same code or sending a wrong verifier returns a Chinese failure response. |
| AC-004 | Device code login lets CLI display a user code, wait while pending, and save a token after browser authorization. | Logic | Mock backend returns pending once, then approved token after `/device/authorize`. | CLI polling continues through pending, then writes the approved token to `~/.codingx/cli.yml`. |
| AC-005 | `/cli-login` supports loopback authorization after login. | UI interaction | User opens `/cli-login?redirectUri=http://127.0.0.1:49152/callback&state=s1&codeChallenge=c1` while not authenticated. | Page shows a login form, logs in successfully, calls CLI authorize API, and navigates to the supplied redirect URI with `code` and `state`. |
| AC-006 | `/cli-login` supports device-code authorization after login. | UI interaction | User opens `/cli-login?deviceCode=ABCD-EFGH` while authenticated. | Page displays current account, authorizes the device code, and shows “授权完成，请回到终端” without redirecting to loopback. |
| AC-007 | CLI chat stream failures caused by invalid login state show the backend Chinese message instead of a generic stream failure. | Logic | Mock `/api/chat/stream` returns HTTP 401 JSON `ApiResponse.message=未登录或登录已失效，请重新登录`. | TUI transcript/error event contains that exact message. |
| AC-008 | Existing TUI-only command contract remains intact. | Logic | CLI runs `codingx`, `codingx tui`, `codingx exec`, `codingx resume`, and `codingx sessions` through command runner tests. | Empty args and `tui` launch TUI; `exec`/`resume`/`sessions` remain rejected as non-interactive task modes. |
| AC-009 | Manual token login remains available for development diagnostics. | Logic | User runs `codingx login http://localhost:5001 token-123`. | CLI writes `serverUrl=http://localhost:5001` and `token=token-123` to the user-level config and does not launch TUI. |
| AC-010 | The new `/cli-login` page is readable in dark and light themes. | UI interaction | User opens `/cli-login` in a browser and toggles root `dark` class. | Main container background is opaque, form text and controls remain readable, and screenshot evidence is saved under `logs/`. |

