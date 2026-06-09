package com.codingx.governance.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.codingx.chat.domain.port.ChatStreamPublisher;
import com.codingx.chat.infrastructure.stream.NoopChatStreamPublisher;
import com.codingx.governance.application.service.HookRuleService;
import com.codingx.governance.domain.model.GovernanceHookRule;
import com.codingx.governance.domain.repository.GovernanceHookRuleRepository;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/**
 * 验证 Hook 生命周期规则作为自动化配置匹配器使用，不再依赖 Hook 审计持久化。
 */
class HookRuleServiceTest {

    /**
     * 启用的 Hook 规则命中触发点和关键字后，应按排序返回给后续桌面通知或宠物联动执行器。
     */
    @Test
    void triggerShouldReturnMatchedEnabledRulesInSortOrder() {
        HookRuleService service = new HookRuleService(
            new InMemoryHookRuleRepository(List.of(
                GovernanceHookRule.builder()
                    .id(1L)
                    .hookCode("task-completed-pet")
                    .hookName("任务完成宠物提示")
                    .triggerPoint("TASK_COMPLETED")
                    .conditionKeyword("编码")
                    .actionType("PET_EVENT")
                    .enabled(1)
                    .sortNo(20)
                    .build(),
                GovernanceHookRule.builder()
                    .id(2L)
                    .hookCode("task-completed-notify")
                    .hookName("任务完成桌面通知")
                    .triggerPoint("TASK_COMPLETED")
                    .actionType("DESKTOP_NOTIFY")
                    .enabled(1)
                    .sortNo(10)
                    .build(),
                GovernanceHookRule.builder()
                    .id(3L)
                    .hookCode("task-failed-notify")
                    .hookName("任务失败桌面通知")
                    .triggerPoint("TASK_FAILED")
                    .actionType("DESKTOP_NOTIFY")
                    .enabled(1)
                    .sortNo(1)
                    .build(),
                GovernanceHookRule.builder()
                    .id(4L)
                    .hookCode("disabled-completed")
                    .hookName("停用完成通知")
                    .triggerPoint("TASK_COMPLETED")
                    .actionType("DESKTOP_NOTIFY")
                    .enabled(0)
                    .sortNo(1)
                    .build()
            ))
            ,
            new NoopChatStreamPublisher()
        );

        List<GovernanceHookRule> matchedRules = service.trigger("TASK_COMPLETED", 2001L, 3001L, null, "编码任务已完成");

        assertEquals(2, matchedRules.size());
        assertEquals("task-completed-notify", matchedRules.get(0).getHookCode());
        assertEquals("task-completed-pet", matchedRules.get(1).getHookCode());
    }

    /**
     * DESKTOP_NOTIFY 动作命中后应直接通过聊天流发布通知事件，供桌面端系统通知桥接消费。
     */
    @Test
    void triggerShouldPublishDesktopNotificationForMatchedDesktopNotifyRule() {
        ChatStreamPublisher chatStreamPublisher = org.mockito.Mockito.mock(ChatStreamPublisher.class);
        HookRuleService service = new HookRuleService(
            new InMemoryHookRuleRepository(List.of(
                GovernanceHookRule.builder()
                    .id(2L)
                    .hookCode("task-completed-notify")
                    .hookName("任务完成桌面通知")
                    .triggerPoint("TASK_COMPLETED")
                    .actionType("DESKTOP_NOTIFY")
                    .actionConfigJson("{\"title\":\"CodingX 任务完成\",\"body\":\"后台任务已完成，请回到会话查看结果\"}")
                    .enabled(1)
                    .sortNo(10)
                    .build()
            )),
            chatStreamPublisher
        );
        ArgumentCaptor<Object> payloadCaptor = ArgumentCaptor.forClass(Object.class);

        service.trigger("TASK_COMPLETED", 2001L, 3001L, null, "新闻推送任务已完成");

        verify(chatStreamPublisher).publishHookNotification(
            org.mockito.ArgumentMatchers.eq(2001L),
            payloadCaptor.capture()
        );
        @SuppressWarnings("unchecked")
        Map<String, Object> payload = (Map<String, Object>) payloadCaptor.getValue();
        assertEquals("task-completed-notify", payload.get("hookCode"));
        assertEquals("任务完成桌面通知", payload.get("hookName"));
        assertEquals("TASK_COMPLETED", payload.get("triggerPoint"));
        assertEquals("DESKTOP_NOTIFY", payload.get("actionType"));
        assertEquals(2001L, payload.get("conversationId"));
        assertEquals(3001L, payload.get("runId"));
        assertEquals("CodingX 任务完成", payload.get("title"));
        assertEquals("后台任务已完成，请回到会话查看结果", payload.get("body"));
        assertEquals("新闻推送任务已完成", payload.get("contextText"));
    }

    /**
     * 非桌面通知动作仍只作为匹配结果返回，避免宠物、脚本等未来动作被错误弹成系统通知。
     */
    @Test
    void triggerShouldNotPublishNotificationForNonDesktopAction() {
        ChatStreamPublisher chatStreamPublisher = org.mockito.Mockito.mock(ChatStreamPublisher.class);
        HookRuleService service = new HookRuleService(
            new InMemoryHookRuleRepository(List.of(
                GovernanceHookRule.builder()
                    .id(1L)
                    .hookCode("task-completed-pet")
                    .hookName("任务完成宠物提示")
                    .triggerPoint("TASK_COMPLETED")
                    .actionType("PET_EVENT")
                    .enabled(1)
                    .sortNo(10)
                    .build()
            )),
            chatStreamPublisher
        );

        List<GovernanceHookRule> matchedRules = service.trigger("TASK_COMPLETED", 2001L, 3001L, null, "任务已完成");

        assertEquals(1, matchedRules.size());
        verify(chatStreamPublisher, never()).publishHookNotification(
            org.mockito.ArgumentMatchers.anyLong(),
            org.mockito.ArgumentMatchers.any()
        );
    }

    private record InMemoryHookRuleRepository(List<GovernanceHookRule> rules) implements GovernanceHookRuleRepository {
        @Override
        public List<GovernanceHookRule> findEnabledByTriggerPoint(String triggerPoint) {
            return rules.stream()
                .filter(rule -> triggerPoint.equals(rule.getTriggerPoint()))
                .filter(rule -> rule.getEnabled() != null && rule.getEnabled() == 1)
                .toList();
        }

        @Override
        public List<GovernanceHookRule> findAll() {
            return rules;
        }

        @Override
        public GovernanceHookRule findById(Long id) {
            return rules.stream().filter(rule -> rule.getId().equals(id)).findFirst().orElse(null);
        }

        @Override
        public void save(GovernanceHookRule rule) {
        }

        @Override
        public void softDeleteById(Long id) {
        }
    }
}
