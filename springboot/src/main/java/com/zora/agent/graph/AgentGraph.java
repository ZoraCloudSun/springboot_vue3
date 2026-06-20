package com.zora.agent.graph;

import com.zora.agent.event.AgentEvent;
import com.zora.agent.tool.CodeExecutionTool;
import com.zora.agent.tool.MathTool;
import com.zora.agent.tool.WebSearchTool;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.FluxSink;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * Agent 图编排器（Phase 3.5 — 多 Agent 编排核心）
 * <p>
 * 实现 Supervisor → Specialist → Summarizer 的多 Agent 协作流程。
 * 所有 Agent 节点通过共享的 {@link AgentState} "黑板"进行通信。
 * </p>
 *
 * <h3>编排流程图</h3>
 * <pre>{@code
 * User Message
 *     │
 *     ▼
 * SupervisorAgent.classify()
 *     │
 *     ├── general ──────────► 直接流式回答
 *     │
 *     ├── research ─► ResearchAgent ─► 搜索结果
 *     ├── math     ─► MathAgent     ─► 计算结果
 *     └── code     ─► CodeAgent     ─► 执行结果
 *     │                                    │
 *     └── (最多 3 次 specialist 调用) ──────┘
 *                      │
 *                      ▼
 *              Summarizer.aggregate()
 *                      │
 *                      ▼
 *              SSE 流式输出最终回答
 * }</pre>
 *
 * <h3>安全限制</h3>
 * <ul>
 * <li>最多调用 {@code maxSpecialistCalls} 次 Specialist Agent（默认 3）</li>
 * <li>每次 Specialist 调用独立执行，互不干扰</li>
 * <li>LLM 调用失败时降级为通用模式</li>
 * </ul>
 *
 * <h3>使用方式</h3>
 * <pre>{@code
 * AgentGraph graph = new AgentGraph(chatModel, streamingModel, webSearchTool, mathTool, codeTool, 3);
 * String finalAnswer = graph.execute(userMessage, messages, emitter);
 * }</pre>
 *
 * @see SupervisorAgent
 * @see ResearchAgent
 * @see MathAgent
 * @see CodeAgent
 * @see AgentState
 */
public class AgentGraph {

    private static final Logger log = LoggerFactory.getLogger(AgentGraph.class);

    /** 非流式聊天模型（用于 Agent 推理和工具调用） */
    private final ChatModel chatModel;

    /** 流式聊天模型（用于最终回答的 SSE 流式输出） */
    private final StreamingChatModel streamingModel;

    /** Supervisor 意图分类器 */
    private final SupervisorAgent supervisor;

    /** 研究搜索专家 */
    private final ResearchAgent researchAgent;

    /** 数学计算专家 */
    private final MathAgent mathAgent;

    /** 代码执行专家（可能为 null 如果 CodeExecutionTool 未配置） */
    private final CodeAgent codeAgent;

    /** 单次对话最多调用的 Specialist Agent 次数 */
    private final int maxSpecialistCalls;

    /** Summarizer 的 System Prompt */
    private static final String SUMMARIZER_PROMPT =
            "你是一个专业的 AI 助手，负责整合多个专家的分析结果，给用户一个完整、连贯的最终回答。\n\n"
            + "## 任务\n"
            + "基于以下专家的分析结果和用户的原始问题，生成一个全面的回答。\n\n"
            + "## 要求\n"
            + "1. 整合所有专家的发现，避免简单罗列\n"
            + "2. 保持回答的连贯性和逻辑性\n"
            + "3. 用中文回答，语气专业友好\n"
            + "4. 如果专家结果之间存在矛盾，指出并解释\n"
            + "5. 在回答末尾可以给出进一步建议\n\n"
            + "## 安全规则\n"
            + "- 不要编造专家没有提供的信息\n"
            + "- 如果某个专家的结果不完整，诚实说明";

    /**
     * 构造 AgentGraph（完整参数）
     *
     * @param chatModel           非流式聊天模型（用于 Agent 推理）
     * @param streamingModel      流式聊天模型（用于最终回答输出）
     * @param webSearchTool       网页搜索工具（可能为 null）
     * @param mathTool            数学计算工具（可能为 null）
     * @param codeExecutionTool   代码执行工具（可能为 null）
     * @param maxSpecialistCalls  最多调用 Specialist 的次数
     */
    public AgentGraph(ChatModel chatModel,
                      StreamingChatModel streamingModel,
                      WebSearchTool webSearchTool,
                      MathTool mathTool,
                      CodeExecutionTool codeExecutionTool,
                      int maxSpecialistCalls) {
        this.chatModel = chatModel;
        this.streamingModel = streamingModel;
        this.supervisor = new SupervisorAgent(chatModel);
        this.researchAgent = webSearchTool != null
                ? new ResearchAgent(chatModel, webSearchTool) : null;
        this.mathAgent = mathTool != null
                ? new MathAgent(chatModel, mathTool) : null;
        this.codeAgent = codeExecutionTool != null
                ? new CodeAgent(chatModel, codeExecutionTool) : null;
        this.maxSpecialistCalls = Math.max(1, Math.min(maxSpecialistCalls, 5));
    }

