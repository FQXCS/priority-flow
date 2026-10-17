import { useState } from 'react';
import type { ReactNode } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { taskApi } from '@/api';
import { useAuthStore } from '@/stores/auth';
import { toast } from '@/stores/toast';
import { Button } from '@/components/ui/Button';
import { Dialog } from '@/components/ui/Dialog';
import { Input } from '@/components/ui/Input';
import { Select } from '@/components/ui/Select';
import { TaskCard } from '@/components/TaskCard';
import { TaskDrawer } from '@/components/TaskDrawer';
import type { TaskVo } from '@/api/types';

/** 我的任务页：三列看板样式（等待中/进行中/已完成一屏总览），项目为筛选条件；切换开始经确认弹窗执行「切回 + 开始」 */
export function MyTaskPage() {
  const { user, projectList } = useAuthStore();
  const queryClient = useQueryClient();
  const [projectId, setProjectId] = useState('');
  const [detailTask, setDetailTask] = useState<TaskVo | null>(null);
  const [switchTarget, setSwitchTarget] = useState<TaskVo | null>(null);
  const [progressTarget, setProgressTarget] = useState<TaskVo | null>(null);
  const [progressValue, setProgressValue] = useState(0);

  // 三个状态并行拉取，列随项目筛选
  const { data: waiting = [], isLoading: waitingLoading } = useQuery({
    queryKey: ['mine', 'waiting', projectId],
    queryFn: () => taskApi.mine('waiting', projectId || undefined),
  });
  const { data: inProgress = [], isLoading: inProgressLoading } = useQuery({
    queryKey: ['mine', 'in_progress', projectId],
    queryFn: () => taskApi.mine('in_progress', projectId || undefined),
  });
  const { data: completed = [], isLoading: completedLoading } = useQuery({
    queryKey: ['mine', 'completed', projectId],
    queryFn: () => taskApi.mine('completed', projectId || undefined),
  });
  // 切换逻辑依据的「当前进行中」不受项目筛选影响（保持原行为）
  const { data: inProgressAll = [] } = useQuery({
    queryKey: ['mine', 'in_progress_switch'],
    queryFn: () => taskApi.mine('in_progress'),
  });

  const isLoading = waitingLoading || inProgressLoading || completedLoading;

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

  const setProgressAct = useMutation({
    mutationFn: ({ task, progress }: { task: TaskVo; progress: number }) => taskApi.setProgress(task.id, progress),
    onSuccess: () => {
      toast('进度已更新', 'success');
      setProgressTarget(null);
      refresh();
    },
    onError: (e: Error) => toast(e.message, 'error'),
  });

  /** 打开进度弹窗：以当前进度初始化滑块 */
  function openProgress(task: TaskVo) {
    setProgressValue(task.progress ?? 0);
    setProgressTarget(task);
  }

  /** 置顶/切换开始确认：进行中的任务 switch-back（保留原 rank）+ 新任务 start */
  async function confirmSwitch(target: TaskVo) {
    setSwitchTarget(null);
    try {
      if (inProgressAll.length > 0) {
        await taskApi.switchBack(inProgressAll[0].id);
      }
      await taskApi.start(target.id);
      toast('已切换到新任务', 'success');
      refresh();
    } catch (e) {
      toast((e as Error).message, 'error');
      refresh();
    }
  }

  return (
    <div className="flex h-full flex-col">
      {/* 筛选栏：与看板页一致，项目为搜索条件（默认全部项目） */}
      <div className="flex shrink-0 flex-wrap items-center gap-2 border-b border-zinc-200 bg-white px-4 py-2 dark:border-zinc-800 dark:bg-zinc-900">
        {/* 定宽包裹：Select 基础样式自带 w-full */}
        <div className="w-44">
          <Select
            value={projectId}
            onChange={(e) => setProjectId(e.target.value)}
            options={[
              { value: '', label: '全部项目' },
              ...projectList.map((p) => ({ value: p.projectId, label: `${p.projectName}（${p.projectKey}）` })),
            ]}
          />
        </div>
      </div>

      {/* 三列看板 */}
      <div className="min-h-0 flex-1 overflow-x-auto p-4">
        {isLoading ? (
          <div className="grid h-full place-items-center text-sm text-zinc-400">加载中…</div>
        ) : (
          <div className="flex h-full min-w-max gap-3">
            <MyColumn title="等待中" count={waiting.length}>
              {waiting.length === 0 ? (
                <EmptyCell />
              ) : (
                waiting.map((task) => (
                  <div key={task.id} className="space-y-1">
                    <TaskCard task={task} onClick={() => setDetailTask(task)} />
                    {task.assigneeId === user?.id && (
                      <div className="flex gap-1">
                        {inProgressAll.length === 0 ? (
                          <Button size="sm" variant="primary" className="flex-1" onClick={() => act.mutate({ task, action: 'start' })}>
                            开始
                          </Button>
                        ) : (
                          <Button
                            size="sm"
                            variant="outline"
                            className="flex-1"
                            title="开始将把当前任务切回等待中"
                            onClick={() => setSwitchTarget(task)}
                          >
                            切换开始
                          </Button>
                        )}
                        <Button size="sm" variant="outline" className="flex-1" onClick={() => openProgress(task)}>
                          设置进度
                        </Button>
                      </div>
                    )}
                  </div>
                ))
              )}
            </MyColumn>

            <MyColumn title="进行中" count={inProgress.length}>
              {inProgress.length === 0 ? (
                <EmptyCell />
              ) : (
                inProgress.map((task) => (
                  <div key={task.id} className="space-y-1">
                    <TaskCard task={task} onClick={() => setDetailTask(task)} />
                    {task.assigneeId === user?.id && (
                      <div className="flex gap-1">
                        <Button size="sm" variant="primary" className="flex-1" onClick={() => act.mutate({ task, action: 'complete' })}>
                          完成
                        </Button>
                        <Button size="sm" variant="outline" className="flex-1" onClick={() => act.mutate({ task, action: 'switchBack' })}>
                          切回等待
                        </Button>
                        <Button size="sm" variant="outline" className="flex-1" onClick={() => openProgress(task)}>
                          设置进度
                        </Button>
                      </div>
                    )}
                  </div>
                ))
              )}
            </MyColumn>

            <MyColumn title="已完成" count={completed.length}>
              {completed.length === 0 ? (
                <EmptyCell />
              ) : (
                completed.map((task) => (
                  <TaskCard key={task.id} task={task} onClick={() => setDetailTask(task)} />
                ))
              )}
            </MyColumn>
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
          {inProgressAll.length > 0 && (
            <span className="mt-1 block text-xs text-amber-600">
              当前进行中的「{inProgressAll[0].title}」将切换回等待中（保留原排序位置）。
            </span>
          )}
        </p>
      </Dialog>

      <Dialog
        open={!!progressTarget}
        title="设置进度"
        onClose={() => setProgressTarget(null)}
        footer={
          <>
            <Button variant="outline" onClick={() => setProgressTarget(null)}>
              取消
            </Button>
            <Button
              variant="primary"
              disabled={setProgressAct.isPending}
              onClick={() => progressTarget && setProgressAct.mutate({ task: progressTarget, progress: progressValue })}
            >
              保存
            </Button>
          </>
        }
      >
        <p className="text-sm text-zinc-600 dark:text-zinc-300">任务名称：{progressTarget?.title}</p>
        <div className="mt-3 flex items-center gap-2">
          {/* 上限 99：100 仅由完成动作产生（与后端 @Max(99) 双重防线），整数输入 */}
          <Input
            type="number"
            min={0}
            max={99}
            step={1}
            value={progressValue}
            onChange={(e) => {
              const v = Math.floor(Number(e.target.value));
              setProgressValue(Number.isNaN(v) ? 0 : Math.max(0, Math.min(99, v)));
            }}
            className="w-28"
          />
          <span className="text-sm text-zinc-500">%</span>
        </div>
        <p className="mt-2 text-xs text-zinc-400">进度范围 0-99，完成任务时自动置为 100%。</p>
      </Dialog>
    </div>
  );
}

/** 列容器：样式与看板页列一致（两页各自持有，避免为本次改动重构看板页） */
function MyColumn({ title, count, children }: { title: string; count: number; children: ReactNode }) {
  return (
    <div className="flex h-full w-72 shrink-0 flex-col rounded-xl bg-zinc-100 dark:bg-zinc-800/60">
      <div className="flex items-center justify-between px-3 py-2.5">
        <span className="text-xs font-semibold text-zinc-600 dark:text-zinc-300">{title}</span>
        <span className="rounded-full bg-zinc-200 px-1.5 text-[11px] text-zinc-500 dark:bg-zinc-700 dark:text-zinc-400">
          {count}
        </span>
      </div>
      <div className="min-h-0 flex-1 space-y-2 overflow-y-auto px-2 pb-2">{children}</div>
    </div>
  );
}

/** 列内空态 */
function EmptyCell() {
  return <div className="py-6 text-center text-xs text-zinc-400">暂无任务</div>;
}
