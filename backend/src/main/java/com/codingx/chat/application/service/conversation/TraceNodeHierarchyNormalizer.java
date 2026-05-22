package com.codingx.chat.application.service;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import com.codingx.chat.domain.model.ChatTraceNode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * 规范化 Trace 节点父子层级，兼容历史节点缺失 parent/depth 的旧数据。
 */
public final class TraceNodeHierarchyNormalizer {

    private TraceNodeHierarchyNormalizer() {
    }

    /**
     * 对节点集合执行层级修复：
     * 1. 已有 parent/depth 的节点保持原值。
     * 2. 非根节点缺失层级时回填为挂到入口节点下，深度至少为 1。
     *
     * @param nodes 原始节点集合。
     * @return 修复后的节点集合。
     */
    public static List<ChatTraceNode> normalize(List<ChatTraceNode> nodes) {
        if (CollUtil.isEmpty(nodes) || !needsHierarchyRepair(nodes)) {
            return nodes;
        }
        Optional<ChatTraceNode> rootNodeOptional = resolveRootNode(nodes);
        if (rootNodeOptional.isEmpty() || StrUtil.isBlank(rootNodeOptional.get().getNodeId())) {
            return nodes;
        }

        String rootNodeId = rootNodeOptional.get().getNodeId();
        List<ChatTraceNode> normalized = new ArrayList<>(nodes.size());
        for (ChatTraceNode node : nodes) {
            if (node == null) {
                normalized.add(null);
                continue;
            }
            boolean isRootNode = StrUtil.equals(node.getNodeId(), rootNodeId);
            if (isRootNode) {
                Integer depth = node.getDepth();
                if (depth == null || depth < 0 || node.getParentNodeId() != null) {
                    normalized.add(node.toBuilder()
                        .parentNodeId(null)
                        .depth(Math.max(depth == null ? 0 : depth, 0))
                        .build());
                } else {
                    normalized.add(node);
                }
                continue;
            }

            String parentNodeId = StrUtil.blankToDefault(node.getParentNodeId(), rootNodeId);
            Integer depth = node.getDepth();
            int normalizedDepth = depth == null || depth <= 0 ? 1 : depth;
            if (StrUtil.equals(parentNodeId, node.getParentNodeId()) && depth != null && depth > 0) {
                normalized.add(node);
                continue;
            }
            normalized.add(node.toBuilder()
                .parentNodeId(parentNodeId)
                .depth(normalizedDepth)
                .build());
        }
        return normalized;
    }

    /**
     * 判断是否存在需要修复的节点层级字段。
     * @param nodes 节点集合。
     * @return 是否需要修复。
     */
    private static boolean needsHierarchyRepair(List<ChatTraceNode> nodes) {
        return nodes.stream()
            .filter(node -> node != null && !isEntryNode(node))
            .anyMatch(node -> StrUtil.isBlank(node.getParentNodeId()) || node.getDepth() == null || node.getDepth() <= 0);
    }

    /**
     * 解析入口节点，优先使用 type=entry，其次按开始时间最早节点兜底。
     * @param nodes 节点集合。
     * @return 入口节点。
     */
    private static Optional<ChatTraceNode> resolveRootNode(List<ChatTraceNode> nodes) {
        Optional<ChatTraceNode> explicitEntry = nodes.stream()
            .filter(node -> node != null && isEntryNode(node) && StrUtil.isNotBlank(node.getNodeId()))
            .findFirst();
        if (explicitEntry.isPresent()) {
            return explicitEntry;
        }
        return nodes.stream()
            .filter(node -> node != null && StrUtil.isNotBlank(node.getNodeId()))
            .min(Comparator
                .comparing(TraceNodeHierarchyNormalizer::resolveStartedAtOrMax)
                .thenComparing(TraceNodeHierarchyNormalizer::resolveCreatedAtOrMax)
                .thenComparing(node -> node.getId() == null ? Long.MAX_VALUE : node.getId()));
    }

    /**
     * 判断节点是否为入口节点。
     * @param node 节点。
     * @return true 表示入口节点。
     */
    private static boolean isEntryNode(ChatTraceNode node) {
        return StrUtil.equalsIgnoreCase(node.getNodeType(), "entry")
            || StrUtil.equalsIgnoreCase(node.getNodeType(), "root");
    }

    /**
     * 获取可比较的开始时间，空值使用最大时间兜底确保排序稳定。
     * @param node 节点。
     * @return 开始时间或兜底值。
     */
    private static LocalDateTime resolveStartedAtOrMax(ChatTraceNode node) {
        return node.getStartedAt() == null ? LocalDateTime.MAX : node.getStartedAt();
    }

    /**
     * 获取可比较的创建时间，空值使用最大时间兜底确保排序稳定。
     * @param node 节点。
     * @return 创建时间或兜底值。
     */
    private static LocalDateTime resolveCreatedAtOrMax(ChatTraceNode node) {
        return node.getCreatedAt() == null ? LocalDateTime.MAX : node.getCreatedAt();
    }
}
