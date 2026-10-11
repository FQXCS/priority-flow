import { useState } from 'react';
import { useQuery, useQueryClient } from '@tanstack/react-query';
import { taskApi } from '@/api';
import { toast } from '@/stores/toast';
import { useAuthStore } from '@/stores/auth';
import { Button } from '@/components/ui/Button';
import { TaskCard } from '@/components/TaskCard';
import { TaskFormDialog } from '@/components/TaskFormDialog';
import { TaskDrawer } from '@/components/TaskDrawer';
import type { TaskVo } from '@/api/types';

/** 我的草稿页：编辑 / 发布 / 删除 */
export function DraftsPage() {
  const { currentProjectId } = useAuthStore();
  const queryClient = useQueryClient();
  const [editing, setEditing] = useState<TaskVo | null>(null);
  const [detailTask, setDetailTask] = useState<TaskVo | null>(null);

  const { data, isLoading } = useQuery({
    queryKey: ['my-drafts'],
    queryFn: () => taskApi.myDrafts(1, 100),
  });

  const refresh = () => {
    void queryClient.invalidateQueries({ queryKey: ['my-drafts'] });
    void queryClient.invalidateQueries({ queryKey: ['board', currentProjectId] });
  };

  async function publish(task: TaskVo) {
    try {
      await taskApi.publish(task.id);
      toast('已发布到待分发', 'success');
      refresh();
    } catch (e) {
      toast((e as Error).message, 'error');
    }
  }

  async function remove(task: TaskVo) {
    if (!window.confirm(`确认删除草稿「${task.title}」？`)) {
      return;
    }
    try {
      await taskApi.deleteDraft(task.id);
      toast('草稿已删除', 'success');
      refresh();
    } catch (e) {
      toast((e as Error).message, 'error');
    }
  }

  const drafts = data?.list ?? [];

  return (
    <div className="h-full overflow-y-auto p-5">
      <div className="mx-auto max-w-3xl space-y-3">
        <h3 className="text-sm font-semibold text-zinc-600 dark:text-zinc-300">我的草稿（{drafts.length}）</h3>
        {isLoading ? (
          <div className="py-16 text-center text-sm text-zinc-400">加载中…</div>
        ) : drafts.length === 0 ? (
          <div className="py-16 text-center text-sm text-zinc-400">暂无草稿，去看板新建一个吧</div>
        ) : (
          <div className="space-y-2">
            {drafts.map((task) => (
              <div key={task.id} className="flex items-start gap-2">
                <div className="min-w-0 flex-1" onClick={() => setDetailTask(task)}>
                  <TaskCard task={task} draggable={false} />
                </div>
                <div className="flex w-40 shrink-0 flex-col gap-1 pt-1">
                  <Button size="sm" variant="primary" onClick={() => setEditing(task)}>
                    编辑
                  </Button>
                  <div className="flex gap-1">
                    <Button size="sm" variant="outline" className="flex-1" onClick={() => void publish(task)}>
                      发布
                    </Button>
                    <Button size="sm" variant="danger" className="flex-1" onClick={() => void remove(task)}>
                      删除
                    </Button>
                  </div>
                </div>
              </div>
            ))}
          </div>
        )}
      </div>

      <TaskFormDialog
        open={!!editing}
        task={editing}
        onClose={() => setEditing(null)}
        projectId={editing?.projectId ?? currentProjectId ?? ''}
        onSaved={refresh}
      />
      <TaskDrawer task={detailTask} onClose={() => setDetailTask(null)} onChanged={refresh} />
    </div>
  );
}
