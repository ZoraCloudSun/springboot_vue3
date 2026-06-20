package com.zora.agent.graph;

import reactor.core.publisher.FluxSink;

/**
 * Agent 图节点接口（Phase 3.5 — 多 Agent 编排）
 * <p>
 * 定义多 Agent 编排图中每个节点的统一契约。
 * 所有 Agent 节点（Supervisor、Specialist、Summarizer）都实现此接口，
 * 通过 {@link AgentState} 共享状态，通过 {@link FluxSink} 推送 SSE 事件。
 * </p>
 *
 * <h3>设计原则</h3>
 * <ul>
 * <li><b>无状态节点</b>：节点不保存自身状态，所有状态通过 AgentState 传递</li>
 * <li><b>可组合</b>：通过组合不同节点构建复杂的工作流，节点之间完全解耦</li>
 * <li><b>可测试</b>：每个节点可以独立单元测试，Mock AgentState 即可</li>
 * </ul>
 *
 * <h3>实现节点</h3>
 * <table>
 * <tr><td>SupervisorAgent</td><td>意图分类，将用户需求路由到合适的 Specialist</td></tr>
 * <tr><td>ResearchAgent</td><td>研究专家，使用 WebSearchTool 搜索互联网</td></tr>
 * <tr><td>MathAgent</td><td>数学专家，使用 MathTool 进行数学计算</td></tr>
 * <tr><td>CodeAgent</td><td>代码专家，使用 CodeExecutionTool 执行代码</td></tr>
 * </table>
 *
 * @see AgentState
 * @see AgentGraph
 */
public interface AgentNode {

    /**
     * 执行节点逻辑
     * <p>
     * 读取 {@link AgentState} 中的当前状态，执行业务逻辑，
     * 将结果写回 state，并通过 emitter 推送 SSE 事件到前端。
     * </p>
     *
     * @param state   当前图状态（会被修改以传递结果给下游节点）
     * @param emitter SSE 事件发射器（推送 thinking/tool_call/tool_result 等事件）
     * @return 节点的执行结果文本（可能为 null，取决于节点类型）
     */
    String execute(AgentState state, FluxSink<String> emitter);

    /**
     * 返回节点名称（用于日志和 SSE 事件标记）
     *
     * @return 节点名称，如 "supervisor"、"research"、"math"、"code"、"summarizer"
     */
    String getName();
}
