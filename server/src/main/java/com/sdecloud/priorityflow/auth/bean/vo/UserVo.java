package com.sdecloud.priorityflow.auth.bean.vo;

import com.sdecloud.priorityflow.auth.persistence.po.SysUserDo;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 用户信息 VO（继承 PO，password_hash 已在 PO 上禁止序列化）。
 *
 * @author priority-flow
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class UserVo extends SysUserDo {

    private static final long serialVersionUID = 1L;
}
