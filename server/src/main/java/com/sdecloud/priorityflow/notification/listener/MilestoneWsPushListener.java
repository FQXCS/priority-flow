package com.sdecloud.priorityflow.notification.listener;

import com.sdecloud.priorityflow.common.event.MilestoneChangedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * WS 推送监听器：里程碑变更复用 /topic/project.{projectId} 主题推送。
 * 消息体只含 milestoneId + changeType + operatorId（与任务消息按字段名区分），
 * 客户端收到后重新拉取里程碑列表；本机触发的变更按 operatorId 去重回显。
 *
 * @author priority-flow
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MilestoneWsPushListener {

    private final SimpMessagingTemplate messagingTemplate;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onMilestoneChanged(MilestoneChangedEvent event) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("milestoneId", event.getMilestoneId());
        payload.put("changeType", event.getChangeType());
        payload.put("operatorId", event.getOperatorId());
        try {
            messagingTemplate.convertAndSend("/topic/project." + event.getProjectId(), payload);
        } catch (Exception e) {
            log.warn("里程碑 WS 推送失败：projectId={}", event.getProjectId());
        }
    }
}
