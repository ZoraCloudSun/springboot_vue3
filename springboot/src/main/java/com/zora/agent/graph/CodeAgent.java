package com.zora.agent.graph;

import com.zora.agent.event.AgentEvent;
import com.zora.agent.tool.CodeExecutionTool;
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
 * CodeAgent — 代码执行专家（Phase 3.5）
 * <p>
 * 专注于代码编写和沙箱执行，使用 {@link CodeExecutionTool}（基于 JDK ScriptEngine）安全运行 JavaScript。
 * 在多 Agent 编排中，当 Supervisor 判断用户需要执行代码时被调用。
 * </p>
 *
 * <h3>工作流程</h3>
 * <ol>
 * <li>接收编程问题，构建代码执行专用的 System Prompt</li>
 * <li>调用 LLM（带 CodeExecutionTool 工具规格），LLM 决定何时需要执行代码</li>
 * <li>如果 LLM 请求工具调用 → 执行 CodeExecutionTool（沙箱 + 超时保护） → 反馈结果</li>
 * <li>LLM 基于执行结果给出分析和建议</li>
 * </ol>
 *
 * <h3>安全说明</h3>
 * <p>
 * 代码在 JDK ScriptEngine 沙箱中执行，具有以下安全措施：
 * </p>
 * <ul>
 * <li>5 秒超时自动中断（防止无限循环）</li>
 * <li>输出限制 10000 字符</li>
 * <li>无法访问文件系统和网络</li>
 * <li>审计日志记录每次执行</li>
 * </ul>
 * <p>
 * 代码执行工具默认关闭，需通过 {@code agent.tools.code-execution.enabled=true} 启用。
 * </p>
 *
 * @see AgentNode
 * @see CodeExecutionTool
 */
public class CodeAgent implements AgentNode {

    private static final Logger log = LoggerFactory.getLogger(CodeAgent.class);

    /** 代码专家 System Prompt */
    private static final String CODE_PROMPT =
            "你是一个专业的编程专家，善于编写和执行 JavaScript 代码来解决问题。\n\n"
            + "## 能力\n"
            + "- 使用 executeCode 工具在安全沙箱中执行 JavaScript 代码\n"
            + "- 能够分析代码执行结果并给出解释\n"
            + "- 支持数据处理、算法演示、简单计算等场景\n\n"
            + "## 工作方式\n"
            + "1. 分析用户的问题，确定需要什么代码\n"
            + "2. 编写简洁、安全的 JavaScript 代码\n"
            + "3. 调用 executeCode 工具执行代码\n"
            + "4. 分析执行结果并给出清晰的解释\n\n"
            + "## 安全限制\n"
            + "- 仅支持 JavaScript 语言\n"
            + "- 代码在沙箱中执行，无法访问文件系统和网络\n"
            + "- 执行超时 5 秒，输出限制 10000 字符\n"
            + "- 仅用于数据处理和算法演示，不生成危险代码\n\n"
            + "## 输出要求\n"
            + "- 展示代码内容（Markdown 代码块）\n"
            + "- 展示执行结果\n"
            + "- 对结果进行解释\n"
            + "- 使用中文回答";

    /** 非流式聊天模型 */
    private final ChatModel chatModel;

    /** 代码执行工具 */
    private final CodeExecutionTool codeExecutionTool;

    /**
     * 构造 CodeAgent
     *
     * @param chatModel         非流式聊天模型
     * @param codeExecutionTool 代码执行工具实例
     */
    public CodeAgent(ChatModel chatModel, CodeExecutionTool codeExecutionTool) {
        this.chatModel = chatModel;
        this.codeExecutionTool = codeExecutionTool;
    }

