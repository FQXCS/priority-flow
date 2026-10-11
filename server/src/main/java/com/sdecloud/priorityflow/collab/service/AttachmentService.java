package com.sdecloud.priorityflow.collab.service;

import com.sdecloud.priorityflow.collab.bean.dto.AttachmentPresignDto;
import com.sdecloud.priorityflow.collab.bean.dto.AttachmentRegisterDto;
import com.sdecloud.priorityflow.collab.bean.vo.AttachmentVo;
import com.sdecloud.priorityflow.collab.bean.vo.PresignVo;

import java.util.List;

/**
 * 附件服务：MinIO 预签名直传（PUT 上传 / GET 下载，短时效 15 分钟）。
 *
 * @author priority-flow
 */
public interface AttachmentService {

    /**
     * 申请上传预签名 URL
     */
    PresignVo presignUpload(String taskId, AttachmentPresignDto dto);

    /**
     * 登记附件（直传完成后调用，校验 objectKey 归属）
     */
    AttachmentVo register(String taskId, AttachmentRegisterDto dto);

    /**
     * 附件列表（附下载预签名 URL）
     */
    List<AttachmentVo> listByTask(String taskId);
}
