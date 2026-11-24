package com.example.aitools.config;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.example.aitools.common.AgentRunStatusEnum;
import com.example.aitools.entity.AgentRun;
import com.example.aitools.mapper.AgentRunMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * 启动时收尾「永远运行中」的智能体运行记录。
 * <p>
 * 运行中记录只存在于内存（runningAgents），进程一旦重启或崩溃，
 * 数据库里的 status=1 就再也不会被推进 —— 用户看到的是永远转圈。
 * 这里在启动阶段统一标记为「失败（服务重启导致中断）」，让状态可信。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AgentRunRecovery implements ApplicationRunner {

    private final AgentRunMapper agentRunMapper;

    @Override
    public void run(ApplicationArguments args) {
        try {
            LambdaUpdateWrapper<AgentRun> w = new LambdaUpdateWrapper<>();
            w.eq(AgentRun::getStatus, AgentRunStatusEnum.RUNNING.getCode())
                    .set(AgentRun::getStatus, AgentRunStatusEnum.FAILED.getCode())
                    .set(AgentRun::getErrorMsg, "服务重启导致本次运行中断，请重新发送")
                    .set(AgentRun::getFinishedAt, LocalDateTime.now());
            int n = agentRunMapper.update(null, w);
            if (n > 0) {
                log.info("[AgentRunRecovery] 收尾 {} 条遗留「运行中」记录", n);
            }
        } catch (Exception e) {
            log.warn("[AgentRunRecovery] 收尾失败（不影响启动）: {}", e.getMessage());
        }
    }
}
