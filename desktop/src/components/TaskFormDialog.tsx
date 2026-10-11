import { FormEvent, useEffect, useState } from 'react';
import { Button } from '@/components/ui/Button';
import { Dialog } from '@/components/ui/Dialog';
import { Input, Textarea } from '@/components/ui/Input';
import { Select } from '@/components/ui/Select';
import { taskApi } from '@/api';
import { toast } from '@/stores/toast';
import { useAuthStore } from '@/stores/auth';
import type { TaskVo } from '@/api/types';

export interface TaskFormDialogProps {
  open: boolean;
  onClose: () => void;
  /** 创建时默认选中的项目（跨项目视图不传，由表单内选择） */
  projectId?: string;
  /** 编辑目标（草稿/待分发） */
  task?: TaskVo | null;
  /** 保存成功回调 */
  onSaved: (task: TaskVo) => void;
}

const PRIORITY_OPTIONS = [
  { value: 'URGENT', label: '紧急' },
  { value: 'HIGH', label: '高' },
  { value: 'MEDIUM', label: '中' },
  { value: 'LOW', label: '低' },
];

const TYPE_OPTIONS = [
  { value: 'REQUIREMENT', label: '需求' },
  { value: 'DEVELOP', label: '开发' },
  { value: 'TEST', label: '测试' },
  { value: 'BUG', label: '缺陷' },
  { value: 'DESIGN', label: '设计' },
  { value: 'OPS', label: '运维' },
  { value: 'OTHER', label: '其他' },
];

function toLocalInput(value?: string): string {
  if (!value) {
    return '';
  }
  return value.slice(0, 16);
}

