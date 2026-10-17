package com.sdecloud.priorityflow.milestone.service.impl;

import com.sdecloud.priorityflow.auth.persistence.po.SysUserDo;
import com.sdecloud.priorityflow.auth.persistence.repository.SysUserRepository;
import com.sdecloud.priorityflow.common.api.ResultCode;
import com.sdecloud.priorityflow.common.event.MilestoneChangedEvent;
import com.sdecloud.priorityflow.common.exception.BusinessException;
import com.sdecloud.priorityflow.common.security.UserContext;
import com.sdecloud.priorityflow.milestone.bean.dto.MilestoneCreateDto;
import com.sdecloud.priorityflow.milestone.bean.dto.MilestoneUpdateDto;
import com.sdecloud.priorityflow.milestone.bean.vo.MilestoneVo;
import com.sdecloud.priorityflow.milestone.persistence.po.MilestoneDo;
import com.sdecloud.priorityflow.milestone.persistence.repository.MilestoneRepository;
import com.sdecloud.priorityflow.milestone.service.MilestoneService;
import com.sdecloud.priorityflow.project.persistence.repository.ProjectMemberRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 里程碑服务实现：校验归属项目与负责人成员身份，写后发领域事件驱动 WS 推送。
 *
 * @author priority-flow
 */
@Service
@RequiredArgsConstructor
public class MilestoneServiceImpl implements MilestoneService {

    private final MilestoneRepository milestoneRepository;
    private final ProjectMemberRepository projectMemberRepository;
    private final SysUserRepository sysUserRepository;
    private final ApplicationEventPublisher eventPublisher;

    @Override
    public List<MilestoneVo> listByProject(String projectId) {
        List<MilestoneDo> list = milestoneRepository.listByProject(projectId);
        Map<String, String> ownerNames = loadOwnerNames(list);
        return list.stream().map(item -> toVo(item, ownerNames)).toList();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public MilestoneVo create(String projectId, MilestoneCreateDto dto) {
        validateOwner(projectId, dto.getOwnerId());
        MilestoneDo entity = new MilestoneDo();
        entity.setProjectId(projectId);
        applyFields(entity, dto.getName(), dto.getOwnerId(), dto.getExpectedDate());
        milestoneRepository.save(entity);
        publish(projectId, entity.getId(), "MILESTONE_CREATED");
        return toVo(entity, loadOwnerNames(List.of(entity)));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public MilestoneVo update(String projectId, String milestoneId, MilestoneUpdateDto dto) {
        MilestoneDo entity = requireMilestone(projectId, milestoneId);
        validateOwner(projectId, dto.getOwnerId());
        applyFields(entity, dto.getName(), dto.getOwnerId(), dto.getExpectedDate());
        if (!milestoneRepository.updateById(entity)) {
            throw new BusinessException(ResultCode.MILESTONE_NOT_FOUND);
        }
        publish(projectId, milestoneId, "MILESTONE_UPDATED");
        return toVo(entity, loadOwnerNames(List.of(entity)));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(String projectId, String milestoneId) {
        MilestoneDo entity = requireMilestone(projectId, milestoneId);
        milestoneRepository.removeById(milestoneId);
        publish(projectId, entity.getId(), "MILESTONE_DELETED");
    }

    /**
     * 查询里程碑并校验归属项目（防止用其他项目的 milestoneId 越权操作）
     */
    private MilestoneDo requireMilestone(String projectId, String milestoneId) {
        MilestoneDo entity = milestoneRepository.getById(milestoneId);
        if (entity == null || !entity.getProjectId().equals(projectId)) {
            throw new BusinessException(ResultCode.MILESTONE_NOT_FOUND);
        }
        return entity;
    }

    /**
     * 负责人必须为项目成员（空视为未指定）
     */
    private void validateOwner(String projectId, String ownerId) {
        if (!StringUtils.hasText(ownerId)) {
            return;
        }
        if (projectMemberRepository.findMember(projectId, ownerId) == null) {
            throw new BusinessException(ResultCode.BAD_PARAM, "负责人必须是项目成员");
        }
    }

    /**
     * 全量覆盖业务字段（名称去首尾空格，负责人空串归一为 null）
     */
    private void applyFields(MilestoneDo entity, String name, String ownerId, java.time.LocalDate expectedDate) {
        entity.setName(name.trim());
        entity.setOwnerId(StringUtils.hasText(ownerId) ? ownerId : null);
        entity.setExpectedDate(expectedDate);
    }

    /**
     * 批量翻译负责人显示名
     */
    private Map<String, String> loadOwnerNames(List<MilestoneDo> list) {
        Set<String> ownerIds = list.stream()
                .map(MilestoneDo::getOwnerId)
                .filter(StringUtils::hasText)
                .collect(Collectors.toSet());
        if (ownerIds.isEmpty()) {
            return Map.of();
        }
        return sysUserRepository.listByIds(ownerIds).stream()
                .collect(Collectors.toMap(SysUserDo::getId, SysUserDo::getDisplayName));
    }

    private MilestoneVo toVo(MilestoneDo entity, Map<String, String> ownerNames) {
        MilestoneVo vo = new MilestoneVo();
        BeanUtils.copyProperties(entity, vo);
        if (StringUtils.hasText(entity.getOwnerId())) {
            vo.setOwnerName(ownerNames.get(entity.getOwnerId()));
        }
        return vo;
    }

    private void publish(String projectId, String milestoneId, String changeType) {
        eventPublisher.publishEvent(MilestoneChangedEvent.of(
                this, milestoneId, projectId, changeType, UserContext.require().getUserId()));
    }
}
