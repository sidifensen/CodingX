import assert from 'node:assert/strict';
import { test } from 'node:test';

import { resolvePackagedApiRedirectUrl } from '../dist/apiProxy.js';

test('rewrites packaged file api requests to configured backend base url', () => {
  const redirected = resolvePackagedApiRedirectUrl(
    'file:///C:/Program%20Files/CodingX/resources/user-dist/api/chat/slash-commands?keyword=review',
    'http://localhost:5001/',
  );

  assert.equal(
    redirected,
    'http://localhost:5001/api/chat/slash-commands?keyword=review',
  );
});

test('ignores non api and non file requests', () => {
  assert.equal(
    resolvePackagedApiRedirectUrl('file:///C:/CodingX/resources/user-dist/index.html', 'http://localhost:5001'),
    null,
  );
  assert.equal(
    resolvePackagedApiRedirectUrl('https://example.com/api/chat/slash-commands', 'http://localhost:5001'),
    null,
  );
});
