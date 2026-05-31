package com.codingx.chat.application.service;

import com.codingx.chat.domain.model.ChatSampleQuestion;
import com.codingx.chat.domain.repository.ChatSampleQuestionRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 负责返回首页欢迎区展示的示例问题。
 */
@Service
@RequiredArgsConstructor
public class ChatSampleQuestionService {

    /**
     * 示例问题仓储，用于按启用状态和排序号读取欢迎区问题。
     */
    private final ChatSampleQuestionRepository chatSampleQuestionRepository;

    /**
     * 返回当前启用的示例问题列表。
     * @return 示例问题列表。
     */
    public List<ChatSampleQuestion> listEnabledQuestions() {
        // 步骤 1：仓储层统一过滤 enabled/deleted 状态并按 sortNo 排序。
        return chatSampleQuestionRepository.findEnabledQuestions();
    }
}
