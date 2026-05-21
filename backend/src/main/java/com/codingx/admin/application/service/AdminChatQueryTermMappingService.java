package com.codingx.admin.application.service;

import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.StrUtil;
import com.codingx.chat.application.service.ConversationQueryTermMappingCacheManager;
import com.codingx.chat.domain.model.ChatQueryTermMapping;
import com.codingx.chat.domain.repository.ChatQueryTermMappingRepository;
import com.codingx.chat.interfaces.request.QueryTermMappingCreateRequest;
import com.codingx.chat.interfaces.request.QueryTermMappingUpdateRequest;
import com.codingx.chat.interfaces.response.PageResult;
import com.codingx.chat.interfaces.response.QueryTermMappingResponse;
import com.codingx.common.error.ErrorMessageCatalog;
import com.codingx.common.exception.BusinessException;
import com.codingx.common.exception.NotFoundException;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 提供关键词映射后台管理服务。
 */
@Service
@RequiredArgsConstructor
public class AdminChatQueryTermMappingService {

    private final ChatQueryTermMappingRepository chatQueryTermMappingRepository;
    private final ConversationQueryTermMappingCacheManager conversationQueryTermMappingCacheManager;

    /**
     * 分页查询映射规则，支持按源词和目标词搜索。
     * @param current 当前页码。
     * @param size 每页数量。
     * @param keyword 搜索关键字。
     * @return 分页结果。
     */
    public PageResult<QueryTermMappingResponse> pageQuery(int current, int size, String keyword) {
        PageResult<ChatQueryTermMapping> page = chatQueryTermMappingRepository.pageQuery(current, size, keyword);
        return PageResult.<QueryTermMappingResponse>builder()
            .records(page.records().stream().map(this::toResponse).toList())
            .total(page.total())
            .size(page.size())
            .current(page.current())
            .pages(page.pages())
            .build();
    }

    /**
     * 查询单条映射规则详情。
     * @param id 主键。
     * @return 映射规则视图。
     */
    public QueryTermMappingResponse queryById(Long id) {
        ChatQueryTermMapping mapping = chatQueryTermMappingRepository.findById(id);
        if (mapping == null) {
            throw new NotFoundException(ErrorMessageCatalog.CHAT_QUERY_TERM_MAPPING_NOT_FOUND);
        }
        return toResponse(mapping);
    }

    /**
     * 创建映射规则。
     * @param request 创建请求。
     * @return 创建结果。
     */
    public QueryTermMappingResponse create(QueryTermMappingCreateRequest request) {
        validateRequired(request.sourceTerm(), request.targetTerm());
        LocalDateTime now = LocalDateTime.now();
        ChatQueryTermMapping persisted = ChatQueryTermMapping.builder()
            .id(IdUtil.getSnowflakeNextId())
            .sourceTerm(request.sourceTerm().trim())
            .targetTerm(request.targetTerm().trim())
            .matchType(request.matchType() == null ? 1 : request.matchType())
            .priority(request.priority() == null ? 0 : request.priority())
            .enabled(request.enabled() != null && !request.enabled() ? 0 : 1)
            .remark(StrUtil.trimToNull(request.remark()))
            .createdAt(now)
            .updatedAt(now)
            .deleted(0)
            .build();
        chatQueryTermMappingRepository.save(persisted);
        conversationQueryTermMappingCacheManager.clear();
        return toResponse(persisted);
    }

