import { useEffect, useState } from 'react';
import { closestCenter, DndContext, PointerSensor, useSensor, useSensors, type DragEndEvent } from '@dnd-kit/core';
import { SortableContext, useSortable, verticalListSortingStrategy } from '@dnd-kit/sortable';
import { CSS } from '@dnd-kit/utilities';
import clsx from 'clsx';
import { weeklyReportApi } from '@/api';
import type { WeeklyReportSaveBody } from '@/api';
import type { WeeklyReportVo } from '@/api/types';
import { useAuthStore } from '@/stores/auth';
import { toast } from '@/stores/toast';
import { Button } from '@/components/ui/Button';
import { Dialog } from '@/components/ui/Dialog';
import { Input } from '@/components/ui/Input';
import { Select } from '@/components/ui/Select';
import {
  addItem,
  currentWeekRange,
  entryToParts,
  isDuplicatePeriod,
  moveItemAt,
  parseEntries,
  removeItemAt,
  toEntryPayload,
  updateItemAt,
  type ItemParts,
} from './reportUtils';

const MAX_ENTRY_COUNT = 100;
const MAX_ENTRY_LENGTH = 500;

interface Props {
  open: boolean;
  /** 编辑目标；null = 生成模式 */
  report: WeeklyReportVo | null;
  /** 已加载的全部周报（同项目同周期防重复生成判断，后端 4002 兜底） */
  reports: WeeklyReportVo[];
  saving: boolean;
  onSubmit: (payload: { report: WeeklyReportVo | null; body: WeeklyReportSaveBody }) => void;
  onClose: () => void;
}

