import tailwindcss from '@tailwindcss/vite';
import react from '@vitejs/plugin-react';
import path from 'path';
import { defineConfig, loadEnv } from 'vite-plus';
import { fileURLToPath } from 'url';

// 步骤：读取未加前缀的环境变量，保持现有配置可用。
const env = loadEnv(process.env.NODE_ENV ?? 'development', '.', '');
// 步骤：派生当前配置文件目录，兼容 ESM 配置解析场景。
const currentDirectory = path.dirname(fileURLToPath(import.meta.url));

/**
 * 定义管理端 VitePlus 构建与开发配置。
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
    // 步骤：统一管理端开发端口，避免与用户端冲突。
    host: '0.0.0.0',
    port: 5003,
    // 步骤：在指定禁用热更新时关闭 HMR，避免编辑阶段页面闪烁。
    hmr: process.env.DISABLE_HMR !== 'true',
    // 步骤：在禁用 HMR 时同步关闭文件监听，减少编辑阶段占用。
    watch: process.env.DISABLE_HMR === 'true' ? null : {},
    // 步骤：统一开发环境后端代理，确保 /api 请求转发到 Spring Boot。
    proxy: {
      '/api': {
        target: env.BACKEND_URL ?? 'http://localhost:5001',
        changeOrigin: true,
        // 步骤：开发代理转发时移除浏览器 Origin，避免本地多端口联调被后端 CORS 拦截。
        configure: (proxy) => {
          proxy.on('proxyReq', (proxyReq) => {
            proxyReq.removeHeader('origin');
          });
        },
      },
    },
  },
  // 步骤：为 VitePlus 检查配置忽略目录，避免扫描构建产物与三方依赖。
  lint: {
    ignorePatterns: ['dist/**', 'node_modules/**'],
  },
  // 步骤：统一格式化规则，保持与用户端配置一致。
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
  // 步骤：配置 Vitest 运行环境，为后续管理端测试留出统一入口。
  test: {
    environment: 'jsdom',
    globals: true,
    css: true,
    include: ['tests/**/*.test.ts', 'tests/**/*.test.tsx'],
  },
  // 步骤：为暂存区文件提供统一检查入口。
  staged: {
    '*': 'vp check --fix',
  },
});
