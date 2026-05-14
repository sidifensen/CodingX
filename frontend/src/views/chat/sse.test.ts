import { parseSseText } from './sse';

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
});
