import { DiffSummary, FileDiffItem } from './types';

/**
 * 从工具结果元数据中读取文件差异列表；所有入口先经过这里，避免 UI 直接依赖后端原始 JSON 形态。
 * @param metadata 工具返回的 resultMetadata 或完整元数据对象。
 * @returns 可渲染的文件差异列表。
 */
export function normalizeFileDiffsFromMetadata(metadata: unknown): FileDiffItem[] {
  if (!isRecord(metadata)) {
    return [];
  }
  const rawFileDiffs = metadata.fileDiffs;
  if (!Array.isArray(rawFileDiffs)) {
    return [];
  }
  return rawFileDiffs
    .map((item) => normalizeFileDiffItem(item))
    .filter((item): item is FileDiffItem => item != null);
}

/**
 * 从工具元数据中读取差异汇总；缺失或不可信时根据文件列表重新计算。
 * @param metadata 工具返回元数据。
 * @param fileDiffs 已归一化的文件差异列表。
 * @returns 差异汇总；没有差异时返回 undefined 让 UI 保持轻量。
 */
export function normalizeDiffSummaryFromMetadata(
  metadata: unknown,
  fileDiffs: FileDiffItem[],
): DiffSummary | undefined {
  const summary = isRecord(metadata) && isRecord(metadata.diffSummary)
    ? {
        filesChanged: normalizeNumber(metadata.diffSummary.filesChanged),
        additions: normalizeNumber(metadata.diffSummary.additions),
        deletions: normalizeNumber(metadata.diffSummary.deletions),
      }
    : undefined;
  if (summary && (summary.filesChanged > 0 || fileDiffs.length === 0)) {
    return summary;
  }
  if (fileDiffs.length === 0) {
    return undefined;
  }
  return summarizeFileDiffs(fileDiffs);
}

/**
 * 重新汇总文件差异，供缺少 diffSummary 的历史数据或侧栏兜底使用。
 * @param fileDiffs 文件差异列表。
 * @returns 汇总结果。
 */
export function summarizeFileDiffs(fileDiffs: FileDiffItem[]): DiffSummary {
  return fileDiffs.reduce<DiffSummary>(
    (summary, fileDiff) => ({
      filesChanged: summary.filesChanged + 1,
      additions: summary.additions + fileDiff.additions,
      deletions: summary.deletions + fileDiff.deletions,
    }),
    { filesChanged: 0, additions: 0, deletions: 0 },
  );
}

/**
 * 将 path/content 类工具的入参转成“正在编辑”的临时差异条目，使 AI 写入开始阶段也能立即出现在过程里。
 * @param toolId 工具编码。
 * @param params 工具参数。
 * @returns 临时文件差异列表。
 */
export function buildPendingFileDiffsFromToolParams(
  toolId: string,
  params: Record<string, unknown> | string | undefined,
): FileDiffItem[] {
  const normalizedToolId = toolId.trim().toLowerCase();
  if (!['write', 'edit', 'apply_patch'].includes(normalizedToolId)) {
    return [];
  }
  const path = extractPendingEditPath(params);
  if (!path) {
    return [];
  }
  const diff = buildPendingUnifiedDiff(path, normalizedToolId, params);
  return [
    {
      path,
      oldPath: path,
      newPath: path,
      status: 'pending',
      additions: countDiffLines(diff, '+'),
      deletions: countDiffLines(diff, '-'),
      diff,
    },
  ];
}

/**
 * 归一化单个后端文件差异对象；路径和 diff 都缺失时丢弃，避免空行污染 UI。
 */
function normalizeFileDiffItem(value: unknown): FileDiffItem | null {
  if (!isRecord(value)) {
    return null;
  }
  const path = normalizeString(value.path) || normalizeString(value.newPath) || normalizeString(value.oldPath);
  const diff = normalizeString(value.diff);
  if (!path && !diff) {
    return null;
  }
  return {
    path: path || '未命名文件',
    oldPath: normalizeString(value.oldPath),
    newPath: normalizeString(value.newPath),
    status: normalizeString(value.status),
    additions: normalizeNumber(value.additions),
    deletions: normalizeNumber(value.deletions),
    diff,
  };
}

/**
 * 从工具入参中提取最可能的文件路径，兼容对象参数和 JSON 字符串参数。
 */