/** 生成/编辑共用弹框：生成模式选项目 + 起止日期（变更后自动重新聚合），条目可增删改后发布 */
export function ReportFormDialog(props: Props) {
  const { open, report, reports, saving, onSubmit, onClose } = props;
  const editing = !!report;
  const { projectList } = useAuthStore();

  const [projectId, setProjectId] = useState('');
  const [periodStart, setPeriodStart] = useState('');
  const [periodEnd, setPeriodEnd] = useState('');
  const [content, setContent] = useState<ItemParts[]>([]);
  const [planContent, setPlanContent] = useState<ItemParts[]>([]);
  const [generating, setGenerating] = useState(false);

  // 打开时初始化：编辑模式读快照条目（拆三段）；生成模式默认本周一~周日
  useEffect(() => {
    if (!open) {
      return;
    }
    setGenerating(false);
    if (report) {
      setContent(parseEntries(report.content));
      setPlanContent(parseEntries(report.planContent));
    } else {
      const range = currentWeekRange();
      setProjectId(projectList[0]?.projectId ?? '');
      setPeriodStart(range.start);
      setPeriodEnd(range.end);
      setContent([]);
      setPlanContent([]);
    }
    // projectList 打开瞬间取首个即可，无需响应后续变化
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [open, report]);

  // 生成模式：项目或周期变化后自动重新聚合（覆盖手工改动 = 文档「改动后重新聚合」语义）
  useEffect(() => {
    if (!open || editing || !projectId || !periodStart || !periodEnd) {
      return;
    }
    if (isDuplicatePeriod(reports, projectId, periodStart, periodEnd)) {
      toast('该周期周报已存在，请直接编辑', 'error');
      return;
    }
    let cancelled = false;
    setGenerating(true);
    weeklyReportApi
      .generate({ projectId, periodStart, periodEnd })
      .then((draft) => {
        if (cancelled) {
          return;
        }
        setContent(draft.content.map(entryToParts));
        setPlanContent(draft.planContent.map(entryToParts));
      })
      .catch((e: Error) => {
        if (!cancelled) {
          toast(e.message, 'error');
        }
      })
      .finally(() => {
        if (!cancelled) {
          setGenerating(false);
        }
      });
    return () => {
      cancelled = true;
    };
  }, [open, editing, projectId, periodStart, periodEnd, reports]);

  const handleSubmit = () => {
    if (!editing && (!projectId || !periodStart || !periodEnd)) {
      toast('请选择项目与统计周期', 'error');
      return;
    }
    if (content.some((item) => !item.text.trim()) || planContent.some((item) => !item.text.trim())) {
      toast('任务说明不能为空', 'error');
      return;
    }
    onSubmit({
      report,
      body: {
        projectId: editing ? report.projectId : projectId,
        periodStart: editing ? report.periodStart : periodStart,
        periodEnd: editing ? report.periodEnd : periodEnd,
        content: content.map(toEntryPayload),
        planContent: planContent.map(toEntryPayload),
      },
    });
  };

  return (
    <Dialog
      open={open}
      title={editing ? '编辑周报' : '生成周报'}
      onClose={onClose}
      width="max-w-3xl"
      footer={
        <>
          <Button variant="outline" onClick={onClose}>
            取消
          </Button>
          <Button variant="primary" disabled={saving || generating} onClick={handleSubmit}>
            {editing ? '保存修改' : generating ? '聚合中…' : '发布'}
          </Button>
        </>
      }
    >
      {editing ? (
        <div className="mb-1 text-sm">
          <span className="font-medium text-zinc-800 dark:text-zinc-100">{report.projectName}</span>
          <span className="ml-2 text-xs text-zinc-400">
            {report.periodStart} ~ {report.periodEnd}（周期不可改）
          </span>
        </div>
      ) : (
        <>
          <label className="mb-1 block text-xs text-zinc-500">项目 *</label>
          <Select
            value={projectId}
            onChange={(e) => setProjectId(e.target.value)}
            options={projectList.map((p) => ({ value: p.projectId, label: `${p.projectName}（${p.projectKey}）` }))}
          />
          <div className="mt-3 grid grid-cols-2 gap-3">
            <div>
              <label className="mb-1 block text-xs text-zinc-500">周期开始 *</label>
              <Input type="date" value={periodStart} onChange={(e) => setPeriodStart(e.target.value)} />
            </div>
            <div>
              <label className="mb-1 block text-xs text-zinc-500">周期结束 *</label>
              <Input type="date" value={periodEnd} onChange={(e) => setPeriodEnd(e.target.value)} />
            </div>
          </div>
        </>
      )}
      {generating && <div className="mt-2 text-xs text-zinc-400">正在按任务看板聚合生成，完成后可编辑…</div>}
      <EntrySection title="本周进展" items={content} disabled={generating} onChange={setContent} />
      <EntrySection title="下周计划" items={planContent} disabled={generating} onChange={setPlanContent} />
    </Dialog>
  );
}

/** 单段条目编辑：每行拆「任务说明 / 进度 / 人员」三列 + 逐条删除 + 添加条目（上限 100 条、说明 500 字符） */
function EntrySection(props: {
  title: string;
  items: ItemParts[];
  disabled?: boolean;
  onChange: (items: ItemParts[]) => void;
}) {
  const { title, items, disabled, onChange } = props;
  const sensors = useSensors(useSensor(PointerSensor, { activationConstraint: { distance: 6 } }));
  // 把手只在 ≥2 条且可编辑时渲染：单条无排序意义，聚合中与编辑同锁
  const showHandle = items.length > 1 && !disabled;

  const handleDragEnd = (event: DragEndEvent) => {
    const { active, over } = event;
    if (!over || active.id === over.id) {
      return;
    }
    onChange(moveItemAt(items, Number(active.id), Number(over.id)));
  };

  return (
    <div className="mt-4">
      <div className="mb-1 flex items-center justify-between">
        <label className="text-xs text-zinc-500">{title}</label>
        <Button
          size="sm"
          variant="ghost"
          disabled={disabled || items.length >= MAX_ENTRY_COUNT}
          onClick={() => onChange(addItem(items, { text: '', progress: '', person: '' }))}
        >
          ＋ 添加条目
        </Button>
      </div>
      {items.length === 0 ? (
        <div className="rounded-lg border border-dashed border-zinc-200 px-3 py-2 text-xs text-zinc-400 dark:border-zinc-700">
          暂无条目，可点击「添加条目」补充系统外工作
        </div>
      ) : (
        <div className="space-y-1.5">
          <div className="flex items-center gap-1.5 text-[11px] text-zinc-400">
            {showHandle && <div className="w-5 shrink-0" />}
            <div className="min-w-0 flex-1 pl-3">任务说明</div>
            <div className="w-20 shrink-0 px-0 text-center">进度%</div>
            <div className="w-28 shrink-0 text-center">人员</div>
            <div className="w-9 shrink-0" />
          </div>
          <DndContext sensors={sensors} collisionDetection={closestCenter} onDragEnd={handleDragEnd}>
            <SortableContext items={items.map((_, index) => String(index))} strategy={verticalListSortingStrategy}>
              {items.map((item, index) => (
                <SortableEntryRow
                  key={index}
                  index={index}
                  item={item}
                  disabled={disabled}
                  showHandle={showHandle}
                  onTextChange={(text) => onChange(updateItemAt(items, index, { ...item, text }))}
                  onProgressChange={(progress) => onChange(updateItemAt(items, index, { ...item, progress }))}
                  onPersonChange={(person) => onChange(updateItemAt(items, index, { ...item, person }))}
                  onRemove={() => onChange(removeItemAt(items, index))}
                />
              ))}
            </SortableContext>
          </DndContext>
        </div>
      )}
    </div>
  );
}

/** 可拖拽条目行：把手默认隐藏、悬停行显示；listeners 仅挂把手，不干扰行内输入 */
function SortableEntryRow(props: {
  index: number;
  item: ItemParts;
  disabled?: boolean;
  showHandle: boolean;
  onTextChange: (text: string) => void;
  onProgressChange: (progress: string) => void;
  onPersonChange: (person: string) => void;
  onRemove: () => void;
}) {
  const { index, item, disabled, showHandle, onTextChange, onProgressChange, onPersonChange, onRemove } = props;
  const { attributes, listeners, setNodeRef, transform, transition, isDragging } = useSortable({
    id: String(index),
    disabled,
  });
  return (
    <div
      ref={setNodeRef}
      style={{ transform: CSS.Transform.toString(transform), transition }}
      className={clsx('group flex items-center gap-1.5', isDragging && 'opacity-40')}
    >
      {showHandle && (
        <button
          type="button"
          title="拖拽排序"
          aria-label="拖拽排序"
          className="w-5 shrink-0 cursor-grab text-center text-sm leading-none text-zinc-400 opacity-0 transition-opacity focus:opacity-100 group-hover:opacity-100"
          {...attributes}
          {...listeners}
        >
          ⠿
        </button>
      )}
      <div className="min-w-0 flex-1">
        <Input
          value={item.text}
          maxLength={MAX_ENTRY_LENGTH}
          disabled={disabled}
          onChange={(e) => onTextChange(e.target.value)}
        />
      </div>
      <div className="w-20 shrink-0">
        <Input
          type="number"
          min={0}
          max={100}
          value={item.progress}
          disabled={disabled}
          onChange={(e) => onProgressChange(e.target.value)}
        />
      </div>
      <div className="w-28 shrink-0">
        <Input
          value={item.person}
          maxLength={100}
          disabled={disabled}
          onChange={(e) => onPersonChange(e.target.value)}
        />
      </div>
      <Button size="icon" variant="ghost" title="删除条目" disabled={disabled} onClick={onRemove}>
        ✕
      </Button>
    </div>
  );
}
