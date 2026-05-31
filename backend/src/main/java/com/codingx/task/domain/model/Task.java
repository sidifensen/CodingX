package com.codingx.task.domain.model;
import cn.hutool.core.util.StrUtil;
import com.codingx.common.error.ErrorMessageCatalog;
import java.time.LocalDateTime;
import java.util.List;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 任务聚合根，维护任务生命周期状态和执行结果。
 */
@Getter
@Builder(toBuilder = true)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class Task {

    /**
     * 任务主键。
     */
    private Long id;

    /**
     * 任务展示标题。
     */
    private String title;

    /**
     * 任务说明，可为空。
     */
    private String description;

    /**
     * 当前任务状态。
     */
    private TaskStatus status;

    /**
     * 任务运行时类型，决定执行器选择。
     */
    private RuntimeType runtimeType;

    /**
     * 任务关联工作空间标识，可为空。
     */
    private Long workspaceId;

    /**
     * 创建人用户标识。
     */
    private Long createdBy;

    /**
     * 任务开始执行时间，未启动时为空。
     */
    private LocalDateTime startedAt;

    /**
     * 任务完成或失败时间，未终结时为空。
     */
    private LocalDateTime finishedAt;

    /**
     * 任务失败时的错误文案，非失败状态为空。
     */
    private String errorMessage;

    /**
     * 任务成功后的摘要内容，可为空。
     */
    private String summary;

    /**
     * 任务绑定的技能编码列表，未选择技能时为空列表。
     */
    private List<String> skillCodes;

    /**
     * 创建未绑定技能的任务。
     * @param id 任务主键。
     * @param title 任务标题。
     * @param description 任务说明。
     * @param runtimeType 运行时类型。
     * @param workspaceId 工作空间标识，可为空。
     * @param createdBy 创建人用户标识。
     * @return 新任务聚合。
     */
    public static Task create(Long id, String title, String description, RuntimeType runtimeType, Long workspaceId, Long createdBy) {
        return create(id, title, description, runtimeType, workspaceId, createdBy, List.of());
    }

    /**
     * 创建初始状态的任务聚合。
     * @param id 任务主键。
     * @param title 任务标题。
     * @param description 任务说明。
     * @param runtimeType 运行时类型。
     * @param workspaceId 工作空间标识，可为空。
     * @param createdBy 创建人用户标识。
     * @param skillCodes 绑定技能编码列表。
     * @return 新任务聚合。
     */
    public static Task create(Long id, String title, String description, RuntimeType runtimeType, Long workspaceId, Long createdBy, List<String> skillCodes) {
        // 步骤 1：主键和创建人是任务归属判断的基础，缺失时拒绝创建。
        if (id == null || createdBy == null) {
            throw new IllegalArgumentException(ErrorMessageCatalog.TASK_ID_AND_CREATOR_REQUIRED);
        }
        // 步骤 2：标题和运行时决定任务能否进入执行器，必须在领域层再次兜底校验。
        if (StrUtil.isBlank(title) || runtimeType == null) {
            throw new IllegalArgumentException(ErrorMessageCatalog.TASK_TITLE_AND_RUNTIME_TYPE_REQUIRED);
        }
        // 步骤 3：任务初始状态固定为 CREATED，技能列表缺省时按空列表保存。
        return Task.builder()
            .id(id)
            .title(title)
            .description(description)
            .status(TaskStatus.CREATED)
            .runtimeType(runtimeType)
            .workspaceId(workspaceId)
            .createdBy(createdBy)
            .skillCodes(skillCodes == null ? List.of() : skillCodes)
            .build();
    }

    /**
     * 将任务从已创建状态切换为运行中。
     */
    public void start() {
        // 步骤 1：只有 CREATED 任务可以启动，避免重复执行已运行或已终结任务。
        ensureStatus(TaskStatus.CREATED, ErrorMessageCatalog.TASK_ONLY_CREATED_CAN_START);
        // 步骤 2：记录启动时间并清空历史错误，执行结果由运行时回写。
        this.status = TaskStatus.RUNNING;
        this.startedAt = LocalDateTime.now();
        this.errorMessage = null;
    }

    /**
     * 将运行中任务标记为成功。
     * @param summary 执行摘要。
     */
    public void complete(String summary) {
        // 步骤 1：只有 RUNNING 任务可以成功收口，防止未启动任务直接完成。
        ensureStatus(TaskStatus.RUNNING, ErrorMessageCatalog.TASK_ONLY_RUNNING_CAN_COMPLETE);
        // 步骤 2：写入摘要和完成时间，并清空错误文案。
        this.status = TaskStatus.SUCCEEDED;
        this.summary = summary;
        this.finishedAt = LocalDateTime.now();
        this.errorMessage = null;
    }

    /**
     * 将运行中任务标记为失败。
     * @param errorMessage 失败原因，可为空。
     */
    public void fail(String errorMessage) {
        // 步骤 1：只有 RUNNING 任务可以失败收口，避免覆盖终态任务。
        ensureStatus(TaskStatus.RUNNING, ErrorMessageCatalog.TASK_ONLY_RUNNING_CAN_FAIL);
        // 步骤 2：错误文案为空时使用统一兜底提示，避免前端展示空错误。
        this.status = TaskStatus.FAILED;
        this.errorMessage = StrUtil.blankToDefault(errorMessage, ErrorMessageCatalog.TASK_UNKNOWN_ERROR);
        this.finishedAt = LocalDateTime.now();
    }

    /**
     * 更新任务摘要。
     * @param summary 最新摘要。
     */
    public void updateSummary(String summary) {
        this.summary = summary;
    }

    /**
     * 校验当前状态是否满足指定流转前置条件。
     * @param expected 期望状态。
     * @param message 状态不匹配时抛出的中文错误文案。
     */
    private void ensureStatus(TaskStatus expected, String message) {
        if (status != expected) {
            throw new IllegalStateException(message);
        }
    }
}
