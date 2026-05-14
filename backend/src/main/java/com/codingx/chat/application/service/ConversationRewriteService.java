package com.codingx.chat.application.service;

import java.util.List;
import org.springframework.stereotype.Service;

/**
 * 负责根据会话上下文改写用户问题，为搜索与意图识别提供更稳定的输入。
 */
@Service
public class ConversationRewriteService {

    /**
     * 将最近上下文与当前问题拼成更明确的改写问法。
     * @param history 历史上下文摘要。
     * @param question 当前问题。
     * @return 改写后的问题。
     */
    public String rewrite(List<String> history, String question) {
        if (history == null || history.isEmpty()) {
            return question;
        }
        return "结合上下文“" + history.getLast() + "”，当前问题是：" + question;
    }
}
