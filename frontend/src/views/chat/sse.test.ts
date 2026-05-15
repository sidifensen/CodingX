import { extractSseEvents, parseSseText } from './sse';

/**
 * 验证 SSE 文本解析器会按事件边界拆分并解析 JSON 负载。
 */
describe('parseSseText', () => {
  /**
   * 标准 meta/message 流应被解析为两个事件。
   */
  it('应解析标准 SSE 文本', () => {
    const events = parseSseText(`
event:meta
data:{"conversationId":2001}

event:message
data:{"type":"response","delta":"你好"}

`);

    expect(events).toEqual([
      {
        event: 'meta',
        data: { conversationId: 2001 },
      },
      {
        event: 'message',
        data: { type: 'response', delta: '你好' },
      },
    ]);
  });

  /**
   * 当流结束时最后一个事件未补齐空行分隔符，也应保留在 remainder 里供调用方自行 flush。
   */
  it('应保留尾部未闭合事件供流结束时继续处理', () => {
    const result = extractSseEvents('event:message\ndata:{"type":"response","delta":"尾包"}');

    expect(result.events).toEqual([]);
    expect(result.remainder).toBe('event:message\ndata:{"type":"response","delta":"尾包"}');
  });
});
