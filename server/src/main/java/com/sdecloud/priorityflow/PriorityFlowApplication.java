package com.sdecloud.priorityflow;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 研发任务优先级看板服务端启动类。
 * <p>模块化单体：auth / project / task / collab / notification / system / common 七个业务域。</p>
 *
 * @author priority-flow
 */
@EnableScheduling
@SpringBootApplication
@MapperScan("com.sdecloud.priorityflow.**.persistence.mapper")
public class PriorityFlowApplication {

    public static void main(String[] args) {
        SpringApplication.run(PriorityFlowApplication.class, args);
    }
}
