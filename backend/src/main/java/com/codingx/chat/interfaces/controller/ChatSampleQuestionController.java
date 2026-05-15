package com.codingx.chat.interfaces.controller;

import com.codingx.chat.application.service.ChatSampleQuestionService;
import com.codingx.chat.interfaces.response.ChatSampleQuestionResponse;
import com.codingx.common.model.ApiResponse;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 提供首页欢迎区示例问题的查询接口。
 */
@RestController
@RequestMapping("/api/chat/sample-questions")
@RequiredArgsConstructor
public class ChatSampleQuestionController {

    private final ChatSampleQuestionService chatSampleQuestionService;

    /**
     * 返回当前启用的示例问题列表。
     * @return 示例问题列表。
     */
    @GetMapping
    public ApiResponse<List<ChatSampleQuestionResponse>> listSampleQuestions() {
        return ApiResponse.success(chatSampleQuestionService.listEnabledQuestions().stream()
            .map(question -> new ChatSampleQuestionResponse(question.getId(), question.getQuestionText(), question.getCategory()))
            .toList());
    }
}
