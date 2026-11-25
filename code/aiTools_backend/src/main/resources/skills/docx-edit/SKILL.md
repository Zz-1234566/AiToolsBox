---
name: docx-edit
title: 修改已有文档
icon: icon.svg
version: 0.1.0
when: 用户上传了 docx 并要求「把某段改成…」「填上姓名/日期」「删掉某节」
tools: [doc-outline, doc-edit]
outputs: [docx]
---

# 修改已有文档

## 流程

### 第 1 步：先看结构
调用 `doc-outline` 拿段落索引与文本，表格结构。
**必须先看清楚原文怎么写的**，不要凭猜测替换。

### 第 2 步：输出编辑指令
```json
[{"op":"replace_text","find":"姓名：","with":"姓名：张三"},
 {"op":"fill_table","table":0,"row":1,"header":"金额","value":"380.00"}]
```

### 第 3 步：执行与校验
- 调用 `doc-edit`
- 编辑后重新解析，确认原文消失、新文出现
- 一处没替换成功就报错，不要静默产出

## 限制
- 只做「填写/替换/追加/删除」，不做整篇重写（那是 doc-generate 的活）
- 不能改页眉页脚里的图片
