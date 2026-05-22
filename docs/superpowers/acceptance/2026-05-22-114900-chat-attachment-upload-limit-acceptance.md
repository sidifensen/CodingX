# 聊天附件上传大小限制修复验收标准

## 功能验收
1. 默认场景下附件上传阈值为 10MB，上传 4MB 文件不应再被 1MB 提前拦截。
2. 当文件超过 10MB 时，接口返回 `ApiResponse` 失败结构，错误码为 `CHAT_ATTACHMENT_TOO_LARGE`，错误文案为中文。
3. `GET /api/chat/attachments/upload-capabilities` 返回的 `maxFileSizeBytes` 与后端业务阈值一致。
4. 容器层 `MaxUploadSizeExceededException` 也必须统一映射为中文错误响应，不透出英文底层异常给前端。

## 数据验收
1. `setting` 表存在配置键 `chat.attachment.max_file_size_bytes`。
2. 默认配置值为 `10485760`（10MB），值类型为 `LONG`。
3. 新增迁移脚本可在旧环境幂等执行，不产生重复键冲突。

## 自动化验收
1. `ChatAttachmentServiceTest` 包含“超过 10MB 被拒绝”测试并通过。
2. `ChatAttachmentControllerTest` 包含“能力接口返回 10MB”测试并通过。
3. `GlobalExceptionHandlerTest` 包含“上传超限映射”测试并通过。
4. 后端验证命令全部通过：
   - `mvn compile`
   - `mvn test`
