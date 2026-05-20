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

    private final ChatExpertRepository chatExpertRepository;

    /**
     * 根据专家编码构建专家上下文。
     * @param expertCode 专家编码。
     * @return 专家 system prompt，上下文缺失时返回空字符串。
     */
    public String buildExpertContext(String expertCode) {
        if (StrUtil.isBlank(expertCode)) {
            return "";
        }
        ChatExpert expert = chatExpertRepository.findByExpertCode(expertCode.trim());
        if (expert == null || expert.getEnabled() == null || expert.getEnabled() != 1) {
            return "";
        }
        if (StrUtil.isBlank(expert.getSystemPrompt())) {
            return "";
        }
        StringBuilder builder = new StringBuilder("以下是当前消息选中的专家角色，请严格遵循其角色设定回答。\n");
        builder.append("专家名称：").append(StrUtil.blankToDefault(expert.getDisplayName(), expert.getExpertCode()));
        if (StrUtil.isNotBlank(expert.getCategory())) {
            builder.append("\n专家分类：").append(expert.getCategory().trim());
        }
        builder.append("\n\n").append(expert.getSystemPrompt().trim());
        return builder.toString();
    }
}
