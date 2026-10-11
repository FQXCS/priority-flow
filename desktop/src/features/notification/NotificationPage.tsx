import { useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { clsx } from 'clsx';
import { notificationApi } from '@/api';
import { Button } from '@/components/ui/Button';
import type { NotificationVo } from '@/api/types';

const PAGE_SIZE = 20;

/** 通知中心：站内通知列表 + 已读管理 */
export function NotificationPage() {
  const queryClient = useQueryClient();
  const [page, setPage] = useState(1);

  const { data: list = [], isLoading } = useQuery({
    queryKey: ['notifications', 'list', page],
    queryFn: () => notificationApi.list(page, PAGE_SIZE),
  });

  const refresh = () => {
    void queryClient.invalidateQueries({ queryKey: ['notifications'] });
  };

  const markRead = useMutation({
    mutationFn: (id: string) => notificationApi.markRead(id),
    onSuccess: refresh,
  });

  const markAllRead = useMutation({
    mutationFn: () => notificationApi.markAllRead(),
    onSuccess: () => {
      refresh();
    },
  });

  return (
    <div className="h-full overflow-y-auto p-5">
      <div className="mx-auto max-w-3xl">
        <div className="mb-3 flex items-center justify-between">
          <h3 className="text-sm font-semibold text-zinc-600 dark:text-zinc-300">通知中心</h3>
          <Button size="sm" variant="outline" onClick={() => markAllRead.mutate()}>
            全部已读
          </Button>
        </div>
        {isLoading ? (
          <div className="py-16 text-center text-sm text-zinc-400">加载中…</div>
        ) : list.length === 0 ? (
          <div className="py-16 text-center text-sm text-zinc-400">暂无通知</div>
        ) : (
          <div className="space-y-2">
            {list.map((n) => (
              <NotificationRow key={n.id} notification={n} onRead={() => markRead.mutate(n.id)} />
            ))}
            <div className="flex justify-center gap-2 pt-2">
              <Button size="sm" variant="outline" disabled={page <= 1} onClick={() => setPage((p) => p - 1)}>
                上一页
              </Button>
              <span className="self-center text-xs text-zinc-400">第 {page} 页</span>
              <Button
                size="sm"
                variant="outline"
                disabled={list.length < PAGE_SIZE}
                onClick={() => setPage((p) => p + 1)}
              >
                下一页
              </Button>
            </div>
          </div>
        )}
      </div>
    </div>
  );
}

function NotificationRow({ notification, onRead }: { notification: NotificationVo; onRead: () => void }) {
  const unread = notification.isRead === '0';
  return (
    <div
      className={clsx(
        'flex items-start gap-3 rounded-xl border px-4 py-3',
        unread
          ? 'border-blue-200 bg-blue-50/60 dark:border-blue-900 dark:bg-blue-900/20'
          : 'border-zinc-200 bg-white dark:border-zinc-700 dark:bg-zinc-900',
      )}
    >
      <div className="min-w-0 flex-1">
        <div className="flex items-center gap-2">
          {unread && <span className="h-2 w-2 shrink-0 rounded-full bg-blue-500" />}
          <span className="text-sm font-medium text-zinc-800 dark:text-zinc-100">{notification.title}</span>
        </div>
        {notification.content && (
          <div className="mt-1 break-words text-xs text-zinc-500 dark:text-zinc-400">{notification.content}</div>
        )}
        <div className="mt-1 text-[11px] text-zinc-400">{notification.createTime?.slice(0, 16)}</div>
      </div>
      {unread && (
        <Button size="sm" variant="ghost" onClick={onRead}>
          标记已读
        </Button>
      )}
    </div>
  );
}
