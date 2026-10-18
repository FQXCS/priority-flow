import { describe, expect, it } from 'vitest';
import {
  addItem,
  composeItemLine,
  currentWeekRange,
  entryToParts,
  formatReportTime,
  groupReportsByProject,
  isDuplicatePeriod,
  moveItemAt,
  nextEntryKey,
  parseEntries,
  parseItemLine,
  PROJECT_TAG_COLORS,
  projectColorClass,
  removeItemAt,
  toEditableEntries,
  toEntryPayload,
  updateItemAt,
} from './reportUtils';
import type { WeeklyReportVo } from '@/api/types';

/** 构造周报测试数据（仅分组逻辑关心的字段） */
function report(
  id: string,
  projectId: string,
  periodStart: string,
  periodEnd: string,
  projectName?: string,
): WeeklyReportVo {
  return { id, projectId, projectName, periodStart, periodEnd };
}

describe('parseEntries', () => {
  it('解析结构化对象数组', () => {
    expect(parseEntries('[{"text":"专利修改","progress":100,"person":"李执行"},{"text":"手工条目"}]')).toEqual([
      { text: '专利修改', progress: '100', person: '李执行' },
      { text: '手工条目', progress: '', person: '' },
    ]);
  });

  it('进度/人员为 null 时归空串', () => {
    expect(parseEntries('[{"text":"a","progress":null,"person":null}]')).toEqual([
      { text: 'a', progress: '', person: '' },
    ]);
  });

  it('历史字符串条目按拼接文本反向解析', () => {
    expect(parseEntries('["发生的100% 李执行","手工条目"]')).toEqual([
      { text: '发生的', progress: '100', person: '李执行' },
      { text: '手工条目', progress: '', person: '' },
    ]);
  });

  it('字符串与对象混合数组兼容', () => {
    expect(parseEntries('[{"text":"a"},"b100% 张三"]')).toEqual([
      { text: 'a', progress: '', person: '' },
      { text: 'b', progress: '100', person: '张三' },
    ]);
  });

  it('null/空串/非法 JSON/非数组回退空数组', () => {
    expect(parseEntries(undefined)).toEqual([]);
    expect(parseEntries('')).toEqual([]);
    expect(parseEntries('not-json')).toEqual([]);
    expect(parseEntries('{"a":1}')).toEqual([]);
  });
});

describe('entryToParts / toEntryPayload', () => {
  it('结构化条目转可编辑三段', () => {
    expect(entryToParts({ text: '联调', progress: 60, person: '李执行' })).toEqual({
      text: '联调',
      progress: '60',
      person: '李执行',
    });
    expect(entryToParts({ text: 'a', progress: null, person: null })).toEqual({ text: 'a', progress: '', person: '' });
  });

  it('三段转入库结构：空进度/人员归 null', () => {
    expect(toEntryPayload({ text: ' 联调 ', progress: ' 60 ', person: ' 李执行 ' })).toEqual({
      text: '联调',
      progress: 60,
      person: '李执行',
    });
    expect(toEntryPayload({ text: '手工条目', progress: '', person: '' })).toEqual({
      text: '手工条目',
      progress: null,
      person: null,
    });
  });

  it('非法进度归 null', () => {
    expect(toEntryPayload({ text: 'a', progress: 'abc', person: '' }).progress).toBeNull();
  });
});

describe('currentWeekRange', () => {
  it('周三 → 本周一 ~ 本周日', () => {
    expect(currentWeekRange(new Date(2026, 9, 14))).toEqual({ start: '2026-10-12', end: '2026-10-18' });
  });

  it('周一当天 → 本周自身', () => {
    expect(currentWeekRange(new Date(2026, 9, 12))).toEqual({ start: '2026-10-12', end: '2026-10-18' });
  });

  it('周日 → 视为本周最后一天（周一为每周首日）', () => {
    expect(currentWeekRange(new Date(2026, 9, 18))).toEqual({ start: '2026-10-12', end: '2026-10-18' });
  });

  it('跨年周日 → 次日为次年周一', () => {
    // 2027-01-03 为周日，本周为 2026-12-28 ~ 2027-01-03
    expect(currentWeekRange(new Date(2027, 0, 3))).toEqual({ start: '2026-12-28', end: '2027-01-03' });
  });
});

