package com.codingx.chat.application.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.codingx.chat.domain.model.ChatTraceNode;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * 验证历史 Trace 节点缺失 parent/depth 时，会被查询层修复为可渲染树结构。
 */
class TraceNodeHierarchyNormalizerTest {

    /**
     * 旧节点缺失层级信息时，应该回填为入口节点的子节点。
     */
    @Test
    void normalizeShouldRepairMissingParentAndDepthForLegacyNodes() {
        ChatTraceNode root = ChatTraceNode.builder()
            .traceId("trace-1")
            .nodeId("root-node")
            .nodeType("entry")
            .nodeName("chat-entry")
            .depth(0)
            .build();
        ChatTraceNode legacyChild = ChatTraceNode.builder()
            .traceId("trace-1")
            .nodeId("node-2")
            .nodeType("INTENT")
            .nodeName("intent-classify")
            .depth(0)
            .parentNodeId(null)
            .build();

        List<ChatTraceNode> normalized = TraceNodeHierarchyNormalizer.normalize(List.of(root, legacyChild));

        assertEquals(2, normalized.size());
        assertNull(normalized.get(0).getParentNodeId());
        assertEquals(0, normalized.get(0).getDepth());
        assertEquals("root-node", normalized.get(1).getParentNodeId());
        assertEquals(1, normalized.get(1).getDepth());
    }

    /**
     * 已有父子层级信息的节点不应被篡改。
     */
    @Test
    void normalizeShouldKeepExistingHierarchyWhenAlreadyValid() {
        ChatTraceNode root = ChatTraceNode.builder()
            .traceId("trace-2")
            .nodeId("root-node")
            .nodeType("entry")
            .nodeName("chat-entry")
            .depth(0)
            .build();
        ChatTraceNode child = ChatTraceNode.builder()
            .traceId("trace-2")
            .nodeId("node-2")
            .nodeType("INTENT")
            .nodeName("intent-classify")
            .depth(1)
            .parentNodeId("root-node")
            .build();

        List<ChatTraceNode> normalized = TraceNodeHierarchyNormalizer.normalize(List.of(root, child));

        assertEquals("root-node", normalized.get(1).getParentNodeId());
        assertEquals(1, normalized.get(1).getDepth());
    }
}
