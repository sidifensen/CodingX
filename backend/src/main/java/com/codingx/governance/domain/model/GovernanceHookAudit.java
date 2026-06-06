package com.codingx.governance.domain.model;

import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 表示一次 Hook 规则触发审计。
 */
@Getter
@Builder(toBuilder = true)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class GovernanceHookAudit {

    /** Hook 审计主键。 */
    private Long id;
    /** 命中的 Hook 编码。 */
    private String hookCode;
    /** 本次触发点。 */
    private String triggerPoint;
    /** 触发会话 ID，可为空。 */
    private Long conversationId;
    /** 触发运行 ID，可为空。 */
    private Long runId;
    /** 关联工具编码，可为空。 */
    private String toolCode;
    /** 触发状态，例如 SUCCESS、FAILED。 */
    private String status;
    /** 审计说明或异常文案。 */
    private String message;
    /** 创建时间。 */
    private LocalDateTime createdAt;
}
