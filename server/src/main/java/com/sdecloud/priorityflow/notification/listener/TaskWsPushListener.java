package com.sdecloud.priorityflow.notification.listener;

import com.sdecloud.priorityflow.common.event.TaskChangedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * WS 推送监听器：看板级任务变更推送到 /topic/project.{projectId}。
 * 推送消息体只含 taskId + changeType + operatorId，客户端收到后重新拉取看板；
 * 本机触发的变更按 operatorId 去重回显。
 *
 * @author priority-flow
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TaskWsPushListener {

    private final SimpMessagingTemplate messagingTemplate;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onTaskChanged(TaskChangedEvent event) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("taskId", event.getTaskId());
        payload.put("changeType", event.getChangeType());
        payload.put("operatorId", event.getOperatorId());
        try {
            messagingTemplate.convertAndSend("/topic/project." + event.getProjectId(), payload);
        } catch (Exception e) {
            log.warn("看板 WS 推送失败：projectId={}", event.getProjectId());
        }
    }
}
