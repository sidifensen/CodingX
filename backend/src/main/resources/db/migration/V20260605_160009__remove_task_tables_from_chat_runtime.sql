-- 聊天运行状态已收敛到 chat_execution_run，专家选择补写到 runtime_context 后删除旧任务表。
CREATE OR REPLACE FUNCTION codingx_safe_jsonb(value TEXT)
RETURNS JSONB
LANGUAGE plpgsql
AS $function$
BEGIN
    RETURN COALESCE(NULLIF(btrim(value), ''), '{}')::jsonb;
EXCEPTION WHEN others THEN
    RETURN '{}'::jsonb;
END;
$function$;

DO $$
BEGIN
    IF to_regclass('public.task_expert') IS NOT NULL THEN
        -- 步骤 1：优先补写已有运行上下文，保留已经迁移过的技能与 MCP 字段。
        WITH legacy_expert_context AS (
            SELECT task_id AS run_id, max(expert_code) AS expert_code
            FROM task_expert
            WHERE expert_code IS NOT NULL AND btrim(expert_code) <> ''
            GROUP BY task_id
        )
        UPDATE chat_execution_step step
        SET metadata_json = (
                codingx_safe_jsonb(step.metadata_json)
                || jsonb_build_object('expertCode', legacy_expert_context.expert_code)
            )::text,
            updated_at = CURRENT_TIMESTAMP
        FROM legacy_expert_context
        WHERE step.run_id = legacy_expert_context.run_id
          AND lower(step.step_type) = 'runtime_context'
          AND (
              step.metadata_json IS NULL
              OR btrim(step.metadata_json) = ''
              OR COALESCE(
                  codingx_safe_jsonb(step.metadata_json) ->> 'expertCode',
                  ''
              ) = ''
          );

        -- 步骤 2：历史 run 没有上下文步骤时补一条隐藏 runtime_context。
        WITH legacy_expert_context AS (
            SELECT task_id AS run_id, max(expert_code) AS expert_code
            FROM task_expert
            WHERE expert_code IS NOT NULL AND btrim(expert_code) <> ''
            GROUP BY task_id
        ),
        missing_context AS (
            SELECT
                legacy_expert_context.run_id,
                legacy_expert_context.expert_code,
                row_number() OVER (ORDER BY legacy_expert_context.run_id) AS row_no
            FROM legacy_expert_context
            WHERE NOT EXISTS (
                SELECT 1
                FROM chat_execution_step existing_step
                WHERE existing_step.run_id = legacy_expert_context.run_id
                  AND lower(existing_step.step_type) = 'runtime_context'
            )
        ),
        id_base AS (
            SELECT GREATEST(
                COALESCE(max(id), 0),
                ((extract(epoch FROM clock_timestamp()) * 1000)::bigint * 1000000)
            ) AS base_id
            FROM chat_execution_step
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
            id_base.base_id + missing_context.row_no,
            missing_context.run_id,
            'runtime_context',
            '运行上下文',
            'COMPLETED',
            0,
            '本轮能力上下文',
            jsonb_build_object(
                'skillCodes', '[]'::jsonb,
                'mcpCodes', '[]'::jsonb,
                'expertCode', missing_context.expert_code
            )::text,
            CURRENT_TIMESTAMP,
            CURRENT_TIMESTAMP
        FROM missing_context
        CROSS JOIN id_base;
    END IF;
END $$;

DROP TABLE IF EXISTS task_expert;
DROP TABLE IF EXISTS task;
DROP FUNCTION IF EXISTS codingx_safe_jsonb(TEXT);
