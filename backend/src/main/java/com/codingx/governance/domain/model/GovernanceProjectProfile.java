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