    /**
     * 执行代码任务
     * <p>
     * 构建代码专用消息列表，调用 LLM 进行工具调用推理，
     * 在沙箱中执行代码并基于结果给出分析。
     * </p>
     *
     * @param state   当前图状态（读取 userMessage，写入 specialistResults）
     * @param emitter SSE 事件发射器
     * @return 代码执行结果和分析文本
     */
    @Override
    public String execute(AgentState state, FluxSink<String> emitter) {
        log.info("CodeAgent: 开始代码执行...");

        // 推送思考事件
        emitter.next(AgentEvent.thinking("CodeAgent 正在分析编程问题...")
                .withField("agent", "code").toJson());

        try {
            // 构建消息列表
            List<dev.langchain4j.data.message.ChatMessage> messages = new ArrayList<>();
            messages.add(SystemMessage.from(CODE_PROMPT));
            messages.add(UserMessage.from(state.getUserMessage()));

            // 构建工具规格（仅 CodeExecutionTool）
            List<ToolSpecification> toolSpecs =
                    ToolSpecifications.toolSpecificationsFrom(codeExecutionTool);

            // 第一轮：LLM 分析并决定是否执行代码
            ChatResponse response = chatModel.chat(
                    ChatRequest.builder()
                            .messages(messages)
                            .toolSpecifications(toolSpecs)
                            .build());

            AiMessage aiMessage = response.aiMessage();

            // 检查是否需要执行代码
            if (aiMessage.hasToolExecutionRequests()) {
                List<ToolExecutionRequest> requests = aiMessage.toolExecutionRequests();
                log.info("CodeAgent: LLM 请求 {} 次代码执行", requests.size());

                messages.add(aiMessage);

                // 执行所有代码请求
                for (ToolExecutionRequest request : requests) {
                    emitter.next(AgentEvent.toolCall(request.name(),
                                    parseArgs(request.arguments()))
                            .withField("agent", "code").toJson());

                    String result = executeTool(request.name(), request.arguments());
                    log.info("CodeAgent: 代码执行完成，结果长度 {} 字符",
                            result != null ? result.length() : 0);

                    emitter.next(AgentEvent.toolResult(request.name(),
                                    truncateResult(result))
                            .withField("agent", "code").toJson());

                    messages.add(ToolExecutionResultMessage.from(request,
                            result != null ? result : "(执行错误)"));
                }

                // 第二轮：基于执行结果生成分析
                emitter.next(AgentEvent.thinking("CodeAgent 正在分析执行结果...")
                        .withField("agent", "code").toJson());

                ChatResponse finalResponse = chatModel.chat(
                        ChatRequest.builder().messages(messages).build());
                String analysis = finalResponse.aiMessage().text();

                state.addSpecialistResult(new AgentState.SpecialistResult(
                        "code", "executeCode", analysis));
                return analysis;
            }

            // 不需要执行代码 → 直接返回
            String answer = aiMessage.text();
            state.addSpecialistResult(new AgentState.SpecialistResult(
                    "code", "none", answer));
            return answer;

        } catch (Exception e) {
            log.error("CodeAgent: 执行失败 — {}", e.getMessage(), e);
            String errorMsg = "代码执行出错: " + e.getMessage();
            emitter.next(AgentEvent.error(errorMsg).toJson());
            return errorMsg;
        }
    }

    /**
     * 执行 CodeExecutionTool 的 @Tool 方法
     */
    private String executeTool(String toolName, String arguments) {
        try {
            for (java.lang.reflect.Method method : codeExecutionTool.getClass().getDeclaredMethods()) {
                if (method.isAnnotationPresent(dev.langchain4j.agent.tool.Tool.class)) {
                    if (method.getName().equals(toolName) || matchesToolAnnotation(method, toolName)) {
                        Object[] args = parseArgsForMethod(method, arguments);
                        Object result = method.invoke(codeExecutionTool, args);
                        return result != null ? result.toString() : "(无返回)";
                    }
                }
            }
        } catch (Exception e) {
            log.error("CodeAgent: 工具执行失败 — {}", e.getMessage());
        }
        return "代码执行工具调用失败";
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

    /**
     * 截断过长的执行结果（前端显示用）
     */
    private String truncateResult(String result) {
        if (result == null) return "(无结果)";
        return result.length() > 2000 ? result.substring(0, 2000) + "...(已截断)" : result;
    }

    @Override
    public String getName() {
        return "code";
    }
}
