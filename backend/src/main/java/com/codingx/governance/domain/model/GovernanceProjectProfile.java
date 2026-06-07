package com.codingx.governance.domain.model;

import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 表示工作空间项目画像扫描结果。
 */
@Getter
@Builder(toBuilder = true)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class GovernanceProjectProfile {

    /** 项目画像主键。 */
    private Long id;
    /** 关联工作空间 ID。 */
    private Long workspaceId;
    /** 被扫描的工作空间路径。 */
    private String workspacePath;
    /** 项目画像摘要。 */
    private String summary;
    /** 技术栈 JSON 文本。 */
    private String techStackJson;
    /** 入口文件 JSON 文本。 */
    private String entrypointsJson;
    /** 建议验证命令 JSON 文本。 */
    private String verificationCommandsJson;
    /** 模块地图 JSON 文本，描述后端、用户端、管理端等仓库组成。 */
    private String moduleMapJson;
    /** 测试命令 JSON 文本，记录按模块推断出的测试或构建命令。 */
    private String testCommandsJson;
    /** 关键入口 JSON 文本，记录启动类、前端入口、控制器和配置文件。 */
    private String keyEntrypointsJson;
    /** 风险点 JSON 文本，记录扫描时发现的维护风险或验证缺口。 */
    private String riskPointsJson;
    /** Agent 输入上下文，供聊天模型理解项目结构和验证方式。 */
    private String agentContext;
    /** 扫描状态，例如 COMPLETED、FAILED。 */
    private String status;
    /** 扫描完成时间。 */
    private LocalDateTime scannedAt;
    /** 创建时间。 */
    private LocalDateTime createdAt;
    /** 更新时间。 */
    private LocalDateTime updatedAt;
    /** 逻辑删除标记，1 表示已删除。 */
    private Integer deleted;
}
