package com.sdecloud.priorityflow.milestone.persistence.repository;

import com.baomidou.mybatisplus.extension.service.IService;
import com.sdecloud.priorityflow.milestone.persistence.po.MilestoneDo;

import java.util.List;

/**
 * 里程碑仓库。
 *
 * @author priority-flow
 */
public interface MilestoneRepository extends IService<MilestoneDo> {

    /**
     * 项目内全部里程碑（预计完成日期升序，未规划日期随后，同序按创建时间升序）
     */
    List<MilestoneDo> listByProject(String projectId);
}
