package com.codingx.chat.interfaces.controller;

import com.codingx.chat.application.service.ChatSampleQuestionService;
import com.codingx.chat.application.service.ChatLightweightViewService;
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

    /**
     * 示例问题应用服务，用于读取当前启用的问题领域对象。
     */
    private final ChatSampleQuestionService chatSampleQuestionService;

    /**
     * 轻量视图服务，用于将示例问题领域对象投影为接口响应。
     */
    private final ChatLightweightViewService chatLightweightViewService;

    /**
     * 返回当前启用的示例问题列表。
     * @return 示例问题列表。
     */
    @GetMapping
    public ApiResponse<List<ChatSampleQuestionResponse>> listSampleQuestions() {
        // 步骤 1：应用服务负责读取已启用的欢迎区示例问题。
        var questions = chatSampleQuestionService.listEnabledQuestions();
        // 步骤 2：响应投影交给视图服务，Controller 不再拆解领域对象字段。
        return ApiResponse.success(chatLightweightViewService.toSampleQuestionResponses(questions));
    }
}
