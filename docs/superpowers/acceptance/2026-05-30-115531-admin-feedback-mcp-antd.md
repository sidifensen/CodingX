# Acceptance Criteria: Admin Feedback and MCP Ant Design Refactor

**Spec:** `docs/superpowers/specs/2026-05-30-115531-admin-feedback-mcp-antd-design.md`
**Date:** 2026-05-30
**Status:** Approved

---

## Criteria

| ID | Description | Test Type | Preconditions | Expected Result |
|----|-------------|-----------|---------------|-----------------|
| AC-001 | Feedback filter controls are reusable and stage input before applying filters. | Logic | Render `FeedbackFilterBar` with keyword `help ` and vote `1`. | `onFilter` is called once with keyword `help` and vote `1` only after filter submit. |
| AC-002 | Feedback table stays on one page for legacy uncounted responses. | Logic | Mock 12 feedback records with `total=0` and `pages=0`. | The page renders `第 1 / 1 页，共 12 条` and no AntD page-2 item. |
| AC-003 | Feedback management page still uses AntD table, form, and select controls. | Logic | Render `FeedbackPage` with one mocked feedback record. | DOM contains `.ant-table`, `.ant-form`, and `.ant-select`. |
| AC-004 | MCP management renders with AntD page primitives. | Logic | Render `MCP` with two mocked configs and tools. | DOM contains `.ant-table`, `.ant-btn`, and no custom `DataTableCard` pagination is required. |
| AC-005 | MCP create/edit form uses an AntD modal and form without changing payload semantics. | Logic | Open create or edit from the MCP page. | Dialog contains `.ant-modal`, `.ant-form`, and submit calls the existing create/update API with trimmed field values. |
| AC-006 | MCP delete confirmation uses an AntD modal and keeps in-app confirmation. | Logic | Click delete for an MCP config. | Dialog named `删除MCP配置` appears and confirming calls `deleteMcpConfig` with the config id. |
| AC-007 | MCP ping result uses an AntD modal and preserves success/failure messages. | Logic | Click test for an MCP tool with mocked ping response. | Dialog named `工具探测结果` shows the backend message and can be closed in-page. |
| AC-008 | MCP client-side pagination still shows page counts and navigates rows. | Logic | Mock 12 MCP config rows and no tools. | Page shows `第 1 / 2 页，共 12 条`; clicking page 2 shows `/mcp_11` and hides `/mcp_1`. |
| AC-009 | Feedback and MCP pages render correctly in the browser. | UI interaction | Admin dev server is running and the browser can access `/feedbacks` and `/mcp`. | CDP evidence shows both pages with AntD table roots and readable non-transparent themed containers. |
