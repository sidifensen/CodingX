import assert from 'node:assert/strict';
import { test } from 'node:test';

import { normalizeDesktopNotificationPayload } from '../dist/desktopNotification.js';

test('normalizes desktop notification payload with configured title and body', () => {
  const payload = normalizeDesktopNotificationPayload({
    title: ' CodingX 任务完成 ',
    body: ' 后台任务已完成 ',
    conversationId: 2001,
    hookCode: 'task-completed',
  });

  assert.equal(payload.title, 'CodingX 任务完成');
  assert.equal(payload.body, '后台任务已完成');
  assert.equal(payload.conversationId, '2001');
  assert.equal(payload.hookCode, 'task-completed');
});

test('uses Chinese fallback text when notification payload is blank', () => {
  const payload = normalizeDesktopNotificationPayload({
    title: '   ',
    body: '',
  });

  assert.equal(payload.title, 'CodingX 通知');
  assert.equal(payload.body, '有一项后台任务状态已更新');
});