    /**
     * 执行多 Agent 编排流程（核心方法）
     * <p>
     * 完整流程：
     * <ol>
     * <li>创建 AgentState 并初始化</li>
     * <li>SupervisorAgent 进行意图分类（循环，最多 maxSpecialistCalls 次）</li>
     * <li>根据分类结果路由到对应 Specialist Agent</li>
     * <li>所有 Specialist 执行完毕后，Summarizer 聚合生成最终回答</li>
     * <li>通过流式模型逐 token 推送最终回答到 SSE</li>
     * </ol>
     * </p>
     *
     * @param userMessage 用户原始消息
     * @param messages    LangChain4j 消息列表（包含系统提示词和历史）
     * @param emitter     SSE 事件发射器
     * @return 最终的完整回答文本
     */
    public String execute(String userMessage,
                          List<dev.langchain4j.data.message.ChatMessage> messages,
                          FluxSink<String> emitter) {
        log.info("AgentGraph: 开始多 Agent 编排，maxSpecialistCalls={}", maxSpecialistCalls);

        // 1. 创建 AgentState
        AgentState state = new AgentState();
        state.setUserMessage(userMessage);
        state.setMessages(messages);
        state.setMaxSpecialistCalls(maxSpecialistCalls);

        try {
            // 2. Specialist 调用循环
            while (state.canCallSpecialist()) {
                // 2a. Supervisor 分类
                String intent = supervisor.execute(state, emitter);

                // 2b. 通用问答 → 跳出循环，直接由 Summarizer 处理
                if ("general".equals(intent)) {
                    log.info("AgentGraph: 意图为 general，跳出 Specialist 循环");
                    break;
                }

                // 2c. 路由到对应 Specialist
                AgentNode specialist = getSpecialist(intent);
                if (specialist == null) {
                    log.warn("AgentGraph: 意图 {} 对应的 Specialist 不可用，降级为 general", intent);
                    state.setIntent("general");
                    break;
                }

                log.info("AgentGraph: 路由到 {}", specialist.getName());
                state.setActiveSpecialist(intent);

                // 2d. 执行 Specialist
                String result = specialist.execute(state, emitter);
                log.info("AgentGraph: {} 执行完成，结果长度 {} 字符",
                        specialist.getName(), result != null ? result.length() : 0);

                // 如果 Specialist 返回 null 或空，可能是错误，跳出循环
                if (result == null || result.isBlank()) {
                    log.warn("AgentGraph: {} 返回空结果，跳出循环", specialist.getName());
                    break;
                }
            }

            // 3. Summarizer 聚合 + 流式输出最终回答
            if (state.getSpecialistCallCount() > 0) {
                // 有 Specialist 参与 → Summarizer 聚合专家结果
                log.info("AgentGraph: Summarizer 正在聚合 {} 个专家结果...",
                        state.getSpecialistCallCount());
                emitter.next(AgentEvent.thinking(
                        "正在整合 " + state.getSpecialistCallCount() + " 个专家的分析结果...")
                        .withField("agent", "summarizer").toJson());

                return summarizeWithSpecialists(state, emitter);

            } else {
                // 无 Specialist 参与 → 直接流式回答
                log.info("AgentGraph: 无 Specialist 参与，直接流式回答");
                emitter.next(AgentEvent.thinking("正在生成回答...")
                        .withField("agent", "summarizer").toJson());

                return directStreamAnswer(state, emitter);
            }

        } catch (Exception e) {
            log.error("AgentGraph: 编排流程异常 — {}", e.getMessage(), e);
            emitter.next(AgentEvent.error("多 Agent 编排异常: " + e.getMessage()).toJson());
            return "抱歉，多 Agent 协作过程中出现了异常。请尝试简化提问或关闭多 Agent 模式。";
        }
    }

    // ==================== Specialist 路由 ====================

    /**
     * 根据意图获取对应的 Specialist Agent
     *
     * @param intent 意图分类（research / math / code）
     * @return 对应的 Specialist Agent，如果不可用则返回 null
     */
    private AgentNode getSpecialist(String intent) {
        return switch (intent) {
            case "research" -> researchAgent;
            case "math" -> mathAgent;
            case "code" -> codeAgent;
            default -> null;
        };
    }

