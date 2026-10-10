# 交接文档目录

## 用途

每次会话/轮次的工作产出，按日期归档。**随项目仓库一起拉取**。

## 文件命名规范

`YYYY-MM-DD-{简述}.md`

例如：
- 2026-10-08-交接报告.md
- 2026-10-09-开发交接README.md
- 2026-10-10-交接报告.md

## 写作纪律

- 每份交接报告应包含：日期、commit 清单、关键决策、待办、给下一位
- 300 行内优先（详尽用专属文档）
- 必填数据用 grep 拿，不瞎编
- 带日期 + 简短主题

## 关联

- `../ui-spec/prototype/`：UI 设计稿
- 仓库根 `README.md`：项目说明
- `../../code/AGENTS.md`：项目协作规范

## 最新状态（截至 2026-10-10）

- **远端 88% 准确** —— 已通过 `mvn compile` + `npm run build:h5` 验证
- **真实 bug 1**：application-dev.yml.example COS TTL 默认 300（应是 3600）
- **真实 bug 2**：application-dev.yml.example 缺 segmentation 配置块
- 见 `2026-10-10-远端clone核对报告.md`
