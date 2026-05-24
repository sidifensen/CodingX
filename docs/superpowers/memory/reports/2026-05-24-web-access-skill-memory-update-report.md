## Summary
- Result: updated
- Source context: backend/src/test/java/com/codingx/chat/application/service/ChatSkillContextServiceTest.java, backend/src/main/resources/db/migration/V20260524_224500__seed_web_access_skill.sql, backend/src/main/resources/skills/web-access/SKILL.md
- Formal commits: e260fa5a5173eca56ebee9d26533180f3a39553e
- Created docs: 1
- Updated docs: 1

## Durable updates made
- Added a lesson that records the invariant between built-in `skill` rows and classpath `SKILL.md` manifests.
- Updated the memory index so future skill work can discover the new lesson quickly.

## Not promoted
- Unrelated frontend working tree changes were intentionally left untouched.
- The pre-existing `.superpowers/brainstorm/...` logs were not modified.

## Open gaps
- The broader skill packaging and migration workflow still lacks a dedicated runbook for bundled assets versus database seed data.