describe('条目增删改', () => {
  it('addItem 追加条目（调用方给定占位内容）', () => {
    expect(addItem(['a'], '')).toEqual(['a', '']);
    expect(addItem(['a'], 'b')).toEqual(['a', 'b']);
    expect(addItem([{ text: 'a', progress: '', person: '' }], { text: '', progress: '', person: '' })).toEqual([
      { text: 'a', progress: '', person: '' },
      { text: '', progress: '', person: '' },
    ]);
  });

  it('removeItemAt 删除指定位置，越界安全', () => {
    expect(removeItemAt(['a', 'b', 'c'], 1)).toEqual(['a', 'c']);
    expect(removeItemAt(['a'], 5)).toEqual(['a']);
    expect(removeItemAt([], 0)).toEqual([]);
  });

  it('updateItemAt 修改指定位置，越界安全', () => {
    expect(updateItemAt(['a', 'b'], 1, 'x')).toEqual(['a', 'x']);
    expect(updateItemAt(['a'], 3, 'x')).toEqual(['a']);
  });

  it('不可变：不改原数组', () => {
    const source = ['a', 'b'];
    removeItemAt(source, 0);
    updateItemAt(source, 0, 'x');
    expect(source).toEqual(['a', 'b']);
  });
});

describe('isDuplicatePeriod', () => {
  const reports = [
    { projectId: 'p1', periodStart: '2026-10-12', periodEnd: '2026-10-18' },
    { projectId: 'p2', periodStart: '2026-10-05', periodEnd: '2026-10-11' },
  ];

  it('同项目同起止命中', () => {
    expect(isDuplicatePeriod(reports, 'p1', '2026-10-12', '2026-10-18')).toBe(true);
  });

  it('起止不同或项目不同不命中', () => {
    expect(isDuplicatePeriod(reports, 'p1', '2026-10-05', '2026-10-11')).toBe(false);
    expect(isDuplicatePeriod(reports, 'p2', '2026-10-12', '2026-10-18')).toBe(false);
    expect(isDuplicatePeriod(reports, 'p3', '2026-10-12', '2026-10-18')).toBe(false);
  });
});

describe('parseItemLine', () => {
  it('聚合格式「说明 进度% 人员」拆三段', () => {
    expect(parseItemLine('发生的100% 李执行')).toEqual({ text: '发生的', progress: '100', person: '李执行' });
    expect(parseItemLine('接口联调0% 未分配')).toEqual({ text: '接口联调', progress: '0', person: '未分配' });
  });

  it('说明含空格或百分号时从行尾解析不受干扰', () => {
    expect(parseItemLine('修复 登录 bug100% 李执行')).toEqual({
      text: '修复 登录 bug',
      progress: '100',
      person: '李执行',
    });
    expect(parseItemLine('完成50%的工作100% 李四')).toEqual({ text: '完成50%的工作', progress: '100', person: '李四' });
    expect(parseItemLine('任务3 100% 王五')).toEqual({ text: '任务3', progress: '100', person: '王五' });
  });

  it('无进度无人员整行归说明', () => {
    expect(parseItemLine('系统外专项工作')).toEqual({ text: '系统外专项工作', progress: '', person: '' });
  });

  it('仅说明与人员（无进度）', () => {
    expect(parseItemLine('组织评审 赵观察')).toEqual({ text: '组织评审', progress: '', person: '赵观察' });
  });
});

describe('composeItemLine', () => {
  it('三段齐全拼接为聚合格式', () => {
    expect(composeItemLine({ text: '发生的', progress: '100', person: '李执行' })).toBe('发生的100% 李执行');
  });

  it('缺进度/缺人员按现有段拼接', () => {
    expect(composeItemLine({ text: '联调', progress: '60', person: '' })).toBe('联调60%');
    expect(composeItemLine({ text: '组织评审', progress: '', person: '赵观察' })).toBe('组织评审 赵观察');
    expect(composeItemLine({ text: '手工条目', progress: '', person: '' })).toBe('手工条目');
  });

  it('与 parseItemLine 互逆（聚合格式往返无损）', () => {
    const line = '专利修改与提交100% 李执行';
    expect(composeItemLine(parseItemLine(line))).toBe(line);
  });

  it('各段去首尾空格', () => {
    expect(composeItemLine({ text: ' 联调 ', progress: ' 60 ', person: ' 李执行 ' })).toBe('联调60% 李执行');
  });
});

describe('moveItemAt', () => {
  it('向后/向前移动', () => {
    expect(moveItemAt(['a', 'b', 'c'], 0, 2)).toEqual(['b', 'c', 'a']);
    expect(moveItemAt(['a', 'b', 'c'], 2, 0)).toEqual(['c', 'a', 'b']);
  });

  it('原位或越界返回原数组', () => {
    const source = ['a', 'b'];
    expect(moveItemAt(source, 1, 1)).toBe(source);
    expect(moveItemAt(source, -1, 1)).toBe(source);
    expect(moveItemAt(source, 0, 5)).toBe(source);
  });

  it('不可变：不改原数组', () => {
    const source = ['a', 'b', 'c'];
    moveItemAt(source, 0, 2);
    expect(source).toEqual(['a', 'b', 'c']);
  });
});

