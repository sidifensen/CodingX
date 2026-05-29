# Skill 文件本地化与环境变量注入设计

## 背景

当前 skill 机制只将 `SKILL.md` 注入模型上下文，但 skill 内的脚本文件（如 `scripts/check-deps.mjs`）无法被 shell 命令访问。模型输出 `node "${CLAUDE_SKILL_DIR}/scripts/check-deps.mjs"` 时，`CLAUDE_SKILL_DIR` 环境变量未设置，导致命令执行失败。

## 目标

1. 支持本地运行时复用用户已安装的 skill 文件
2. 支持云端运行时按需下载 skill 文件到后端临时目录
3. shell 命令执行时自动注入 `CLAUDE_SKILL_DIR` 环境变量
4. 多 skill 场景下正确设置多个环境变量

## 架构设计

### 运行时分场景处理

**本地运行时（`runtime_target=local`）**：
- skill 文件由用户提前安装到本地目录（如 `~/.codingx/skills/<skillCode>/`）
- 前端发送消息时携带 `skillPaths: { "web-access": "C:\\Users\\x\\.codingx\\skills\\web-access" }`
- 后端直接使用前端传来的路径设置环境变量

**云端运行时（`runtime_target=cloud`）**：
- 后端从 RustFS 下载 skill 文件到临时目录 `<tmpdir>/codingx-skills-<uuid>/<skillCode>/`
- 响应结束后立即删除临时目录，避免堆积
- 下次使用时重新下载（skill 文件通常很小，几十 KB）

### 环境变量命名规则

**单个 skill**：
```bash
CLAUDE_SKILL_DIR=/path/to/web-access
```

**多个 skill**：
```bash
CLAUDE_SKILL_DIR_WEB_ACCESS=/path/to/web-access
CLAUDE_SKILL_DIR_SKILL_CREATOR=/path/to/skill-creator
```

环境变量名转换规则：`skillCode.toUpperCase().replace("-", "_")`

## 组件设计

### 1. 前端改动

**ChatView.tsx**：
- 新增状态：`skillPaths: Record<string, string>`，记录已选 skill 的本地路径
- 发送消息时，将 `skillPaths` 作为请求参数传递给后端
- 本地运行时：从本地存储读取已安装 skill 的路径
- 云端运行时：`skillPaths` 为空对象，后端自动下载

**skill 安装流程（本地运行时）**：
- 用户点击"安装 web-access"
- 前端从 RustFS 下载所有文件到 `~/.codingx/skills/web-access/`
- 记录路径到 localStorage：`installed_skills: { "web-access": "C:\\Users\\x\\.codingx\\skills\\web-access" }`
- 后续使用时从 localStorage 读取路径

### 2. 后端 Controller 改动

**ChatStreamController.java**：
- 新增请求参数：`@RequestParam(required = false) Map<String, String> skillPaths`
- 传递给 `ChatStreamExecutionService`

### 3. 工具执行上下文扩展

**ChatToolExecutionContext.java**：
```java
private static final ThreadLocal<Map<String, Path>> SKILL_DIRECTORIES = new ThreadLocal<>();

public static void bindSkillDirectories(Map<String, Path> skillDirs) {
    SKILL_DIRECTORIES.set(skillDirs);
}

public static Map<String, Path> currentSkillDirectories() {
    return Optional.ofNullable(SKILL_DIRECTORIES.get()).orElse(Map.of());
}

public static void clear() {
    CURRENT_TOOL_WORKING_DIRECTORY.remove();
    SKILL_DIRECTORIES.remove();
}
```

### 4. Skill 文件下载服务

**新增 SkillLocalCacheService.java**：
```java
@Service
@RequiredArgsConstructor
public class SkillLocalCacheService {
    
    private final ChatSkillRepository chatSkillRepository;
    private final RustFsSkillPackageClient rustFsClient;
    
    /**
     * 下载 skill 文件到临时目录。
     * @param skillCode skill 编码
     * @return 临时目录路径
     */
    public Path downloadSkillToTemp(String skillCode) {
        ChatSkill skill = chatSkillRepository.findBySkillCode(skillCode);
        if (skill == null || StrUtil.isBlank(skill.getStorageKey())) {
            throw new BusinessException("SKILL_NOT_FOUND", "Skill not found: " + skillCode);
        }
        
        // 创建临时目录：<tmpdir>/codingx-skills-<uuid>/<skillCode>/
        Path tempDir = Paths.get(
            System.getProperty("java.io.tmpdir"),
            "codingx-skills-" + UUID.randomUUID().toString(),
            skillCode
        );
        Files.createDirectories(tempDir);
        
        // 从 RustFS 下载所有文件
        List<SkillObjectMetadata> files = rustFsClient.listDirectory(skill.getStorageKey());
        for (SkillObjectMetadata file : files) {
            byte[] content = rustFsClient.downloadDirectoryFile(
                skill.getStorageKey(),
                file.relativePath()
            );
            Path targetFile = tempDir.resolve(file.relativePath());
            Files.createDirectories(targetFile.getParent());
            Files.write(targetFile, content);
        }
        
        return tempDir;
    }
}
```

### 5. 流式执行服务改动

