package com.codingx.chat.interfaces.controller;

import com.codingx.chat.application.service.AdminChatSkillService;
import com.codingx.chat.domain.model.ChatSkill;
import com.codingx.chat.interfaces.response.AdminSkillPackageEntryResponse;
import com.codingx.chat.interfaces.response.AdminSkillPackageFileContentResponse;
import com.codingx.common.model.ApiResponse;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

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

    /**
     * 上传技能包并自动解析技能元信息。
     * @param file 技能包文件。
     * @param category 可选分类。
     * @return 解析后的技能记录。
     */
    @PostMapping("/upload")
    public ApiResponse<ChatSkill> uploadSkillPackage(
        @RequestParam("file") MultipartFile file,
        @RequestParam(value = "category", required = false) String category
    ) {
        return ApiResponse.success(adminChatSkillService.uploadSkillPackage(file, category));
    }

    /**
     * 返回技能包文件树，供管理端资源管理器按目录浏览。
     * @param id 技能主键。
     * @return 目录树条目列表。
     */
    @GetMapping("/{id}/package/entries")
    public ApiResponse<List<AdminSkillPackageEntryResponse>> listPackageEntries(@PathVariable Long id) {
        List<AdminSkillPackageEntryResponse> responses = adminChatSkillService.listPackageEntries(id)
            .stream()
            .map(entry -> new AdminSkillPackageEntryResponse(
                entry.path(),
                entry.name(),
                entry.directory(),
                entry.size()
            ))
            .toList();
        return ApiResponse.success(responses);
    }

    /**
     * 在线读取技能包中的文本文件内容，供管理端预览。
     * @param id 技能主键。
     * @param path 文件相对路径。
     * @return 文件内容预览。
     */
    @GetMapping("/{id}/package/file-content")
    public ApiResponse<AdminSkillPackageFileContentResponse> readPackageFileContent(
        @PathVariable Long id,
        @RequestParam("path") String path
    ) {
        AdminChatSkillService.SkillPackageFileContent content = adminChatSkillService.readPackageFileContent(id, path);
        return ApiResponse.success(new AdminSkillPackageFileContentResponse(
            content.path(),
            content.content(),
            content.truncated()
        ));
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
