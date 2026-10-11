package com.sdecloud.priorityflow.collab.bean.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 附件登记入参（预签名直传完成后调用）。
 *
 * @author priority-flow
 */
@Data
public class AttachmentRegisterDto {

    /** 文件名 */
    @NotBlank(message = "文件名不能为空")
    private String fileName;

    /** MinIO 对象键（presign 接口下发，防伪造） */
    @NotNull(message = "对象键不能为空")
    private String objectKey;

    /** 文件大小（字节） */
    @NotNull(message = "文件大小不能为空")
    private Long fileSize;

    /** 内容类型 */
    private String contentType;
}