    /**
     * 更新映射规则。
     * @param id 主键。
     * @param request 更新请求。
     * @return 更新结果。
     */
    public QueryTermMappingResponse update(Long id, QueryTermMappingUpdateRequest request) {
        ChatQueryTermMapping existing = chatQueryTermMappingRepository.findById(id);
        if (existing == null) {
            throw new NotFoundException(ErrorMessageCatalog.CHAT_QUERY_TERM_MAPPING_NOT_FOUND);
        }
        validateRequired(request.sourceTerm(), request.targetTerm());
        ChatQueryTermMapping persisted = existing.toBuilder()
            .sourceTerm(request.sourceTerm().trim())
            .targetTerm(request.targetTerm().trim())
            .matchType(request.matchType() == null ? 1 : request.matchType())
            .priority(request.priority() == null ? 0 : request.priority())
            .enabled(request.enabled() != null && !request.enabled() ? 0 : 1)
            .remark(StrUtil.trimToNull(request.remark()))
            .updatedAt(LocalDateTime.now())
            .build();
        chatQueryTermMappingRepository.save(persisted);
        conversationQueryTermMappingCacheManager.clear();
        return toResponse(persisted);
    }

    /**
     * 删除映射规则。
     * @param id 主键。
     */
    public void delete(Long id) {
        if (chatQueryTermMappingRepository.findById(id) == null) {
            throw new NotFoundException(ErrorMessageCatalog.CHAT_QUERY_TERM_MAPPING_NOT_FOUND);
        }
        chatQueryTermMappingRepository.softDeleteById(id);
        conversationQueryTermMappingCacheManager.clear();
    }

    /**
     * 保留旧接口供少量历史调用兼容，统一映射为新字段。
     * @return 映射规则列表。
     */
    public List<ChatQueryTermMapping> listAllMappings() {
        return chatQueryTermMappingRepository.findAllMappings();
    }

    /**
     * 保留旧保存接口供兼容路径调用。
     * @param mapping 领域对象。
     * @return 持久化后的对象。
     */
    public ChatQueryTermMapping save(ChatQueryTermMapping mapping) {
        ChatQueryTermMapping persisted = mapping.toBuilder()
            .id(mapping.getId() == null ? IdUtil.getSnowflakeNextId() : mapping.getId())
            .sourceTerm(StrUtil.blankToDefault(StrUtil.trim(mapping.getSourceTerm()), ""))
            .targetTerm(StrUtil.blankToDefault(StrUtil.trim(mapping.getTargetTerm()), ""))
            .matchType(mapping.getMatchType() == null ? 1 : mapping.getMatchType())
            .priority(mapping.getPriority() == null ? 0 : mapping.getPriority())
            .enabled(mapping.getEnabled() == null ? 1 : mapping.getEnabled())
            .remark(StrUtil.trimToNull(mapping.getRemark()))
            .createdAt(mapping.getCreatedAt() == null ? LocalDateTime.now() : mapping.getCreatedAt())
            .updatedAt(LocalDateTime.now())
            .deleted(mapping.getDeleted() == null ? 0 : mapping.getDeleted())
            .build();
        validateRequired(persisted.getSourceTerm(), persisted.getTargetTerm());
        chatQueryTermMappingRepository.save(persisted);
        conversationQueryTermMappingCacheManager.clear();
        return persisted;
    }

    private void validateRequired(String sourceTerm, String targetTerm) {
        if (StrUtil.isBlank(sourceTerm)) {
            throw new BusinessException("CHAT_QUERY_TERM_MAPPING_INVALID", ErrorMessageCatalog.CHAT_QUERY_TERM_MAPPING_SOURCE_REQUIRED);
        }
        if (StrUtil.isBlank(targetTerm)) {
            throw new BusinessException("CHAT_QUERY_TERM_MAPPING_INVALID", ErrorMessageCatalog.CHAT_QUERY_TERM_MAPPING_TARGET_REQUIRED);
        }
    }

    private QueryTermMappingResponse toResponse(ChatQueryTermMapping mapping) {
        return QueryTermMappingResponse.builder()
            .id(mapping.getId())
            .sourceTerm(mapping.getSourceTerm())
            .targetTerm(mapping.getTargetTerm())
            .matchType(mapping.getMatchType())
            .priority(mapping.getPriority())
            .enabled(mapping.getEnabled() != null && mapping.getEnabled() == 1)
            .remark(mapping.getRemark())
            .createTime(mapping.getCreatedAt())
            .updateTime(mapping.getUpdatedAt())
            .build();
    }
}
