package com.codingx.chat.domain.repository;

import com.codingx.chat.domain.model.ChatSampleQuestion;
import java.util.List;

/**
 * 定义示例问题仓储需要提供的查询能力。
 */
public interface ChatSampleQuestionRepository {

    /**
     * 返回当前启用的示例问题列表。
     * @return 示例问题列表。
     */
    List<ChatSampleQuestion> findEnabledQuestions();
}
