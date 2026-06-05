package com.codingx.common.persistence;
import static org.junit.jupiter.api.Assertions.assertEquals;
import com.baomidou.mybatisplus.annotation.TableName;
import com.codingx.chat.infrastructure.persistence.dataobject.ChatConversationDO;
import com.codingx.chat.infrastructure.persistence.dataobject.ChatMessageDO;
import org.junit.jupiter.api.Test;

/**
 * 验证 TableNameMapping 的关键场景。
 */
class TableNameMappingTest {

    /**
     * 执行 persistenceObjectsUsePrefixFreeTableNames 定义的处理逻辑。
     */
    @Test
    void persistenceObjectsUsePrefixFreeTableNames() {
        // 任务表已从聊天运行模型中移除；这里仅保留仍存在的持久化对象映射样例。
        assertEquals("chat_conversation", tableNameOf(ChatConversationDO.class));
        assertEquals("chat_message", tableNameOf(ChatMessageDO.class));
    }

    /**
     * 执行 tableNameOf 定义的处理逻辑。
     * @param type 数据对象类型。
     * @return 表注解中声明的表名。
     */
    private String tableNameOf(Class<?> type) {
        return type.getAnnotation(TableName.class).value();
    }
}
