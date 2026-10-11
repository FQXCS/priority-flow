import { InputHTMLAttributes, TextareaHTMLAttributes, forwardRef } from 'react';
import clsx from 'clsx';

export interface InputProps extends InputHTMLAttributes<HTMLInputElement> {
  invalid?: boolean;
}

/** 文本输入框 */
export const Input = forwardRef<HTMLInputElement, InputProps>(({ className, invalid, ...props }, ref) => (
  <input
    ref={ref}
    className={clsx(
      'h-9 w-full rounded-md border bg-white px-3 text-sm text-zinc-900 placeholder-zinc-400 outline-none transition-colors',
      'focus:border-blue-500 focus:ring-1 focus:ring-blue-500',
      'dark:bg-zinc-900 dark:text-zinc-100 dark:placeholder-zinc-500',
      invalid ? 'border-red-500' : 'border-zinc-300 dark:border-zinc-700',
      className,
    )}
    {...props}
  />
));
Input.displayName = 'Input';

export interface TextareaProps extends TextareaHTMLAttributes<HTMLTextAreaElement> {
  invalid?: boolean;
}

/** 多行文本框 */
export const Textarea = forwardRef<HTMLTextAreaElement, TextareaProps>(({ className, invalid, ...props }, ref) => (
  <textarea
    ref={ref}
    className={clsx(
      'w-full rounded-md border bg-white px-3 py-2 text-sm text-zinc-900 placeholder-zinc-400 outline-none transition-colors',
      'focus:border-blue-500 focus:ring-1 focus:ring-blue-500',
      'dark:bg-zinc-900 dark:text-zinc-100 dark:placeholder-zinc-500',
      invalid ? 'border-red-500' : 'border-zinc-300 dark:border-zinc-700',
      className,
    )}
    {...props}
  />
));
Textarea.displayName = 'Textarea';
