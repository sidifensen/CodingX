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

    private final ChatSampleQuestionRepository chatSampleQuestionRepository;

    /**
     * 返回当前启用的示例问题列表。
     * @return 示例问题列表。
     */
    public List<ChatSampleQuestion> listEnabledQuestions() {
        return chatSampleQuestionRepository.findEnabledQuestions();
    }
}
