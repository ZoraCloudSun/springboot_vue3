package com.zora.agent.graph;

import java.util.ArrayList;
import java.util.List;

/**
 * Agent 图状态（Phase 3.5 — 多 Agent 编排）
 * <p>
 * 作为多 Agent 协作流程中的"黑板"，各 Agent 节点通过读写此状态对象来传递信息。
 * 不使用数据库持久化，仅在单次对话请求的内存中使用。
 * </p>
 *
 * <h3>状态流转</h3>
 * <pre>{@code
 * 用户消息 → SupervisorAgent 写入 intent
 *   → AgentGraph 根据 intent 路由到 Specialist
 *     → Specialist 执行后将结果写入 specialistResults
 *       → Summarizer 读取所有 specialistResults 聚合最终回答
 * }</pre>
 *
 * <h3>设计原则</h3>
 * <ul>
 * <li><b>可变状态</b>：各 Agent 节点直接修改此对象，避免不可变拷贝的性能开销</li>
 * <li><b>透明追踪</b>：specialistCallCount 用于限制最多调用次数，防止无限循环</li>
 * <li><b>可扩展</b>：新增 Specialist 类型只需在路由表中注册，无需修改此状态类</li>
 * </ul>
 */
public class AgentState {

    /** 用户原始消息 */
    private String userMessage;

    /** 当前对话的 LangChain4j 消息列表（包含历史 + 系统提示词） */
    private List<dev.langchain4j.data.message.ChatMessage> messages;

    /** Supervisor 分类结果：research / math / code / general */
    private String intent;

    /** 当前活跃的 Specialist Agent 名称（用于 SSE 事件标记） */
    private String activeSpecialist;

    /** 各 Specialist 的执行结果列表 */
    private List<SpecialistResult> specialistResults = new ArrayList<>();

    /** Specialist 调用次数计数器（限制最多 {@code maxSpecialistCalls} 次） */
    private int specialistCallCount = 0;

    /** 单次对话最多调用的 Specialist 次数 */
    private int maxSpecialistCalls = 3;

    // ==================== 便捷方法 ====================

    /**
     * 添���一个 Specialist 执行结果并递增调用计数
     *
     * @param result Specialist 执行结果
     */
    public void addSpecialistResult(SpecialistResult result) {
        this.specialistResults.add(result);
        this.specialistCallCount++;
    }

    /**
     * 检查是否还能继续调用 Specialist
     *
     * @return true 如果调用次数未达上限
     */
    public boolean canCallSpecialist() {
        return specialistCallCount < maxSpecialistCalls;
    }

    /**
     * 获取所有 Specialist 结果的文本摘要（供 Summarizer 使用）
     *
     * @return 格式化的结果文本，每个结果一行
     */
    public String getResultsSummary() {
        if (specialistResults.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < specialistResults.size(); i++) {
            SpecialistResult r = specialistResults.get(i);
            sb.append("【").append(r.agentName()).append("】执行结果：\n");
            sb.append(r.result()).append("\n\n");
        }
        return sb.toString();
    }

    // ==================== getter / setter ====================

    public String getUserMessage() { return userMessage; }
    public void setUserMessage(String userMessage) { this.userMessage = userMessage; }

    public List<dev.langchain4j.data.message.ChatMessage> getMessages() { return messages; }
    public void setMessages(List<dev.langchain4j.data.message.ChatMessage> messages) { this.messages = messages; }

    public String getIntent() { return intent; }
    public void setIntent(String intent) { this.intent = intent; }

    public String getActiveSpecialist() { return activeSpecialist; }
    public void setActiveSpecialist(String activeSpecialist) { this.activeSpecialist = activeSpecialist; }

    public List<SpecialistResult> getSpecialistResults() { return specialistResults; }
    public void setSpecialistResults(List<SpecialistResult> specialistResults) { this.specialistResults = specialistResults; }

    public int getSpecialistCallCount() { return specialistCallCount; }
    public void setSpecialistCallCount(int specialistCallCount) { this.specialistCallCount = specialistCallCount; }

    public int getMaxSpecialistCalls() { return maxSpecialistCalls; }
    public void setMaxSpecialistCalls(int maxSpecialistCalls) { this.maxSpecialistCalls = maxSpecialistCalls; }

    // ==================== 内部类 ====================

    /**
     * Specialist Agent 执行结果记录
     *
     * @param agentName Agent 名称（如 "research"、"math"、"code"）
     * @param toolName  使用的工具名称（如 "searchWeb"、"calculate"、"executeCode"）
     * @param result    工具执行结果或 Agent 生成的分析文本
     */
    public record SpecialistResult(String agentName, String toolName, String result) {
        /**
         * 创建不含工具的纯分析结果
         */
        public SpecialistResult(String agentName, String result) {
            this(agentName, "none", result);
        }
    }
}
