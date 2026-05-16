package com.codingx.chat.interfaces.controller;

import com.codingx.chat.application.service.AdminChatSkillService;
import com.codingx.chat.domain.model.ChatSkill;
import com.codingx.common.model.ApiResponse;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 提供聊天技能后台管理接口。
 */
@RestController
@RequestMapping("/api/admin/chat/skills")
@RequiredArgsConstructor
public class AdminChatSkillController {

    private final AdminChatSkillService adminChatSkillService;

    @GetMapping
    public ApiResponse<List<ChatSkill>> listSkills() {
        return ApiResponse.<List<ChatSkill>>success(adminChatSkillService.listAll());
    }

    @PostMapping
    public ApiResponse<ChatSkill> createSkill(@RequestBody ChatSkill request) {
        return ApiResponse.<ChatSkill>success(adminChatSkillService.create(request));
    }

    @PutMapping("/{id}")
    public ApiResponse<ChatSkill> updateSkill(@PathVariable Long id, @RequestBody ChatSkill request) {
        return ApiResponse.<ChatSkill>success(adminChatSkillService.update(id, request));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> deleteSkill(@PathVariable Long id) {
        adminChatSkillService.delete(id);
        return ApiResponse.successMessage("删除成功");
    }
}
