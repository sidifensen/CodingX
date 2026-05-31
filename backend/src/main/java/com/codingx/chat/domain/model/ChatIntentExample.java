package com.codingx.chat.domain.model;

import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 表示意图识别提示词使用的示例问题。
 */
@Getter
@Builder(toBuilder = true)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class ChatIntentExample {

    /** 示例主键，数据库生成，新建示例时为空。 */
    private Long id;
    /** 绑定的意图编码，来源于 chat_intent_node.intent_code。 */
    private String intentCode;
    /** 示例问题文本，用于提示词和模型识别意图时提供参考。 */
    private String exampleText;
    /** 排序号，数值越小越优先注入到提示词上下文。 */
    private Integer sortNo;
    /** 示例创建时间，由仓储写入时生成。 */
    private LocalDateTime createdAt;
}
