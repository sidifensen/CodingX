package com.codingx.admin.interfaces.controller;

import com.codingx.skill.application.service.AdminChatSkillService;
import com.codingx.skill.application.service.SkillPackageViewService;
import com.codingx.skill.domain.model.ChatSkill;
import com.codingx.skill.interfaces.response.AdminSkillPackageEntryResponse;
import com.codingx.skill.interfaces.response.AdminSkillPackageFileContentResponse;
import com.codingx.skill.interfaces.response.AdminSkillPackageMigrationSummaryResponse;
import com.codingx.chat.interfaces.response.PageResult;
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
@RequestMapping("/api/admin/skills")
@RequiredArgsConstructor
public class AdminChatSkillController {

    /**
     * 管理端技能应用服务，承接技能 CRUD、上传、迁移和包内容读取。
     */
    private final AdminChatSkillService adminChatSkillService;

    /**
     * 技能包视图服务，负责把服务层内部结果转换为接口响应对象。
     */
    private final SkillPackageViewService skillPackageViewService;

    /**
     * 分页查询技能列表，默认每页 10 条，供管理端技能页分页展示。
     * @param current 当前页码（从 1 开始）。
     * @param size 每页条数。
     * @return 技能分页结果。
     */
    @GetMapping
    public ApiResponse<PageResult<ChatSkill>> listSkills(
        @RequestParam(defaultValue = "1") int current,
        @RequestParam(defaultValue = "10") int size
    ) {
        // 步骤 1：分页参数直接传给应用服务，排序和分页边界由仓储层处理。
        return ApiResponse.success(adminChatSkillService.pageSkills(current, size));
    }

    /**
     * 新增手工配置的聊天技能。
     * @param request 技能配置请求。
     * @return 新增后的技能记录。
     */
    @PostMapping
    public ApiResponse<ChatSkill> createSkill(@RequestBody ChatSkill request) {
        // 步骤 1：应用服务负责必填校验、技能编码去重和默认值补齐。
        return ApiResponse.<ChatSkill>success(adminChatSkillService.create(request));
    }

    /**
     * 上传技能包并自动解析技能元信息。
     * @param file 技能包文件。
     * @param files 技能文件列表（目录上传）。
     * @param category 可选分类。
     * @param forceOverwrite 是否强制覆盖同名技能。
     * @return 解析后的技能记录。
     */
    @PostMapping("/upload")
    public ApiResponse<ChatSkill> uploadSkillPackage(
        @RequestParam(value = "file", required = false) MultipartFile file,
        @RequestParam(value = "files", required = false) List<MultipartFile> files,
        @RequestParam(value = "category", required = false) String category,
        @RequestParam(value = "forceOverwrite", required = false, defaultValue = "false") Boolean forceOverwrite
    ) {
        // 步骤 1：上传服务负责解析 zip/skill 或目录上传内容，并处理同名存储键覆盖策略。
        return ApiResponse.success(adminChatSkillService.uploadSkillPackage(file, files, category, forceOverwrite));
    }

    /**
     * 迁移历史压缩包技能为目录化存储，便于运行时直接按路径读取资源。
     * @return 迁移统计信息。
     */
    @PostMapping("/migrate-packages")
    public ApiResponse<AdminSkillPackageMigrationSummaryResponse> migrateSkillPackages() {
        // 步骤 1：应用服务负责筛选历史压缩包技能并执行目录化迁移。
        AdminChatSkillService.SkillPackageMigrationSummary summary = adminChatSkillService.migrateUploadedSkillPackages();
        // 步骤 2：迁移统计响应由视图服务生成，Controller 不再拆解失败项。
        return ApiResponse.success(skillPackageViewService.toMigrationSummaryResponse(summary));
    }

    /**
     * 返回技能包文件树，供管理端资源管理器按目录浏览。
     * @param id 技能主键。
     * @return 目录树条目列表。
     */
    @GetMapping("/{id}/package/entries")
    public ApiResponse<List<AdminSkillPackageEntryResponse>> listPackageEntries(@PathVariable Long id) {
        // 步骤 1：应用服务负责必要的历史包惰性迁移和目录排序。
        List<AdminChatSkillService.SkillPackageEntry> entries = adminChatSkillService.listPackageEntries(id);
        // 步骤 2：目录树响应由视图服务统一投影。
        return ApiResponse.success(skillPackageViewService.toEntryResponses(entries));
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
        // 步骤 1：应用服务负责路径规范化、二进制拦截、图片 data URL 和文本截断。
        AdminChatSkillService.SkillPackageFileContent content = adminChatSkillService.readPackageFileContent(id, path);
        // 步骤 2：文件预览响应由视图服务统一生成。
        return ApiResponse.success(skillPackageViewService.toFileContentResponse(content));
    }

    /**
     * 更新技能基础配置。
     * @param id 技能主键。
     * @param request 技能配置请求。
     * @return 更新后的技能记录。
     */
    @PutMapping("/{id}")
    public ApiResponse<ChatSkill> updateSkill(@PathVariable Long id, @RequestBody ChatSkill request) {
        // 步骤 1：应用服务负责读取旧记录、校验编码唯一性并保留不可覆盖字段。
        return ApiResponse.<ChatSkill>success(adminChatSkillService.update(id, request));
    }

    /**
     * 删除技能配置及其对象存储资源。
     * @param id 技能主键。
     * @return 删除成功提示。
     */
    @DeleteMapping("/{id}")
    public ApiResponse<Void> deleteSkill(@PathVariable Long id) {
        // 步骤 1：应用服务负责删除对象存储资源和数据库记录。
        adminChatSkillService.delete(id);
        // 步骤 2：删除成功后只返回统一中文提示。
        return ApiResponse.successMessage("删除成功");
    }
}

