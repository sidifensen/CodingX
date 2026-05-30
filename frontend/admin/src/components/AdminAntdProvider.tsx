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

  /* 意图树左侧使用 AntD Tree/Button，但由页面级行布局接管宽度、选中态和对齐，避免组件默认居中规则互相叠加。 */
  .admin-intent-tree-scroll {
    scrollbar-gutter: stable;
    scrollbar-width: thin;
    scrollbar-color: var(--theme-scrollbar-thumb) var(--theme-scrollbar-track);
  }

  .admin-intent-tree-scroll::-webkit-scrollbar {
    height: 10px;
    width: 10px;
  }

  .admin-intent-tree-scroll::-webkit-scrollbar-track {
    background: var(--theme-scrollbar-track);
  }

  .admin-intent-tree-scroll::-webkit-scrollbar-thumb {
    background: var(--theme-scrollbar-thumb);
    border: 2px solid var(--theme-scrollbar-track);
    border-radius: 999px;
  }

  .admin-intent-tree-scroll::-webkit-scrollbar-thumb:hover {
    background: var(--theme-scrollbar-thumb-hover);
  }

  .admin-intent-tree.ant-tree {
    background: transparent !important;
    color: var(--theme-ink) !important;
  }

  .admin-intent-tree .ant-tree-list-holder-inner {
    gap: 6px;
  }

  .admin-intent-tree .ant-tree-treenode {
    align-items: stretch;
    display: flex;
    padding: 0 !important;
    width: 100%;
  }

  .admin-intent-tree .ant-tree-indent {
    align-self: stretch;
    display: inline-flex;
    flex: 0 0 auto;
    min-height: 48px;
  }

  .admin-intent-tree .ant-tree-indent-unit {
    width: 14px;
  }

  .admin-intent-tree .ant-tree-switcher {
    display: none !important;
    min-width: 0 !important;
    width: 0 !important;
  }

  .admin-intent-tree .ant-tree-node-content-wrapper {
    background: transparent !important;
    border-radius: 10px;
    flex: 1 1 auto;
    height: auto;
    line-height: normal;
    min-width: 0;
    padding: 0 !important;
  }

  .admin-intent-tree .ant-tree-node-content-wrapper:hover,
  .admin-intent-tree .ant-tree-node-content-wrapper.ant-tree-node-selected {
    background: transparent !important;
  }

  .admin-intent-tree .ant-tree-title {
    display: block;
    min-width: 0;
    width: 100%;
  }

  .admin-intent-tree-row {
    align-items: center;
    background: transparent;
    border: 1px solid transparent;
    border-radius: 10px;
    color: var(--theme-ink);
    display: grid;
    gap: 8px;
    grid-template-columns: 30px minmax(0, 1fr) max-content;
    min-height: 48px;
    padding: 6px 8px;
    transition: background-color 160ms ease, border-color 160ms ease, box-shadow 160ms ease;
    width: 100%;
  }

  .admin-intent-tree-row:hover {
    background: var(--theme-surface-container-low);
    border-color: var(--theme-border-hairline);
  }

  .admin-intent-tree-row-selected {
    background: var(--theme-surface-container);
    border-color: var(--theme-border-strong);
    box-shadow: inset 3px 0 0 var(--theme-primary);
  }

  .admin-intent-tree-toggle.ant-btn {
    align-items: center !important;
    background: transparent !important;
    border: 0 !important;
    color: var(--theme-secondary) !important;
    display: inline-flex !important;
    height: 28px;
    justify-content: center !important;
    min-width: 28px;
    padding: 0 !important;
    width: 28px;
  }

  .admin-intent-tree-toggle.ant-btn:hover {
    background: var(--theme-surface-container-lowest) !important;
    color: var(--theme-ink) !important;
  }

  .admin-intent-tree-toggle-placeholder {
    display: block;
    height: 28px;
    width: 28px;
  }

  .admin-intent-tree-select.ant-btn {
    align-items: center !important;
    background: transparent !important;
    border: 0 !important;
    box-shadow: none !important;
    color: inherit !important;
    display: flex !important;
    height: auto;
    justify-content: flex-start !important;
    min-width: 0;
    padding: 0 !important;
    text-align: left;
    width: 100%;
  }

  .admin-intent-tree-select.ant-btn > span:not(.ant-btn-icon) {
    display: block;
    min-width: 0;
    width: 100%;
  }

  .admin-intent-tree-label,
  .admin-intent-cascade-label {
    display: block;
    line-height: 1.25;
    min-width: 0;
    text-align: left;
    width: 100%;
  }

  .admin-intent-tree-name,
  .admin-intent-cascade-name {
    color: var(--theme-ink);
    display: block;
    font-weight: 600;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }

  .admin-intent-tree-code,
  .admin-intent-cascade-code {
    color: var(--theme-secondary);
    display: block;
    font-family: 'JetBrains Mono', monospace;
    font-size: 11px;
    margin-top: 2px;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }

  .admin-intent-tree-badges,
  .admin-intent-cascade-badges {
    align-items: center;
    display: flex;
    flex: 0 0 auto;
    flex-wrap: wrap;
    gap: 4px;
    justify-content: flex-end;
    min-width: 0;
  }

  .admin-intent-cascade {
    align-items: flex-start;
  }

  .admin-intent-cascade-column {
    display: flex;
    flex: 1 0 200px;
    flex-direction: column;
    max-width: 250px;
    min-width: 200px;
    overflow: hidden;
  }

  .admin-intent-cascade-column-header {
    background: var(--theme-surface-container-lowest);
    flex: 0 0 auto;
  }

  .admin-intent-cascade-column-body {
    flex: 1 1 auto;
    min-height: 0;
    overflow-y: visible;
    scrollbar-width: thin;
    scrollbar-color: var(--theme-scrollbar-thumb) var(--theme-scrollbar-track);
  }

  .admin-intent-cascade-column-body::-webkit-scrollbar {
    width: 10px;
  }

  .admin-intent-cascade-column-body::-webkit-scrollbar-track {
    background: var(--theme-scrollbar-track);
  }

  .admin-intent-cascade-column-body::-webkit-scrollbar-thumb {
    background: var(--theme-scrollbar-thumb);
    border: 2px solid var(--theme-scrollbar-track);
    border-radius: 999px;
  }

  .admin-intent-cascade-item.ant-btn {
    align-items: center !important;
    background: transparent !important;
    border: 1px solid transparent !important;
    border-radius: 10px;
    box-shadow: none !important;
    color: var(--theme-secondary) !important;
    display: flex !important;
    height: auto;
    justify-content: flex-start !important;
    min-height: 76px;
    padding: 8px 10px !important;
    text-align: left;
    white-space: normal;
    width: 100%;
  }

  .admin-intent-cascade-item.ant-btn:hover {
    background: var(--theme-surface-container-lowest) !important;
    border-color: var(--theme-border-hairline) !important;
    color: var(--theme-ink) !important;
  }

  .admin-intent-cascade-item.ant-btn > span:not(.ant-btn-icon) {
    display: block;
    min-width: 0;
    width: 100%;
  }

  .admin-intent-cascade-item-selected.ant-btn {
    background: var(--theme-surface-container) !important;
    border-color: var(--theme-border-strong) !important;
    color: var(--theme-ink) !important;
    box-shadow: inset 3px 0 0 var(--theme-primary) !important;
  }

  .admin-intent-cascade-item-path.ant-btn {
    background: var(--theme-surface-container-lowest) !important;
    border-color: var(--theme-border-hairline) !important;
    color: var(--theme-ink) !important;
  }

  .admin-intent-cascade-row {
    align-items: center;
    display: grid;
    gap: 6px;
    grid-template-columns: minmax(0, 1fr);
    min-width: 0;
    width: 100%;
  }

  .admin-intent-cascade-badges {
    justify-content: flex-start;
  }

  @media (max-width: 640px) {
    .admin-intent-tree-row {
      grid-template-columns: 30px minmax(0, 1fr);
    }

    .admin-intent-tree-badges {
      grid-column: 2;
      justify-content: flex-start;
    }

    .admin-intent-cascade-badges {
      grid-column: 1;
    }
  }

  /* 系统配置页在宽屏下需要把配置项铺满主内容区，避免 AntD Button 默认居中和半宽网格造成内容过窄。 */
  .admin-settings-navigator,
  .admin-settings-detail-panel,
  .admin-settings-category-nav {
    min-width: 0;
  }

  .admin-settings-category-button.ant-btn,
  .admin-settings-category-toggle.ant-btn {
    align-items: center !important;
    display: flex !important;
    height: auto;
    justify-content: space-between !important;
    min-width: 0;
    text-align: left;
    white-space: normal;
    width: 100%;
  }

  .admin-settings-category-button.ant-btn {
    min-height: 52px;
  }

  .admin-settings-category-toggle.ant-btn {
    min-height: 64px;
    padding: 14px 18px !important;
  }

  .admin-settings-category-toggle.ant-btn p {
    margin: 0;
  }

  .admin-settings-category-button.ant-btn > span:not(.ant-btn-icon),
  .admin-settings-category-toggle.ant-btn > span:not(.ant-btn-icon) {
    display: block;
    flex: 1 1 auto;
    min-width: 0;
    width: 100%;
  }

  .admin-settings-category-toggle.ant-btn .ant-btn-icon {
    color: var(--theme-secondary);
    flex: 0 0 auto;
    margin-inline-start: 16px;
  }

  .admin-settings-category-button-content {
    align-items: center;
    display: grid;
    gap: 8px;
    grid-template-columns: minmax(0, 1fr) max-content;
    min-width: 0;
    width: 100%;
  }

  .admin-settings-category-label {
    display: block;
    font-weight: 600;
    min-width: 0;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }

  .admin-settings-category-count {
    color: var(--theme-secondary);
    display: block;
    font-size: 11px;
    white-space: nowrap;
  }

  .admin-settings-category-button.ant-btn-primary .admin-settings-category-count {
    color: var(--theme-on-primary);
    opacity: 0.78;
  }

  .admin-settings-field-grid {
    display: grid;
    gap: 12px;
    grid-template-columns: repeat(auto-fit, minmax(min(100%, 420px), 1fr));
    width: 100%;
  }

  .admin-settings-field-card {
    min-width: 0;
    width: 100%;
  }

  .admin-settings-table-editor {
    grid-column: 1 / -1;
    min-width: 0;
    overflow: hidden;
    width: 100%;
  }

  @media (min-width: 1536px) {
    .admin-settings-field-grid {
      grid-template-columns: repeat(auto-fit, minmax(min(100%, 520px), 1fr));
    }
  }

  @media (max-width: 768px) {
    .admin-settings-category-button-content {
      grid-template-columns: minmax(0, 1fr);
      gap: 2px;
    }

    .admin-settings-category-count {
      white-space: normal;
    }
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
