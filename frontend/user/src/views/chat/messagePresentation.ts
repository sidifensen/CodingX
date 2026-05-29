import type { ChatAttachmentItem, ChatMessageItem } from './types';

/**
 * 构造发送阶段的乐观用户消息，确保技能绑定信息在后端回放返回前也能驱动气泡展示。
 */
export function buildOptimisticUserMessage({
  id,
  conversationId,
  content,
  skillCodes,
  attachments,
}: {
  id: string;
  conversationId: string;
  content: string;
  skillCodes: string[];
  attachments: ChatAttachmentItem[];
}): ChatMessageItem {
  return {
    id,
    conversationId,
    role: 'USER',
    content,
    skillCodes: normalizeSkillCodes(skillCodes),
    attachments,
    status: 'COMPLETED',
  };
}

/**
 * 解析消息技能气泡的显示文本；气泡用于替代原始 @token，因此不再额外展示 @ 前缀。
 */
export function resolveSkillChipLabel(skillCode: string, skillNameMap: Map<string, string>) {
  const displayName = skillNameMap.get(skillCode)?.trim();
  if (displayName) {
    return displayName;
  }
  return skillCode.trim();
}

/**
 * 清理技能编码列表，保持消息对象中的技能顺序稳定且不带空值。
 */
function normalizeSkillCodes(skillCodes: string[]) {
  return skillCodes
    .map((skillCode) => skillCode.trim())
    .filter(Boolean);
}
