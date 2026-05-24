## Summary
- Result: updated
- Source spec: none
- Source context: frontend/user/src/views/chat/useChatWorkspace.ts, frontend/user/tests/views/chat/useChatWorkspace.test.ts
- Source design: none
- Formal commits: 6576a5b0d243648431c1165c9c38bbfefa0fe8c2
- Created docs: 1
- Updated docs: 3
- Deferred docs: 0

## Durable updates made
- Lessons: added a problem-focused lesson for stale-closure replay overwriting streamed assistant content
- Contracts: documented that post-stream replay must not let empty history overwrite richer local正文
- Module cards: recorded the stale-closure replay pitfall in the chat process trace module card
- Index: linked the new lesson from the chat memory index

## Not promoted
- Unrelated working tree changes in `frontend/user/src/index.css`, `frontend/user/src/views/ChatView.tsx`, and `frontend/user/tests/views/ChatView.test.tsx` were intentionally left alone
- `.superpowers/` untracked content was not modified

## Open gaps
- The broader replay/hydration runbook for chat workspace snapshots still remains separate from this lesson
