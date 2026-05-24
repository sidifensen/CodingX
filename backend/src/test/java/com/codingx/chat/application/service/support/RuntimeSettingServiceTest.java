package com.codingx.chat.application.service.support;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

import com.codingx.chat.domain.model.ChatRuntimeSetting;
import com.codingx.chat.domain.repository.ChatRuntimeSettingRepository;
import com.codingx.chat.application.service.RuntimeSettingService;
import com.codingx.config.AiProperties;
import com.codingx.config.ChatExecutorRuntimeProperties;
import com.codingx.config.ChatMemoryProperties;
import com.codingx.config.RuntimeProperties;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * 验证系统配置表中的本地工具调用轮次上限读取逻辑。
 */
@ExtendWith(MockitoExtension.class)
class RuntimeSettingServiceTest {

    @Mock
    private ChatRuntimeSettingRepository chatRuntimeSettingRepository;

    @Mock
    private RuntimeProperties runtimeProperties;

    @Mock
    private ChatExecutorRuntimeProperties chatExecutorRuntimeProperties;

    @Mock
    private ChatMemoryProperties chatMemoryProperties;

    @Mock
    private AiProperties aiProperties;

    @InjectMocks
    private RuntimeSettingService runtimeSettingService;

    /**
     * 系统配置存在时，应优先读取数据库中的显式配置值。
     */
    @Test
    void chatToolMaxRoundsReadsConfiguredValue() {
        when(chatRuntimeSettingRepository.findAll()).thenReturn(List.of(
            ChatRuntimeSetting.builder()
                .id(1L)
                .settingKey("chat.tool.max_rounds")
                .settingValue("10")
                .valueType("INTEGER")
                .categoryCode("chat.tool")
                .description("本地工具调用最大轮次")
                .sortNo(10)
                .restartRequired(false)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .deleted(0)
                .build()
        ));

        runtimeSettingService.init();

        assertEquals(10, runtimeSettingService.chatToolMaxRounds());
    }

    /**
     * 系统配置缺失时，应回退到代码内置默认值 10。
     */
    @Test
    void chatToolMaxRoundsFallsBackToDefaultWhenMissing() {
        when(chatRuntimeSettingRepository.findAll()).thenReturn(List.of());

        runtimeSettingService.init();

        assertEquals(10, runtimeSettingService.chatToolMaxRounds());
    }
}
