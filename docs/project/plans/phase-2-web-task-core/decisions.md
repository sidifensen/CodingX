# 阶段决策记录

更新时间：2026-05-13

## 决策 001

- 标题：后端采用单 `pom.xml` 的包级 DDD 单体
- 日期：2026-05-13
- 背景：当前目标是尽快完成 `Phase 2-3` 的 Web MVP 后端，但后续仍需扩展到 `Skill / MCP / Electron / Cloud`
- 结论：不拆 Maven 多模块，不做微服务；采用单 `Spring Boot` 应用，在包结构上按业务模块与 `interfaces/application/domain/infrastructure` 分层
- 影响范围：`backend/` 工程结构、后续模块扩展方式、测试与文档组织方式

## 决策 002

- 标题：配置与密钥统一通过环境变量导入
- 日期：2026-05-13
- 背景：参考项目 `D:\code\GraphHire\backend` 已有 `.env` 导入方式，当前工程也需要兼容本地密钥与部署配置
- 结论：通过 `spring.config.import=optional:file:.env[.properties],optional:file:backend/.env[.properties]` 加载 `.env`；仓库中仅保留 `.env.example`
- 影响范围：数据库连接、Redis、Sa-Token、AI Key、跨域等配置
