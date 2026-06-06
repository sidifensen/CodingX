package com.codingx.skill.application.service;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.io.IoUtil;
import cn.hutool.core.util.StrUtil;
import com.codingx.common.storage.RustFsSkillPackageClient;
import com.codingx.skill.domain.model.ChatSkill;
import com.codingx.skill.domain.repository.ChatSkillRepository;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.core.io.ClassPathResource;

/**
 * 负责按当前消息选择的技能编码读取技能说明，并组装成可注入模型的上下文提示。
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ChatSkillContextService {

    private static final String ROOT_SKILL_MANIFEST = "SKILL.md";
    private static final String WEB_ACCESS_SKILL_CODE = "web-access";
    private static final String STORAGE_FORMAT_DIRECTORY = "directory";
    private static final String STORAGE_FORMAT_ZIP = "zip";
    private static final String SOURCE_TYPE_BUILT_IN = "built-in";
    private static final String BUILT_IN_SKILL_RESOURCE_ROOT = "skills";
    private static final int MAX_SKILL_CONTEXT_COUNT = 6;
    private static final int MAX_SINGLE_MANIFEST_CHARS = 8_000;
    private static final int MAX_TOTAL_CONTEXT_CHARS = 24_000;

    /** 技能仓储，用于按技能编码读取启用配置和存储位置。 */
    private final ChatSkillRepository chatSkillRepository;
    /** 技能包存储客户端，用于从 RustFS 读取远程技能包内容。 */
    private final RustFsSkillPackageClient rustFsSkillPackageClient;

    /**
     * 构建技能上下文提示词，缺失或读取失败的技能会自动跳过。
     * @param selectedSkillCodes 当前消息选择的技能编码。
     * @return 可直接作为系统提示注入模型的文本；无有效技能时返回空字符串。
     */
    public String buildSkillContext(List<String> selectedSkillCodes) {
        // 步骤 1：去重并标准化用户选择的技能编码，没有有效编码时不注入任何技能提示。
        LinkedHashSet<String> normalizedCodes = normalizeSelectedSkillCodes(selectedSkillCodes);
        if (normalizedCodes.isEmpty()) {
            return "";
        }

        // 步骤 2：按顺序加载技能配置和 SKILL.md，超过数量或字符上限的技能进入跳过列表。
        StringBuilder contentBuilder = new StringBuilder();
        int loadedSkillCount = 0;
        List<String> loadedSkillCodes = new ArrayList<>();
        List<String> skippedSkillCodes = new ArrayList<>();
        for (String skillCode : normalizedCodes) {
            if (loadedSkillCount >= MAX_SKILL_CONTEXT_COUNT || contentBuilder.length() >= MAX_TOTAL_CONTEXT_CHARS) {
                skippedSkillCodes.add(skillCode + ":超过上下文上限");
                continue;
            }
            ChatSkill skill = chatSkillRepository.findBySkillCode(skillCode);
            if (skill == null) {
                skippedSkillCodes.add(skillCode + ":配置不存在");
                continue;
            }
            String manifestContent = readSkillManifest(skill);
            if (StrUtil.isBlank(manifestContent)) {
                skippedSkillCodes.add(skillCode + ":SKILL.md为空或读取失败");
                continue;
            }
            String normalizedManifest = StrUtil.subPre(manifestContent.trim(), MAX_SINGLE_MANIFEST_CHARS);
            if (StrUtil.isBlank(normalizedManifest)) {
                skippedSkillCodes.add(skillCode + ":SKILL.md为空");
                continue;
            }
            String manifestWithRuntimeGuidance = appendSkillRuntimeGuidance(skill, normalizedManifest);
            contentBuilder
                .append("## /")
                .append(skill.getSkillCode())
                .append("（")
                .append(StrUtil.blankToDefault(skill.getDisplayName(), skill.getSkillCode()))
                .append("）\n")
                .append(manifestWithRuntimeGuidance)
                .append("\n\n");
            loadedSkillCount++;
            loadedSkillCodes.add(skill.getSkillCode());
        }

        // 步骤 3：没有任何技能成功加载时返回空字符串并打印跳过原因，避免注入空模板。
        if (contentBuilder.isEmpty()) {
            log.warn("技能上下文未生效: 选择技能={}, 跳过技能={}", normalizedCodes, skippedSkillCodes);
            return "";
        }
        // 步骤 4：构造系统提示，明确已选技能是本轮任务意图的一部分，防止模型忽略短句技能问题。
        log.info(
            "技能上下文已生效: 选择数={}, 生效数={}, 生效技能={}, 跳过技能={}, 上下文长度={}",
            normalizedCodes.size(),
            loadedSkillCodes.size(),
            loadedSkillCodes,
            skippedSkillCodes,
            contentBuilder.length()
        );
        return """
            用户已显式选择以下技能，这些技能就是本轮任务意图的一部分。
            请优先按已选技能的说明文档判断和执行当前问题，不要因为用户正文较短或像闲聊就忽略已选技能。
            技能编码不是可执行工具名，禁止把 /skill 或 skill code 当作 tool_call 名称；如需执行能力，应按技能文档选择当前运行环境真实可用的工具或直接给出下一步。
            运行时会从模型可见的用户正文开头剥离 @skill 前缀；当用户正文使用“这个”“这些”“它”“有什么区别”等指代时，默认先指向本轮已选技能，多技能时按已选技能列表进行解释或对比。
            当用户只问“这是什么”“这是啥”等短指代且缺少 URL、页面、搜索词、附件或其他必要目标时，最终回答只允许追问具体目标，禁止输出“这是你当前选中的技能”或概括技能用途。
            只有用户明确要求介绍技能本身时，才可简述技能；否则只能把已选技能作为上下文线索，不能声称已经读取网页、连接浏览器或执行技能。
            如果缺少 URL、页面、搜索词、附件或其他必要目标，应围绕已选技能追问缺失信息；只有在可用工具实际完成后，才能给出带有执行结果的结论。
            若技能内容确实与当前问题无关，只说明缺少可执行目标，不要生硬引用技能文档。

            %s
            """.formatted(contentBuilder.toString().trim());
    }

    private LinkedHashSet<String> normalizeSelectedSkillCodes(List<String> selectedSkillCodes) {
        LinkedHashSet<String> normalizedCodes = new LinkedHashSet<>();
        if (CollUtil.isEmpty(selectedSkillCodes)) {
            return normalizedCodes;
        }
        for (String skillCode : selectedSkillCodes) {
            if (StrUtil.isNotBlank(skillCode)) {
                normalizedCodes.add(skillCode.trim());
            }
        }
        return normalizedCodes;
    }

    /**
     * 为需要真实浏览器能力的第三方技能追加 CodingX 本地执行约束。
     * 业务背景：对象存储中的 web-access 可能保留上游 curl 示例，但本项目命令工具在 Windows 环境下由
     * PowerShell 执行；这里不改第三方包，只在注入模型上下文时补充稳定用法。
     */
    private String appendSkillRuntimeGuidance(ChatSkill skill, String manifestContent) {
        // 步骤 1：仅 web-access 需要本地浏览器/CDP 运行约束，其他技能保持原始 manifest 内容。
        if (!isWebAccessSkill(skill) || StrUtil.contains(manifestContent, "CodingX 运行时约束")) {
            return manifestContent;
        }
        // 步骤 2：追加 PowerShell 专用调用方式与浏览器复用顺序，覆盖上游 curl 示例对模型的误导。
        return manifestContent + """

            ### CodingX 运行时约束

            - 当前 CodingX 后端暴露给模型的 `bash` / `shell_command` 会在 Windows PowerShell 中执行命令，不是 Bash；先调用 node "$env:CLAUDE_SKILL_DIR\\scripts\\check-deps.mjs" 检查 Node、Chrome remote-debugging 与 CDP Proxy 状态。
            - CDP Proxy 的 GET 请求使用 `Invoke-RestMethod -Uri 'http://localhost:3456/targets'`、`Invoke-RestMethod -Uri 'http://localhost:3456/info?target=TARGET_ID'` 等 PowerShell 写法；不要把上游 curl 示例原样搬到 PowerShell。
            - CDP Proxy 的 POST 请求使用 `Invoke-WebRequest -Method Post -Body 'document.title' -Uri "http://localhost:3456/eval?target=$targetId"`；不要照搬上游示例里的 curl -s -X POST 和 --data-raw。
            - 连接指定网页或用户说“连接一下”时，先 `/targets` 枚举用户当前 Chrome 页面，优先复用 URL 或标题匹配的现有 tab；只有 `/targets` 中没有匹配目标时才调用 `/new` 创建后台 tab，创建时再补上 `?url=...`。
            - 拿到可用 `targetId` 后必须继续调用 `/info` 确认标题、最终 URL 和 ready 状态，再调用 `/eval` 读取 `document.title` 与 `document.body.innerText`，或用 `/screenshot` 保存证据。
            - 只输出“已创建 tab”“准备读取页面”“正在执行 /eval”都属于中间过程，禁止在这里结束回复；没有页面标题、URL、正文摘要或截图结果时，不得向用户声明已完成。
            - `targetId` 只代表浏览器目标已选中或创建，不代表任务完成；如果 `/info` 返回 `about:blank`、空标题或与目标 URL 不匹配，应回到 `/targets` 重新选择现有目标或重新导航，不能把空白页当作成功结果。
            """;
    }

    private boolean isWebAccessSkill(ChatSkill skill) {
        return skill != null && StrUtil.equalsIgnoreCase(skill.getSkillCode(), WEB_ACCESS_SKILL_CODE);
    }

    /**
     * 读取技能根级 SKILL.md，兼容目录格式与历史 zip 格式。
     * @param skill 技能配置。
     * @return SKILL.md 文本，读取失败返回空字符串。
     */
    private String readSkillManifest(ChatSkill skill) {
        try {
            byte[] bytes = readManifestBytes(skill);
            if (bytes.length == 0 || looksLikeBinary(bytes)) {
                return "";
            }
            return new String(bytes, StandardCharsets.UTF_8);
        } catch (Exception ignored) {
            return "";
        }
    }

    /**
     * 统一解析技能 SKILL.md 字节，优先对象存储，内置技能缺少 storageKey 时回退类路径技能目录。
     * @param skill 技能配置。
     * @return SKILL.md 字节，缺失返回空数组。
     */
    private byte[] readManifestBytes(ChatSkill skill) {
        if (StrUtil.isNotBlank(skill.getStorageKey())) {
            return isLegacyZipStorage(skill)
                ? readManifestFromZip(skill.getStorageKey())
                : rustFsSkillPackageClient.downloadDirectoryFile(skill.getStorageKey(), ROOT_SKILL_MANIFEST);
        }
        if (isBuiltInSkill(skill)) {
            return readBuiltInManifest(skill.getSkillCode());
        }
        return new byte[0];
    }

    /**
     * 内置技能回退读取：当数据库未配置存储键时，直接读取类路径内置技能目录的 SKILL.md。
     * @param skillCode 技能编码。
     * @return SKILL.md 字节。
     */
    private byte[] readBuiltInManifest(String skillCode) {
        if (StrUtil.isBlank(skillCode)) {
            return new byte[0];
        }
        String resourcePath = BUILT_IN_SKILL_RESOURCE_ROOT + "/" + skillCode + "/" + ROOT_SKILL_MANIFEST;
        ClassPathResource resource = new ClassPathResource(resourcePath);
        if (!resource.exists()) {
            return new byte[0];
        }
        try (InputStream inputStream = resource.getInputStream()) {
            return IoUtil.readBytes(inputStream);
        } catch (IOException ignored) {
            return new byte[0];
        }
    }

    private boolean isBuiltInSkill(ChatSkill skill) {
        return StrUtil.equalsIgnoreCase(StrUtil.blankToDefault(skill.getSourceType(), ""), SOURCE_TYPE_BUILT_IN);
    }

    private boolean isLegacyZipStorage(ChatSkill skill) {
        String storageFormat = StrUtil.blankToDefault(skill.getPackageStorageFormat(), "").trim().toLowerCase(Locale.ROOT);
        if (StrUtil.equals(storageFormat, STORAGE_FORMAT_DIRECTORY)) {
            return false;
        }
        if (StrUtil.equals(storageFormat, STORAGE_FORMAT_ZIP)) {
            return true;
        }
        String storageKey = StrUtil.blankToDefault(skill.getStorageKey(), "").toLowerCase(Locale.ROOT);
        return storageKey.endsWith(".zip") || storageKey.endsWith(".skill");
    }

    private byte[] readManifestFromZip(String storageKey) {
        // 步骤 1：下载历史 zip 技能包，后续在内存中扫描 SKILL.md，避免落盘。
        byte[] archiveBytes = rustFsSkillPackageClient.download(storageKey);
        byte[] nestedManifestBytes = null;
        try (ZipInputStream zipInputStream = new ZipInputStream(new ByteArrayInputStream(archiveBytes), StandardCharsets.UTF_8)) {
            ZipEntry entry;
            // 步骤 2：逐个读取文件条目并规范化路径，优先返回根目录 SKILL.md。
            while ((entry = zipInputStream.getNextEntry()) != null) {
                if (entry.isDirectory()) {
                    continue;
                }
                String normalizedPath = normalizeArchivePath(entry.getName());
                if (StrUtil.isBlank(normalizedPath)) {
                    continue;
                }
                byte[] entryBytes = IoUtil.readBytes(zipInputStream, false);
                if (StrUtil.equalsIgnoreCase(normalizedPath, ROOT_SKILL_MANIFEST)) {
                    return entryBytes;
                }
                if (nestedManifestBytes == null && StrUtil.endWithIgnoreCase(normalizedPath, "/" + ROOT_SKILL_MANIFEST)) {
                    nestedManifestBytes = entryBytes;
                }
            }
        } catch (IOException exception) {
            // 步骤 3：压缩包损坏或编码异常时按“未读到 manifest”处理，调用方会跳过该技能。
            return new byte[0];
        }
        // 步骤 4：没有根目录 SKILL.md 时允许使用第一份嵌套 SKILL.md 兼容旧包结构。
        return nestedManifestBytes == null ? new byte[0] : nestedManifestBytes;
    }

    private String normalizeArchivePath(String originalPath) {
        String normalizedPath = StrUtil.blankToDefault(originalPath, "")
            .replace("\\", "/")
            .trim();
        while (StrUtil.startWith(normalizedPath, "./")) {
            normalizedPath = normalizedPath.substring(2);
        }
        normalizedPath = StrUtil.removePrefix(normalizedPath, "/");
        normalizedPath = StrUtil.removeSuffix(normalizedPath, "/");
        return normalizedPath;
    }

    private boolean looksLikeBinary(byte[] bytes) {
        int sampleLength = Math.min(bytes.length, 1024);
        for (int index = 0; index < sampleLength; index++) {
            if (bytes[index] == 0) {
                return true;
            }
        }
        return false;
    }
}
