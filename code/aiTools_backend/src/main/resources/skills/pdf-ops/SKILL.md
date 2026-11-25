---
name: pdf-ops
title: PDF 处理
icon: icon.svg
version: 0.1.0
when: 用户要「合并 PDF / 拆分 / 提取某几页 / PDF 转图片」
tools: [pdf-merge, pdf-split, pdf-to-image]
outputs: [pdf, png]
---

# PDF 处理

## 流程

### 合并
```
pdf-merge(files=[file_1, file_2]) → 一个 pdf
```
确认顺序：用户没说就按上传顺序，并在回复里说明。

### 拆分
```
pdf-split(file=file_1, ranges="1-3,4-8") → 多个 pdf
```
或按页拆成单页。

### 提取页面
```
pdf-split(file=file_1, ranges="2") → 第 2 页
```

### 转图片
```
pdf-to-image(file=file_1, format=png, dpi=150) → 每页一张图
```

## 校验
- 页数对不对（合并后 = 各文件页数之和）
- 拆分后总页数守恒
- 转图片张数 = 页数
