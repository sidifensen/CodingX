# Chat 模块目录说明

## 分层关系

- `interfaces -> application -> domain`
- `infrastructure` 负责实现技术细节，并实现 `domain/application` 定义的契约

## 目录分组规则

### application

- `application/command`：用例输入命令对象
- `application/service/chat`：聊天主流程与会话运行编排
- `application/service/conversation`：意图、改写、摘要、Trace 相关服务
- `application/service/search`：搜索与后处理链路
- `application/service/support`：Prompt、运行时配置、产物存储等支撑服务

### domain

- `domain/model`：领域模型
- `domain/port`：对外端口抽象（AI、流推送）
- `domain/repository/conversation`：会话与消息相关仓储接口
- `domain/repository/intent`：意图与运行配置相关仓储接口
- `domain/repository/runtime`：执行与追踪相关仓储接口

### infrastructure/persistence

- `dataobject/conversation|intent|runtime`：各分组的数据对象
- `mapper/conversation|intent|runtime`：各分组的 MyBatis Mapper
- `repository/conversation|intent|runtime`：各分组的仓储实现

## 新增文件约束

- 新增仓储、Mapper、DO 时，按 `conversation / intent / runtime` 对应分组落位
- 新增应用服务时，优先落在 `chat / conversation / search / support` 对应目录
- 禁止在 `interfaces` 直接访问持久层 Mapper
