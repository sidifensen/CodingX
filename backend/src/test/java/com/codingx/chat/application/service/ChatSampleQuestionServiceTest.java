package com.codingx.chat.application.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

import com.codingx.chat.domain.model.ChatSampleQuestion;
import com.codingx.chat.domain.repository.ChatSampleQuestionRepository;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * 验证示例问题服务会返回启用中的欢迎区问题列表。
 */
@ExtendWith(MockitoExtension.class)
class ChatSampleQuestionServiceTest {

    @Mock
    private ChatSampleQuestionRepository chatSampleQuestionRepository;

    @InjectMocks
    private ChatSampleQuestionService chatSampleQuestionService;

    /**
     * 服务应直接返回仓储中的启用示例问题。
     */
    @Test
    void listEnabledQuestionsReturnsRepositoryData() {
        when(chatSampleQuestionRepository.findEnabledQuestions()).thenReturn(List.of(
            ChatSampleQuestion.builder().id(1L).questionText("请介绍一下 OA 系统的主要功能").category("业务系统").build()
        ));

        List<ChatSampleQuestion> questions = chatSampleQuestionService.listEnabledQuestions();

        assertEquals(1, questions.size());
        assertEquals("请介绍一下 OA 系统的主要功能", questions.getFirst().getQuestionText());
    }
}
