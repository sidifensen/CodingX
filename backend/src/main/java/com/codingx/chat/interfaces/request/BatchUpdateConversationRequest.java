package com.codingx.chat.interfaces.request;

import com.codingx.common.error.ErrorMessageCatalog;
import jakarta.validation.constraints.NotEmpty;
import java.util.List;

/**
 * 批量会话置顶操作请求体，供侧边栏多选会话后一次性调整置顶状态。
 *
 * @param conversationIds 前端批量选中的会话主键列表，不能为空；服务层会过滤空值和重复 ID。
 * @param pinned 批量更新后的目标置顶状态，true 表示置顶，false 表示取消置顶。
 */
public record BatchUpdateConversationRequest(
    @NotEmpty(message = ErrorMessageCatalog.CHAT_CONVERSATION_BATCH_IDS_REQUIRED) List<Long> conversationIds, // 前端批量选中的会话主键列表，不能为空；服务层会过滤空值和重复 ID。
    boolean pinned // 批量更新后的目标置顶状态，true 表示置顶，false 表示取消置顶。
) {
}
