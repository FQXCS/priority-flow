/**
 * 计划周报纯逻辑（页面/弹框/单测三方复用）：条目解析与增删改、本周周期计算、防重复判断、
 * 看板按项目分组与项目标签取色、创建时间格式化。项目无日期库，周期计算自写（周一为首日）。
 */
import type { WeeklyReportEntry, WeeklyReportVo } from '@/api/types';

function pad(n: number): string {
  return n < 10 ? `0${n}` : String(n);
}

function formatDate(d: Date): string {
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}`;
}

/** 本周一 ~ 本周日（yyyy-MM-dd），周一为每周首日 */
export function currentWeekRange(now = new Date()): { start: string; end: string } {
  const day = now.getDay();
  const mondayOffset = day === 0 ? -6 : 1 - day;
  const monday = new Date(now.getFullYear(), now.getMonth(), now.getDate() + mondayOffset);
  const sunday = new Date(monday.getFullYear(), monday.getMonth(), monday.getDate() + 6);
  return { start: formatDate(monday), end: formatDate(sunday) };
}

/** 追加条目（返回新数组，内容由调用方给定） */
export function addItem<T>(items: T[], item: T): T[] {
  return [...items, item];
}

/** 删除指定位置条目（越界安全） */
export function removeItemAt<T>(items: T[], index: number): T[] {
  if (index < 0 || index >= items.length) {
    return items;
  }
  return items.filter((_, i) => i !== index);
}

/** 修改指定位置条目（越界安全） */
export function updateItemAt<T>(items: T[], index: number, item: T): T[] {
  if (index < 0 || index >= items.length) {
    return items;
  }
  return items.map((current, i) => (i === index ? item : current));
}

/** 同项目同周期（起止三元组）是否已有周报 */
export function isDuplicatePeriod(
  reports: { projectId: string; periodStart: string; periodEnd: string }[],
  projectId: string,
  start: string,
  end: string,
): boolean {
  return reports.some((r) => r.projectId === projectId && r.periodStart === start && r.periodEnd === end);
}

/** 条目三段结构：任务说明 / 进度 / 人员（存储仍为拼接文本「说明 进度% 人员」） */
export interface ItemParts {
  text: string;
  progress: string;
  person: string;
}

/**
 * 把拼接文本行反向解析为三段（从行尾解析：末段为人员，人员前的连续数字+%为进度，其余为说明）。
 * 无进度/人员的自由文本整行归入说明。
 */
export function parseItemLine(line: string): ItemParts {
  const percentIdx = line.lastIndexOf('%');
  const spaceIdx = line.lastIndexOf(' ');
  if (percentIdx === -1) {
    if (spaceIdx === -1) {
      return { text: line.trimEnd(), progress: '', person: '' };
    }
    return { text: line.slice(0, spaceIdx), progress: '', person: line.slice(spaceIdx + 1) };
  }
  let digitStart = percentIdx;
  while (digitStart > 0 && line.charAt(digitStart - 1) >= '0' && line.charAt(digitStart - 1) <= '9') {
    digitStart--;
  }
  const progress = line.slice(digitStart, percentIdx);
  const person = spaceIdx > percentIdx ? line.slice(spaceIdx + 1) : '';
  return { text: line.slice(0, digitStart).trimEnd(), progress, person };
}

/** 三段拼接回存储文本行（说明 进度% 人员；缺进度/人员时按现有段拼接） */
export function composeItemLine(parts: ItemParts): string {
  const text = parts.text.trim();
  const progress = parts.progress.trim();
  const person = parts.person.trim();
  if (progress && person) {
    return `${text}${progress}% ${person}`;
  }
  if (progress) {
    return `${text}${progress}%`;
  }
  if (person) {
    return `${text} ${person}`;
  }
  return text;
}

/**
 * 解析 content/planContent JSON 为可编辑三段列表：
 * 对象数组（现行存储）直接映射；字符串条目（历史数据）按「说明 进度% 人员」反向解析。
 */
export function parseEntries(json?: string): ItemParts[] {
  if (!json) {
    return [];
  }
  try {
    const parsed: unknown = JSON.parse(json);
    if (!Array.isArray(parsed)) {
      return [];
    }
    return parsed.map((item): ItemParts => {
      if (typeof item === 'string') {
        return parseItemLine(item);
      }
      if (item !== null && typeof item === 'object') {
        return entryToParts(item as WeeklyReportEntry);
      }
      return { text: '', progress: '', person: '' };
    });
  } catch {
    return [];
  }
}

/** 结构化条目 → 可编辑三段 */
export function entryToParts(entry: WeeklyReportEntry): ItemParts {
  return {
    text: entry.text,
    progress: entry.progress === null || entry.progress === undefined ? '' : String(entry.progress),
    person: entry.person ?? '',
  };
}

/** 三段编辑态 → 入库结构（进度空/非法 = null，人员空 = null） */
export function toEntryPayload(parts: ItemParts): WeeklyReportEntry {
  const progress = parts.progress.trim();
  const person = parts.person.trim();
  const progressNumber = Number(progress);
  return {
    text: parts.text.trim(),
    progress: progress === '' || !Number.isFinite(progressNumber) ? null : progressNumber,
    person: person === '' ? null : person,
  };
}

/** 拖拽排序：把 from 位置条目移到 to（不可变；原位或越界返回原数组） */
export function moveItemAt<T>(items: T[], from: number, to: number): T[] {
  if (from === to || from < 0 || from >= items.length || to < 0 || to >= items.length) {
    return items;
  }
  const next = [...items];
  const moved = next.splice(from, 1)[0];
  next.splice(to, 0, moved);
  return next;
}

/** ISO 创建时间 → yyyy-MM-dd HH:mm:ss（截断小数秒；空/过短返回空串） */
export function formatReportTime(createTime?: string): string {
  if (!createTime || createTime.length < 19) {
    return '';
  }
  return createTime.slice(0, 19).replace('T', ' ');
}

/** 项目彩色标签色板（bg+text 类对，含暗色变体；样式先例 Badge.STATUS_META） */
export const PROJECT_TAG_COLORS = [
  'bg-blue-100 text-blue-700 dark:bg-blue-900/40 dark:text-blue-300',
  'bg-emerald-100 text-emerald-700 dark:bg-emerald-900/40 dark:text-emerald-300',
  'bg-amber-100 text-amber-700 dark:bg-amber-900/40 dark:text-amber-300',
  'bg-violet-100 text-violet-700 dark:bg-violet-900/40 dark:text-violet-300',
  'bg-rose-100 text-rose-700 dark:bg-rose-900/40 dark:text-rose-300',
  'bg-cyan-100 text-cyan-700 dark:bg-cyan-900/40 dark:text-cyan-300',
  'bg-sky-100 text-sky-700 dark:bg-sky-900/40 dark:text-sky-300',
  'bg-indigo-100 text-indigo-700 dark:bg-indigo-900/40 dark:text-indigo-300',
];

/** 按 projectId 稳定取标签色（与 Avatar 同款 hash 算法，项目改名不换色） */
export function projectColorClass(projectId: string): string {
  let hash = 0;
  for (const ch of projectId) {
    hash = (hash * 31 + ch.charCodeAt(0)) >>> 0;
  }
  return PROJECT_TAG_COLORS[hash % PROJECT_TAG_COLORS.length];
}

/** 看板列：一个项目的周报分组 */
export interface ReportProjectGroup {
  projectId: string;
  projectName: string;
  reports: WeeklyReportVo[];
}

const NAME_COLLATOR = new Intl.Collator('zh-Hans-CN');

function compareGroup(a: ReportProjectGroup, b: ReportProjectGroup): number {
  const byName = NAME_COLLATOR.compare(a.projectName, b.projectName);
  if (byName !== 0) {
    return byName;
  }
  return a.projectId < b.projectId ? -1 : a.projectId > b.projectId ? 1 : 0;
}

/** 列内排序：周期倒序（新的在上），同周期按创建时间倒序兜底 */
function compareReportDesc(a: WeeklyReportVo, b: WeeklyReportVo): number {
  return (
    b.periodStart.localeCompare(a.periodStart) ||
    b.periodEnd.localeCompare(a.periodEnd) ||
    (b.createTime ?? '').localeCompare(a.createTime ?? '')
  );
}

/**
 * 周报按项目分组为看板列：列 = 全部参与项目（含无周报空列）；
 * projectId 不在项目集中的周报追加孤儿列兜底（排最后）；
 * 列序按项目名（zh）排序、同名按 projectId；不修改入参。
 */
export function groupReportsByProject(
  reports: WeeklyReportVo[],
  projects: { projectId: string; projectName: string }[],
): ReportProjectGroup[] {
  const columns = new Map<string, ReportProjectGroup>();
  for (const project of projects) {
    columns.set(project.projectId, { projectId: project.projectId, projectName: project.projectName, reports: [] });
  }
  const orphans = new Map<string, ReportProjectGroup>();
  for (const report of reports) {
    const column = columns.get(report.projectId);
    if (column) {
      column.reports.push(report);
      continue;
    }
    let orphan = orphans.get(report.projectId);
    if (!orphan) {
      orphan = { projectId: report.projectId, projectName: report.projectName ?? '', reports: [] };
      orphans.set(report.projectId, orphan);
    }
    orphan.reports.push(report);
  }
  const groups = [...columns.values()].sort(compareGroup).concat([...orphans.values()].sort(compareGroup));
  for (const group of groups) {
    group.reports.sort(compareReportDesc);
  }
  return groups;
}
