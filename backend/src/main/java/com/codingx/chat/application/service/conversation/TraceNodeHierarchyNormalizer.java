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

    /**
     * 工具类只提供静态规范化方法，不允许实例化。
     */
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
        // 步骤 1：空集合或所有节点层级完整时直接返回原集合，避免不必要复制。
        if (CollUtil.isEmpty(nodes) || !needsHierarchyRepair(nodes)) {
            return nodes;
        }
        // 步骤 2：解析入口节点；无法确定根节点时保持原数据，避免误改历史链路结构。
        Optional<ChatTraceNode> rootNodeOptional = resolveRootNode(nodes);
        if (rootNodeOptional.isEmpty() || StrUtil.isBlank(rootNodeOptional.get().getNodeId())) {
            return nodes;
        }

        // 步骤 3：逐个节点修复 parent/depth，保留 null 节点占位以维持原列表顺序。
        String rootNodeId = rootNodeOptional.get().getNodeId();
        List<ChatTraceNode> normalized = new ArrayList<>(nodes.size());
        for (ChatTraceNode node : nodes) {
            if (node == null) {
                normalized.add(null);
                continue;
            }
            boolean isRootNode = StrUtil.equals(node.getNodeId(), rootNodeId);
            if (isRootNode) {
                // 步骤 4：根节点必须无父节点且深度不小于 0，异常历史值在这里收敛。
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

            // 步骤 5：非根节点缺父节点时挂到入口节点下，缺深度或深度非法时兜底为 1。
            String parentNodeId = StrUtil.blankToDefault(node.getParentNodeId(), rootNodeId);
            Integer depth = node.getDepth();
            int normalizedDepth = depth == null || depth <= 0 ? 1 : depth;
            if (StrUtil.equals(parentNodeId, node.getParentNodeId()) && depth != null && depth > 0) {
                // 步骤 6：层级已经有效的节点保持原对象，减少对象重建和审计差异。
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
        // 步骤 1：只检查非入口节点；入口节点自身由 normalize 中单独收敛。
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
        // 步骤 1：优先使用显式 entry/root 节点，符合新 Trace 写入约定。
        Optional<ChatTraceNode> explicitEntry = nodes.stream()
            .filter(node -> node != null && isEntryNode(node) && StrUtil.isNotBlank(node.getNodeId()))
            .findFirst();
        if (explicitEntry.isPresent()) {
            return explicitEntry;
        }
        // 步骤 2：历史数据没有入口类型时，以最早开始、最早创建、最小主键作为稳定兜底根节点。
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
        // 步骤 1：兼容新 entry 类型和旧 root 类型，两者都视为链路入口。
        return StrUtil.equalsIgnoreCase(node.getNodeType(), "entry")
            || StrUtil.equalsIgnoreCase(node.getNodeType(), "root");
    }

    /**
     * 获取可比较的开始时间，空值使用最大时间兜底确保排序稳定。
     * @param node 节点。
     * @return 开始时间或兜底值。
     */
    private static LocalDateTime resolveStartedAtOrMax(ChatTraceNode node) {
        // 步骤 1：空开始时间排到最后，避免缺失值抢占根节点选择。
        return node.getStartedAt() == null ? LocalDateTime.MAX : node.getStartedAt();
    }

    /**
     * 获取可比较的创建时间，空值使用最大时间兜底确保排序稳定。
     * @param node 节点。
     * @return 创建时间或兜底值。
     */
    private static LocalDateTime resolveCreatedAtOrMax(ChatTraceNode node) {
        // 步骤 1：空创建时间排到最后，作为开始时间相同时的第二排序条件。
        return node.getCreatedAt() == null ? LocalDateTime.MAX : node.getCreatedAt();
    }
}
