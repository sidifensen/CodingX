import { AuthStorage } from '@/utils/authStorage';

/**
 * 验证登录态存储会兼容后端返回的长整型 userId 字符串，避免会话被误判为脏数据。
 */
describe('AuthStorage', () => {
  beforeEach(() => {
    window.localStorage.clear();
  });

  /**
   * 当本地存储中的 userId 为字符串时，仍应被恢复为可用会话。
   */
  it('应兼容字符串形式的 userId 并恢复登录会话', () => {
    window.localStorage.setItem(
      'codingx.auth.session',
      JSON.stringify({
        token: 'token-123',
        userId: '1001',
        username: 'admin',
        displayName: 'CodingX Admin',
        userType: 'ADMIN',
      }),
    );

    expect(AuthStorage.getSession()).toEqual({
      token: 'token-123',
      userId: '1001',
      username: 'admin',
      displayName: 'CodingX Admin',
      userType: 'ADMIN',
    });
  });
});
