package com.zora.agent.graph;

import com.zora.agent.event.AgentEvent;
import com.zora.agent.tool.MathTool;
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
 * MathAgent — 数学计算专家（Phase 3.5）
 * <p>
 * 专注于数学表达式计算和数值分析，使用 {@link MathTool}（基于 exp4j）进行安全计算。
 * 在多 Agent 编排中，当 Supervisor 判断用户需要进行数学计算时被调用。
 * </p>
 *
 * <h3>工作流程</h3>
 * <ol>
 * <li>接收数学问题，构建计算专用的 System Prompt</li>
 * <li>调用 LLM（带 MathTool 工具规格），LLM 决定何时使用计算工具</li>
 * <li>如果 LLM 请求工具调用 → 执行 MathTool → 将结果反馈给 LLM</li>
 * <li>LLM 基于计算结果给出解释和答案</li>
 * </ol>
 *
 * <h3>支持的运算</h3>
 * <p>
 * 基本运算、三角函数、反三角函数、双曲函数、对数、幂/根、取整、阶乘等。
 * 详见 {@link MathTool} 类文档。
 * </p>
 *
 * @see AgentNode
 * @see MathTool
 */
public class MathAgent implements AgentNode {

    private static final Logger log = LoggerFactory.getLogger(MathAgent.class);

    /** 数学专家 System Prompt */
    private static final String MATH_PROMPT =
            "你是一个专业的数学专家，善于解决各类数学计算问题。\n\n"
            + "## 能力\n"
            + "- 使用 calculate 工具进行数学表达式求值\n"
            + "- 支持基本运算、三角函数、对数、阶乘等多种运算\n"
            + "- 能够将复杂问题拆解为多个简单计算步骤\n\n"
            + "## 工作方式\n"
            + "1. 分析用户的数学问题，提取需要计算的表达式\n"
            + "2. 调用 calculate 工具进行精确计算\n"
            + "3. 如果问题复杂，分步骤计算并解释每一��\n"
            + "4. 对计算结果给出清晰的解释\n\n"
            + "## 输出要求\n"
            + "- 展示计算步骤和中间结果\n"
            + "- 最终答案要突出显示\n"
            + "- 使用中文回答，数学符号使用标准 LaTeX 风格\n"
            + "- 如果计算结果异常（如除零），解释原因";

    /** 非流式聊天模型 */
    private final ChatModel chatModel;

    /** 数学计算工具 */
    private final MathTool mathTool;

    /**
     * 构造 MathAgent
     *
     * @param chatModel 非流式聊天模型
     * @param mathTool  数学计算工具实例
     */
    public MathAgent(ChatModel chatModel, MathTool mathTool) {
        this.chatModel = chatModel;
        this.mathTool = mathTool;
    }

    /**
     * 执行数学计算任务
     * <p>
     * 构建数学专用消息列表，调用 LLM 进行工具调用推理，
     * 执行计算工具并基于结果给出解释。
     * </p>
     *
     * @param state   当前图状态（读取 userMessage，写入 specialistResults）
     * @param emitter SSE 事件发射器
     * @return 数学计算结果和解释文本
     */
    @Override
    public String execute(AgentState state, FluxSink<String> emitter) {
        log.info("MathAgent: 开始数学计算...");

        // 推送思考事件
        emitter.next(AgentEvent.thinking("MathAgent 正在分析数学问题...")
                .withField("agent", "math").toJson());

        try {
            // 构建消息列表
            List<dev.langchain4j.data.message.ChatMessage> messages = new ArrayList<>();
            messages.add(SystemMessage.from(MATH_PROMPT));
            messages.add(UserMessage.from(state.getUserMessage()));

            // 构建工具规格（仅 MathTool）
            List<ToolSpecification> toolSpecs =
                    ToolSpecifications.toolSpecificationsFrom(mathTool);

            // 第一轮：LLM 分析并决定是否计算
            ChatResponse response = chatModel.chat(
                    ChatRequest.builder()
                            .messages(messages)
                            .toolSpecifications(toolSpecs)
                            .build());

            AiMessage aiMessage = response.aiMessage();

            // 检查是否需要计算
            if (aiMessage.hasToolExecutionRequests()) {
                List<ToolExecutionRequest> requests = aiMessage.toolExecutionRequests();
                log.info("MathAgent: LLM 请求 {} 次计算", requests.size());

                messages.add(aiMessage);

                // 执行所有计算请求
                for (ToolExecutionRequest request : requests) {
                    emitter.next(AgentEvent.toolCall(request.name(),
                                    parseArgs(request.arguments()))
                            .withField("agent", "math").toJson());

                    String result = executeTool(request.name(), request.arguments());
                    log.info("MathAgent: 计算完成，结果 = {}", result);

                    emitter.next(AgentEvent.toolResult(request.name(), result)
                            .withField("agent", "math").toJson());

                    messages.add(ToolExecutionResultMessage.from(request,
                            result != null ? result : "(计算错误)"));
                }

                // 第二轮：基于计算结果生成解释
                emitter.next(AgentEvent.thinking("MathAgent 正在分析计算结果...")
                        .withField("agent", "math").toJson());

                ChatResponse finalResponse = chatModel.chat(
                        ChatRequest.builder().messages(messages).build());
                String explanation = finalResponse.aiMessage().text();

                state.addSpecialistResult(new AgentState.SpecialistResult(
                        "math", "calculate", explanation));
                return explanation;
            }

            // 不需要计算 → 直接返回
            String answer = aiMessage.text();
            state.addSpecialistResult(new AgentState.SpecialistResult(
                    "math", "none", answer));
            return answer;

        } catch (Exception e) {
            log.error("MathAgent: 执行失败 — {}", e.getMessage(), e);
            String errorMsg = "数学计算出错: " + e.getMessage();
            emitter.next(AgentEvent.error(errorMsg).toJson());
            return errorMsg;
        }
    }

    /**
     * 执行 MathTool 的 @Tool 方法
     */
    private String executeTool(String toolName, String arguments) {
        try {
            for (java.lang.reflect.Method method : mathTool.getClass().getDeclaredMethods()) {
                if (method.isAnnotationPresent(dev.langchain4j.agent.tool.Tool.class)) {
                    if (method.getName().equals(toolName) || matchesToolAnnotation(method, toolName)) {
                        Object[] args = parseArgsForMethod(method, arguments);
                        Object result = method.invoke(mathTool, args);
                        return result != null ? result.toString() : "(无返回)";
                    }
                }
            }
        } catch (Exception e) {
            log.error("MathAgent: 工具执行失败 — {}", e.getMessage());
        }
        return "计算工具执行失败";
    }

    private boolean matchesToolAnnotation(java.lang.reflect.Method method, String toolName) {
        dev.langchain4j.agent.tool.Tool tool = method.getAnnotation(dev.langchain4j.agent.tool.Tool.class);
        if (tool == null) return false;
        String name = tool.name();
        return !name.isEmpty() && name.equals(toolName);
    }

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
                result[i] = argsMap.get(paramName);
            } else if (argsMap.size() > i) {
                // 按位置匹配
                result[i] = argsMap.values().toArray()[i];
            }
        }
        return result;
    }

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
        return "math";
    }
}
