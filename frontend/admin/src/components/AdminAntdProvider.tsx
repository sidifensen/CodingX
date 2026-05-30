import React from 'react';
import { ConfigProvider, theme } from 'antd';
import zhCN from 'antd/locale/zh_CN';

interface AdminAntdProviderProps {
  children: React.ReactNode;
}

interface AdminAntdThemeTokens {
  background: string;
  borderHairline: string;
  borderStrong: string;
  error: string;
  errorContainer: string;
  ink: string;
  onErrorContainer: string;
  onInk: string;
  onPrimary: string;
  primary: string;
  secondary: string;
  surfaceContainerLow: string;
  surfaceContainerLowest: string;
}

/**
 * 管理端 Ant Design 主题桥接层：把现有 CSS 主题变量映射到 AntD token。
 */
export function AdminAntdProvider({ children }: AdminAntdProviderProps) {
  const [themeState, setThemeState] = React.useState(() => readAdminAntdTheme());

  React.useEffect(() => {
    const styleElementId = 'admin-antd-theme-overrides';
    let styleElement = document.getElementById(styleElementId) as HTMLStyleElement | null;
    if (!styleElement) {
      styleElement = document.createElement('style');
      styleElement.id = styleElementId;
    }
    // HMR 下同一个 style 节点可能已存在，挂载时刷新文本可避免保留旧版 AntD 覆盖规则。
    styleElement.textContent = adminAntdThemeOverrideCss;
    document.head.appendChild(styleElement);

    const keepThemeOverrideLast = () => {
      const currentStyleElement = document.getElementById(styleElementId);
      if (currentStyleElement && currentStyleElement.nextSibling) {
        document.head.appendChild(currentStyleElement);
      }
    };
    const observer = new MutationObserver(keepThemeOverrideLast);
    observer.observe(document.head, { childList: true });
    keepThemeOverrideLast();
    return () => observer.disconnect();
  }, []);

  React.useEffect(() => {
    const root = document.documentElement;
    const syncThemeMode = () => setThemeState(readAdminAntdTheme());
    const observer = new MutationObserver(syncThemeMode);
    observer.observe(root, { attributes: true, attributeFilter: ['class'] });
    const animationFrameId = window.requestAnimationFrame(syncThemeMode);
    const timeoutId = window.setTimeout(syncThemeMode, 0);
    window.addEventListener('focus', syncThemeMode);
    document.addEventListener('visibilitychange', syncThemeMode);
    syncThemeMode();
    return () => {
      observer.disconnect();
      window.cancelAnimationFrame(animationFrameId);
      window.clearTimeout(timeoutId);
      window.removeEventListener('focus', syncThemeMode);
      document.removeEventListener('visibilitychange', syncThemeMode);
    };
  }, []);

  const { isDarkMode, tokens } = themeState;

  return (
    <ConfigProvider
      locale={zhCN}
      componentSize="middle"
      theme={{
        algorithm: isDarkMode ? theme.darkAlgorithm : theme.defaultAlgorithm,
        token: {
          borderRadius: 8,
          colorBgBase: tokens.background,
          colorBgContainer: tokens.surfaceContainerLowest,
          colorBgElevated: tokens.surfaceContainerLowest,
          colorBorder: tokens.borderStrong,
          colorError: tokens.error,
          colorLink: tokens.primary,
          colorPrimary: tokens.primary,
          colorText: tokens.ink,
          colorTextDescription: tokens.secondary,
          colorTextDisabled: tokens.secondary,
          fontFamily: 'Inter, sans-serif',
        },
        components: {
          Alert: {
            colorErrorBg: tokens.errorContainer,
            colorErrorBorder: tokens.error,
            colorErrorText: tokens.onErrorContainer,
          },
          Button: {
            defaultBg: tokens.surfaceContainerLowest,
            defaultBorderColor: tokens.borderStrong,
            defaultColor: tokens.ink,
            primaryColor: tokens.onPrimary,
          },
          Card: {
            colorBgContainer: tokens.surfaceContainerLowest,
            colorBorderSecondary: tokens.borderHairline,
          },
          Descriptions: {
            labelBg: tokens.surfaceContainerLow,
          },
          Select: {
            optionSelectedBg: tokens.surfaceContainerLow,
          },
          Table: {
            borderColor: tokens.borderHairline,
            headerBg: tokens.surfaceContainerLow,
            headerColor: tokens.secondary,
            rowHoverBg: tokens.surfaceContainerLow,
          },
          Tag: {
            defaultBg: tokens.surfaceContainerLow,
          },
        },
      }}
    >
      {children}
    </ConfigProvider>
  );
}

function readAdminAntdTheme(): { isDarkMode: boolean; tokens: AdminAntdThemeTokens } {
  const root = document.documentElement;
  const style = getComputedStyle(root);
  const read = (name: string, fallback: string) => style.getPropertyValue(name).trim() || fallback;
  return {
    isDarkMode: root.classList.contains('dark'),
    tokens: {
      background: read('--theme-background', '#f9f9f9'),
      borderHairline: read('--theme-border-hairline', '#f0f0f3'),
      borderStrong: read('--theme-border-strong', '#dcdee0'),
      error: read('--theme-error', '#ba1a1a'),
      errorContainer: read('--theme-error-container', '#ffdad6'),
      ink: read('--theme-ink', '#171717'),
      onErrorContainer: read('--theme-on-error-container', '#93000a'),
      onInk: read('--theme-on-ink', '#ffffff'),
      onPrimary: read('--theme-on-primary', '#ffffff'),
      primary: read('--theme-primary', '#000000'),
      secondary: read('--theme-secondary', '#5a5e66'),
      surfaceContainerLow: read('--theme-surface-container-low', '#f3f3f3'),
      surfaceContainerLowest: read('--theme-surface-container-lowest', '#ffffff'),
    },
  };
}

