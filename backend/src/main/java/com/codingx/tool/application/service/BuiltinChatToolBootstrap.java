package com.codingx.tool.application.service;

import com.codingx.tool.domain.model.ChatTool;
import com.codingx.tool.domain.repository.ChatToolRepository;
import jakarta.annotation.PostConstruct;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 启动期补齐后端已接入的内置工具配置。
 * 业务意图：本地开发库默认不重跑 init.sql，新增内置工具若只写 seed 会让旧库在用户态调用时报“工具已禁用”。
 */
@Component
@RequiredArgsConstructor
public class BuiltinChatToolBootstrap {

    /**
     * 内置工具配置仓储，用于检查旧库是否已有对应工具记录。
     */
    private final ChatToolRepository chatToolRepository;

    /**
     * 启动后同步当前版本必须存在的内置工具记录；已有记录保持管理员配置不变。
     */
    @PostConstruct
    void ensureBuiltinTools() {
        for (BuiltinToolSeed seed : builtinToolSeeds()) {
            if (chatToolRepository.findByToolCode(seed.toolCode()) != null) {
                continue;
            }
            LocalDateTime now = LocalDateTime.now();
            chatToolRepository.save(ChatTool.builder()
                .id(seed.id())
                .toolCode(seed.toolCode())
                .displayName(seed.displayName())
                .description(seed.description())
                .category(seed.category())
                .sourceType(seed.sourceType())
                .enabled(1)
                .sortNo(seed.sortNo())
                .createdAt(now)
                .updatedAt(now)
                .deleted(0)
                .build());
        }
    }

    /**
     * 当前版本新增且旧库可能缺失的内置工具清单。
     *
     * @return 内置工具种子记录。
     */
    private List<BuiltinToolSeed> builtinToolSeeds() {
        return List.of(
            new BuiltinToolSeed(
                9135L,
                "git_diff",
                "Git 差异读取",
                "读取当前本地工作区的未暂存、已暂存、提交或分支差异",
                "代码审查",
                "codex-cli",
                28
            )
        );
    }

    /**
     * 描述一条需要在旧库中补齐的内置工具配置。
     */
    private record BuiltinToolSeed(
        Long id,
        String toolCode,
        String displayName,
        String description,
        String category,
        String sourceType,
        Integer sortNo
    ) {
    }
}
