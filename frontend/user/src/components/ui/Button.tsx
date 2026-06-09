import React from 'react';

type ButtonVariant = 'default' | 'outline' | 'ghost' | 'danger';
type ButtonSize = 'default' | 'sm' | 'icon';

interface ButtonProps extends React.ButtonHTMLAttributes<HTMLButtonElement> {
  /** 按钮视觉语义，沿用 shadcn/ui 的 variant 思路约束用户端按钮风格。 */
  variant?: ButtonVariant;
  /** 按钮尺寸语义，图标按钮固定尺寸以避免列表行布局抖动。 */
  size?: ButtonSize;
}

const variantClassNames: Record<ButtonVariant, string> = {
  default: 'bg-primary text-primary-foreground hover:opacity-90',
  outline:
    'border border-border bg-surface text-muted hover:border-border-active hover:text-foreground',
  ghost: 'text-muted hover:bg-surface-container hover:text-foreground',
  danger: 'bg-error text-background hover:opacity-90',
};

const sizeClassNames: Record<ButtonSize, string> = {
  default: 'h-10 gap-2 rounded-md px-4 text-sm',
  sm: 'h-8 gap-1.5 rounded-md px-3 text-xs',
  icon: 'h-8 w-8 rounded-md p-0',
};

/**
 * 用户端本地按钮组件；在未正式接入 shadcn/ui CLI 前，集中复用其 variant/size 结构。
 */
export function Button({
  className,
  variant = 'default',
  size = 'default',
  type = 'button',
  ...props
}: ButtonProps) {
  return (
    <button
      className={joinClassNames(
        'inline-flex shrink-0 items-center justify-center font-medium transition-[background-color,border-color,color,opacity] focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-border-active focus-visible:ring-offset-2 focus-visible:ring-offset-background disabled:cursor-not-allowed disabled:opacity-50',
        variantClassNames[variant],
        sizeClassNames[size],
        className,
      )}
      type={type}
      {...props}
    />
  );
}

/**
 * 轻量拼接 Tailwind 类名，避免为一个本地基础组件引入额外依赖。
 */
function joinClassNames(...classNames: Array<string | false | null | undefined>) {
  return classNames.filter(Boolean).join(' ');
}
