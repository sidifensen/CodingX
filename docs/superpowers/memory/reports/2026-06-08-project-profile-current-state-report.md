## Summary
- Result: updated
- Source context: `backend/src/main/java/com/codingx/governance/application/service/ProjectProfileService.java`, `backend/src/main/resources/db/migration/V20260608_123000__deduplicate_project_profile.sql`
- Source design: none
- Formal commits: d157cda8
- Created docs: 1
- Updated docs: 1
- Deferred docs: 0

## Durable updates made
- Contracts: updated `docs/superpowers/memory/governance/governance-workbench-contract.md` to state that `governance_project_profile` is a current-state table, not a scan history table.
- Contracts: documented that `GET /api/admin/governance/project-profiles` must return one current profile per workspace, with legacy duplicates collapsed to the most recent scan.
- Contracts: recorded the active unique index rule for `workspace_id` so future scans update the existing project profile instead of creating a new active row.

## Not promoted
- The admin project profile layout concern was not promoted into a separate UI memory because this delivery only changed backend data semantics and documentation.
- The scanner quality gap for unknown folders was left out of this report because it remains a separate product/design improvement.

## Open gaps
- Gap: If the product later needs scan history, introduce a separate project profile scan log table instead of overloading `governance_project_profile`.
