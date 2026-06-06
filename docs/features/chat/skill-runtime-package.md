# Skill Runtime 技能包

## 功能用途

技能包不再只作为一段 `SKILL.md` 文本注入模型。后端会解析已选择技能的 `SKILL.md`、`.codex-skill/skill.json`、`resources/` 和 `scripts/` 目录，形成可诊断的运行时描述，并在系统提示中补充工具、资源和脚本边界。

## 使用入口

用户在聊天输入区选择技能后，`ChatSkillContextService` 构建技能上下文。目录化上传技能会从对象存储读取，内置技能缺少 `storageKey` 时回退读取类路径 `skills/<skillCode>/SKILL.md`。

## 核心流程

1. `SkillRuntimeService.loadSelectedSkillRuntimes` 按用户选择顺序读取技能配置，只解析本轮显式选择的技能，未选择技能不会进入上下文。
2. 目录化技能优先从对象存储读取根级 `SKILL.md` 和 `.codex-skill/skill.json`；历史 zip 技能继续兼容内存扫描；内置技能可从类路径读取。
3. `.codex-skill/skill.json` 中的 `tools`、`resources`、`scripts` 会解析成 `SkillRuntimeMetadata`。对象存储目录列表中 `resources/` 和 `scripts/` 下的文件会进入运行时描述。
4. `SkillResourceBoundaryService` 统一校验资源相对路径，拒绝空路径、绝对路径、`.` 和 `..` 片段，防止模型读取技能包目录之外的对象。
5. `ChatSkillContextService` 在原有 `SKILL.md` 后追加“技能运行时元数据”，列出工具声明、资源文件、脚本文件，并强调资源和脚本只能位于当前技能包目录内。
6. `web-access` 仍会追加 CodingX 的 PowerShell/CDP Proxy 运行时约束，保证技能文档中的上游 curl 示例不会误导 Windows 本地工具执行。

## 关键文件

- `backend/src/main/java/com/codingx/skill/application/service/SkillRuntimeService.java`：解析技能包 prompt、metadata、resources 和 scripts。
- `backend/src/main/java/com/codingx/skill/application/service/SkillResourceBoundaryService.java`：校验并读取技能资源，阻断路径越界。
- `backend/src/main/java/com/codingx/skill/application/service/ChatSkillContextService.java`：将技能运行时摘要注入本轮系统提示。
- `backend/src/main/java/com/codingx/common/storage/RustFsSkillPackageClient.java`：目录化技能包的对象存储读写能力。

## 测试与验证

- `mvn -Dtest=SkillRuntimeServiceTest,ChatSkillContextServiceTest,SkillPackageViewServiceTest test`
