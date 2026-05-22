package com.codingx.admin.application.service;

import cn.hutool.core.util.StrUtil;
import com.codingx.auth.domain.repository.UserRepository;
import com.codingx.chat.application.service.ConversationTraceView;
import com.codingx.chat.application.service.TraceNodeHierarchyNormalizer;
import com.codingx.chat.domain.model.ChatTraceRun;
import com.codingx.chat.domain.repository.ChatTraceNodeRepository;
import com.codingx.chat.domain.repository.ChatTraceRunRepository;
import com.codingx.common.error.ErrorMessageCatalog;
import com.codingx.common.exception.NotFoundException;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 提供聊天 Trace 后台查询服务。
 */
@Service
@RequiredArgsConstructor
public class AdminChatTraceService {

    private final ChatTraceRunRepository chatTraceRunRepository;
    private final ChatTraceNodeRepository chatTraceNodeRepository;
    private final UserRepository userRepository;

    public ConversationTraceView getTrace(String traceId) {
        return new ConversationTraceView(
            chatTraceRunRepository.findByTraceId(traceId).orElseThrow(() -> new NotFoundException(ErrorMessageCatalog.CHAT_TRACE_NOT_FOUND)),
            TraceNodeHierarchyNormalizer.normalize(chatTraceNodeRepository.findByTraceId(traceId))
        );
    }

    public List<ChatTraceRun> listRecentTraces() {
        return chatTraceRunRepository.findRecent(50);
    }

    /**
     * 按页查询链路列表，并补齐 username 字段供前端展示。
     *
     * @param current 页码。
     * @param size 每页大小。
     * @param traceId 可选 traceId 过滤。
     * @return 分页结果。
     */
    public AdminTraceRunPageResultView pageTraces(int current, int size, String traceId) {
        AdminTraceRunPageView pageView = chatTraceRunRepository.pageByFilters(current, size, traceId);
        List<AdminTraceRunListItemView> records = pageView.records().stream()
            .map(traceRun -> AdminTraceRunListItemView.from(traceRun, resolveUsername(traceRun.getUserId())))
            .toList();
        return new AdminTraceRunPageResultView(records, pageView.total(), pageView.size(), pageView.current(), pageView.pages());
    }

    /**
     * 解析用户显示名，避免前端在列表页发起额外用户查询。
     *
     * @param userId 用户 ID。
     * @return 用户名，不可用时返回 null。
     */
    private String resolveUsername(Long userId) {
        if (userId == null) {
            return null;
        }
        return userRepository.findById(userId)
            .map(user -> StrUtil.isBlank(user.getUsername()) ? null : user.getUsername())
            .orElse(null);
    }
}
