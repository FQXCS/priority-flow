import clsx from 'clsx';
import { useToastStore } from '@/stores/toast';

/** 全局轻提示容器（挂在 App 根部） */
export function ToastContainer() {
  const toasts = useToastStore((s) => s.toasts);
  return (
    <div className="pointer-events-none fixed right-4 top-4 z-[100] flex w-80 flex-col gap-2">
      {toasts.map((t) => (
        <div
          key={t.id}
          className={clsx(
            'pointer-events-auto rounded-lg px-4 py-2.5 text-sm shadow-lg',
            t.type === 'success' && 'bg-emerald-600 text-white',
            t.type === 'error' && 'bg-red-600 text-white',
            t.type === 'info' && 'bg-zinc-800 text-white dark:bg-zinc-700',
          )}
        >
          {t.text}
        </div>
      ))}
    </div>
  );
}