**ChatStreamExecutionService.java**：
```java
public void executeStream(..., Map<String, String> skillPaths, ...) {
    Path tempSkillRoot = null;
    try {
        // 解析 skill 目录
        Map<String, Path> skillDirs = resolveSkillDirectories(
            selectedSkillCodes, 
            skillPaths, 
            workspace.getRuntimeTarget()
        );
        
        // 绑定到上下文
        ChatToolExecutionContext.bindSkillDirectories(skillDirs);
        
        // ... 执行流式响应
        
    } finally {
        // 清理云端运行时的临时目录
        if (tempSkillRoot != null) {
            FileUtil.del(tempSkillRoot.toFile());
        }
        ChatToolExecutionContext.clear();
    }
}

private Map<String, Path> resolveSkillDirectories(
    List<String> skillCodes,
    Map<String, String> skillPaths,
    RuntimeTarget runtimeTarget
) {
    Map<String, Path> result = new HashMap<>();
    
    for (String skillCode : skillCodes) {
        if (runtimeTarget == RuntimeTarget.LOCAL) {
            // 本地运行时：使用前端传来的路径
            String localPath = skillPaths.get(skillCode);
            if (StrUtil.isNotBlank(localPath)) {
                result.put(skillCode, Paths.get(localPath));
            }
        } else {
            // 云端运行时：下载到后端临时目录
            Path tempDir = skillLocalCacheService.downloadSkillToTemp(skillCode);
            result.put(skillCode, tempDir);
        }
    }
    
    return result;
}
```

### 6. Shell 执行器改动

**CodexBuiltinChatToolExecutor.java**：
```java
private String[] resolveShellCommand(String command) {
    String osName = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
    ProcessBuilder pb;
    
    if (osName.contains("win")) {
        pb = new ProcessBuilder("powershell", "-NoProfile", "-Command", command);
    } else {
        pb = new ProcessBuilder("sh", "-lc", command);
    }
    
    pb.directory(resolveToolWorkingDirectory().toFile());
    
    // 注入 skill 环境变量
    injectSkillEnvironmentVariables(pb.environment());
    
    return pb.command().toArray(new String[0]);
}

private void injectSkillEnvironmentVariables(Map<String, String> env) {
    Map<String, Path> skillDirs = ChatToolExecutionContext.currentSkillDirectories();
    
    if (skillDirs.isEmpty()) {
        return;
    }
    
    if (skillDirs.size() == 1) {
        // 单个 skill：设置 CLAUDE_SKILL_DIR
        Map.Entry<String, Path> entry = skillDirs.entrySet().iterator().next();
        env.put("CLAUDE_SKILL_DIR", entry.getValue().toString());
    } else {
        // 多个 skill：分别设置 CLAUDE_SKILL_DIR_<CODE>
        for (Map.Entry<String, Path> entry : skillDirs.entrySet()) {
            String envKey = "CLAUDE_SKILL_DIR_" + 
                entry.getKey().toUpperCase().replace("-", "_");
            env.put(envKey, entry.getValue().toString());
        }
    }
}
```

同步修改 `executeExecCommand()` 方法，确保后台命令会话也能正确注入环境变量。

## 数据流示例

### 本地运行时

1. 用户在前端安装 `web-access` skill → 下载到 `C:\Users\x\.codingx\skills\web-access\`
2. 用户选择 `@web-access` 发送消息
3. 前端发送：
   ```json
   {
     "skillCodes": ["web-access"],
     "skillPaths": {
       "web-access": "C:\\Users\\x\\.codingx\\skills\\web-access"
     }
   }
   ```
4. 后端绑定到上下文：`ChatToolExecutionContext.bindSkillDirectories(...)`
5. 模型输出：`node "${CLAUDE_SKILL_DIR}/scripts/check-deps.mjs"`
6. Shell 执行时注入：`CLAUDE_SKILL_DIR=C:\Users\x\.codingx\skills\web-access`
7. 命令成功执行

### 云端运行时

1. 用户选择 `@web-access` 发送消息（无需安装）
2. 前端发送：
   ```json
   {
     "skillCodes": ["web-access"],
     "skillPaths": {}
   }
   ```
3. 后端检测到云端运行时，从 RustFS 下载到 `/tmp/codingx-skills-abc123/web-access/`
4. 绑定到上下文并注入环境变量
5. 命令执行完成后，`finally` 块删除临时目录

## 错误处理

1. **skill 不存在**：返回友好错误提示，不阻塞其他 skill
2. **RustFS 下载失败**：记录日志，跳过该 skill
3. **本地路径无效**：验证路径存在且可读，否则忽略
4. **临时目录清理失败**：记录警告日志，不影响响应

## 测试策略

1. **单元测试**：
   - `SkillLocalCacheService.downloadSkillToTemp()` 下载逻辑
   - `injectSkillEnvironmentVariables()` 环境变量注入逻辑
   - 单个/多个 skill 的环境变量命名

2. **集成测试**：
   - 本地运行时：传入 skillPaths，验证环境变量正确注入
   - 云端运行时：验证临时目录创建、使用、清理
   - 多 skill 场景：验证多个环境变量同时注入

3. **手工验证**：
   - 在聊天界面选择 `@web-access`，发送消息触发 `node "${CLAUDE_SKILL_DIR}/scripts/check-deps.mjs"`
   - 验证命令成功执行，输出正确结果

## 兼容性

- 不影响现有 skill 上下文注入（`SKILL.md` 仍然正常工作）
- 不影响未选择 skill 的消息（环境变量不注入）
- 向后兼容：前端不传 `skillPaths` 时，云端运行时自动下载

## 性能考虑

- skill 文件通常很小（几十 KB），下载耗时可忽略（< 100ms）
- 云端运行时每次下载，但响应结束立即清理，不占用磁盘
- 本地运行时零下载，直接复用本地文件

## 安全约束

- 本地路径验证：只允许访问用户 home 目录下的 `.codingx/skills/` 路径
- 云端临时目录：使用随机 UUID，避免路径冲突和越权访问
- 环境变量注入：只注入 `CLAUDE_SKILL_DIR` 相关变量，不覆盖系统环境变量
