package com.zora.agent.graph;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.FluxSink;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * SupervisorAgent 意图分类测试（Phase 3.5）
 * <p>
 * Mock ChatLanguageModel，测试四种意图分类场景和异常降级。
 * </p>
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("SupervisorAgent 意图分类测试")
class SupervisorAgentTest {

    @Mock
    private ChatModel chatModel;

    @Mock
    private FluxSink<String> emitter;

    private SupervisorAgent supervisor;

    @BeforeEach
    void setUp() {
        supervisor = new SupervisorAgent(chatModel);
    }

    @Nested
    @DisplayName("意图分类")
    class IntentClassification {

        @Test
        @DisplayName("搜索类问题应分类为 research")
        void shouldClassifyAsResearch() {
            mockLlmResponse("research");

            AgentState state = createState("帮我搜索最新的 AI 新闻");
            String intent = supervisor.execute(state, emitter);

            assertEquals("research", intent);
            assertEquals("research", state.getIntent());
        }

        @Test
        @DisplayName("数学类问题应分类为 math")
        void shouldClassifyAsMath() {
            mockLlmResponse("math");

            AgentState state = createState("计算 sqrt(144) 加 30 的结果");
            String intent = supervisor.execute(state, emitter);

            assertEquals("math", intent);
            assertEquals("math", state.getIntent());
        }

        @Test
        @DisplayName("代码类问题应分类为 code")
        void shouldClassifyAsCode() {
            mockLlmResponse("code");

            AgentState state = createState("写一个 JavaScript 函数计算斐波那契数列");
            String intent = supervisor.execute(state, emitter);

            assertEquals("code", intent);
            assertEquals("code", state.getIntent());
        }

        @Test
        @DisplayName("一般性问题应分类为 general")
        void shouldClassifyAsGeneral() {
            mockLlmResponse("general");

            AgentState state = createState("你好，今天天气怎么样？");
            String intent = supervisor.execute(state, emitter);

            assertEquals("general", intent);
            assertEquals("general", state.getIntent());
        }
    }

    @Nested
    @DisplayName("分类结果清理")
    class ClassificationCleanup {

        @Test
        @DisplayName("LLM 返回带额外文字的结果也应正确解析")
        void shouldCleanExtraText() {
            // LLM 有时会返回 "research." 或 "I think math is best" 等
            mockLlmResponse("I think research would be best for this query.");

            AgentState state = createState("帮我搜索新闻");
            String intent = supervisor.execute(state, emitter);

            assertEquals("research", intent);
        }

        @Test
        @DisplayName("LLM 返回大写结果也应正确识别")
        void shouldHandleUppercase() {
            mockLlmResponse("RESEARCH");

            AgentState state = createState("搜索最新新闻");
            String intent = supervisor.execute(state, emitter);

            assertEquals("research", intent);
        }

        @Test
        @DisplayName("无法识别的分类应降级为 general")
        void shouldFallbackToGeneral() {
            mockLlmResponse("unknown_category_xyz");

            AgentState state = createState("你好");
            String intent = supervisor.execute(state, emitter);

            assertEquals("general", intent);
        }

        @Test
        @DisplayName("LLM 返回空字符串应降级为 general")
        void shouldFallbackToGeneralOnEmpty() {
            mockLlmResponse("");

            AgentState state = createState("测试消息");
            String intent = supervisor.execute(state, emitter);

            assertEquals("general", intent);
        }
    }

    @Nested
    @DisplayName("异常处理")
    class ErrorHandling {

        @Test
        @DisplayName("LLM 调用异常应降级为 general")
        void shouldFallbackToGeneralOnLlmError() {
            when(chatModel.chat(any(ChatRequest.class)))
                    .thenThrow(new RuntimeException("AI 服务异常"));

            AgentState state = createState("测试消息");
            String intent = supervisor.execute(state, emitter);

            assertEquals("general", intent);
            assertEquals("general", state.getIntent());
        }

        @Test
        @DisplayName("getName 应返回 supervisor")
        void getNameShouldReturnSupervisor() {
            assertEquals("supervisor", supervisor.getName());
        }
    }

    // ==================== 辅助方法 ====================

    private void mockLlmResponse(String text) {
        ChatResponse mockResponse = mock(ChatResponse.class);
        when(mockResponse.aiMessage()).thenReturn(AiMessage.from(text));
        when(chatModel.chat(any(ChatRequest.class))).thenReturn(mockResponse);
    }

    private AgentState createState(String userMessage) {
        AgentState state = new AgentState();
        state.setUserMessage(userMessage);
        return state;
    }
}