const adminAntdThemeOverrideCss = `
  .ant-card,
  .ant-table-wrapper .ant-table,
  .ant-table-wrapper .ant-table-container,
  .ant-table-wrapper .ant-table-cell,
  .ant-table-wrapper .ant-table-placeholder,
  .ant-table-wrapper .ant-table-placeholder > td,
  .ant-empty,
  .ant-modal-content,
  .ant-popover-inner,
  .ant-select-dropdown {
    background: var(--theme-surface-container-lowest) !important;
    background-color: var(--theme-surface-container-lowest) !important;
    color: var(--theme-ink) !important;
  }

  .ant-table-wrapper .ant-table-thead > tr > th,
  .ant-descriptions .ant-descriptions-item-label {
    background: var(--theme-surface-container-low) !important;
    background-color: var(--theme-surface-container-low) !important;
    color: var(--theme-secondary) !important;
    border-color: var(--theme-border-hairline) !important;
  }

  .ant-table-wrapper .ant-table-tbody > tr > td,
  .ant-table-wrapper .ant-table-tbody > tr.ant-table-placeholder > td,
  .ant-card,
  .ant-descriptions .ant-descriptions-item-content {
    background-color: var(--theme-surface-container-lowest) !important;
    border-color: var(--theme-border-hairline) !important;
    color: var(--theme-ink) !important;
  }

  .ant-table-wrapper .ant-table-tbody > tr.ant-table-row:hover > td {
    background: var(--theme-surface-container-low) !important;
    background-color: var(--theme-surface-container-low) !important;
  }

  .admin-data-table.ant-table-wrapper .ant-table,
  .admin-data-table.ant-table-wrapper .ant-table-container {
    border-inline-start: 0 !important;
  }

  .admin-data-table.ant-table-wrapper .ant-table-cell,
  .admin-data-table.ant-table-wrapper .ant-table-thead > tr > th,
  .admin-data-table.ant-table-wrapper .ant-table-tbody > tr > td {
    border-inline-end: 0 !important;
    padding: 14px 16px !important;
  }

  .admin-data-table.ant-table-wrapper .ant-table-thead > tr > th::before {
    display: none !important;
  }

  .admin-data-table .ant-table-pagination {
    padding-inline: 8px;
  }

  .admin-data-table .admin-pagination-page-button {
    background: transparent;
    border: 0;
    color: inherit;
    cursor: pointer;
    height: 100%;
    min-width: 100%;
    padding: 0;
  }

  .admin-table-actions .ant-btn {
    align-items: center;
    display: inline-flex;
  }

  .ant-input-affix-wrapper,
  .ant-input-affix-wrapper.ant-input-outlined,
  .ant-input-affix-wrapper.ant-input-affix-wrapper,
  .ant-select.ant-select-outlined:not(.ant-select-customize-input) .ant-select-selector,
  .ant-select .ant-select-selector {
    background: var(--theme-surface-container-lowest) !important;
    background-color: var(--theme-surface-container-lowest) !important;
    border-color: var(--theme-border-strong) !important;
    color: var(--theme-ink) !important;
  }

  .ant-input,
  .ant-input-affix-wrapper .ant-input,
  .ant-select-selection-item,
  .ant-select-item,
  .ant-empty-description,
  .ant-typography {
    color: var(--theme-ink) !important;
  }

  .ant-input::placeholder {
    color: var(--theme-secondary) !important;
  }

  .ant-btn-default,
  .ant-btn.ant-btn-default {
    background: var(--theme-surface-container-lowest) !important;
    background-color: var(--theme-surface-container-lowest) !important;
    border-color: var(--theme-border-strong) !important;
    color: var(--theme-ink) !important;
  }

  .ant-btn-primary {
    background: var(--theme-primary) !important;
    background-color: var(--theme-primary) !important;
    border-color: var(--theme-primary) !important;
    color: var(--theme-on-primary) !important;
  }

  :root.dark .ant-input-affix-wrapper.ant-input-outlined,
  :root.dark .ant-input-affix-wrapper.ant-input-affix-wrapper,
  :root.dark .ant-select.ant-select-outlined:not(.ant-select-customize-input) .ant-select-selector,
  :root.dark .ant-btn.ant-btn-default {
    background: var(--theme-surface-container-lowest) !important;
    background-color: var(--theme-surface-container-lowest) !important;
    border-color: var(--theme-border-strong) !important;
    color: var(--theme-ink) !important;
  }

  :root.dark .ant-input-affix-wrapper .ant-input,
  :root.dark .ant-select-selection-item {
    color: var(--theme-ink) !important;
  }

  .ant-pagination,
  .ant-pagination .ant-pagination-total-text,
  .ant-pagination .ant-pagination-item a {
    color: var(--theme-ink) !important;
  }

  .ant-alert-error {
    background: var(--theme-error-container) !important;
    border-color: var(--theme-error) !important;
    color: var(--theme-on-error-container) !important;
  }
`;
