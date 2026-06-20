package com.zora.agent.graph;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * AgentState 图状态测试（Phase 3.5）
 * <p>
 * 测试 AgentState 的创建、状态修改、结果累加和便捷方法。
 * </p>
 */
@DisplayName("AgentState 图状态测试")
class AgentStateTest {

    @Nested
    @DisplayName("基本属性")
    class BasicProperties {

        @Test
        @DisplayName("新创建的 AgentState 应有空结果列表")
        void newStateShouldHaveEmptyResults() {
            AgentState state = new AgentState();
            assertNotNull(state.getSpecialistResults());
            assertTrue(state.getSpecialistResults().isEmpty());
        }

        @Test
        @DisplayName("新创建的 AgentState specialistCallCount 应为 0")
        void newStateShouldHaveZeroCallCount() {
            AgentState state = new AgentState();
            assertEquals(0, state.getSpecialistCallCount());
        }

        @Test
        @DisplayName("新创建的 AgentState 默认 maxSpecialistCalls 应为 3")
        void newStateShouldHaveDefaultMaxCalls() {
            AgentState state = new AgentState();
            assertEquals(3, state.getMaxSpecialistCalls());
        }

        @Test
        @DisplayName("setter/getter 应正确工作")
        void settersAndGettersShouldWork() {
            AgentState state = new AgentState();
            state.setUserMessage("帮我计算");
            state.setIntent("math");
            state.setActiveSpecialist("math");

            assertEquals("帮我计算", state.getUserMessage());
            assertEquals("math", state.getIntent());
            assertEquals("math", state.getActiveSpecialist());
        }
    }

    @Nested
    @DisplayName("Specialist 结果管理")
    class SpecialistResults {

        @Test
        @DisplayName("addSpecialistResult 应递增调用计数")
        void addResultShouldIncrementCount() {
            AgentState state = new AgentState();
            state.addSpecialistResult(new AgentState.SpecialistResult("math", "calculate", "42"));

            assertEquals(1, state.getSpecialistCallCount());
            assertEquals(1, state.getSpecialistResults().size());
        }

        @Test
        @DisplayName("添加多个结果应正确累加")
        void multipleResultsShouldAccumulate() {
            AgentState state = new AgentState();
            state.addSpecialistResult(new AgentState.SpecialistResult("research", "searchWeb", "搜索结果1"));
            state.addSpecialistResult(new AgentState.SpecialistResult("math", "calculate", "结果2"));

            assertEquals(2, state.getSpecialistCallCount());
            assertEquals(2, state.getSpecialistResults().size());
            assertEquals("research", state.getSpecialistResults().get(0).agentName());
            assertEquals("math", state.getSpecialistResults().get(1).agentName());
        }

        @Test
        @DisplayName("SpecialistResult 二参数构造应默认 toolName 为 none")
        void twoArgConstructorShouldDefaultToolName() {
            AgentState.SpecialistResult result = new AgentState.SpecialistResult("general", "直接回答");

            assertEquals("general", result.agentName());
            assertEquals("none", result.toolName());
            assertEquals("直接回答", result.result());
        }
    }

    @Nested
    @DisplayName("调用限制")
    class CallLimits {

        @Test
        @DisplayName("未达到上限时 canCallSpecialist 返回 true")
        void canCallWhenBelowLimit() {
            AgentState state = new AgentState();
            assertTrue(state.canCallSpecialist());
        }

        @Test
        @DisplayName("达到上限时 canCallSpecialist 返回 false")
        void cannotCallWhenAtLimit() {
            AgentState state = new AgentState();
            state.setMaxSpecialistCalls(1);
            state.addSpecialistResult(new AgentState.SpecialistResult("math", "calculate", "42"));

            assertFalse(state.canCallSpecialist());
        }

        @Test
        @DisplayName("超过上限时 canCallSpecialist 返回 false")
        void cannotCallWhenOverLimit() {
            AgentState state = new AgentState();
            state.setMaxSpecialistCalls(2);
            state.addSpecialistResult(new AgentState.SpecialistResult("r1", "t1", "v1"));
            state.addSpecialistResult(new AgentState.SpecialistResult("r2", "t2", "v2"));
            state.addSpecialistResult(new AgentState.SpecialistResult("r3", "t3", "v3"));

            assertEquals(3, state.getSpecialistCallCount());
            assertFalse(state.canCallSpecialist());
        }

        @Test
        @DisplayName("可自定义 maxSpecialistCalls")
        void maxSpecialistCallsCanBeCustomized() {
            AgentState state = new AgentState();
            state.setMaxSpecialistCalls(5);
            assertEquals(5, state.getMaxSpecialistCalls());

            // 添加 4 个结果后应仍能继续
            for (int i = 0; i < 4; i++) {
                state.addSpecialistResult(new AgentState.SpecialistResult("r" + i, "t" + i, "v" + i));
            }
            assertTrue(state.canCallSpecialist());
        }
    }

    @Nested
    @DisplayName("结果摘要")
    class ResultsSummary {

        @Test
        @DisplayName("空结果列表的摘要应为空字符串")
        void emptyResultsShouldReturnEmptySummary() {
            AgentState state = new AgentState();
            assertEquals("", state.getResultsSummary());
        }

        @Test
        @DisplayName("有结果时应包含 agent 名称和结果内容")
        void shouldIncludeAgentNameAndResult() {
            AgentState state = new AgentState();
            state.addSpecialistResult(new AgentState.SpecialistResult("research", "searchWeb", "找到了 5 条相关信息"));

            String summary = state.getResultsSummary();
            assertTrue(summary.contains("research"));
            assertTrue(summary.contains("找到了 5 条相关信息"));
            assertTrue(summary.contains("执行结果"));
        }

        @Test
        @DisplayName("多个结果应全部包含在摘要中")
        void shouldContainAllResults() {
            AgentState state = new AgentState();
            state.addSpecialistResult(new AgentState.SpecialistResult("math", "calculate", "42"));
            state.addSpecialistResult(new AgentState.SpecialistResult("research", "searchWeb", "搜索结果"));

            String summary = state.getResultsSummary();
            assertTrue(summary.contains("math"));
            assertTrue(summary.contains("research"));
            assertTrue(summary.contains("42"));
            assertTrue(summary.contains("搜索结果"));
        }
    }
}