    // ==================== Summarizer ====================

    /**
     * 带 Specialist 结果的聚合回答
     * <p>
     * 将用户原始问题、各 Specialist 结果拼接后，
     * 通过流式模型逐 token 输出最终回答。
     * </p>
     */
    private String summarizeWithSpecialists(AgentState state, FluxSink<String> emitter) {
        try {
            // 构建 Summarizer 消息
            List<dev.langchain4j.data.message.ChatMessage> sumMessages = new ArrayList<>();
            sumMessages.add(SystemMessage.from(SUMMARIZER_PROMPT));

            // 用户原始问题
            sumMessages.add(UserMessage.from("用户原始问题：" + state.getUserMessage()));

            // 各 Specialist 结果
            sumMessages.add(UserMessage.from(
                    "专家分析结果：\n\n" + state.getResultsSummary()));

            sumMessages.add(UserMessage.from(
                    "请基于以上专家结果，给用户一个完整、连贯的最终回答。"));

            // 使用非流式模型生成完整聚合回答
            // 然后用逐字符推送模拟流式效果
            ChatResponse response = chatModel.chat(
                    ChatRequest.builder().messages(sumMessages).build());
            String finalAnswer = response.aiMessage().text();

            // 流式推送 token
            streamTokens(finalAnswer, emitter);

            return finalAnswer;

        } catch (Exception e) {
            log.error("AgentGraph: Summarizer 失败 — {}", e.getMessage(), e);
            String fallback = "根据专家分析，以下是整合的回答：\n\n" + state.getResultsSummary();
            streamTokens(fallback, emitter);
            return fallback;
        }
    }

    /**
     * 直接流式回答（无 Specialist 参与时使用）
     * <p>
     * 使用流式模型直接回答用户问题，逐 token 推送到 SSE。
     * </p>
     */
    private String directStreamAnswer(AgentState state, FluxSink<String> emitter) {
        try {
            // 构建消息（使用传入的 messages 作为上下文）
            List<dev.langchain4j.data.message.ChatMessage> answerMessages =
                    new ArrayList<>(state.getMessages());
            answerMessages.add(UserMessage.from(state.getUserMessage()));

            // 使用 CompletableFuture 等待流式完成
            CompletableFuture<String> future = new CompletableFuture<>();
            StringBuilder fullAnswer = new StringBuilder();

            streamingModel.chat(
                    ChatRequest.builder().messages(answerMessages).build(),
                    new StreamingChatResponseHandler() {
                        @Override
                        public void onPartialResponse(String token) {
                            fullAnswer.append(token);
                            emitter.next(AgentEvent.token(token).toJson());
                        }

                        @Override
                        public void onCompleteResponse(ChatResponse response) {
                            future.complete(fullAnswer.toString());
                        }

                        @Override
                        public void onError(Throwable error) {
                            log.error("AgentGraph: 流式回答出错 — {}", error.getMessage());
                            future.completeExceptionally(error);
                        }
                    });

            // 等待流式完成（超时 60 秒）
            return future.get(60, TimeUnit.SECONDS);

        } catch (Exception e) {
            log.error("AgentGraph: 直接回答失败 — {}", e.getMessage(), e);

            // 降级：使用非流式模型
            try {
                List<dev.langchain4j.data.message.ChatMessage> fallbackMessages =
                        new ArrayList<>(state.getMessages());
                fallbackMessages.add(UserMessage.from(state.getUserMessage()));

                ChatResponse response = chatModel.chat(
                        ChatRequest.builder().messages(fallbackMessages).build());
                String fallbackAnswer = response.aiMessage().text();
                streamTokens(fallbackAnswer, emitter);
                return fallbackAnswer;
            } catch (Exception e2) {
                String errorMsg = "抱歉，AI 服务暂时不可用。";
                emitter.next(AgentEvent.token(errorMsg).toJson());
                return errorMsg;
            }
        }
    }

    /**
     * 将文本逐字符推送到 SSE 流（模拟流式输出效果）
     * <p>
     * 每次推送约 3 个字符，中文友好。
     * </p>
     *
     * @param text    要推送的文本
     * @param emitter SSE 事件发射器
     */
    private void streamTokens(String text, FluxSink<String> emitter) {
        if (text == null || text.isEmpty()) return;

        int i = 0;
        while (i < text.length()) {
            int chunkSize = Math.min(3, text.length() - i);
            String chunk = text.substring(i, i + chunkSize);
            emitter.next(AgentEvent.token(chunk).toJson());
            i += chunkSize;
        }
    }
}
