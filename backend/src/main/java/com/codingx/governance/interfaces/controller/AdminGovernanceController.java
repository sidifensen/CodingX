package com.codingx.governance.interfaces.controller;

import com.codingx.common.model.ApiResponse;
import com.codingx.governance.application.service.HookRuleService;
import com.codingx.governance.application.service.LongTermMemoryService;
import com.codingx.governance.application.service.PermissionPolicyService;
import com.codingx.governance.application.service.ProjectProfileService;
import com.codingx.governance.application.service.SlashCommandService;
import com.codingx.governance.domain.model.GovernanceHookAudit;
import com.codingx.governance.domain.model.GovernanceHookRule;
import com.codingx.governance.domain.model.GovernanceLongTermMemory;
import com.codingx.governance.domain.model.GovernancePermissionAudit;
import com.codingx.governance.domain.model.GovernancePermissionPolicy;
import com.codingx.governance.domain.model.GovernanceProjectProfile;
import com.codingx.governance.domain.model.GovernanceSlashCommand;
import java.nio.file.Path;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 提供管理端治理中心接口，集中维护权限策略、Hook、项目画像和 Slash Command。
 */
@RestController
@RequestMapping("/api/admin/governance")
@RequiredArgsConstructor
public class AdminGovernanceController {

    /** 权限策略服务，用于策略 CRUD 与审计查询。 */
    private final PermissionPolicyService permissionPolicyService;
    /** Hook 规则服务，用于 Hook CRUD 与审计查询。 */
    private final HookRuleService hookRuleService;
    /** 项目画像服务，用于查询和刷新工作空间扫描结果。 */
    private final ProjectProfileService projectProfileService;
    /** Slash Command 服务，用于管理命令目录。 */
    private final SlashCommandService slashCommandService;
    /** 长期记忆服务，用于管理端查看并启停已提取的长期记忆。 */
    private final LongTermMemoryService longTermMemoryService;

    /**
     * 查询权限策略列表。
     * @return 权限策略列表。
     */
    @GetMapping("/permission-policies")
    public ApiResponse<List<GovernancePermissionPolicy>> listPermissionPolicies() {
        return ApiResponse.success(permissionPolicyService.listPolicies());
    }

    /**
     * 新增权限策略。
     * @param request 策略配置。
     * @return 保存后的策略。
     */
    @PostMapping("/permission-policies")
    public ApiResponse<GovernancePermissionPolicy> createPermissionPolicy(@RequestBody GovernancePermissionPolicy request) {
        return ApiResponse.success(permissionPolicyService.createPolicy(request));
    }

    /**
     * 更新权限策略。
     * @param id 策略主键。
     * @param request 策略配置。
     * @return 更新后的策略。
     */
    @PutMapping("/permission-policies/{id}")
    public ApiResponse<GovernancePermissionPolicy> updatePermissionPolicy(
        @PathVariable Long id,
        @RequestBody GovernancePermissionPolicy request
    ) {
        return ApiResponse.success(permissionPolicyService.updatePolicy(id, request));
    }

    /**
     * 删除权限策略。
     * @param id 策略主键。
     * @return 删除结果。
     */
    @DeleteMapping("/permission-policies/{id}")
    public ApiResponse<Void> deletePermissionPolicy(@PathVariable Long id) {
        permissionPolicyService.deletePolicy(id);
        return ApiResponse.successMessage("删除成功");
    }

    /**
     * 查询最近权限审计。
     * @param limit 最大返回条数。
     * @return 权限审计列表。
     */
    @GetMapping("/permission-audits")
    public ApiResponse<List<GovernancePermissionAudit>> listPermissionAudits(@RequestParam(defaultValue = "50") int limit) {
        return ApiResponse.success(permissionPolicyService.listRecentAudits(limit));
    }

    /**
     * 查询 Hook 规则列表。
     * @return Hook 规则列表。
     */
    @GetMapping("/hook-rules")
    public ApiResponse<List<GovernanceHookRule>> listHookRules() {
        return ApiResponse.success(hookRuleService.listRules());
    }

    /**
     * 新增 Hook 规则。
     * @param request Hook 规则配置。
     * @return 保存后的 Hook 规则。
     */
    @PostMapping("/hook-rules")
    public ApiResponse<GovernanceHookRule> createHookRule(@RequestBody GovernanceHookRule request) {
        return ApiResponse.success(hookRuleService.createRule(request));
    }

    /**
     * 更新 Hook 规则。
     * @param id Hook 规则主键。
     * @param request Hook 规则配置。
     * @return 更新后的 Hook 规则。
     */
    @PutMapping("/hook-rules/{id}")
    public ApiResponse<GovernanceHookRule> updateHookRule(@PathVariable Long id, @RequestBody GovernanceHookRule request) {
        return ApiResponse.success(hookRuleService.updateRule(id, request));
    }

