# Admin Feedback and MCP Ant Design Refactor

**Date:** 2026-05-30
**Status:** Approved

## Goal

Refactor the admin feedback management page into focused components and rebuild the admin MCP management page with Ant Design components while preserving existing API behavior.

## Scope

- Feedback management keeps the existing list, filter, refresh, pagination, error, and detail-link behavior.
- Feedback page-level logic remains responsible for API calls and state orchestration.
- Feedback display units are split into reusable page components for the filter bar and table.
- MCP management keeps existing config/tool loading, create/edit/delete config, ping result, client-side pagination, and error behavior.
- MCP page uses Ant Design primitives for layout controls, table, forms, modal dialogs, alerts, tags, switches, and loading state.
- No backend API, database, routing, or authentication behavior changes are included.

## Component Design

Feedback components:

- `FeedbackFilterBar` owns filter inputs and emits staged keyword/vote values only when the user clicks filter or presses enter.
- `FeedbackTable` owns AntD table columns, vote tags, action links, empty/loading text, and pagination summary.
- Feedback pagination normalization remains a small utility because it protects the page from legacy uncounted responses.

MCP components:

- `McpToolbar` renders page title copy and AntD action buttons.
- `McpTable` renders merged config/tool rows through AntD Table with controlled pagination.
- `McpConfigModal` renders create/edit config form through AntD Modal and Form.
- `McpDeleteModal` renders delete confirmation through AntD Modal.
- `McpPingResultModal` renders ping result metadata through AntD Modal and Descriptions.
- Shared MCP transformation helpers stay pure so tests can cover behavior without browser state.

## UX and Theme Constraints

- Use Ant Design components rather than browser-native controls for formal page interactions.
- Keep the admin interface dense and operational: no nested cards, no marketing hero, no decorative background.
- Preserve light/dark readability by relying on the existing `AdminAntdProvider` token bridge and AntD theme overrides.
- Keep modal interactions in-app; do not use `window.alert`, `window.confirm`, or `window.prompt`.
- Keep table scroll widths stable so operation columns do not collapse on smaller admin viewports.

## Error Handling

- API errors continue to display the backend-provided `Error.message` text through page alerts or modal form alerts.
- Failed ping requests continue to show an in-page result modal with failed status and message.
- Delete of a config without an id remains blocked with the current Chinese error message.

## Verification

- Add failing tests first for reusable feedback component behavior and MCP AntD rendering.
- Run targeted admin tests for feedback and MCP pages.
- Run admin build.
- Run CDP browser verification for `/feedbacks` and `/mcp`, including screenshot evidence saved under `logs/`.
