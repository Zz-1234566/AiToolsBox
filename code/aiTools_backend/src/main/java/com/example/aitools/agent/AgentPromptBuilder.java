package com.example.aitools.agent;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 智能体提示词构造：system 提示词（人设 + 协议 + 工具清单）+ 附件说明。
 *
 * <p><b>协议为什么要写死这么啰嗦</b>：本项目用「文本 JSON」而不是原生 function calling
 * （理由见设计文档 §3.2），代价是模型可能不按格式输出 ——
 * 于是提示词里必须给出：格式、两个示例、字段说明、以及「只输出 JSON」的硬约束。
 * 解析器的容错（{@link AgentActionParser}）是第二道防线，提示词是第一道。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AgentPromptBuilder {

    /**
     * 构造 system 提示词。
     *
     * @param tools      可用工具（来自 DB）
     * @param attachments 本次运行的附件（可能是空列表）
     */
    public String buildSystemPrompt(List<AgentToolCatalog.AgentTool> tools,
                                     List<AgentAttachment> attachments) {
        return buildSystemPrompt(tools, attachments, null);
    }

    /**
     * 构造 system 提示词（带用户画像）。
     *
     * @param profileText 用户画像摘要，为空则不注入
     */
    public String buildSystemPrompt(List<AgentToolCatalog.AgentTool> tools,
                                     List<AgentAttachment> attachments,
                                     String profileText) {
        StringBuilder sb = new StringBuilder();
        if (profileText != null && !profileText.isBlank()) {
            sb.append("【用户长期偏好（仅作表达风格参考，与本会话内容无关）】\n");
            sb.append("注意：以下偏好不代表本会话中出现过任何事实；不得据此猜测或复述历史对话内容。\n");
            sb.append(profileText.trim());
            sb.append("\n\n");
        }
        sb.append("""
                你是「智汇工具箱」的智能体。用户给你一个目标，你负责拆解步骤、挑选工具、产出最终答案。

                【最重要的输出规则】
                你每一轮回复**只能输出一个 JSON 对象**，不要输出任何其它文字、不要用 Markdown 代码块包裹。
                JSON 对象的 action 字段有两种取值：

                1) 需要调用工具时：
                {"thought":"你的思考（简短说明为什么要调这个工具）","action":"tool_call","tool":"工具编码","args":{"参数名":"参数值"}}

                2) 已经能给出最终答案时：
                {"thought":"你的思考（简短说明为什么可以收尾了）","action":"final","output":"给用户的最终答案"}

                【规则】
                - 一次只调一个工具。工具执行完会把结果发回给你，你再决定下一步。
                - args 里只能出现下面「可用工具」里写明的参数名。
                - 需要文件时，用附件清单里的附件 ID（例如 "file_1"），不要自己编文件名。
                【看图 vs 改图（两者输入都是图片，靠意图区分）】
                - 用户问“这是什么/图中有什么/读一下图上的字/帮我分析这张图” → 用 image-understand（只回答，不改图）。
                - 用户要求“去掉背景/抠图/要透明底” → 用 image-bg-remover（output=image）。
                - 用户要求“换成红底/蓝底/白底/做证件照” → 用 id-photo-bg-change（output=image）。
                - 用户只发了图片、没说目的时，先问一句“你想识别内容，还是处理这张图（去背景/换底色）？”，不要擅自选。

                【参数缺失必须先问，不许猜】
                - 换底色但没说是红/蓝/白 → 先问“要红底、蓝底还是白底？”，问完再调用，不要默认白底。
                - 用户说“处理这张图”但没说要识别还是改图 → 先问，二选一。
                - 需要文件但附件清单为空 → 让用户先上传，不要硬调工具。
                - 已经有合理默认值的（去背景默认透明底）可以直接用，不必追问。
                - output=image 的工具会返回图片链接，你可以把它写进最终答案，但不要声称自己“看到了”处理后的图。

                - 你**无法直接看到图片内容**。用户让你看图（这是谁/画面里有什么/图里写了什么）时，
                  必须调用 image-understand 工具，args 形如 {"file":"file_1","question":"用户想知道的点"}。
                  不要仅因为看不到图就说“没有附件”——附件清单里有文件就必须先用工具读。
                  图片类附件一律用 image-understand，不要用 doc-to-text（它只支持文本类文档）。
                - 已经拿到需要的信息就直接给 final，不要反复调同一个工具。
                - 工具报错时，你会收到错误信息，可以换一种做法或直接说明为什么做不到。

                【可用工具】
                """);

        if (tools == null || tools.isEmpty()) {
            sb.append("（当前没有可用工具，请直接用 final 回答。）\n");
        } else {
            for (AgentToolCatalog.AgentTool t : tools) {
                sb.append("- ").append(t.getCode()).append("（").append(t.getName()).append("）：");
                sb.append(t.getDescription() == null || t.getDescription().isBlank()
                        ? "无说明" : t.getDescription());
                sb.append(" | 输入=").append(t.getInputType());
                sb.append(" 输出=").append(t.getOutputType() == null ? "text" : t.getOutputType());
                sb.append(" | 参数：").append(paramHint(t));
                sb.append('\n');
            }
        }

        sb.append('\n').append(describeAttachments(attachments));

        sb.append("""

                【示例 1：需要调用工具】
                {"thought":"用户上传的是录音，要先转成文字","action":"tool_call","tool":"audio-transcribe","args":{"file":"file_1"}}

                【示例 2：可以给最终答案了】
                {"thought":"文字已拿到，整理成纪要","action":"final","output":"（会议纪要正文）"}
                """);
        return sb.toString();
    }

    /**
     * 附件清单说明（告诉模型有哪些文件可用、ID 是什么）。
     */
    public String describeAttachments(List<AgentAttachment> attachments) {
        if (attachments == null || attachments.isEmpty()) {
            return "【当前附件】无。\n";
        }
        StringBuilder sb = new StringBuilder("【当前附件】\n");
        for (AgentAttachment a : attachments) {
            sb.append("- ").append(a.getId()).append("：").append(a.getFileName());
            if (a.getSize() != null && a.getSize() > 0) {
                sb.append("（").append(a.getSize() / 1024).append(" KB）");
            }
            sb.append('\n');
        }
        return sb.toString();
    }

    /**
     * 某个工具的参数提示（喂给模型的 args 说明）。
     */
    private String paramHint(AgentToolCatalog.AgentTool t) {
        boolean needsFile = t.needsFile();
        boolean process = t.getPipeline().contains(AgentToolCatalog.STEP_PROCESS);
        if (needsFile && process) {
            return "{\"file\":\"附件ID\"}（先解析文件再做加工）";
        }
        if (needsFile) {
            return "{\"file\":\"附件ID\"}";
        }
        return "{\"text\":\"要处理的文字\"}";
    }
}
