/**
 * 格式化 AI 生成的工作总结文本
 * 兜底：即使 AI 没输出换行，也在关键位置智能分段
 * @param {String} text
 * @returns {String}
 */
export const formatAiResult = (text) => {
  if (!text) return text

  let result = text

  // 1. 在完整大标题前补换行（避免"四、下一步计划"被拆成两行）
  //    完整标题放 alternation 最前面，正则从左到右匹配，优先整体匹配
  result = result.replace(
    /(一、已完成事项|二、进行中事项|三、遇到的问题|四、下一步计划|已完成事项|进行中事项|遇到的问题|下一步计划|一、|二、|三、|四、)/g,
    '\n$1'
  )

  // 2. 在 "数字." 前补换行（如 1. 2. 3.，避免和上一点挤一起）
  //    匹配 "1." 形式，前面不是行首/换行时补 \n
  result = result.replace(/([^\n])\s*(\d+\.)/g, '$1\n$2')

  // 3. 去掉开头多余换行，压缩连续多个换行为一个空行
  result = result.replace(/^\n+/, '')
  result = result.replace(/\n{3,}/g, '\n\n')

  return result
}
