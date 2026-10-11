package com.sdecloud.priorityflow.system.bean.vo;

import com.sdecloud.priorityflow.auth.persistence.po.SysUserDo;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 系统用户 VO（继承 PO，密码哈希已在 PO 上禁止序列化）。
 *
 * @author priority-flow
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class SystemUserVo extends SysUserDo {

    private static final long serialVersionUID = 1L;
}
