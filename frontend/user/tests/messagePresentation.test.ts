import { describe, expect, it } from 'vitest';
import {
  buildOptimisticUserMessage,
  resolveSkillChipLabel,
} from '../src/views/chat/messagePresentation';

describe('messagePresentation', () => {
  it('keeps selected skill codes on optimistic user messages', () => {
    const message = buildOptimisticUserMessage({
      id: 'optimistic-user-1',
      conversationId: 'pending-conversation',
      content: '@web-access 这是啥',
      skillCodes: ['web-access'],
      attachments: [],
    });

    expect(message.skillCodes).toEqual(['web-access']);
  });

  it('renders skill chip labels without the raw @ token', () => {
    const skillNameMap = new Map([['web-access', '网页访问']]);

    expect(resolveSkillChipLabel('web-access', skillNameMap)).toBe('网页访问');
    expect(resolveSkillChipLabel('custom-skill', skillNameMap)).toBe('custom-skill');
  });
});
