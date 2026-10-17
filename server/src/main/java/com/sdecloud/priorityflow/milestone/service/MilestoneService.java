package com.sdecloud.priorityflow.milestone.service;

import com.sdecloud.priorityflow.milestone.bean.dto.MilestoneCreateDto;
import com.sdecloud.priorityflow.milestone.bean.dto.MilestoneUpdateDto;
import com.sdecloud.priorityflow.milestone.bean.vo.MilestoneVo;

import java.util.List;

/**
 * 里程碑服务：项目内阶段目标的增删改查。
 *
 * @author priority-flow
 */
public interface MilestoneService {

    /**
     * 项目内里程碑列表（预计完成日期升序 + 未规划日期）
     */
    List<MilestoneVo> listByProject(String projectId);

    /**
     * 创建里程碑
     */
    MilestoneVo create(String projectId, MilestoneCreateDto dto);

    /**
     * 编辑里程碑（全量覆盖，日期可置空）
     */
    MilestoneVo update(String projectId, String milestoneId, MilestoneUpdateDto dto);

    /**
     * 删除里程碑（逻辑删）
     */
    void delete(String projectId, String milestoneId);
}
