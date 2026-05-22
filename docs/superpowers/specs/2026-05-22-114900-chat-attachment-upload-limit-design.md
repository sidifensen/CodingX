# 聊天附件上传大小限制修复设计

## 背景
- 线上上传 4MB 文件时报 `MaxUploadSizeExceededException`，堆栈显示容器层单文件上限实际是 `1048576`（1MB）。
- 现有业务层附件校验曾使用固定阈值，且阈值未统一纳入系统配置表，导致“容器层限制、业务层限制、前端提示”可能不一致。

## 问题根因
1. Servlet multipart 默认或环境配置未显式覆盖，导致 Tomcat 在请求解析阶段按 1MB 拦截。
2. 容器层在 Controller 之前抛错，业务层无法返回统一中文业务错误语义。
3. 上传大小阈值没有通过 `setting` 表统一管理，运营不可动态调整。

## 目标
1. 默认上传阈值调整为 10MB。
2. 上传阈值写入系统配置表并由运行时配置服务读取。
3. 前端能力接口返回值与后端校验阈值一致。
4. 容器层超限场景也返回统一 `ApiResponse` 中文错误。

## 方案
1. 在 `setting` 表新增配置键 `chat.attachment.max_file_size_bytes`，默认值 `10485760`。
2. `RuntimeSettingService` 增加 `chatAttachmentMaxFileSizeBytes()`，统一读取数据库覆盖值并回退 `RuntimeProperties` 默认值。
3. `ChatAttachmentService` 校验逻辑改为读取运行时配置，不再硬编码阈值。
4. `ChatAttachmentController` 的 `upload-capabilities` 改为返回运行时阈值，消除前后端偏差。
5. 显式配置 `spring.servlet.multipart.max-file-size` 与 `max-request-size` 为高于业务阈值的硬上限，避免 1MB 提前拦截。
6. `GlobalExceptionHandler` 增加 `MaxUploadSizeExceededException` 处理，将容器超限统一映射为中文业务错误。

## 风险与约束
- 若环境变量把 Servlet 层上限设得低于业务阈值，仍会先触发容器层拦截；已通过全局异常处理保证返回语义一致。
- 若运营把数据库阈值设置得非常大，仍受 Servlet 层硬上限兜底保护。

## 验证策略
- 单元测试覆盖：
  - 超过 10MB 文件在业务层被拒绝。
  - `upload-capabilities` 返回 10MB。
  - `MaxUploadSizeExceededException` 映射为统一中文错误。
- 后端构建与回归：
  - `mvn compile`
  - `mvn test`