function extractPendingEditPath(params: Record<string, unknown> | string | undefined): string {
  const objectParams = typeof params === 'string' ? parseJsonRecord(params) : params;
  if (!isRecord(objectParams)) {
    return extractPatchPath(typeof params === 'string' ? params : '');
  }
  return (
    normalizeString(objectParams.path) ||
    normalizeString(objectParams.filePath) ||
    normalizeString(objectParams.targetPath) ||
    normalizeString(objectParams.filename) ||
    extractPatchPath(normalizeString(objectParams.patch)) ||
    ''
  );
}

/**
 * 根据工具开始事件的入参构造临时 unified diff，让用户在真实写盘前就能看到 AI 正在新增的内容。
 */
function buildPendingUnifiedDiff(
  path: string,
  toolId: string,
  params: Record<string, unknown> | string | undefined,
): string {
  const objectParams = typeof params === 'string' ? parseJsonRecord(params) : params;
  if (isRecord(objectParams)) {
    const patch = normalizeString(objectParams.patch);
    if (patch) {
      return patch;
    }
    const nextContent = normalizeString(objectParams.content) || normalizeString(objectParams.newString);
    const previousContent = normalizeString(objectParams.oldString);
    if (nextContent || previousContent) {
      return buildContentPreviewDiff(path, previousContent, nextContent);
    }
  }
  if (toolId === 'apply_patch' && typeof params === 'string') {
    return params;
  }
  return '';
}

/**
 * 把 start 事件里的目标内容转换为一段最小 diff 预览；完成态会被后端真实 git diff 替换。
 */
function buildContentPreviewDiff(path: string, previousContent: string, nextContent: string): string {
  const previousLines = splitPreviewLines(previousContent);
  const nextLines = splitPreviewLines(nextContent);
  return [
    `diff --git a/${path} b/${path}`,
    previousLines.length === 0 ? '--- /dev/null' : `--- a/${path}`,
    `+++ b/${path}`,
    `@@ -1,${previousLines.length} +1,${nextLines.length} @@`,
    ...previousLines.map((line) => `-${line}`),
    ...nextLines.map((line) => `+${line}`),
  ].join('\n');
}

/**
 * 统计 diff 中真实增删行数，忽略 +++/--- 文件头。
 */
function countDiffLines(diff: string, prefix: '+' | '-'): number {
  if (!diff) {
    return 0;
  }
  const headerPrefix = prefix === '+' ? '+++' : '---';
  return diff
    .split(/\r?\n/)
    .filter((line) => line.startsWith(prefix) && !line.startsWith(headerPrefix)).length;
}

/**
 * 从 Codex patch 或 unified diff 里提取目标路径，供 apply_patch 开始阶段也能显示文件列表。
 */
function extractPatchPath(patch: string): string {
  if (!patch) {
    return '';
  }
  const codexMatch = patch.match(/^\*\*\* (?:Add|Update|Delete) File:\s*(.+)$/m);
  if (codexMatch?.[1]) {
    return codexMatch[1].trim();
  }
  const gitMatch = patch.match(/^diff --git\s+a\/(.+?)\s+b\/(.+)$/m);
  if (gitMatch?.[2]) {
    return gitMatch[2].trim();
  }
  const newPathMatch = patch.match(/^\+\+\+\s+(?:b\/)?(.+)$/m);
  return newPathMatch?.[1]?.trim() ?? '';
}

/**
 * 切分临时预览内容，保留用户可见行，避免文件尾换行额外生成空增量。
 */
function splitPreviewLines(content: string): string[] {
  if (!content) {
    return [];
  }
  const normalizedContent = content.replace(/\r\n/g, '\n');
  const lines = normalizedContent.split('\n');
  return normalizedContent.endsWith('\n') ? lines.slice(0, -1) : lines;
}

/**
 * 解析 JSON 对象字符串，失败时返回空对象以保持调用链无异常。
 */
function parseJsonRecord(value: string): Record<string, unknown> | undefined {
  try {
    const parsed = JSON.parse(value) as unknown;
    return isRecord(parsed) ? parsed : undefined;
  } catch {
    return undefined;
  }
}

/**
 * 归一化字符串字段，空白值统一视为缺失。
 */
function normalizeString(value: unknown): string {
  return typeof value === 'string' ? value.trim() : '';
}

/**
 * 归一化数字字段，后端旧数据可能以字符串形式存储统计值。
 */
function normalizeNumber(value: unknown): number {
  if (typeof value === 'number' && Number.isFinite(value)) {
    return value;
  }
  if (typeof value === 'string' && value.trim()) {
    const parsed = Number(value);
    return Number.isFinite(parsed) ? parsed : 0;
  }
  return 0;
}

/**
 * 判断未知值是否为普通对象，排除 null 和数组。
 */
function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null && !Array.isArray(value);
}
