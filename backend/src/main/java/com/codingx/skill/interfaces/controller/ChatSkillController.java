package com.codingx.skill.interfaces.controller;

import com.codingx.skill.application.service.ChatSkillQueryService;
import com.codingx.skill.domain.model.ChatSkill;
import com.codingx.common.model.ApiResponse;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 提供用户侧聊天技能查询接口。
 */
@RestController
@RequestMapping({"/api/skills", "/api/chat/skills"})
@RequiredArgsConstructor
public class ChatSkillController {

    private final ChatSkillQueryService chatSkillQueryService;

    /**
     * 查询当前启用技能列表，兼容用户侧旧路径与新路径。
     * @return 技能列表。
     */
    @GetMapping
    public ApiResponse<List<ChatSkill>> listEnabledSkills() {
        return ApiResponse.success(chatSkillQueryService.listEnabledSkills());
    }
}

