package com.sdecloud.priorityflow.task.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 任务类型枚举。
 *
 * @author priority-flow
 */
@Getter
@AllArgsConstructor
public enum TaskTypeEnum {

    /** 需求 */
    REQUIREMENT("REQUIREMENT", "需求"),

    /** 开发 */
    DEVELOP("DEVELOP", "开发"),

    /** 测试 */
    TEST("TEST", "测试"),

    /** 缺陷 */
    BUG("BUG", "缺陷"),

    /** 设计 */
    DESIGN("DESIGN", "设计"),

    /** 运维 */
    OPS("OPS", "运维"),

    /** 其他 */
    OTHER("OTHER", "其他");

    /** 数据库存储值 */
    @EnumValue
    private final String code;

    /** 中文名称 */
    private final String label;
}
