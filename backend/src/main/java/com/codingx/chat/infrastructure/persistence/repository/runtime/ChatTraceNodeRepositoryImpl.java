package com.codingx.chat.infrastructure.persistence.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.codingx.chat.domain.model.ChatTraceNode;
import com.codingx.chat.domain.repository.ChatTraceNodeRepository;
import com.codingx.chat.infrastructure.persistence.dataobject.ChatTraceNodeDO;
import com.codingx.chat.infrastructure.persistence.mapper.ChatTraceNodeMapper;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

/**
 * 实现 Trace 节点仓储的 MyBatis 持久化逻辑。
 */
@Repository
@RequiredArgsConstructor
public class ChatTraceNodeRepositoryImpl implements ChatTraceNodeRepository {

    /** Trace 节点 Mapper，用于读写 chat_trace_node 表的链路节点。 */
    private final ChatTraceNodeMapper chatTraceNodeMapper;

    @Override
    public void save(ChatTraceNode traceNode) {
        ChatTraceNodeDO dataObject = toDataObject(traceNode);
        if (chatTraceNodeMapper.selectById(traceNode.getId()) == null) {
            chatTraceNodeMapper.insert(dataObject);
        } else {
            chatTraceNodeMapper.updateById(dataObject);
        }
    }

    @Override
    public List<ChatTraceNode> findByTraceId(String traceId) {
        return chatTraceNodeMapper.selectList(new LambdaQueryWrapper<ChatTraceNodeDO>()
                .eq(ChatTraceNodeDO::getTraceId, traceId)
                .orderByAsc(ChatTraceNodeDO::getDepth)
                .orderByAsc(ChatTraceNodeDO::getCreatedAt))
            .stream()
            .map(this::toDomain)
            .toList();
    }

    private ChatTraceNodeDO toDataObject(ChatTraceNode traceNode) {
        ChatTraceNodeDO dataObject = new ChatTraceNodeDO();
        dataObject.setId(traceNode.getId());
        dataObject.setTraceId(traceNode.getTraceId());
        dataObject.setNodeId(traceNode.getNodeId());
        dataObject.setParentNodeId(traceNode.getParentNodeId());
        dataObject.setDepth(traceNode.getDepth());
        dataObject.setNodeType(traceNode.getNodeType());
        dataObject.setNodeName(traceNode.getNodeName());
        dataObject.setClassName(traceNode.getClassName());
        dataObject.setMethodName(traceNode.getMethodName());
        dataObject.setStatus(traceNode.getStatus());
        dataObject.setErrorMessage(traceNode.getErrorMessage());
        dataObject.setDurationMs(traceNode.getDurationMs());
        dataObject.setExtraDataJson(traceNode.getExtraDataJson());
        dataObject.setStartedAt(traceNode.getStartedAt());
        dataObject.setFinishedAt(traceNode.getFinishedAt());
        dataObject.setCreatedAt(traceNode.getCreatedAt());
        return dataObject;
    }

    private ChatTraceNode toDomain(ChatTraceNodeDO dataObject) {
        return ChatTraceNode.builder()
            .id(dataObject.getId())
            .traceId(dataObject.getTraceId())
            .nodeId(dataObject.getNodeId())
            .parentNodeId(dataObject.getParentNodeId())
            .depth(dataObject.getDepth())
            .nodeType(dataObject.getNodeType())
            .nodeName(dataObject.getNodeName())
            .className(dataObject.getClassName())
            .methodName(dataObject.getMethodName())
            .status(dataObject.getStatus())
            .errorMessage(dataObject.getErrorMessage())
            .durationMs(dataObject.getDurationMs())
            .extraDataJson(dataObject.getExtraDataJson())
            .startedAt(dataObject.getStartedAt())
            .finishedAt(dataObject.getFinishedAt())
            .createdAt(dataObject.getCreatedAt())
            .build();
    }
}
