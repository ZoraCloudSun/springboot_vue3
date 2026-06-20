package com.zora.agent.graph;

import com.zora.agent.event.AgentEvent;
import com.zora.agent.tool.WebSearchTool;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.agent.tool.ToolSpecifications;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
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
 * ResearchAgent — 研究搜索专家（Phase 3.5）
 * <p>
 * 专注于互联网搜索和信息收集，使用 {@link WebSearchTool} 获取实时信息。
 * 在多 Agent 编排中，当 Supervisor 判断用户需要搜索最新信息时被调用。
 * </p>
 *
 * <h3>工作流程</h3>
 * <ol>
 * <li>接收用户问题，构建研究专用的 System Prompt</li>
 * <li>调用 LLM（带 WebSearchTool 工具规格），LLM 决定是否需要搜索</li>
 * <li>如果 LLM 请求工具调用 → 执行 WebSearchTool → 将结果反馈给 LLM</li>
 * <li>LLM 基于搜索结果生成专业分析报告</li>
 * </ol>
 *
 * <h3>与其他 Specialist 的区别</h3>
 * <ul>
 * <li>ResearchAgent：搜索外部信息，依赖互联网</li>
 * <li>MathAgent：数学计算，依赖 exp4j 引擎</li>
 * <li>CodeAgent：代码执行，依赖 ScriptEngine</li>
 * </ul>
 *
 * @see AgentNode
 * @see WebSearchTool
 */
public class ResearchAgent implements AgentNode {

    private static final Logger log = LoggerFactory.getLogger(ResearchAgent.class);

    /** 研究专家 System Prompt */
    private static final String RESEARCH_PROMPT =
            "你是一个专业的研究分析师，善于搜索和解读互联网信息。\n\n"
            + "## 能力\n"
            + "- 使用 searchWeb 工具搜索互联网获取最新信息\n"
            + "- 从搜索结果中提取关键信息，进行归纳总结\n"
            + "- 验证信息来源的可靠性，优先采用权威来源\n\n"
            + "## 工作方式\n"
            + "1. 分析用户的问题，确定搜索关键词\n"
            + "2. 调用 searchWeb 工具进行搜索\n"
            + "3. 基于搜索结果给出详细、准确的回答\n"
            + "4. 如果搜索结果不充分，尝试用不同关键词重新搜索\n\n"
            + "## 输出要求\n"
            + "- 回答应包含信息来源（标题 + 链接）\n"
            + "- 按重要性排列信息，最重要的在前\n"
            + "- 使用中文回答，保持专业客观的语气\n"
            + "- 如果找不到相关信息，诚实地说明";

    /** 非流式聊天模型 */
    private final ChatModel chatModel;

    /** 网页搜索工具 */
    private final WebSearchTool webSearchTool;

    /**
     * 构造 ResearchAgent
     *
     * @param chatModel    非流式聊天模型
     * @param webSearchTool 网页搜索工具实例
     */
    public ResearchAgent(ChatModel chatModel, WebSearchTool webSearchTool) {
        this.chatModel = chatModel;
        this.webSearchTool = webSearchTool;
    }

