package com.zora.agent.graph;

import com.zora.agent.event.AgentEvent;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.FluxSink;

import java.util.ArrayList;
import java.util.List;

/**
 * Supervisor Agent — 意图分类器（Phase 3.5）
 * <p>
 * 作为多 Agent 编排的"入口路由器"，分析用户消息并判断最合适的 Specialist Agent 来处理。
 * 使用 LLM 进行零样本分类（zero-shot classification），将用户需求分为 4 类：
 * </p>
 *
 * <h3>分类类别</h3>
 * <table>
 * <tr><th>分类</th><th>路由目标</th><th>触发条件</th></tr>
 * <tr><td>research</td><td>ResearchAgent</td><td>需要搜索互联网、查找实时信息、获取最新数据</td></tr>
 * <tr><td>math</td><td>MathAgent</td><td>需要数学计算、数值运算、公式求解</td></tr>
 * <tr><td>code</td><td>CodeAgent</td><td>需要编写/执行代码、调试程序、数据处理</td></tr>
 * <tr><td>general</td><td>直接回答</td><td>一般性问答、闲聊，不需要调用工具</td></tr>
 * </table>
 *
 * <h3>实现方式</h3>
 * <p>
 * 使用非流式 {@link ChatLanguageModel} 调用，传入专用分类 System Prompt + 用户消息。
 * LLM 只回复一个单词（分类标签），通过解析首行提取分类结果。
 * 如果 LLM 返回非预期值或调用失败，降级为 "general"（直接回答）。
 * </p>
 *
 * @see AgentNode
 * @see AgentState
 */
public class SupervisorAgent implements AgentNode {

    private static final Logger log = LoggerFactory.getLogger(SupervisorAgent.class);

    /** 意图分类专用 System Prompt */
    private static final String CLASSIFICATION_PROMPT =
            "你是一个智能任务分类器。分析用户的消息，判断最适合由哪个专家处理：\n\n"
            + "## 专家类型\n"
            + "- **research**: 需要搜索互联网信息、查找最新数据、获取实时资讯、查询事实\n"
            + "- **math**: 需要进行数学计算、数值运算、公式求解、统计分析\n"
            + "- **code**: 需要编写代码、执行程序、调试错误、处理数据转换\n"
            + "- **general**: 一般性问答、闲聊、概念解释，不需要调用任何工具\n\n"
            + "## 分类规则\n"
            + "1. 如果用户明确要求搜索、查询最新信息 → research\n"
            + "2. 如果用户要求计算、算数、求解数学问题 → math\n"
            + "3. 如果用户要求写代码、运行程序、代码调试 → code\n"
            + "4. 其他情况（解释概念、闲聊、建议类问题）→ general\n\n"
            + "## 输出格式\n"
            + "只回复一个单词：research、math、code 或 general。不要回复其他任何内容。";

    /** 非流式聊天模型（用于分类推理） */
    private final ChatModel chatModel;

    /**
     * 构造 SupervisorAgent
     *
     * @param chatModel 非流式聊天模型（从 AgentServiceImpl 注入）
     */
    public SupervisorAgent(ChatModel chatModel) {
        this.chatModel = chatModel;
    }

    /**
     * 执行意图分类
     * <p>
     * 将用户消息发送给 LLM 进行意图分类，结果写入 {@code state.intent}。
     * 同时通过 emitter 推送思考事件到前端。
     * </p>
     *
     * @param state   当前图状态（读取 userMessage，写入 intent）
     * @param emitter SSE 事件发射器
     * @return 分类标签字符串（research/math/code/general）
     */
    @Override
    public String execute(AgentState state, FluxSink<String> emitter) {
        log.info("SupervisorAgent: 开始意图分类...");

        // 推送思考事件到前端（标记为 supervisor agent）
        emitter.next(AgentEvent.thinking("Supervisor 正在分析任务意图...")
                .withField("agent", "supervisor").toJson());

        try {
            // 构建分类请求消息
            List<dev.langchain4j.data.message.ChatMessage> classifyMessages = new ArrayList<>();
            classifyMessages.add(dev.langchain4j.data.message.SystemMessage.from(CLASSIFICATION_PROMPT));
            classifyMessages.add(UserMessage.from(state.getUserMessage()));

            // 调用 LLM 进行分类（非流式）
            ChatResponse response = chatModel.chat(
                    ChatRequest.builder()
                            .messages(classifyMessages)
                            .build());

            String classification = response.aiMessage().text();
            if (classification == null || classification.isBlank()) {
                log.warn("SupervisorAgent: LLM 返回空分类，降级为 general");
                state.setIntent("general");
                return "general";
            }

            // 清理分类结果：取第一行、去除空白、转小写
            String cleaned = classification.trim().toLowerCase()
                    .replaceAll("[^a-z]", "");  // 去掉标点等非字母字符

            // 验证分类是否有效
            String intent;
            if (cleaned.contains("research")) {
                intent = "research";
            } else if (cleaned.contains("math")) {
                intent = "math";
            } else if (cleaned.contains("code")) {
                intent = "code";
            } else {
                intent = "general";
            }

            log.info("SupervisorAgent: 分类结果 = {}", intent);
            state.setIntent(intent);
            state.setActiveSpecialist(intent);

            // 推送分类结果到前端
            emitter.next(AgentEvent.thinking(
                    "任务分类: " + getIntentChineseName(intent) + " → "
                            + getRoutingDescription(intent))
                    .withField("agent", "supervisor").toJson());

            return intent;

        } catch (Exception e) {
            log.error("SupervisorAgent: 分类失败，降级为 general — {}", e.getMessage());
            emitter.next(AgentEvent.thinking("意图分类失败，降级为通用模式")
                    .withField("agent", "supervisor").toJson());
            state.setIntent("general");
            return "general";
        }
    }

    @Override
    public String getName() {
        return "supervisor";
    }

    // ==================== 辅助方法 ====================

    /**
     * 获取意图的中文名称
     */
    private String getIntentChineseName(String intent) {
        return switch (intent) {
            case "research" -> "研究搜索";
            case "math" -> "数学计算";
            case "code" -> "代码执行";
            default -> "通用问答";
        };
    }

    /**
     * 获取路由描述
     */
    private String getRoutingDescription(String intent) {
        return switch (intent) {
            case "research" -> "启动 ResearchAgent 进行网络搜索";
            case "math" -> "启动 MathAgent 进行数学计算";
            case "code" -> "启动 CodeAgent 执行代码";
            default -> "直接回答用户问题";
        };
    }
}
