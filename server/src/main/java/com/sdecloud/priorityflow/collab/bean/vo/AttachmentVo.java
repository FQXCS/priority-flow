package com.sdecloud.priorityflow.collab.bean.vo;

import com.sdecloud.priorityflow.collab.persistence.po.TaskAttachmentDo;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 附件 VO（继承 PO，附加下载预签名 URL 与上传人姓名）。
 *
 * @author priority-flow
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class AttachmentVo extends TaskAttachmentDo {

    private static final long serialVersionUID = 1L;

    /** 下载预签名 URL（短时效 15 分钟） */
    private String downloadUrl;

    /** 上传人姓名 */
    private String uploaderName;
}
