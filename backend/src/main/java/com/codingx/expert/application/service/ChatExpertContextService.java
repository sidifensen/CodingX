package com.codingx.expert.application.service;

import cn.hutool.core.util.StrUtil;
import com.codingx.expert.domain.model.ChatExpert;
import com.codingx.expert.domain.repository.ChatExpertRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 构建当前消息所选专家的提示词上下文。
 */
@Service
@RequiredArgsConstructor
public class ChatExpertContextService {

    /**
     * 专家配置仓储，用于按专家编码读取 system prompt。
     */
    private final ChatExpertRepository chatExpertRepository;

    /**
     * 根据专家编码构建专家上下文。
     * @param expertCode 专家编码。
     * @return 专家 system prompt，上下文缺失时返回空字符串。
     */
    public String buildExpertContext(String expertCode) {
        // 步骤 1：未选择专家时不注入任何专家上下文，保持普通聊天链路不受影响。
        if (StrUtil.isBlank(expertCode)) {
            return "";
        }
        // 步骤 2：只允许启用状态的专家进入模型上下文，缺失或禁用都按无专家处理。
        ChatExpert expert = chatExpertRepository.findByExpertCode(expertCode.trim());
        if (expert == null || expert.getEnabled() == null || expert.getEnabled() != 1) {
            return "";
        }
        // 步骤 3：专家没有系统提示词时不注入空壳角色，避免误导模型。
        if (StrUtil.isBlank(expert.getSystemPrompt())) {
            return "";
        }
        // 步骤 4：组装专家名称、分类和提示词，作为模型系统上下文的一部分。
        StringBuilder builder = new StringBuilder("以下是当前消息选中的专家角色，请严格遵循其角色设定回答。\n");
        builder.append("专家名称：").append(StrUtil.blankToDefault(expert.getDisplayName(), expert.getExpertCode()));
        if (StrUtil.isNotBlank(expert.getCategory())) {
            builder.append("\n专家分类：").append(expert.getCategory().trim());
        }
        builder.append("\n\n").append(expert.getSystemPrompt().trim());
        return builder.toString();
    }
}
