import React from 'react';

interface PaginationProps {
  current: number;
  pages: number;
  loading?: boolean;
  onChange: (page: number) => void;
}

type PaginationToken = number | 'ellipsis-left' | 'ellipsis-right';

const MAX_VISIBLE_PAGES = 7;

/**
 * 通用分页器：支持页码窗口、省略号、前后翻页与前往指定页。
 */
export function Pagination({ current, pages, loading = false, onChange }: PaginationProps) {
  const safeCurrent = Math.max(1, current);
  const safePages = Math.max(1, pages);
  const [gotoValue, setGotoValue] = React.useState(String(safeCurrent));

  React.useEffect(() => {
    setGotoValue(String(safeCurrent));
  }, [safeCurrent]);

  const pageTokens = React.useMemo(() => {
    return buildPageTokens(safeCurrent, safePages);
  }, [safeCurrent, safePages]);

  const canPrev = !loading && safeCurrent > 1;
  const canNext = !loading && safeCurrent < safePages;

  /**
   * 跳转到指定页，统一做边界保护，避免传递非法页码。
   */
  const goToPage = (nextPage: number) => {
    if (loading) {
      return;
    }
    const normalized = Math.min(Math.max(1, nextPage), safePages);
    if (normalized === safeCurrent) {
      return;
    }
    onChange(normalized);
  };

  const handleGotoSubmit = () => {
    const parsed = Number(gotoValue);
    if (!Number.isFinite(parsed)) {
      setGotoValue(String(safeCurrent));
      return;
    }
    goToPage(Math.round(parsed));
  };

  return (
    <div className="flex flex-wrap items-center justify-end gap-sm">
      <button
        type="button"
        aria-label="上一页"
        className="h-10 min-w-10 rounded-lg border border-border-hairline bg-surface-container-lowest px-sm text-secondary transition-colors hover:bg-surface-container-low hover:text-ink disabled:cursor-not-allowed disabled:opacity-45"
        disabled={!canPrev}
        onClick={() => goToPage(safeCurrent - 1)}
      >
        <span className="material-symbols-outlined text-[18px]">chevron_left</span>
      </button>

      {pageTokens.map((token) => {
        if (typeof token !== 'number') {
          return (
            <span key={token} className="inline-flex h-10 min-w-10 items-center justify-center text-secondary">
              ...
            </span>
          );
        }
        const isActive = token === safeCurrent;
        return (
          <button
            key={token}
            type="button"
            aria-label={`第 ${token} 页`}
            className={[
              'h-10 min-w-10 rounded-lg border px-sm text-[14px] transition-colors',
              isActive
                ? 'border-primary bg-primary/15 font-semibold text-primary'
                : 'border-border-hairline bg-surface-container-lowest text-ink hover:bg-surface-container-low',
            ].join(' ')}
            disabled={loading}
            onClick={() => goToPage(token)}
          >
            {token}
          </button>
        );
      })}

      <button
        type="button"
        aria-label="下一页"
        className="h-10 min-w-10 rounded-lg border border-border-hairline bg-surface-container-lowest px-sm text-secondary transition-colors hover:bg-surface-container-low hover:text-ink disabled:cursor-not-allowed disabled:opacity-45"
        disabled={!canNext}
        onClick={() => goToPage(safeCurrent + 1)}
      >
        <span className="material-symbols-outlined text-[18px]">chevron_right</span>
      </button>

      <span className="text-[14px] text-secondary">前往</span>
      <input
        aria-label="前往页码"
        value={gotoValue}
        inputMode="numeric"
        className="h-10 w-16 rounded-lg border border-border-hairline bg-surface-container-lowest px-sm text-center text-ink outline-none transition-colors focus:border-border-strong"
        onChange={(event) => setGotoValue(event.target.value.replace(/[^\d]/g, ''))}
        onKeyDown={(event) => {
          if (event.key === 'Enter') {
            handleGotoSubmit();
          }
        }}
      />
      <span className="text-[14px] text-secondary">页</span>
      <button
        type="button"
        aria-label="前往"
        className="h-10 rounded-lg border border-border-strong bg-surface-container-lowest px-md text-button font-button text-ink transition-colors hover:bg-surface-container-low disabled:cursor-not-allowed disabled:opacity-45"
        disabled={loading}
        onClick={handleGotoSubmit}
      >
        前往
      </button>
    </div>
  );
}

/**
 * 根据当前页和总页数生成页码窗口。
 * 规则：总页数较小时全量展示；较大时保留首尾并对当前页附近开窗。
 */
function buildPageTokens(current: number, pages: number): PaginationToken[] {
  if (pages <= MAX_VISIBLE_PAGES) {
    return Array.from({ length: pages }, (_, index) => index + 1);
  }
  if (current <= 4) {
    return [1, 2, 3, 4, 5, 'ellipsis-right', pages];
  }
  if (current >= pages - 3) {
    return [1, 'ellipsis-left', pages - 4, pages - 3, pages - 2, pages - 1, pages];
  }
  return [1, 'ellipsis-left', current - 1, current, current + 1, 'ellipsis-right', pages];
}