/** 任务创建/编辑表单：保存草稿 或 直接发布；创建时可选所属项目，编辑时项目跟随任务 */
export function TaskFormDialog({ open, onClose, projectId, task, onSaved }: TaskFormDialogProps) {
  const editing = !!task;
  const projectList = useAuthStore((s) => s.projectList);
  const [selectedProjectId, setSelectedProjectId] = useState('');
  const [title, setTitle] = useState('');
  const [description, setDescription] = useState('');
  const [priority, setPriority] = useState('');
  const [taskType, setTaskType] = useState('');
  const [labelsText, setLabelsText] = useState('');
  const [estimatedHours, setEstimatedHours] = useState('');
  const [remainingHours, setRemainingHours] = useState('');
  const [expectedFinishAt, setExpectedFinishAt] = useState('');
  const [deadline, setDeadline] = useState('');
  const [saving, setSaving] = useState(false);

  useEffect(() => {
    if (open) {
      setSelectedProjectId(task?.projectId ?? projectId ?? projectList[0]?.projectId ?? '');
      setTitle(task?.title ?? '');
      setDescription(task?.description ?? '');
      setPriority(task?.priority ?? '');
      setTaskType(task?.taskType ?? '');
      setLabelsText(task?.labels ?? '');
      setEstimatedHours(task?.estimatedHours != null ? String(task.estimatedHours) : '');
      setRemainingHours(task?.remainingHours != null ? String(task.remainingHours) : '');
      setExpectedFinishAt(toLocalInput(task?.expectedFinishAt));
      setDeadline(toLocalInput(task?.deadline));
    }
  }, [open, task]);

  async function handleSave(action: 'DRAFT' | 'PUBLISH') {
    if (!editing && !selectedProjectId) {
      toast('请选择所属项目', 'error');
      return;
    }
    if (!title.trim()) {
      toast('标题不能为空', 'error');
      return;
    }
    if (action === 'PUBLISH' && !priority) {
      toast('发布任务必须选择优先级', 'error');
      return;
    }
    setSaving(true);
    try {
      const body: Record<string, unknown> = {
        // 编辑时项目跟随任务本身，创建时取表单选择
        projectId: task?.projectId ?? selectedProjectId,
        title: title.trim(),
        description,
        action,
      };
      if (priority) {
        body.priority = priority;
      }
      if (taskType) {
        body.taskType = taskType;
      }
      if (labelsText.trim()) {
        body.labels = labelsText.split(/[,，\s]+/).filter(Boolean);
      }
      if (estimatedHours) {
        body.estimatedHours = Number(estimatedHours);
      }
      if (remainingHours) {
        body.remainingHours = Number(remainingHours);
      }
      if (expectedFinishAt) {
        body.expectedFinishAt = expectedFinishAt.replace('T', ' ') + ':00';
      }
      if (deadline) {
        body.deadline = deadline.replace('T', ' ') + ':00';
      }
      const saved = editing && task
        ? await taskApi.update(task.id, body)
        : await taskApi.create(body);
      toast(action === 'PUBLISH' ? '任务已发布' : '草稿已保存', 'success');
      onSaved(saved);
      onClose();
    } catch (e) {
      toast((e as Error).message, 'error');
    } finally {
      setSaving(false);
    }
  }

  return (
    <Dialog
      open={open}
      title={editing ? `编辑任务${task?.taskNo ? ` ${task.taskNo}` : ''}` : '新建任务'}
      onClose={onClose}
      width="max-w-xl"
      footer={
        <>
          <Button variant="outline" onClick={onClose}>
            取消
          </Button>
          {!editing && (
            <Button variant="outline" disabled={saving} onClick={() => void handleSave('DRAFT')}>
              存为草稿
            </Button>
          )}
          <Button variant="primary" disabled={saving} onClick={() => void handleSave('PUBLISH')}>
            {editing ? '保存' : '直接发布'}
          </Button>
        </>
      }
    >
      <form className="space-y-3" onSubmit={(e: FormEvent) => e.preventDefault()}>
        {!editing && (
          <div>
            <label className="mb-1 block text-xs text-zinc-500">所属项目 *</label>
            <Select
              value={selectedProjectId}
              onChange={(e) => setSelectedProjectId(e.target.value)}
              options={projectList.map((p) => ({ value: p.projectId, label: `${p.projectName}（${p.projectKey}）` }))}
              placeholder={projectList.length === 0 ? '暂无项目' : ''}
            />
          </div>
        )}
        <div>
          <label className="mb-1 block text-xs text-zinc-500">标题 *</label>
          <Input value={title} onChange={(e) => setTitle(e.target.value)} placeholder="一句话描述任务" />
        </div>
        <div>
          <label className="mb-1 block text-xs text-zinc-500">描述（富文本内容将以纯文本清洗入库）</label>
          <Textarea rows={4} value={description} onChange={(e) => setDescription(e.target.value)} />
        </div>
        <div className="grid grid-cols-2 gap-3">
          <div>
            <label className="mb-1 block text-xs text-zinc-500">优先级（发布必填）</label>
            <Select value={priority} onChange={(e) => setPriority(e.target.value)} options={PRIORITY_OPTIONS} placeholder="未设置" />
          </div>
          <div>
            <label className="mb-1 block text-xs text-zinc-500">任务类型</label>
            <Select value={taskType} onChange={(e) => setTaskType(e.target.value)} options={TYPE_OPTIONS} placeholder="未设置" />
          </div>
          <div>
            <label className="mb-1 block text-xs text-zinc-500">预计工时（小时）</label>
            <Input type="number" step="0.5" min="0" value={estimatedHours} onChange={(e) => setEstimatedHours(e.target.value)} />
          </div>
          <div>
            <label className="mb-1 block text-xs text-zinc-500">剩余工时（小时）</label>
            <Input type="number" step="0.5" min="0" value={remainingHours} onChange={(e) => setRemainingHours(e.target.value)} />
          </div>
          <div>
            <label className="mb-1 block text-xs text-zinc-500">预计完成时间</label>
            <Input type="datetime-local" value={expectedFinishAt} onChange={(e) => setExpectedFinishAt(e.target.value)} />
          </div>
          <div>
            <label className="mb-1 block text-xs text-zinc-500">截止时间（逾期将提醒）</label>
            <Input type="datetime-local" value={deadline} onChange={(e) => setDeadline(e.target.value)} />
          </div>
        </div>
        <div>
          <label className="mb-1 block text-xs text-zinc-500">标签（逗号/空格分隔）</label>
          <Input value={labelsText} onChange={(e) => setLabelsText(e.target.value)} placeholder="前端 紧急迭代" />
        </div>
      </form>
    </Dialog>
  );
}
