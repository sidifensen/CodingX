/**
 * 表示单条 SSE 事件。
 */
export interface ParsedSseEvent {
  event: string;
  data: unknown;
}

/**
 * 从文本块中解析标准 SSE 事件，供测试和本地调试复用。
 * @param rawText 原始 SSE 文本。
 * @returns 解析后的事件数组。
 */
export function parseSseText(rawText: string): ParsedSseEvent[] {
  return rawText
    .split(/\r?\n\r?\n/)
    .map((chunk) => chunk.trim())
    .filter(Boolean)
    .map((chunk) => {
      const lines = chunk.split(/\r?\n/);
      const event = lines.find((line) => line.startsWith('event:'))?.slice(6).trim() ?? 'message';
      const dataText = lines
        .filter((line) => line.startsWith('data:'))
        .map((line) => line.slice(5).trim())
        .join('\n');
      return {
        event,
        data: safeParseJson(dataText),
      };
    });
}

/**
 * 从流式缓冲区中提取已经完整的 SSE 事件，并保留尾部未完成片段供下次继续拼接。
 * @param buffer 当前累积的文本缓冲。
 * @returns 已完成事件与剩余尾片段。
 */
export function extractSseEvents(buffer: string): { events: ParsedSseEvent[]; remainder: string } {
  const normalized = buffer.replace(/\r\n/g, '\n');
  const chunks = normalized.split('\n\n');
  const remainder = normalized.endsWith('\n\n') ? '' : chunks.pop() ?? '';
  return {
    events: chunks.filter(Boolean).flatMap((chunk) => parseSseText(`${chunk}\n\n`)),
    remainder,
  };
}

/**
 * 在容错前提下解析事件 JSON，避免后端偶发返回纯文本时直接中断整个流。
 * @param value 事件数据文本。
 * @returns JSON 对象或原始字符串。
 */
function safeParseJson(value: string): unknown {
  if (!value) {
    return {};
  }
  try {
    return JSON.parse(value);
  } catch {
    return value;
  }
}