    /**
     * 删除 Hook 规则。
     * @param id Hook 规则主键。
     * @return 删除结果。
     */
    @DeleteMapping("/hook-rules/{id}")
    public ApiResponse<Void> deleteHookRule(@PathVariable Long id) {
        hookRuleService.deleteRule(id);
        return ApiResponse.successMessage("删除成功");
    }

    /**
     * 查询最近 Hook 审计。
     * @param limit 最大返回条数。
     * @return Hook 审计列表。
     */
    @GetMapping("/hook-audits")
    public ApiResponse<List<GovernanceHookAudit>> listHookAudits(@RequestParam(defaultValue = "50") int limit) {
        return ApiResponse.success(hookRuleService.listRecentAudits(limit));
    }

    /**
     * 查询最近项目画像。
     * @param limit 最大返回条数。
     * @return 项目画像列表。
     */
    @GetMapping("/project-profiles")
    public ApiResponse<List<GovernanceProjectProfile>> listProjectProfiles(@RequestParam(defaultValue = "20") int limit) {
        return ApiResponse.success(projectProfileService.listRecentProfiles(limit));
    }

    /**
     * 扫描指定工作空间路径并生成项目画像。
     * @param request 扫描请求。
     * @return 新生成的画像。
     */
    @PostMapping("/project-profiles/scan")
    public ApiResponse<GovernanceProjectProfile> scanProjectProfile(@RequestBody ProjectProfileScanRequest request) {
        // 步骤 1：扫描只把协议参数转换为 Path，目录存在性和越界语义由服务层统一校验。
        return ApiResponse.success(projectProfileService.scanWorkspace(request.workspaceId(), Path.of(request.workspacePath())));
    }

    /**
     * 查询长期记忆列表，供管理端查看用户级和项目级记忆。
     * @param status 状态筛选，ALL 或空值表示全部状态。
     * @param limit 最大返回条数。
     * @return 长期记忆列表。
     */
    @GetMapping("/long-term-memories")
    public ApiResponse<List<GovernanceLongTermMemory>> listLongTermMemories(
        @RequestParam(defaultValue = "ALL") String status,
        @RequestParam(defaultValue = "50") int limit
    ) {
        return ApiResponse.success(longTermMemoryService.listAdminMemories(status, limit));
    }

    /**
     * 更新长期记忆状态，供管理员启用或停用项目约定。
     * @param id 长期记忆主键。
     * @param request 状态更新请求。
     * @return 更新后的长期记忆。
     */
    @PatchMapping("/long-term-memories/{id}/status")
    public ApiResponse<GovernanceLongTermMemory> updateLongTermMemoryStatus(
        @PathVariable Long id,
        @RequestBody MemoryStatusUpdateRequest request
    ) {
        return ApiResponse.success(longTermMemoryService.updateAdminMemoryStatus(id, request == null ? null : request.status()));
    }

    /**
     * 查询 Slash Command 配置列表。
     * @return 命令配置列表。
     */
    @GetMapping("/slash-commands")
    public ApiResponse<List<GovernanceSlashCommand>> listSlashCommands() {
        return ApiResponse.success(slashCommandService.listAllCommands());
    }

    /**
     * 新增 Slash Command。
     * @param request 命令配置。
     * @return 保存后的命令。
     */
    @PostMapping("/slash-commands")
    public ApiResponse<GovernanceSlashCommand> createSlashCommand(@RequestBody GovernanceSlashCommand request) {
        return ApiResponse.success(slashCommandService.createCommand(request));
    }

    /**
     * 更新 Slash Command。
     * @param id 命令主键。
     * @param request 命令配置。
     * @return 更新后的命令。
     */
    @PutMapping("/slash-commands/{id}")
    public ApiResponse<GovernanceSlashCommand> updateSlashCommand(
        @PathVariable Long id,
        @RequestBody GovernanceSlashCommand request
    ) {
        return ApiResponse.success(slashCommandService.updateCommand(id, request));
    }

    /**
     * 删除 Slash Command。
     * @param id 命令主键。
     * @return 删除结果。
     */
    @DeleteMapping("/slash-commands/{id}")
    public ApiResponse<Void> deleteSlashCommand(@PathVariable Long id) {
        slashCommandService.deleteCommand(id);
        return ApiResponse.successMessage("删除成功");
    }

    /**
     * 项目画像扫描请求。
     *
     * @param workspaceId 工作空间 ID。
     * @param workspacePath 工作空间路径。
     */
    public record ProjectProfileScanRequest(Long workspaceId, String workspacePath) {
    }

    /**
     * 长期记忆状态更新请求。
     *
     * @param status 目标状态，允许 ACTIVE 或 REJECTED。
     */
    public record MemoryStatusUpdateRequest(String status) {
    }
}
