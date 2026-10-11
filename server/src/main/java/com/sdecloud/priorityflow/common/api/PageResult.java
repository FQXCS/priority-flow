package com.sdecloud.priorityflow.common.api;

import com.baomidou.mybatisplus.core.metadata.IPage;
import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * 分页结果包装。
 *
 * @param <T> 数据类型
 * @author priority-flow
 */
@Data
public class PageResult<T> implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 总记录数 */
    private long total;

    /** 当前页码（1 起） */
    private long pageNum;

    /** 每页大小 */
    private long pageSize;

    /** 当前页数据 */
    private List<T> list;

    /**
     * 从 MyBatis-Plus 分页对象构建
     */
    public static <T> PageResult<T> of(IPage<T> page) {
        PageResult<T> result = new PageResult<>();
        result.setTotal(page.getTotal());
        result.setPageNum(page.getCurrent());
        result.setPageSize(page.getSize());
        result.setList(page.getRecords());
        return result;
    }
}
