import { defineConfig, devices } from '@playwright/test';

const userBaseUrl = process.env.CODINGX_E2E_USER_URL ?? 'http://127.0.0.1:5002';
const adminBaseUrl = process.env.CODINGX_E2E_ADMIN_URL ?? 'http://127.0.0.1:5003';

/**
 * 独立前端 E2E 配置：分别启动用户端与管理端开发服务，并用浏览器验证关键入口。
 */
export default defineConfig({
  testDir: './tests',
  fullyParallel: true,
  forbidOnly: Boolean(process.env.CI),
  retries: process.env.CI ? 1 : 0,
  reporter: [['list'], ['html', { outputFolder: 'playwright-report', open: 'never' }]],
  use: {
    trace: 'on-first-retry',
    screenshot: 'only-on-failure',
    video: 'retain-on-failure',
  },
  webServer: [
    {
      command: 'npm run dev -- --host=127.0.0.1 --port=5002',
      cwd: '../user',
      url: userBaseUrl,
      reuseExistingServer: !process.env.CI,
      timeout: 120_000,
    },
    {
      command: 'npm run dev -- --host=127.0.0.1 --port=5003',
      cwd: '../admin',
      url: adminBaseUrl,
      reuseExistingServer: !process.env.CI,
      timeout: 120_000,
    },
  ],
  projects: [
    {
      name: 'user-chromium',
      testMatch: /user-.*\.spec\.ts/,
      use: {
        ...devices['Desktop Chrome'],
        baseURL: userBaseUrl,
      },
    },
    {
      name: 'admin-chromium',
      testMatch: /admin-.*\.spec\.ts/,
      use: {
        ...devices['Desktop Chrome'],
        baseURL: adminBaseUrl,
      },
    },
  ],
});
