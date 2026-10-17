-- 任务进度百分比：0-100，仅完成动作置 100，手动设置上限 99
ALTER TABLE task_info ADD COLUMN progress int NOT NULL DEFAULT 0
    COMMENT '进度百分比(0-100,仅完成动作置100,手动设置上限99)' AFTER remaining_hours;

-- 存量已完成任务回填 100
UPDATE task_info SET progress = 100 WHERE task_status = 'COMPLETED';
