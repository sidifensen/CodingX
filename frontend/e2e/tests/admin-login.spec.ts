import { expect, test } from '@playwright/test';

test.describe('管理端认证入口', () => {
  test('未登录访问受保护页面时跳转登录页并展示空凭证提示', async ({ page }) => {
    await page.goto('/skills');

    await expect(page.getByRole('heading', { name: 'CodingX 管理端登录' })).toBeVisible();
    await expect(page).toHaveURL(/\/login$/);

    await page.getByPlaceholder('请输入管理员账号').fill('');
    await page.getByPlaceholder('请输入密码').fill('');
    await page.getByRole('button', { name: '登录管理端' }).click();

    // 管理端会同步展示全局认证提示和表单内提示，这里只校验表单上下文的错误。
    await expect(page.locator('form').getByText('请输入账号和密码')).toBeVisible();
  });
});
