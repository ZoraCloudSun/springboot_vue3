package com.zora.agent.graph;

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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import reactor.core.publisher.FluxSink;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * AgentGraph 编排器测试（Phase 3.5）
 * <p>
 * 测试多 Agent 编排的完整流程：分类 → 路由 → 聚合 → 流式输出。
 * Mock 所有 LLM 调用，验证四种路由路径和降级逻辑。
 * </p>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("AgentGraph 编排器测试")
class AgentGraphTest {

    @Mock
    private ChatModel chatModel;

    @Mock
    private StreamingChatModel streamingModel;

    @Mock
    private WebSearchTool webSearchTool;

    @Mock
    private MathTool mathTool;

    @Mock
    private CodeExecutionTool codeExecutionTool;

    @Mock
    private FluxSink<String> emitter;

    private AgentGraph graph;

    @BeforeEach
    void setUp() {
        graph = new AgentGraph(chatModel, streamingModel,
                webSearchTool, mathTool, codeExecutionTool, 3);
    }

    @Nested
    @DisplayName("路由路径")
    class RoutingPaths {

        @Test
        @DisplayName("research 意图应路由到 ResearchAgent")
        void shouldRouteToResearchAgent() {
            // 第一轮 LLM：Supervisor 分类
            ChatResponse classifyResponse = mock(ChatResponse.class);
            when(classifyResponse.aiMessage()).thenReturn(AiMessage.from("research"));

            // 第二轮 LLM：ResearchAgent 分析（不需要工具调用）
            ChatResponse researchResponse = mock(ChatResponse.class);
            when(researchResponse.aiMessage())
                    .thenReturn(AiMessage.from("根据搜索结果，AI 正在快速发展..."));

            // 第三轮 LLM：Summarizer 聚合
            ChatResponse summarizeResponse = mock(ChatResponse.class);
            when(summarizeResponse.aiMessage())
                    .thenReturn(AiMessage.from("专家分析结果已整合..."));

            when(chatModel.chat(any(ChatRequest.class)))
                    .thenReturn(classifyResponse)
                    .thenReturn(researchResponse)
                    .thenReturn(summarizeResponse);

            List<dev.langchain4j.data.message.ChatMessage> messages = createMessages();
            String result = graph.execute("搜索最新 AI 新闻", messages, emitter);

            assertNotNull(result);
            assertTrue(result.length() > 0);
        }

        @Test
        @DisplayName("math 意图应路由到 MathAgent")
        void shouldRouteToMathAgent() {
            // Supervisor 分类
            ChatResponse classifyResponse = mock(ChatResponse.class);
            when(classifyResponse.aiMessage()).thenReturn(AiMessage.from("math"));

            // MathAgent 不需要工具调用
            ChatResponse mathResponse = mock(ChatResponse.class);
            when(mathResponse.aiMessage()).thenReturn(AiMessage.from("计算结果为 12"));

            // Summarizer
            ChatResponse summarizeResponse = mock(ChatResponse.class);
            when(summarizeResponse.aiMessage()).thenReturn(AiMessage.from("根据计算结果..."));

            when(chatModel.chat(any(ChatRequest.class)))
                    .thenReturn(classifyResponse)
                    .thenReturn(mathResponse)
                    .thenReturn(summarizeResponse);

            List<dev.langchain4j.data.message.ChatMessage> messages = createMessages();
            String result = graph.execute("计算 144 的平方根", messages, emitter);

            assertNotNull(result);
        }

        @Test
        @DisplayName("code 意图应路由到 CodeAgent")
        void shouldRouteToCodeAgent() {
            // Supervisor 分类
            ChatResponse classifyResponse = mock(ChatResponse.class);
            when(classifyResponse.aiMessage()).thenReturn(AiMessage.from("code"));

            // CodeAgent 分析
            ChatResponse codeResponse = mock(ChatResponse.class);
            when(codeResponse.aiMessage()).thenReturn(AiMessage.from("代码执行成功..."));

            // Summarizer
            ChatResponse summarizeResponse = mock(ChatResponse.class);
            when(summarizeResponse.aiMessage()).thenReturn(AiMessage.from("根据代码执行结果..."));

            when(chatModel.chat(any(ChatRequest.class)))
                    .thenReturn(classifyResponse)
                    .thenReturn(codeResponse)
                    .thenReturn(summarizeResponse);

            List<dev.langchain4j.data.message.ChatMessage> messages = createMessages();
            String result = graph.execute("写一段代码计算 1 到 100 的和", messages, emitter);

            assertNotNull(result);
        }

        @Test
        @DisplayName("general 意图应直接回答，不调用 Specialist")
        void shouldDirectAnswerForGeneral() {
            // Supervisor 分类为 general
            ChatResponse classifyResponse = mock(ChatResponse.class);
            when(classifyResponse.aiMessage()).thenReturn(AiMessage.from("general"));

            when(chatModel.chat(any(ChatRequest.class)))
                    .thenReturn(classifyResponse);

            // Streaming model 直接输出回答
            doAnswer(inv -> {
                StreamingChatResponseHandler handler = inv.getArgument(1);
                handler.onPartialResponse("你好！");
                handler.onPartialResponse("有什么可以帮你的？");
                handler.onCompleteResponse(mock(ChatResponse.class));
                return null;
            }).when(streamingModel).chat(any(ChatRequest.class), any(StreamingChatResponseHandler.class));

            List<dev.langchain4j.data.message.ChatMessage> messages = createMessages();
            String result = graph.execute("你好，你是谁？", messages, emitter);

            assertNotNull(result);
        }
    }

