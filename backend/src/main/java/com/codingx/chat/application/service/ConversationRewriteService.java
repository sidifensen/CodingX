package com.codingx.chat.application.service;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 负责根据会话上下文改写用户问题，为搜索与意图识别提供更稳定的输入。
 */
@Service
@RequiredArgsConstructor
public class ConversationRewriteService {

    private final PromptTemplateLoader promptTemplateLoader;
    private final AiPromptExecutionService aiPromptExecutionService;
    private final ConversationQueryTermMappingService conversationQueryTermMappingService;

    /**
     * 使用 Prompt 驱动方式改写当前问题，失败时回退原问题。
     * @param history 历史上下文摘要。
     * @param question 当前问题。
     * @return 改写后的问题。
     */
    public String rewrite(List<String> history, String question) {
        if (StrUtil.isBlank(question)) {
            return question;
        }
        String normalizedQuestion = conversationQueryTermMappingService.normalize(question);
        String prompt = promptTemplateLoader.load("rewrite");
        String userPrompt = buildRewriteInput(history, normalizedQuestion);
        try {
            String raw = aiPromptExecutionService.complete(prompt, userPrompt);
            JSONObject root = JSONUtil.parseObj(raw);
            String rewrite = StrUtil.trim(root.getStr("rewrite"));
            return StrUtil.isBlank(rewrite) ? normalizedQuestion : rewrite;
        } catch (Exception exception) {
            return normalizedQuestion;
        }
    }

    /**
     * 组装改写阶段的用户输入，明确区分历史与当前问题。
     * @param history 历史消息。
     * @param question 当前问题。
     * @return 改写输入文本。
     */
    private String buildRewriteInput(List<String> history, String question) {
        String latestHistory = history == null || history.isEmpty() ? "无" : history.getLast();
        return "历史上下文：" + latestHistory + "\n当前问题：" + question;
    }
}
