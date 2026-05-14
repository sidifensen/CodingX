import tailwindcss from '@tailwindcss/vite';
import react from '@vitejs/plugin-react';
import path from 'path';
import { defineConfig, loadEnv } from 'vite-plus';
import { fileURLToPath } from 'url';

// 步骤：读取未加前缀的环境变量，保持现有 AI Studio 配置可用。
const env = loadEnv(process.env.NODE_ENV ?? 'development', '.', '');
// 步骤：派生当前配置文件目录，兼容 ESM 配置解析场景。
const currentDirectory = path.dirname(fileURLToPath(import.meta.url));

/**
 * 定义前端 Vite+ 统一工具链配置，并兼容现有环境变量注入方式。
 */
export default defineConfig({
  plugins: [react(), tailwindcss()],
  define: {
    'process.env.GEMINI_API_KEY': JSON.stringify(env.GEMINI_API_KEY),
  },
  resolve: {
    alias: {
      '@': path.resolve(currentDirectory, 'src'),
    },
  },
  server: {
    // 步骤：统一当前前端开发端口，便于与仓库约定保持一致。
    host: '0.0.0.0',
    port: 5001,
    // 步骤：在 AI Studio 指定禁用热更新时关闭 HMR，避免代理编辑期间页面闪烁。
    hmr: process.env.DISABLE_HMR !== 'true',
    // 步骤：在禁用 HMR 时同步关闭文件监听，减少编辑阶段的额外占用。
    watch: process.env.DISABLE_HMR === 'true' ? null : {},
    // 步骤：统一开发环境后端代理，确保前端 /api 请求可直接转发到 Spring Boot 服务。
    proxy: {
      '/api': {
        target: env.BACKEND_URL ?? 'http://localhost:5001',
        changeOrigin: true,
      },
    },
  },
  // 步骤：为 Vite+ 的代码检查配置忽略目录，避免扫描构建产物与第三方依赖。
  lint: {
    ignorePatterns: ['dist/**', 'node_modules/**'],
  },
  // 步骤：统一前端格式化规则，保持与参考项目一致的 Vite+ 使用方式。
  fmt: {
    ignorePatterns: ['dist/**', 'node_modules/**', '.env', '.env.*', 'coverage/**'],
    printWidth: 100,
    tabWidth: 2,
    useTabs: false,
    singleQuote: true,
    semi: true,
    trailingComma: 'all',
    insertFinalNewline: true,
    jsxSingleQuote: false,
    proseWrap: 'preserve',
    endOfLine: 'lf',
  },
  // 步骤：配置 Vitest 运行环境，支持 React 组件最小测试能力。
  test: {
    environment: 'jsdom',
    globals: true,
    css: true,
    setupFiles: ['./src/tests/setup.ts'],
    include: ['src/**/*.test.ts', 'src/**/*.test.tsx'],
  },
  // 步骤：为暂存区文件提供统一检查入口，复用 Vite+ 一体化能力。
  staged: {
    '*': 'vp check --fix',
  },
});