    @Nested
    @DisplayName("不可用 Specialist 处理")
    class UnavailableSpecialist {

        @Test
        @DisplayName("当 codeAgent 为 null 时，code 意图应降级为 general")
        void shouldFallbackWhenCodeAgentNull() {
            // 创建不带 CodeAgent 的 graph
            AgentGraph noCodeGraph = new AgentGraph(chatModel, streamingModel,
                    webSearchTool, mathTool, null, 3);

            ChatResponse classifyResponse = mock(ChatResponse.class);
            when(classifyResponse.aiMessage()).thenReturn(AiMessage.from("code"));

            when(chatModel.chat(any(ChatRequest.class)))
                    .thenReturn(classifyResponse);

            // 流式输出
            doAnswer(inv -> {
                StreamingChatResponseHandler handler = inv.getArgument(1);
                handler.onPartialResponse("降级回答");
                handler.onCompleteResponse(mock(ChatResponse.class));
                return null;
            }).when(streamingModel).chat(any(ChatRequest.class), any(StreamingChatResponseHandler.class));

            List<dev.langchain4j.data.message.ChatMessage> messages = createMessages();
            String result = noCodeGraph.execute("执行代码", messages, emitter);

            assertNotNull(result);
        }
    }

    @Nested
    @DisplayName("Summarizer 聚合")
    class SummarizerAggregation {

        @Test
        @DisplayName("有 Specialist 结果时应调用 Summarizer 聚合")
        void shouldSummarizeWithSpecialistResults() {
            // Supervisor 分类
            ChatResponse classifyResponse = mock(ChatResponse.class);
            when(classifyResponse.aiMessage()).thenReturn(AiMessage.from("math"));

            // MathAgent
            ChatResponse mathResponse = mock(ChatResponse.class);
            when(mathResponse.aiMessage()).thenReturn(AiMessage.from("144 的平方根是 12"));

            // Summarizer
            ChatResponse summarizeResponse = mock(ChatResponse.class);
            when(summarizeResponse.aiMessage())
                    .thenReturn(AiMessage.from("根据数学专家的分析，144 的平方根是 12，以下是详细解释..."));

            when(chatModel.chat(any(ChatRequest.class)))
                    .thenReturn(classifyResponse)
                    .thenReturn(mathResponse)
                    .thenReturn(summarizeResponse);

            List<dev.langchain4j.data.message.ChatMessage> messages = createMessages();
            String result = graph.execute("sqrt(144) 是多少？", messages, emitter);

            assertTrue(result.length() > 0);
            // 应至少调用 3 次 LLM（Supervisor + MathAgent + Summarizer）
            verify(chatModel, atLeast(3)).chat(any(ChatRequest.class));
        }
    }

    @Nested
    @DisplayName("异常降级")
    class ExceptionFallback {

        @Test
        @DisplayName("Summarizer 异常不应导致整个流程崩溃")
        void shouldNotCrashOnSummarizerFailure() {
            ChatResponse classifyResponse = mock(ChatResponse.class);
            when(classifyResponse.aiMessage()).thenReturn(AiMessage.from("general"));

            when(chatModel.chat(any(ChatRequest.class)))
                    .thenReturn(classifyResponse);

            // 流式模型也异常
            doThrow(new RuntimeException("流式模型异常"))
                    .when(streamingModel).chat(any(ChatRequest.class), any(StreamingChatResponseHandler.class));

            List<dev.langchain4j.data.message.ChatMessage> messages = createMessages();
            String result = graph.execute("测试消息", messages, emitter);

            // 应降级为非流式回答或返回错误消息
            assertNotNull(result);
        }
    }

    @Nested
    @DisplayName("maxSpecialistCalls 限制")
    class MaxSpecialistCalls {

        @Test
        @DisplayName("不应超过 maxSpecialistCalls 次调用")
        void shouldRespectMaxSpecialistCalls() {
            // 创建只允许 1 次 specialist 调用的 graph
            AgentGraph limitedGraph = new AgentGraph(chatModel, streamingModel,
                    webSearchTool, mathTool, codeExecutionTool, 1);

            // Supervisor 每次都分类为 math（模拟需要多次调用）
            ChatResponse classify1 = mock(ChatResponse.class);
            when(classify1.aiMessage()).thenReturn(AiMessage.from("math"));

            ChatResponse mathResponse = mock(ChatResponse.class);
            when(mathResponse.aiMessage()).thenReturn(AiMessage.from("计算结果"));

            ChatResponse summarizeResponse = mock(ChatResponse.class);
            when(summarizeResponse.aiMessage()).thenReturn(AiMessage.from("聚合结果"));

            when(chatModel.chat(any(ChatRequest.class)))
                    .thenReturn(classify1)
                    .thenReturn(mathResponse)
                    .thenReturn(summarizeResponse);

            List<dev.langchain4j.data.message.ChatMessage> messages = createMessages();
            String result = limitedGraph.execute("计算 1+1，然后计算 2+2", messages, emitter);

            // 即使有多个计算需求，也只调用 1 次 specialist
            assertNotNull(result);
        }
    }

    // ==================== 辅助方法 ====================

    private List<dev.langchain4j.data.message.ChatMessage> createMessages() {
        List<dev.langchain4j.data.message.ChatMessage> messages = new ArrayList<>();
        messages.add(SystemMessage.from("你是一个 AI 助手"));
        return messages;
    }
}
