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

    /**
     * 关键词映射仓储，负责管理端配置的查询、保存和软删除。
     */
    private final ChatQueryTermMappingRepository chatQueryTermMappingRepository;

    /**
     * 聊天查询词映射缓存管理器，配置变更后用于清空运行时缓存。
     */
    private final ConversationQueryTermMappingCacheManager conversationQueryTermMappingCacheManager;

    /**
     * 分页查询映射规则，支持按源词和目标词搜索。
     * @param current 当前页码。
     * @param size 每页数量。
     * @param keyword 搜索关键字。
     * @return 分页结果。
     */
    public PageResult<QueryTermMappingResponse> pageQuery(int current, int size, String keyword) {
        // 步骤 1：仓储层按分页参数和关键字完成过滤，应用层不拼接查询条件。
        PageResult<ChatQueryTermMapping> page = chatQueryTermMappingRepository.pageQuery(current, size, keyword);
        // 步骤 2：将领域对象转换为管理端响应结构，保留分页元信息原样返回。
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
        // 步骤 1：按主键读取配置，查不到时统一抛出中文业务异常。
        ChatQueryTermMapping mapping = chatQueryTermMappingRepository.findById(id);
        if (mapping == null) {
            throw new NotFoundException(ErrorMessageCatalog.CHAT_QUERY_TERM_MAPPING_NOT_FOUND);
        }
        // 步骤 2：对外只返回响应对象，避免 Controller 直接暴露领域字段。
        return toResponse(mapping);
    }

    /**
     * 创建映射规则。
     * @param request 创建请求。
     * @return 创建结果。
     */
    public QueryTermMappingResponse create(QueryTermMappingCreateRequest request) {
        // 步骤 1：源词和目标词是映射规则生效的最小必填项，先校验再组装领域对象。
        validateRequired(request.sourceTerm(), request.targetTerm());
        LocalDateTime now = LocalDateTime.now();
        // 步骤 2：创建时补齐默认匹配方式、优先级、启用状态和审计时间。
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
        // 步骤 3：保存后清空运行时缓存，确保聊天链路下一次读取到最新配置。
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
        // 步骤 1：更新必须基于已有记录，避免把不存在的配置误写成新配置。
        ChatQueryTermMapping existing = chatQueryTermMappingRepository.findById(id);
        if (existing == null) {
            throw new NotFoundException(ErrorMessageCatalog.CHAT_QUERY_TERM_MAPPING_NOT_FOUND);
        }
        // 步骤 2：更新同样要求源词和目标词完整，保证规则可被运行时匹配。
        validateRequired(request.sourceTerm(), request.targetTerm());
        // 步骤 3：保留原主键和创建时间，仅覆盖管理端可编辑字段并刷新更新时间。
        ChatQueryTermMapping persisted = existing.toBuilder()
            .sourceTerm(request.sourceTerm().trim())
            .targetTerm(request.targetTerm().trim())
            .matchType(request.matchType() == null ? 1 : request.matchType())
            .priority(request.priority() == null ? 0 : request.priority())
            .enabled(request.enabled() != null && !request.enabled() ? 0 : 1)
            .remark(StrUtil.trimToNull(request.remark()))
            .updatedAt(LocalDateTime.now())
            .build();
        // 步骤 4：持久化后立即清理缓存，避免旧映射继续影响会话查询。
        chatQueryTermMappingRepository.save(persisted);
        conversationQueryTermMappingCacheManager.clear();
        return toResponse(persisted);
    }

    /**
     * 删除映射规则。
     * @param id 主键。
     */
    public void delete(Long id) {
        // 步骤 1：删除前确认配置存在，避免管理端把重复删除误判为成功。
        if (chatQueryTermMappingRepository.findById(id) == null) {
            throw new NotFoundException(ErrorMessageCatalog.CHAT_QUERY_TERM_MAPPING_NOT_FOUND);
        }
        // 步骤 2：执行软删除并清空缓存，历史数据保留给审计和回滚使用。
        chatQueryTermMappingRepository.softDeleteById(id);
        conversationQueryTermMappingCacheManager.clear();
    }

    /**
     * 保留旧接口供少量历史调用兼容，统一映射为新字段。
     * @return 映射规则列表。
     */
    public List<ChatQueryTermMapping> listAllMappings() {
        // 步骤 1：兼容旧调用方直接读取领域对象列表，不在此处转换响应 VO。
        return chatQueryTermMappingRepository.findAllMappings();
    }

    /**
     * 保留旧保存接口供兼容路径调用。
     * @param mapping 领域对象。
     * @return 持久化后的对象。
     */
    public ChatQueryTermMapping save(ChatQueryTermMapping mapping) {
        // 步骤 1：兼容入口可能传入旧字段或空默认值，这里统一修剪文本并补齐默认状态。
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
        // 步骤 2：默认值补齐后再校验必填项，确保空白字符串也会被拦截。
        validateRequired(persisted.getSourceTerm(), persisted.getTargetTerm());
        // 步骤 3：保存兼容路径数据后同样清理缓存，保持新旧入口行为一致。
        chatQueryTermMappingRepository.save(persisted);
        conversationQueryTermMappingCacheManager.clear();
        return persisted;
    }

    private void validateRequired(String sourceTerm, String targetTerm) {
        // 步骤 1：源词为空时规则无法命中用户问题，直接阻断保存。
        if (StrUtil.isBlank(sourceTerm)) {
            throw new BusinessException("CHAT_QUERY_TERM_MAPPING_INVALID", ErrorMessageCatalog.CHAT_QUERY_TERM_MAPPING_SOURCE_REQUIRED);
        }
        // 步骤 2：目标词为空时无法生成替换后的查询词，同样返回业务错误。
        if (StrUtil.isBlank(targetTerm)) {
            throw new BusinessException("CHAT_QUERY_TERM_MAPPING_INVALID", ErrorMessageCatalog.CHAT_QUERY_TERM_MAPPING_TARGET_REQUIRED);
        }
    }

    private QueryTermMappingResponse toResponse(ChatQueryTermMapping mapping) {
        // 步骤 1：把数据库整型开关转换为布尔值，减少前端对历史字段格式的感知。
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
