import { useMemo, useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { clsx } from 'clsx';
import { taskApi } from '@/api';
import { useAuthStore } from '@/stores/auth';
import { toast } from '@/stores/toast';
import { Button } from '@/components/ui/Button';
import { Dialog } from '@/components/ui/Dialog';
import { TaskCard } from '@/components/TaskCard';
import { TaskDrawer } from '@/components/TaskDrawer';
import type { TaskVo } from '@/api/types';

const TABS = [
  { key: 'waiting', label: '等待中' },
  { key: 'in_progress', label: '进行中' },
  { key: 'completed', label: '已完成' },
];

/** 我的任务页：三个作用域页签；等待中卡片可拖到队首触发"切换任务"确认 */
export function MyTaskPage() {
  const { user } = useAuthStore();
  const queryClient = useQueryClient();
  const [tab, setTab] = useState('waiting');
  const [detailTask, setDetailTask] = useState<TaskVo | null>(null);
  const [switchTarget, setSwitchTarget] = useState<TaskVo | null>(null);

  const { data: tasks = [], isLoading } = useQuery({
    queryKey: ['mine', tab],
    queryFn: () => taskApi.mine(tab),
  });

  const { data: inProgress = [] } = useQuery({
    queryKey: ['mine', 'in_progress'],
    queryFn: () => taskApi.mine('in_progress'),
  });

  const refresh = () => {
    void queryClient.invalidateQueries({ queryKey: ['mine'] });
  };

  const act = useMutation({
    mutationFn: ({ task, action }: { task: TaskVo; action: 'start' | 'complete' | 'switchBack' }) =>
      action === 'start' ? taskApi.start(task.id) : action === 'complete' ? taskApi.complete(task.id) : taskApi.switchBack(task.id),
    onSuccess: (_data, variables) => {
      toast(
        variables.action === 'start' ? '任务已开始' : variables.action === 'complete' ? '任务已完成' : '已切换回等待中',
        'success',
      );
      refresh();
    },
    onError: (e: Error) => toast(e.message, 'error'),
  });

  /** 拖到队首确认切换：进行中的任务 switch-back（保留原 rank）+ 新任务 start */
  async function confirmSwitch(target: TaskVo) {
    setSwitchTarget(null);
    try {
      if (inProgress.length > 0) {
        await taskApi.switchBack(inProgress[0].id);
      }
      await taskApi.start(target.id);
      toast('已切换到新任务', 'success');
      refresh();
    } catch (e) {
      toast((e as Error).message, 'error');
      refresh();
    }
  }

  /** 等待中列表：把卡片上移到队首 = 切换语义（用上下移按钮近似拖拽，避免嵌套 DndContext） */
  const sortedWaiting = useMemo(() => [...tasks], [tasks]);

  return (
    <div className="h-full overflow-y-auto p-5">
      <div className="mx-auto max-w-3xl space-y-4">
        <div className="flex gap-1 rounded-lg bg-zinc-100 p-1 dark:bg-zinc-800">
          {TABS.map((t) => (
            <button
              key={t.key}
              onClick={() => setTab(t.key)}
              className={clsx(
                'flex-1 rounded-md px-3 py-1.5 text-sm transition-colors',
                tab === t.key
                  ? 'bg-white font-medium text-blue-600 shadow-sm dark:bg-zinc-900 dark:text-blue-400'
                  : 'text-zinc-500 hover:text-zinc-700 dark:text-zinc-400',
              )}
            >
              {t.label}
            </button>
          ))}
        </div>

        {isLoading ? (
          <div className="py-16 text-center text-sm text-zinc-400">加载中…</div>
        ) : tasks.length === 0 ? (
          <div className="py-16 text-center text-sm text-zinc-400">暂无任务</div>
        ) : (
          <div className="space-y-2">
            {sortedWaiting.map((task, index) => (
              <div key={task.id} className="flex items-start gap-2">
                <div className="min-w-0 flex-1" onClick={() => setDetailTask(task)}>
                  <TaskCard task={task} draggable={false} />
                </div>
                {tab === 'waiting' && task.assigneeId === user?.id && (
                  <div className="flex w-24 shrink-0 flex-col gap-1 pt-1">
                    {index > 0 && (
                      <Button
                        size="sm"
                        variant="outline"
                        title="移到队首（开始时确认切换）"
                        onClick={() => setSwitchTarget(task)}
                      >
                        ↑ 置顶
                      </Button>
                    )}
                    {inProgress.length === 0 ? (
                      <Button size="sm" variant="primary" onClick={() => act.mutate({ task, action: 'start' })}>
                        开始
                      </Button>
                    ) : (
                      <Button size="sm" variant="outline" onClick={() => setSwitchTarget(task)} title="开始将把当前任务切回等待中">
                        切换开始
                      </Button>
                    )}
                  </div>
                )}
                {tab === 'in_progress' && task.assigneeId === user?.id && (
                  <div className="flex w-24 shrink-0 flex-col gap-1 pt-1">
                    <Button size="sm" variant="primary" onClick={() => act.mutate({ task, action: 'complete' })}>
                      完成
                    </Button>
                    <Button size="sm" variant="outline" onClick={() => act.mutate({ task, action: 'switchBack' })}>
                      切回等待
                    </Button>
                  </div>
                )}
              </div>
            ))}
          </div>
        )}
      </div>

      <TaskDrawer task={detailTask} onClose={() => setDetailTask(null)} onChanged={refresh} />

      <Dialog
        open={!!switchTarget}
        title="开始新任务"
        onClose={() => setSwitchTarget(null)}
        footer={
          <>
            <Button variant="outline" onClick={() => setSwitchTarget(null)}>
              取消
            </Button>
            <Button variant="primary" onClick={() => switchTarget && void confirmSwitch(switchTarget)}>
              确认开始
            </Button>
          </>
        }
      >
        <p className="text-sm text-zinc-600 dark:text-zinc-300">
          确认开始「{switchTarget?.title}」？
          {inProgress.length > 0 && (
            <span className="mt-1 block text-xs text-amber-600">
              当前进行中的「{inProgress[0].title}」将切换回等待中（保留原排序位置）。
            </span>
          )}
        </p>
      </Dialog>
    </div>
  );
}
