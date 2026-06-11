import { expect, test } from '@playwright/test';

test.describe('用户端基础登录拦截', () => {
  test('未登录发送消息时在真实浏览器中打开登录弹窗', async ({ page }) => {
    // 只拦截真实后端接口，避免误伤 Vite 加载的 src/api/*.ts 前端模块。
    await page.route('**/*', async (route) => {
      if (!new URL(route.request().url()).pathname.startsWith('/api/')) {
        await route.continue();
        return;
      }
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({ success: true, code: 'OK', message: 'success', data: [] }),
      });
    });

    await page.goto('/');
    // 真实浏览器里通过输入框和发送按钮触发登录拦截，覆盖弹窗层级与默认值回填。
    await page.getByPlaceholder('输入问题，或先选择技能/MCP...').fill('请帮我分析项目结构');
    await page.getByRole('button', { name: '发送消息' }).click();

    await expect(page.getByRole('dialog', { name: '登录弹窗' })).toBeVisible();
    await expect(page.getByRole('heading', { name: '登录 CodingX' })).toBeVisible();
    await expect(page.getByLabel('账号')).toHaveValue('admin');
    await expect(page.getByLabel('密码')).toHaveValue('123456');
  });
});
