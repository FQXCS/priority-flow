package com.sdecloud.priorityflow.task.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 任务优先级枚举：urgent 值越大越紧急（队列不变式：任意 i&lt;j 不允许 priority[i] &gt; priority[j]）。
 *
 * @author priority-flow
 */
@Getter
@AllArgsConstructor
public enum PriorityEnum {

    /** 紧急 */
    URGENT("URGENT", 4, "紧急"),

    /** 高 */
    HIGH("HIGH", 3, "高"),

    /** 中 */
    MEDIUM("MEDIUM", 2, "中"),

    /** 低 */
    LOW("LOW", 1, "低");

    /** 数据库存储值 */
    @EnumValue
    private final String code;

    /** 紧急度（越大越紧急，用于不变式比较） */
    private final int urgency;

    /** 中文名称 */
    private final String label;

    /**
     * 是否比 another 更紧急
     */
    public boolean moreUrgentThan(PriorityEnum another) {
        return this.urgency > another.urgency;
    }
}
