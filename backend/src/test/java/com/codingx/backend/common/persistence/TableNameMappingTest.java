package com.codingx.backend.common.persistence;
import static org.junit.jupiter.api.Assertions.assertEquals;
import com.baomidou.mybatisplus.annotation.TableName;
import com.codingx.backend.artifact.infrastructure.persistence.dataobject.TaskArtifactDO;
import com.codingx.backend.chat.infrastructure.persistence.dataobject.ChatConversationDO;
import com.codingx.backend.chat.infrastructure.persistence.dataobject.ChatMessageDO;
import com.codingx.backend.event.infrastructure.persistence.dataobject.TaskEventDO;
import com.codingx.backend.task.infrastructure.persistence.dataobject.TaskDO;
import org.junit.jupiter.api.Test;

/**
 * Tests the key scenarios covered by TableNameMapping.
 */
class TableNameMappingTest {

    /**
     * Executes the logic defined by persistenceObjectsUsePrefixFreeTableNames.
     */
    @Test
    void persistenceObjectsUsePrefixFreeTableNames() {

        assertEquals("task", tableNameOf(TaskDO.class));
        assertEquals("task_event", tableNameOf(TaskEventDO.class));
        assertEquals("task_artifact", tableNameOf(TaskArtifactDO.class));
        assertEquals("chat_conversation", tableNameOf(ChatConversationDO.class));
        assertEquals("chat_message", tableNameOf(ChatMessageDO.class));
    }

    /**
     * Executes the logic defined by tableNameOf.
     * @param type input argument.
     * @return processing result.
     */
    private String tableNameOf(Class<?> type) {
        return type.getAnnotation(TableName.class).value();
    }
}
