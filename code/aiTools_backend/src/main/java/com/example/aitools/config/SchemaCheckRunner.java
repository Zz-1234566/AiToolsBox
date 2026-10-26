package com.example.aitools.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * **启动期 schema 自检**：检查代码用到的关键列在库里是否都存在。
 *
 * <p><b>为什么需要</b>：schema 变更（加列）跟着代码走，但**已部署的库不会自动变**。
 * 少一列时，MyBatis 会在**运行时**抛
 * {@code Unknown column 'xxx' in 'field list'} ——
 * 而且往往是在某个异步任务里抛（如批量任务落库），堆栈又长又绕，
 * 排查的人很难立刻反应过来是「库结构没跟上代码」。
 *
 * <p>本组件在应用启动时就把这类问题挑出来，**直接打印要执行哪个脚本**，
 * 避免「功能莫名其妙全挂了」这种最糟的体验。
 *
 * <p><b>不阻断启动</b>：schema 不匹配时打 ERROR 日志但仍让应用起来 ——
 * 有些列只影响个别功能，不该让整个服务起不来。
 * 但日志里的提示足够明确，照着执行脚本即可。
 */
@Slf4j
@Component
public class SchemaCheckRunner implements ApplicationRunner {

    /**
     * 必须存在的表 → 必须存在的列（逗号分隔）。
     * <p>只列「代码里 <b>直接写字段</b>的」关键列，避免把整张表都列进来。
     */
    private static final String[][] REQUIRED = {
            // 批量任务表：error_msg 是 BatchTaskRecoveryJob（僵尸回收）写失败原因用的列，
            // 缺它 → completeBatch / failRemaining 全部抛 SQL 错误 → 批量任务全线不可用
            {"sys_batch_task", "batch_id,user_id,tool_code,file_count,success_count,fail_count,"
                    + "processed_index,status,error_msg,result_summary,create_time,update_time,"
                    + "finished_at,dr"},
            // 工具表：工作流节点按 tool_code 找工具，输入/输出类型决定能否连线
            {"sys_aitools_tool", "tool_code,tool_type,tool_name,input_type,output_type,status,dr"},
            // 用户表：鉴权与角色
            {"sys_user", "id,account,password,username,avatar,role,status,dr"},
    };

    /** 缺列时提示执行哪个迁移脚本 */
    private static final String MIGRATION_HINT =
            "sql/v1_2026_10_26_batch_task_error_msg.sql（全新安装直接跑 sql/init.sql）";

    private final DataSource dataSource;

    public SchemaCheckRunner(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public void run(ApplicationArguments args) {
        List<String> problems = new ArrayList<>();
        try (Connection conn = dataSource.getConnection()) {
            for (String[] spec : REQUIRED) {
                String table = spec[0];
                List<String> missing = new ArrayList<>();
                for (String col : spec[1].split(",")) {
                    if (!columnExists(conn, table, col)) {
                        missing.add(col.trim());
                    }
                }
                if (!missing.isEmpty()) {
                    problems.add("表 " + table + " 缺列：" + String.join(", ", missing));
                }
            }
        } catch (Exception e) {
            // 连不上库不是 schema 问题（可能是 DB 还没起），交给 Spring 自己报错，别在这里添乱
            log.debug("[schema-check] 跳过 schema 自检（数据库不可用）: {}", e.getMessage());
            return;
        }
        if (problems.isEmpty()) {
            log.info("[schema-check] schema 自检通过（{} 张表）", REQUIRED.length);
            return;
        }
        log.error("""

                ==================== 数据库结构与代码不匹配 ====================
                以下问题会导致运行时抛 Unknown column：
                {}
                ----------------------------------------------------------------------------
                修复方式：执行版本化迁移脚本（与代码一起入库，随代码走）：
                  {}
                ===============================================================================
                """, String.join("\n  - ", problems), MIGRATION_HINT);
    }

    private boolean columnExists(Connection conn, String table, String column) throws Exception {
        String sql = "SELECT 1 FROM information_schema.COLUMNS "
                + "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = ? AND COLUMN_NAME = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, table);
            ps.setString(2, column);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }
}
