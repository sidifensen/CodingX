# CodingX Backend

## Run

1. Copy `.env.example` to `.env`
2. Update database, redis, and AI values
3. Run `mvn spring-boot:run`

## Verify

- `mvn test`
- `mvn package`

## DDD 架构理解

- 典型调用链路：`interfaces -> application -> domain`
- `infrastructure` 负责技术实现（如持久化、外部服务、消息流），用于实现 `domain/application` 定义的接口能力
- 建议约束：`interfaces` 不直接访问 `domain` 细节；`domain` 不依赖 `infrastructure`
