# CodingX Backend

## Run

1. Copy `.env.example` to `.env`
2. Update database, redis, and AI values
3. Run `mvn spring-boot:run`

## Verify

- `mvn test`
- `mvn package`

## DDD 架构理解

- 依赖关系：`interfaces -> application -> domain`
- `infrastructure` 负责技术实现，并实现 `application/domain` 定义的接口

```text
interfaces  ->  application  ->  domain
                    ^             ^
                    |             |
             infrastructure -------
```

- `interfaces`：处理 HTTP 请求与响应，只调用 `application`
- `application`：编排用例流程，调用 `domain` 模型与仓储接口
- `domain`：承载核心业务规则，不依赖外层技术实现
- `infrastructure`：持久化、外部服务、消息流等技术细节实现
- `chat` 模块的分组落位规则见 [Chat 模块目录说明](src/main/java/com/codingx/chat/README.md)
