package com.codingx.chat.domain.model;

import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 聊天目标步骤实体，步骤是目标的展示快照，不再复用工具 executionSteps。
 */
@Getter
@Builder(toBuilder = true)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class ChatGoalStep {

    /** 步骤主键，创建或替换步骤快照时由服务生成。 */
    private Long id;
    /** 所属目标 ID，用于和 chat_goal 建立父子关系。 */
    private Long goalId;
    /** 模型传入或后端生成的步骤稳定键。 */
    private String stepKey;
    /** 步骤标题，展示在目标浮窗步骤列表。 */
    private String title;
    /** 步骤状态，来自模型工具入参并由服务规范化。 */
    private ChatGoalStepStatus status;
    /** 步骤排序号，数值越小越靠前。 */
    private Integer sortNo;
    /** 步骤详情或阻塞原因，可为空。 */
    private String detail;
    /** 步骤进入进行中的时间，可为空。 */
    private LocalDateTime startedAt;
    /** 步骤完成时间，可为空。 */
    private LocalDateTime completedAt;
    /** 步骤最近更新时间。 */
    private LocalDateTime updatedAt;
    /** 逻辑删除标记，0 表示正常，1 表示删除。 */
    private Integer deleted;
}