    /**
     * 执行研究搜索任务
     * <p>
     * 构建研究专用消息列表，调用 LLM 进行工具调用推理，
     * 执行搜索工具并基于结果给出分析报告。
     * </p>
     *
     * @param state   当前图状态（读取 userMessage，写入 specialistResults）
     * @param emitter SSE 事件发射器
     * @return 研究分析报告文本
     */
    @Override
    public String execute(AgentState state, FluxSink<String> emitter) {
        log.info("ResearchAgent: 开始研究搜索...");

        // 推送思考事件
        emitter.next(AgentEvent.thinking("ResearchAgent 正在搜索互联网信息...")
                .withField("agent", "research").toJson());

        try {
            // 构建消息列表：System Prompt + 用户消息
            List<dev.langchain4j.data.message.ChatMessage> messages = new ArrayList<>();
            messages.add(SystemMessage.from(RESEARCH_PROMPT));
            messages.add(UserMessage.from(state.getUserMessage()));

            // 构建工具规格列表（仅 WebSearchTool）
            List<ToolSpecification> toolSpecs =
                    ToolSpecifications.toolSpecificationsFrom(webSearchTool);

            // 第一轮：LLM 分析问题并决定是否搜索
            ChatResponse response = chatModel.chat(
                    ChatRequest.builder()
                            .messages(messages)
                            .toolSpecifications(toolSpecs)
                            .build());

            AiMessage aiMessage = response.aiMessage();

            // 检查是否需要搜索
            if (aiMessage.hasToolExecutionRequests()) {
                List<ToolExecutionRequest> requests = aiMessage.toolExecutionRequests();
                log.info("ResearchAgent: LLM 请求 {} 次搜索", requests.size());

                // 将 AI 的工具调用请求加入消息历史
                messages.add(aiMessage);

                // 执行所有搜索请求
                for (ToolExecutionRequest request : requests) {
                    // 推送工具调用事件
                    emitter.next(AgentEvent.toolCall(request.name(),
                                    parseArgs(request.arguments()))
                            .withField("agent", "research").toJson());

                    // 执行搜索
                    String result = executeTool(request.name(), request.arguments());
                    log.info("ResearchAgent: 搜索完成，结果长度 {} 字符",
                            result != null ? result.length() : 0);

                    // 推送工具结果事件
                    emitter.next(AgentEvent.toolResult(request.name(),
                                    truncateResult(result))
                            .withField("agent", "research").toJson());

                    // 将工具结果加入消息历史
                    messages.add(ToolExecutionResultMessage.from(request,
                            result != null ? result : "(无结果)"));
                }

                // 第二轮：LLM 基于搜索结果生成分析报告
                emitter.next(AgentEvent.thinking("ResearchAgent 正在分析搜索结果...")
                        .withField("agent", "research").toJson());

                ChatResponse finalResponse = chatModel.chat(
                        ChatRequest.builder().messages(messages).build());
                String analysis = finalResponse.aiMessage().text();

                // 记录 Specialist 结果
                state.addSpecialistResult(new AgentState.SpecialistResult(
                        "research", "searchWeb", analysis));

                return analysis;
            }

            // 不需要搜索 → 直接返回 LLM 回答
            String answer = aiMessage.text();
            state.addSpecialistResult(new AgentState.SpecialistResult(
                    "research", "none", answer));
            return answer;

        } catch (Exception e) {
            log.error("ResearchAgent: 执行失败 — {}", e.getMessage(), e);
            String errorMsg = "研究搜索过程出错: " + e.getMessage();
            emitter.next(AgentEvent.error(errorMsg).toJson());
            return errorMsg;
        }
    }

    /**
     * 执行工具方法（通过反射调用 WebSearchTool 的 @Tool 方法）
     */
    private String executeTool(String toolName, String arguments) {
        try {
            for (java.lang.reflect.Method method : webSearchTool.getClass().getDeclaredMethods()) {
                if (method.isAnnotationPresent(dev.langchain4j.agent.tool.Tool.class)) {
                    String methodName = method.getAnnotation(dev.langchain4j.agent.tool.Tool.class).name();
                    if (methodName.isEmpty()) {
                        methodName = method.getName();
                    }
                    if (methodName.equals(toolName) || method.getName().equals(toolName)) {
                        // 解析参数
                        Object[] args = parseArgsForMethod(method, arguments);
                        Object result = method.invoke(webSearchTool, args);
                        return result != null ? result.toString() : "(无返回)";
                    }
                }
            }
        } catch (Exception e) {
            log.error("ResearchAgent: 工具执行失败 — {}", e.getMessage());
        }
        return "搜索工具执行失败";
    }

    /**
     * 解析 JSON 参数为方法调用参数数组
     */
    private Object[] parseArgsForMethod(java.lang.reflect.Method method, String arguments) throws Exception {
        if (arguments == null || arguments.isBlank()) {
            return new Object[method.getParameterCount()];
        }
        com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
        @SuppressWarnings("unchecked")
        java.util.Map<String, Object> argsMap = mapper.readValue(arguments, java.util.Map.class);
        Object[] result = new Object[method.getParameterCount()];
        java.lang.reflect.Parameter[] params = method.getParameters();
        for (int i = 0; i < params.length; i++) {
            String paramName = params[i].getName();
            if (params[i].isAnnotationPresent(dev.langchain4j.agent.tool.P.class)) {
                paramName = params[i].getAnnotation(dev.langchain4j.agent.tool.P.class).value();
            }
            if (argsMap.containsKey(paramName)) {
                Object val = argsMap.get(paramName);
                if (params[i].getType() == Integer.class || params[i].getType() == int.class) {
                    result[i] = val instanceof Number ? ((Number) val).intValue() : Integer.parseInt(val.toString());
                } else {
                    result[i] = val;
                }
            }
        }
        return result;
    }

    /**
     * 截断过长的搜索结果（保留前 2000 字符用于前端显示）
     */
    private String truncateResult(String result) {
        if (result == null) return "(无结果)";
        return result.length() > 2000 ? result.substring(0, 2000) + "...(已截断)" : result;
    }

    /**
     * 解析工具参数 JSON 为 Map
     */
    @SuppressWarnings("unchecked")
    private java.util.Map<String, Object> parseArgs(String argumentsJson) {
        try {
            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            return mapper.readValue(argumentsJson, java.util.Map.class);
        } catch (Exception e) {
            return java.util.Map.of("raw", argumentsJson);
        }
    }

    @Override
    public String getName() {
        return "research";
    }
}
