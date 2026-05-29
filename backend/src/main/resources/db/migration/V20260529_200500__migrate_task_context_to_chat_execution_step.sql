WITH selected_runs AS (
    SELECT task_id AS run_id FROM task_skill
    UNION
    SELECT task_id AS run_id FROM task_mcp
),
skill_context AS (
    SELECT task_id AS run_id, jsonb_agg(skill_code ORDER BY id) AS skill_codes
    FROM (
        SELECT DISTINCT ON (task_id, skill_code) task_id, skill_code, id
        FROM task_skill
        WHERE skill_code IS NOT NULL AND btrim(skill_code) <> ''
        ORDER BY task_id, skill_code, id
    ) dedup_skill
    GROUP BY task_id
),
mcp_context AS (
    SELECT task_id AS run_id, jsonb_agg(mcp_code ORDER BY id) AS mcp_codes
    FROM (
        SELECT DISTINCT ON (task_id, mcp_code) task_id, mcp_code, id
        FROM task_mcp
        WHERE mcp_code IS NOT NULL AND btrim(mcp_code) <> ''
        ORDER BY task_id, mcp_code, id
    ) dedup_mcp
    GROUP BY task_id
),
context_rows AS (
    SELECT
        selected_runs.run_id,
        COALESCE(skill_context.skill_codes, '[]'::jsonb) AS skill_codes,
        COALESCE(mcp_context.mcp_codes, '[]'::jsonb) AS mcp_codes,
        row_number() OVER (ORDER BY selected_runs.run_id) AS row_no
    FROM selected_runs
    LEFT JOIN skill_context ON skill_context.run_id = selected_runs.run_id
    LEFT JOIN mcp_context ON mcp_context.run_id = selected_runs.run_id
)
INSERT INTO chat_execution_step (
    id,
    run_id,
    step_type,
    step_title,
    step_status,
    sequence_no,
    content,
    metadata_json,
    created_at,
    updated_at
)
SELECT
    ((extract(epoch FROM clock_timestamp()) * 1000)::bigint * 1000000 + context_rows.row_no) AS id,
    context_rows.run_id,
    'runtime_context',
    '运行上下文',
    'COMPLETED',
    0,
    '本轮能力上下文',
    jsonb_build_object(
        'skillCodes', context_rows.skill_codes,
        'mcpCodes', context_rows.mcp_codes,
        'expertCode', NULL
    )::text,
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
FROM context_rows
WHERE NOT EXISTS (
    SELECT 1
    FROM chat_execution_step existing_step
    WHERE existing_step.run_id = context_rows.run_id
      AND existing_step.step_type = 'runtime_context'
);

WITH skill_mentions AS (
    SELECT task_id AS run_id, string_agg('@' || skill_code, ' ' ORDER BY id) AS mentions
    FROM (
        SELECT DISTINCT ON (task_id, skill_code) task_id, skill_code, id
        FROM task_skill
        WHERE skill_code IS NOT NULL AND btrim(skill_code) <> ''
        ORDER BY task_id, skill_code, id
    ) dedup_skill
    GROUP BY task_id
)
UPDATE chat_message
SET content = skill_mentions.mentions || ' ' || btrim(regexp_replace(chat_message.content, '^[[:space:]]*(?:@[A-Za-z0-9_.:-]+[[:space:]]*)+', '')),
    updated_at = CURRENT_TIMESTAMP
FROM skill_mentions
WHERE chat_message.run_id = skill_mentions.run_id
  AND chat_message.role = 'USER'
  AND chat_message.content IS NOT NULL
  AND chat_message.content !~ '^[[:space:]]*@';

DROP TABLE IF EXISTS task_artifact;
DROP TABLE IF EXISTS task_event;
DROP TABLE IF EXISTS task_mcp;
DROP TABLE IF EXISTS task_skill;
