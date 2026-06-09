package com.codingx.chat.infrastructure.persistence.repository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.MybatisMapperBuilderAssistant;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.codingx.admin.application.service.AdminTraceRunPageView;
import com.codingx.chat.infrastructure.persistence.dataobject.ChatTraceRunDO;
import com.codingx.chat.infrastructure.persistence.mapper.ChatTraceRunMapper;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * 验证 Trace 根链路仓储的管理端分页排序规则。
 */
@ExtendWith(MockitoExtension.class)
class ChatTraceRunRepositoryImplTest {

    /**
     * 纯 Mockito 单测不会启动 MyBatis 容器，需要手动初始化表元数据供 LambdaQueryWrapper 使用。
     */
    @BeforeAll
    static void initMybatisPlusTableInfo() {
        TableInfoHelper.initTableInfo(
            new MybatisMapperBuilderAssistant(new MybatisConfiguration(), ""),
            ChatTraceRunDO.class
        );
    }

    /** Trace 根记录 Mapper，用于模拟数据库返回顺序。 */
    @Mock
    private ChatTraceRunMapper chatTraceRunMapper;

    /** 被测 Trace 根链路仓储。 */
    @InjectMocks
    private ChatTraceRunRepositoryImpl repository;

    /**
     * 慢链路入口按耗时倒序展示时，空耗时链路必须排在已完成耗时链路之后。
     */
    @Test
    void pageByFiltersSortsDurationDescWithNullDurationLast() {
        ChatTraceRunDO running = trace(1L, "trace-running", null, LocalDateTime.of(2026, 6, 9, 12, 0, 0));
        ChatTraceRunDO fast = trace(2L, "trace-fast", 20_000L, LocalDateTime.of(2026, 6, 9, 12, 1, 0));
        ChatTraceRunDO slow = trace(3L, "trace-slow", 100_000L, LocalDateTime.of(2026, 6, 9, 12, 2, 0));
        when(chatTraceRunMapper.selectList(any())).thenReturn(List.of(running, fast, slow));

        AdminTraceRunPageView pageView = repository.pageByFilters(1, 10, null, "duration_desc");

        assertEquals("trace-slow", pageView.records().get(0).getTraceId());
        assertEquals("trace-fast", pageView.records().get(1).getTraceId());
        assertEquals("trace-running", pageView.records().get(2).getTraceId());
    }

    /**
     * 构造 Trace 数据对象，仅填充排序和映射所需字段。
     */
    private ChatTraceRunDO trace(Long id, String traceId, Long durationMs, LocalDateTime startedAt) {
        ChatTraceRunDO dataObject = new ChatTraceRunDO();
        dataObject.setId(id);
        dataObject.setTraceId(traceId);
        dataObject.setTraceName("chat-entry");
        dataObject.setDurationMs(durationMs);
        dataObject.setStartedAt(startedAt);
        dataObject.setDeleted(0);
        return dataObject;
    }
}
