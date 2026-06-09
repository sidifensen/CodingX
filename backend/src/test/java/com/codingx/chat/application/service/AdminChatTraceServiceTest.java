package com.codingx.chat.application.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.codingx.admin.application.service.AdminChatTraceService;
import com.codingx.admin.application.service.AdminTraceRunPageResultView;
import com.codingx.admin.application.service.AdminTraceRunPageView;
import com.codingx.auth.domain.model.User;
import com.codingx.auth.domain.model.UserStatus;
import com.codingx.auth.domain.model.UserType;
import com.codingx.auth.domain.repository.UserRepository;
import com.codingx.chat.domain.model.ChatTraceRun;
import com.codingx.chat.domain.repository.ChatTraceNodeRepository;
import com.codingx.chat.domain.repository.ChatTraceRunRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * 验证管理端 Trace 服务在分页查询时会透传筛选参数，并补齐用户名展示字段。
 */
@ExtendWith(MockitoExtension.class)
class AdminChatTraceServiceTest {

    @Mock
    private ChatTraceRunRepository chatTraceRunRepository;

    @Mock
    private ChatTraceNodeRepository chatTraceNodeRepository;

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private AdminChatTraceService adminChatTraceService;

    /**
     * 分页查询应将 traceId 过滤条件透传到仓储，并把 userId 解析为 username。
     */
    @Test
    void pageTracesMapsUsernameAndForwardsFilter() {
        when(chatTraceRunRepository.pageByFilters(2, 5, "trace-xyz", null)).thenReturn(new AdminTraceRunPageView(
            List.of(ChatTraceRun.builder()
                .traceId("trace-xyz")
                .traceName("chat-entry")
                .userId(2001L)
                .status("SUCCESS")
                .startedAt(LocalDateTime.of(2026, 5, 16, 18, 10, 0))
                .build()),
            11,
            5,
            2,
            3
        ));
        when(userRepository.findById(2001L)).thenReturn(Optional.of(
            User.create(2001L, "admin", "管理员", "hash", UserType.ADMIN, UserStatus.ACTIVE)
        ));

        AdminTraceRunPageResultView pageView = adminChatTraceService.pageTraces(2, 5, "trace-xyz", null);

        assertEquals(11, pageView.total());
        assertEquals(1, pageView.records().size());
        assertEquals("admin", pageView.records().getFirst().username());
        verify(chatTraceRunRepository).pageByFilters(2, 5, "trace-xyz", null);
        verify(userRepository).findById(2001L);
    }

    /**
     * Dashboard 的“查看最慢链路”入口会携带排序参数，服务层必须透传给仓储保持查询口径一致。
     */
    @Test
    void pageTracesForwardsDurationSort() {
        when(chatTraceRunRepository.pageByFilters(1, 10, null, "duration_desc")).thenReturn(new AdminTraceRunPageView(
            List.of(ChatTraceRun.builder().traceId("trace-slow").traceName("chat-entry").durationMs(100_000L).status("SUCCESS").build()),
            1,
            10,
            1,
            1
        ));

        AdminTraceRunPageResultView pageView = adminChatTraceService.pageTraces(1, 10, null, "duration_desc");

        assertEquals(1, pageView.records().size());
        assertEquals(100_000L, pageView.records().getFirst().durationMs());
        verify(chatTraceRunRepository).pageByFilters(1, 10, null, "duration_desc");
    }

    /**
     * 当 traceRun 缺少 userId 时，服务不应执行无意义用户查询。
     */
    @Test
    void pageTracesSkipsUserLookupWhenUserIdMissing() {
        when(chatTraceRunRepository.pageByFilters(1, 10, null, null)).thenReturn(new AdminTraceRunPageView(
            List.of(ChatTraceRun.builder().traceId("trace-1").traceName("chat-entry").status("SUCCESS").build()),
            1,
            10,
            1,
            1
        ));

        AdminTraceRunPageResultView pageView = adminChatTraceService.pageTraces(1, 10, null, null);

        assertEquals(1, pageView.records().size());
        assertEquals(null, pageView.records().getFirst().username());
        verify(chatTraceRunRepository).pageByFilters(1, 10, null, null);
    }
}
