-- 回填历史聊天消息的真实模型商：openai-compatible 只是内部协议适配器，不应作为审计 provider 保留。
WITH candidate_provider AS (
    SELECT
        model_value.setting_value AS model_name,
        MIN(provider_value.setting_value) AS provider_name
    FROM setting model_value
    JOIN setting provider_value
        ON provider_value.setting_key = regexp_replace(model_value.setting_key, '\.model$', '.provider')
        AND provider_value.deleted = 0
    WHERE model_value.setting_key LIKE 'ai.chat.candidates.%.model'
        AND model_value.deleted = 0
    GROUP BY model_value.setting_value
    HAVING COUNT(DISTINCT provider_value.setting_value) = 1
)
UPDATE chat_message message
SET provider = candidate_provider.provider_name,
    updated_at = CURRENT_TIMESTAMP
FROM candidate_provider
WHERE message.provider = 'openai-compatible'
    AND message.model = candidate_provider.model_name
    AND message.deleted = 0;