describe('toEditableEntries / nextEntryKey', () => {
  it('保序且分配唯一 key', () => {
    const entries = toEditableEntries([
      { text: 'a', progress: '1', person: '李执行' },
      { text: 'b', progress: '', person: '' },
    ]);
    expect(entries.map((e) => e.parts.text)).toEqual(['a', 'b']);
    expect(new Set(entries.map((e) => e.key)).size).toBe(2);
  });

  it('空数组返回空数组', () => {
    expect(toEditableEntries([])).toEqual([]);
  });

  it('nextEntryKey 连续调用不重复', () => {
    expect(nextEntryKey()).not.toBe(nextEntryKey());
  });
});

describe('formatReportTime', () => {
  it('ISO 串转 yyyy-MM-dd HH:mm:ss 并截断小数秒', () => {
    expect(formatReportTime('2026-10-14T09:05:03')).toBe('2026-10-14 09:05:03');
    expect(formatReportTime('2026-10-14T09:05:03.123')).toBe('2026-10-14 09:05:03');
  });

  it('空/过短返回空串', () => {
    expect(formatReportTime(undefined)).toBe('');
    expect(formatReportTime('')).toBe('');
    expect(formatReportTime('2026-10-14')).toBe('');
  });
});

describe('projectColorClass', () => {
  it('同 id 两次取色一致', () => {
    expect(projectColorClass('p1')).toBe(projectColorClass('p1'));
  });

  it('结果属于色板，空串不抛错', () => {
    expect(PROJECT_TAG_COLORS).toContain(projectColorClass('abc'));
    expect(PROJECT_TAG_COLORS).toContain(projectColorClass(''));
  });
});

describe('groupReportsByProject', () => {
  const projects = [
    { projectId: 'p2', projectName: '山东' },
    { projectId: 'p1', projectName: '产品' },
    { projectId: 'p3', projectName: '烟台' },
  ];

  it('列 = 全部参与项目（含空列），列序与项目名 zh 排序一致', () => {
    const groups = groupReportsByProject([], projects);
    expect(groups.map((g) => g.projectId)).toEqual(
      [...projects].sort((a, b) => a.projectName.localeCompare(b.projectName, 'zh-Hans-CN')).map((p) => p.projectId),
    );
    expect(groups.map((g) => g.reports.length)).toEqual([0, 0, 0]);
  });

  it('同名项目按 projectId 次序', () => {
    const groups = groupReportsByProject([], [
      { projectId: 'b1', projectName: '同名' },
      { projectId: 'a2', projectName: '同名' },
    ]);
    expect(groups.map((g) => g.projectId)).toEqual(['a2', 'b1']);
  });

  it('周报归入对应项目列，孤儿周报追加末尾', () => {
    const groups = groupReportsByProject(
      [report('r1', 'px', '2026-10-05', '2026-10-11', '孤儿项目'), report('r2', 'p1', '2026-10-12', '2026-10-18')],
      projects,
    );
    expect(groups[groups.length - 1].projectId).toBe('px');
    expect(groups[groups.length - 1].projectName).toBe('孤儿项目');
    expect(groups[groups.length - 1].reports).toHaveLength(1);
    const p1 = groups.find((g) => g.projectId === 'p1');
    expect(p1?.reports.map((r) => r.id)).toEqual(['r2']);
  });

  it('列内按周期倒序，新的在上', () => {
    const groups = groupReportsByProject(
      [
        report('r1', 'p1', '2026-09-28', '2026-10-04'),
        report('r2', 'p1', '2026-10-12', '2026-10-18'),
        report('r3', 'p1', '2026-10-05', '2026-10-11'),
      ],
      projects,
    );
    const p1 = groups.find((g) => g.projectId === 'p1');
    expect(p1?.reports.map((r) => r.id)).toEqual(['r2', 'r3', 'r1']);
  });

  it('同周期按创建时间倒序兜底', () => {
    const earlier = { ...report('ra', 'p1', '2026-10-12', '2026-10-18'), createTime: '2026-10-14T09:00:00' };
    const later = { ...report('rb', 'p1', '2026-10-12', '2026-10-18'), createTime: '2026-10-15T09:00:00' };
    const groups = groupReportsByProject([earlier, later], projects);
    const p1 = groups.find((g) => g.projectId === 'p1');
    expect(p1?.reports.map((r) => r.id)).toEqual(['rb', 'ra']);
  });

  it('空入参返回空数组', () => {
    expect(groupReportsByProject([], [])).toEqual([]);
  });

  it('不可变：不修改入参数组', () => {
    const input = [report('r1', 'p1', '2026-10-12', '2026-10-18')];
    groupReportsByProject(input, projects);
    expect(input).toEqual([report('r1', 'p1', '2026-10-12', '2026-10-18')]);
  });
});
