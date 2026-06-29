<template>
  <div class="chat-container">
    <!-- 侧边栏 -->
    <aside class="sidebar" :class="{ collapsed: sidebarCollapsed }">
      <!-- 侧边栏头部：Logo + 新对话 -->
      <div class="sidebar-top">
        <div class="sidebar-brand" v-if="!sidebarCollapsed">
          <div class="brand-icon">
            <svg xmlns="http://www.w3.org/2000/svg" width="20" height="20" fill="currentColor" class="bi bi-robot" viewBox="0 0 16 16">
              <path d="M6 12.5a.5.5 0 0 1 .5-.5h3a.5.5 0 0 1 0 1h-3a.5.5 0 0 1-.5-.5M3 8.062C3 6.76 4.235 5.765 5.53 5.886a26.6 26.6 0 0 0 4.94 0C11.765 5.765 13 6.76 13 8.062v1.157a.93.93 0 0 1-.765.935c-.845.147-2.34.346-4.235.346s-3.39-.2-4.235-.346A.93.93 0 0 1 3 9.219zm4.542-.827a.25.25 0 0 0-.217.068l-.92.9a25 25 0 0 1-1.871-.183.25.25 0 0 0-.068.495c.55.076 1.232.149 2.02.193a.25.25 0 0 0 .189-.071l.754-.736.847 1.71a.25.25 0 0 0 .404.062l.932-.97a25 25 0 0 0 1.922-.188.25.25 0 0 0-.068-.495c-.538.074-1.207.145-1.98.189a.25.25 0 0 0-.166.076l-.754.785-.842-1.7a.25.25 0 0 0-.182-.135"/>
              <path d="M8.5 1.866a1 1 0 1 0-1 0V3h-2A4.5 4.5 0 0 0 1 7.5V8a1 1 0 0 0-1 1v2a1 1 0 0 0 1 1v1a2 2 0 0 0 2 2h10a2 2 0 0 0 2-2v-1a1 1 0 0 0 1-1V9a1 1 0 0 0-1-1v-.5A4.5 4.5 0 0 0 10.5 3h-2zM14 7.5V13a1 1 0 0 1-1 1H3a1 1 0 0 1-1-1V7.5A3.5 3.5 0 0 1 5.5 4h5A3.5 3.5 0 0 1 14 7.5"/>
            </svg>
          </div>
          <span class="brand-text">AI 对话</span>
          <el-tooltip content="收起侧边栏" placement="bottom" :show-after="400" :hide-after="0">
            <button class="collapse-trigger" @click="sidebarCollapsed = true">
              <svg xmlns="http://www.w3.org/2000/svg" width="20" height="20" fill="currentColor" viewBox="0 0 16 16">
                <path d="M8.5 3a4 4 0 0 0-3.8 2.745.5.5 0 1 1-.949-.313 5.002 5.002 0 0 1 9.654.595A3 3 0 0 1 13 12H4.5a.5.5 0 0 1 0-1H13a2 2 0 0 0 .001-4h-.026a.5.5 0 0 1-.5-.445A4 4 0 0 0 8.5 3M0 7.5A.5.5 0 0 1 .5 7h5a.5.5 0 0 1 0 1h-5a.5.5 0 0 1-.5-.5m2 2a.5.5 0 0 1 .5-.5h9a.5.5 0 0 1 0 1h-9a.5.5 0 0 1-.5-.5m-2 4a.5.5 0 0 1 .5-.5h9a.5.5 0 0 1 0 1h-9a.5.5 0 0 1-.5-.5"/>
              </svg>
            </button>
          </el-tooltip>
        </div>
        <button
          v-if="!sidebarCollapsed"
          class="new-chat-btn"
          @click="handleNewChat"
        >
          <el-icon :size="16"><Plus /></el-icon>
          <span>新建对话</span>
        </button>
        <el-tooltip
          v-if="sidebarCollapsed"
          content="展开侧边栏"
          placement="right"
          :show-after="400"
          :hide-after="0"
        >
          <button
            class="expand-trigger"
            @click="sidebarCollapsed = false"
          >
            <svg xmlns="http://www.w3.org/2000/svg" width="18" height="18" fill="currentColor" viewBox="0 0 16 16"
                 style="transform: scaleX(-1)">
              <path d="M8.5 3a4 4 0 0 0-3.8 2.745.5.5 0 1 1-.949-.313 5.002 5.002 0 0 1 9.654.595A3 3 0 0 1 13 12H4.5a.5.5 0 0 1 0-1H13a2 2 0 0 0 .001-4h-.026a.5.5 0 0 1-.5-.445A4 4 0 0 0 8.5 3M0 7.5A.5.5 0 0 1 .5 7h5a.5.5 0 0 1 0 1h-5a.5.5 0 0 1-.5-.5m2 2a.5.5 0 0 1 .5-.5h9a.5.5 0 0 1 0 1h-9a.5.5 0 0 1-.5-.5m-2 4a.5.5 0 0 1 .5-.5h9a.5.5 0 0 1 0 1h-9a.5.5 0 0 1-.5-.5"/>
            </svg>
          </button>
        </el-tooltip>
      </div>

      <!-- 搜索框 -->
      <div class="sidebar-search" v-if="!sidebarCollapsed">
        <div class="search-input-wrap">
          <el-icon class="search-icon" :size="14"><Search /></el-icon>
          <input
            v-model="searchQuery"
            class="search-input"
            placeholder="搜索对话..."
            @input="handleSearch"
          />
        </div>
      </div>

      <!-- 对话列表（正常模式 / 回收站模式） -->
      <div class="sidebar-list" v-if="!sidebarCollapsed">
        <!-- 正常对话列表 -->
        <template v-if="!showTrash">
          <template v-if="groupedConversations.length > 0">
            <!-- 批量管理控制条（仅在批量管理模式显示） -->
            <div class="batch-control-bar" v-if="batchManageMode">
              <el-checkbox
                :model-value="isAllSelected"
                :indeterminate="selectedConvIds.size > 0 && !isAllSelected"
                size="small"
                @change="isAllSelected ? deselectAll() : selectAllVisible()"
              >
                全选
              </el-checkbox>
              <span v-if="selectedConvIds.size > 0" class="batch-count">已选 {{ selectedConvIds.size }} 项</span>
              <button class="batch-exit-btn" @click="exitBatchManage">完成</button>
            </div>
            <div
              v-for="group in groupedConversations"
              :key="group.label"
              class="conv-group"
            >
              <div class="group-label">{{ group.label }}</div>
              <div
                v-for="conv in group.items"
                :key="conv.id"
                class="conv-item"
                :class="{ active: conv.id === currentConversationId, selected: selectedConvIds.has(conv.id) }"
                @click="batchManageMode ? toggleSelectConv(conv.id) : handleSelectConversation(conv.id)"
              >
                <!-- 批量选择复选框（仅在批量管理模式显示） -->
                <el-checkbox
                  v-if="batchManageMode"
                  :model-value="selectedConvIds.has(conv.id)"
                  class="conv-checkbox"
                  @click.stop
                  @change="toggleSelectConv(conv.id)"
                />
                <div class="conv-item-content">
                  <div class="conv-item-title">{{ conv.title }}</div>
                </div>
                <!-- "..." 菜单按钮（hover 显示，批量模式隐藏） -->
                <div class="conv-menu-wrap" v-if="!batchManageMode" @click.stop>
                  <el-tooltip content="更多操作" placement="top" :show-after="500" :hide-after="0">
                    <button
                      class="conv-item-menu"
                      @click="toggleConvMenu(conv.id, $event)"
                    >
                      <el-icon :size="14"><MoreFilled /></el-icon>
                    </button>
                  </el-tooltip>
                </div>
              </div>
            </div>
          </template>
          <div v-else class="empty-state">
            <el-icon :size="32" class="empty-icon"><ChatLineRound /></el-icon>
            <span class="empty-text">暂无对话</span>
          </div>
        </template>

        <!-- 回收站列表 -->
        <template v-else>
          <div class="trash-header">
            <div class="trash-header-left">
              <span class="trash-title">回收站</span>
              <span class="trash-hint">30 天后自动清除</span>
            </div>
            <button
              v-if="deletedConversations.length > 0 && !batchManageMode"
              class="trash-batch-btn"
              @click="enterBatchManage()"
            >
              <el-icon :size="14"><List /></el-icon>
              <span>批量管理</span>
            </button>
          </div>
          <!-- 批量管理控制条（仅在批量管理模式显示） -->
          <div class="batch-control-bar" v-if="batchManageMode && deletedConversations.length > 0">
            <el-checkbox
              :model-value="isAllSelected"
              :indeterminate="selectedConvIds.size > 0 && !isAllSelected"
              size="small"
              @change="isAllSelected ? deselectAll() : selectAllVisible()"
            >
              全选
            </el-checkbox>
            <span v-if="selectedConvIds.size > 0" class="batch-count">已选 {{ selectedConvIds.size }} 项</span>
            <button class="batch-exit-btn" @click="exitBatchManage">完成</button>
          </div>
          <template v-if="deletedConversations.length > 0">
            <div
              v-for="conv in deletedConversations"
              :key="conv.id"
              class="conv-item trash-item"
              :class="{ selected: selectedConvIds.has(conv.id) }"
              @click="batchManageMode ? toggleSelectConv(conv.id) : undefined"
            >
              <!-- 批量选择复选框（仅在批量管理模式显示） -->
              <el-checkbox
                v-if="batchManageMode"
                :model-value="selectedConvIds.has(conv.id)"
                class="conv-checkbox"
                @click.stop
                @change="toggleSelectConv(conv.id)"
              />
              <div class="conv-item-content">
                <div class="conv-item-title">{{ conv.title }}</div>
                <div class="conv-item-meta">{{ formatDeletedTime(conv.deletedAt) }}</div>
              </div>
              <!-- 操作按钮（仅图标 + hover tooltip，批量模式隐藏） -->
              <div v-if="!batchManageMode" class="trash-actions">
                <el-tooltip content="恢复对话" placement="top" :show-after="400">
                  <button
                    class="trash-action-btn restore"
                    @click.stop="handleRestore(conv.id)"
                  >
                    <el-icon :size="15"><RefreshLeft /></el-icon>
                  </button>
                </el-tooltip>
                <el-tooltip content="永久删除" placement="top" :show-after="400">
                  <button
                    class="trash-action-btn danger"
                    @click.stop="handlePermanentDelete(conv.id)"
                  >
                    <el-icon :size="15"><Delete /></el-icon>
                  </button>
                </el-tooltip>
              </div>
            </div>
          </template>
          <div v-else class="empty-state">
            <el-icon :size="32" class="empty-icon"><Delete /></el-icon>
            <span class="empty-text">回收站为空</span>
          </div>
        </template>
      </div>

      <!-- 批量操作栏 -->
      <Transition name="batch-bar">
        <div v-if="batchMode && batchManageMode && !sidebarCollapsed" class="batch-action-bar">
          <span class="batch-action-count">已选 {{ selectedConvIds.size }} 项</span>
          <div class="batch-action-btns">
            <!-- 正常模式：批量删除 -->
            <template v-if="!showTrash">
              <button class="batch-btn batch-btn-delete" @click="handleBatchDelete">
                <el-icon :size="12"><Delete /></el-icon>
                批量删除
              </button>
            </template>
            <!-- 回收站模式：批量恢复 + 批量永久删除（仅图标 + hover tooltip） -->
            <template v-else>
              <el-tooltip content="批量恢复" placement="top" :show-after="400">
                <button class="batch-btn batch-btn-icon batch-btn-restore" @click="handleBatchRestore">
                  <el-icon :size="14"><RefreshLeft /></el-icon>
                </button>
              </el-tooltip>
              <el-tooltip content="批量永久删除" placement="top" :show-after="400">
                <button class="batch-btn batch-btn-icon batch-btn-danger" @click="handleBatchPermanentDelete">
                  <el-icon :size="14"><Delete /></el-icon>
                </button>
              </el-tooltip>
            </template>
            <button class="batch-btn batch-btn-cancel" @click="deselectAll">取消</button>
          </div>
        </div>
      </Transition>
            <!-- Phase 4 智能推荐卡片 -->
      <RecommendCard
        v-if="!sidebarCollapsed && !showTrash"
        @ask-question="handleRecommendedQuestion"
      />

      <!-- 侧边栏底部 -->
      <div class="sidebar-footer" v-if="!sidebarCollapsed">
        <button
          class="footer-btn"
          :class="{ active: showTrash }"
          @click="toggleTrash"
        >
          <el-icon :size="16"><Delete /></el-icon>
          <span>回收站</span>
          <span v-if="deletedConversations.length" class="trash-badge">
            {{ deletedConversations.length }}
          </span>
        </button>
        <button class="footer-btn" @click="$router.push('/knowledge')">
          <el-icon :size="16"><Collection /></el-icon>
          <span>知识库</span>
        </button>
        <button class="footer-btn" @click="$router.push('/dashboard')">
          <el-icon :size="16"><DataAnalysis /></el-icon>
          <span>仪表盘</span>
        </button>
        <button class="footer-btn" @click="$router.push('/home')">
          <el-icon :size="16"><HomeFilled /></el-icon>
          <span>返回主页</span>
        </button>
      </div>
    </aside>

    <!-- 主聊天区 -->
    <main class="chat-main">
      <!-- 顶部栏 -->
      <header class="chat-header">
        <div class="header-left">
          <el-tooltip
            v-if="sidebarCollapsed"
            content="展开侧边栏"
            placement="bottom"
            :show-after="400"
            :hide-after="0"
          >
            <button
              class="header-menu-btn"
              @click="sidebarCollapsed = false"
            >
              <svg xmlns="http://www.w3.org/2000/svg" width="20" height="20" fill="currentColor" viewBox="0 0 16 16"
                   style="transform: scaleX(-1)">
                <path d="M8.5 3a4 4 0 0 0-3.8 2.745.5.5 0 1 1-.949-.313 5.002 5.002 0 0 1 9.654.595A3 3 0 0 1 13 12H4.5a.5.5 0 0 1 0-1H13a2 2 0 0 0 .001-4h-.026a.5.5 0 0 1-.5-.445A4 4 0 0 0 8.5 3M0 7.5A.5.5 0 0 1 .5 7h5a.5.5 0 0 1 0 1h-5a.5.5 0 0 1-.5-.5m2 2a.5.5 0 0 1 .5-.5h9a.5.5 0 0 1 0 1h-9a.5.5 0 0 1-.5-.5m-2 4a.5.5 0 0 1 .5-.5h9a.5.5 0 0 1 0 1h-9a.5.5 0 0 1-.5-.5"/>
              </svg>
            </button>
          </el-tooltip>
          <h1 class="header-title">{{ currentTitle || '新对话' }}</h1>
        </div>
        <div class="header-right">
          <span class="header-badge">DeepSeek</span>
        </div>
      </header>

      <!-- Phase 3.3: 推理面板已移入 AI 消息气泡内部 -->

      <!-- 消息列表 -->
      <div class="chat-messages" ref="messagesContainer">
        <!-- 欢迎页 -->
        <div v-if="messages.length === 0 && !isStreaming" class="welcome-screen">
          <div class="welcome-logo">
            <div class="logo-ring">
              <el-icon :size="36"><ChatDotRound /></el-icon>
            </div>
          </div>
          <h2 class="welcome-title">有什么我可以帮你的？</h2>
          <p class="welcome-sub">我可以帮你编写代码、分析数据、回答问题</p>
          <div class="suggestion-grid">
            <button
              v-for="q in suggestions"
              :key="q"
              class="suggestion-card"
              @click="handleSuggestion(q)"
            >
              <span>{{ q }}</span>
            </button>
          </div>
        </div>

        <!-- 消息列表 -->
        <div
          v-for="(msg, index) in messages"
          :key="index"
          class="message-row"
          :class="msg.role"
          :data-msg-idx="msg.role === 'user' ? index : undefined"
        >
          <div class="message-body">
            <div class="message-sender">
              <!-- 头像内联到发送者行，与输入框边缘对齐 -->
              <div v-if="msg.role === 'assistant'" class="avatar avatar-ai">
                <svg xmlns="http://www.w3.org/2000/svg" width="16" height="16" fill="currentColor" class="bi bi-robot" viewBox="0 0 16 16">
                  <path d="M6 12.5a.5.5 0 0 1 .5-.5h3a.5.5 0 0 1 0 1h-3a.5.5 0 0 1-.5-.5M3 8.062C3 6.76 4.235 5.765 5.53 5.886a26.6 26.6 0 0 0 4.94 0C11.765 5.765 13 6.76 13 8.062v1.157a.93.93 0 0 1-.765.935c-.845.147-2.34.346-4.235.346s-3.39-.2-4.235-.346A.93.93 0 0 1 3 9.219zm4.542-.827a.25.25 0 0 0-.217.068l-.92.9a25 25 0 0 1-1.871-.183.25.25 0 0 0-.068.495c.55.076 1.232.149 2.02.193a.25.25 0 0 0 .189-.071l.754-.736.847 1.71a.25.25 0 0 0 .404.062l.932-.97a25 25 0 0 0 1.922-.188.25.25 0 0 0-.068-.495c-.538.074-1.207.145-1.98.189a.25.25 0 0 0-.166.076l-.754.785-.842-1.7a.25.25 0 0 0-.182-.135"/>
                  <path d="M8.5 1.866a1 1 0 1 0-1 0V3h-2A4.5 4.5 0 0 0 1 7.5V8a1 1 0 0 0-1 1v2a1 1 0 0 0 1 1v1a2 2 0 0 0 2 2h10a2 2 0 0 0 2-2v-1a1 1 0 0 0 1-1V9a1 1 0 0 0-1-1v-.5A4.5 4.5 0 0 0 10.5 3h-2zM14 7.5V13a1 1 0 0 1-1 1H3a1 1 0 0 1-1-1V7.5A3.5 3.5 0 0 1 5.5 4h5A3.5 3.5 0 0 1 14 7.5"/>
                </svg>
              </div>
              <span>{{ msg.role === 'user' ? '你' : 'AI' }}</span>
              <div v-if="msg.role === 'user'" class="avatar avatar-user">
                <el-icon :size="16"><UserFilled /></el-icon>
              </div>
            </div>

            <!-- Agent 推理过程（嵌入 AI 消息气泡内） -->
            <div
              v-if="msg.role === 'assistant' && msg.reasoningSteps && msg.reasoningSteps.length > 0"
              class="reasoning-inline"
            >
              <button
                class="reasoning-toggle"
                @click="msg.showReasoning = !msg.showReasoning"
              >
                <el-icon :size="12"><Cpu /></el-icon>
                <span>推理过程 ({{ msg.reasoningSteps.length }} 步)</span>
                <el-icon :size="12"><ArrowUp v-if="msg.showReasoning" /><ArrowDown v-else /></el-icon>
              </button>
              <Transition name="reasoning-panel">
                <div v-if="msg.showReasoning" class="reasoning-steps-inline reasoning-done">
                  <div
                    v-for="(step, idx) in msg.reasoningSteps"
                    :key="idx"
                    class="reasoning-step"
                    :class="'step-' + step.type"
                  >
                    <template v-if="step.type === 'thinking'">
                      <span class="step-icon step-icon-think">
                        <el-icon :size="12"><Loading /></el-icon>
                      </span>
                      <span class="step-text">{{ step.content }}</span>
                    </template>
                    <template v-else-if="step.type === 'tool_call'">
                      <span class="step-icon step-icon-tool">
                        <el-icon :size="12"><Tools /></el-icon>
                      </span>
                      <span class="step-label">调用工具：</span>
                      <span class="step-tool-name">{{ step.tool }}</span>
                      <span class="step-args" v-if="step.args && Object.keys(step.args).length">
                        <code>{{ formatToolArgs(step.args) }}</code>
                      </span>
                    </template>
                    <template v-else-if="step.type === 'tool_result'">
                      <span class="step-icon step-icon-result">
                        <el-icon :size="12"><CircleCheck /></el-icon>
                      </span>
                      <span class="step-label">工具返回：</span>
                      <span class="step-result-text">{{ formatToolResult(step.content) }}</span>
                    </template>
                  </div>
                </div>
              </Transition>
            </div>

            <div
              v-if="msg.role === 'assistant'"
              class="message-text markdown-body"
              v-html="renderMarkdown(msg.content)"
            ></div>
            <div v-else class="message-text message-text-user">{{ msg.content }}</div>
            <div v-if="msg.role === 'assistant' && msg.content" class="message-actions">
              <button class="action-btn" @click="copyContent(msg.content)" title="复制">
                <el-icon :size="14"><CopyDocument /></el-icon>
              </button>
            </div>
          </div>
        </div>

        <!-- 流式生成中 -->
        <div v-if="isStreaming" class="message-row assistant">
          <div class="message-body">
            <div class="message-sender">
              <div class="avatar avatar-ai">
                <el-icon :size="16"><ChatDotRound /></el-icon>
              </div>
              <span>AI</span>
              <span v-if="agentMode && !streamingContent" class="agent-thinking-label">
                <el-icon :size="12" class="spin-icon"><Cpu /></el-icon>
                思考中...
              </span>
              <span class="typing-indicator" v-if="!agentMode || streamingContent">
                <span></span><span></span><span></span>
              </span>
            </div>

            <!-- Agent 推理过程（流式消息气泡内，实时更新） -->
            <div
              v-if="agentMode && reasoningSteps.length > 0"
              class="reasoning-inline"
            >
              <button
                class="reasoning-toggle"
                @click="showReasoning = !showReasoning"
              >
                <el-icon :size="12"><Cpu /></el-icon>
                <span>推理过程 ({{ reasoningSteps.length }} 步)</span>
                <el-icon :size="12"><ArrowUp v-if="showReasoning" /><ArrowDown v-else /></el-icon>
              </button>
              <Transition name="reasoning-panel">
                <div v-if="showReasoning" class="reasoning-steps-inline">
                  <!--
                    TransitionGroup: 即使所有步骤同时 push 到数组，
                    CSS transition-delay 会根据 idx 递增，让步骤逐个"飞入"。
                  -->
                  <TransitionGroup name="step-anim" tag="div">
                    <div
                      v-for="(step, idx) in reasoningSteps"
                      :key="step.ts + '-' + idx"
                      class="reasoning-step"
                      :class="'step-' + step.type"
                      :style="{ transitionDelay: (idx * STEP_STAGGER_MS) + 'ms' }"
                    >
                      <template v-if="step.type === 'thinking'">
                        <span class="step-icon step-icon-think">
                          <el-icon :size="12"><Loading /></el-icon>
                        </span>
                        <span class="step-text">{{ step.content }}</span>
                      </template>
                      <template v-else-if="step.type === 'tool_call'">
                        <span class="step-icon step-icon-tool">
                          <el-icon :size="12"><Tools /></el-icon>
                        </span>
                        <span class="step-label">调用工具：</span>
                        <span class="step-tool-name">{{ step.tool }}</span>
                        <span class="step-args" v-if="step.args && Object.keys(step.args).length">
                          <code>{{ formatToolArgs(step.args) }}</code>
                        </span>
                      </template>
                      <template v-else-if="step.type === 'tool_result'">
                        <span class="step-icon step-icon-result">
                          <el-icon :size="12"><CircleCheck /></el-icon>
                        </span>
                        <span class="step-label">工具返回：</span>
                        <span class="step-result-text">{{ formatToolResult(step.content) }}</span>
                      </template>
                    </div>
                  </TransitionGroup>
                </div>
              </Transition>
            </div>

            <!-- Agent 推理中暂未输出 token 时显示占位提示 -->
            <div
              v-if="agentMode && !streamingContent && reasoningSteps.length > 0"
              class="message-text agent-thinking-text"
            >
              正在分析问题，请稍候...
            </div>
            <div
              v-else-if="streamingContent"
              class="message-text markdown-body"
              v-html="renderMarkdown(streamingContent)"
            ></div>
          </div>
        </div>

        <!-- 滚动到底部按钮（离开底部时显示，流式中带加载动画） -->
        <Transition name="scroll-btn">
          <button
            v-if="showScrollBtn"
            class="scroll-bottom-btn"
            :class="{ streaming: isStreaming }"
            @click="handleScrollToBottom"
          >
            <svg
              class="scroll-btn-ring"
              :class="{ spinning: isStreaming }"
              viewBox="0 0 36 36"
            >
              <circle cx="18" cy="18" r="15" fill="none" stroke="#e0ddd6" stroke-width="2" />
              <circle
                class="ring-progress"
                cx="18" cy="18" r="15" fill="none" stroke="#3b82f6" stroke-width="2"
                stroke-linecap="round"
                stroke-dasharray="94.2" stroke-dashoffset="94.2"
              />
            </svg>
            <el-icon :size="16" class="scroll-btn-icon"><ArrowDown /></el-icon>
          </button>
        </Transition>
      </div>

      <!-- 右侧对话时间轴（独立于消息滚动区，始终可见） -->
      <div
        class="conv-timeline"
        :class="{ expanded: timelineHovered }"
        @mouseenter="timelineHovered = true"
        @mouseleave="timelineHovered = false"
      >
        <div class="timeline-track">
          <div
            v-for="(item, idx) in userMessageAnchors"
            :key="idx"
            class="timeline-segment"
            @click="scrollToUserMessage(item.index)"
          >
            <div class="timeline-bar"></div>
            <Transition name="timeline-tip">
              <div v-if="timelineHovered" class="timeline-tooltip">
                {{ truncateText(item.preview, 40) }}
              </div>
            </Transition>
          </div>
          <div v-if="userMessageAnchors.length === 0" class="timeline-empty">
            <div class="timeline-bar placeholder"></div>
          </div>
        </div>
      </div>

      <!-- 输入区域 -->
      <div class="chat-input-area">
        <div class="input-box">
          <textarea
            v-model="inputMessage"
            class="input-textarea"
            placeholder="输入你的问题..."
            :disabled="isStreaming"
            @keydown.enter.exact="handleSend"
            @input="autoResize"
            rows="1"
            ref="textareaRef"
          ></textarea>
          <!-- 药丸按钮 + 发送（千问风格：功能按钮在输入框内部） -->
          <div class="input-actions">
            <div class="input-pills">
              <!-- 上传按钮（+ 号，点击展开选项） -->
              <div class="upload-btn-wrap">
                <button
                  class="pill-btn pill-upload"
                  :class="{ open: uploadMenuOpen }"
                  title="上传文件"
                  @click.stop="uploadMenuOpen = !uploadMenuOpen"
                >
                  <el-icon :size="14"><Plus /></el-icon>
                </button>
                <Transition name="upload-menu">
                  <div v-if="uploadMenuOpen" class="upload-dropdown">
                    <button class="upload-dropdown-item" @click.stop="handleUpload('image')">
                      <el-icon :size="14"><Picture /></el-icon>
                      <span>上传图片</span>
                    </button>
                    <button class="upload-dropdown-item" @click.stop="handleUpload('document')">
                      <el-icon :size="14"><Document /></el-icon>
                      <span>上传文档</span>
                    </button>
                  </div>
                </Transition>
              </div>
              <button
                class="pill-btn"
                :class="{ active: agentMode }"
                @click="agentMode = !agentMode"
                title="Agent 智能体：工具调用 + 推理"
              >
                <el-icon :size="14"><Cpu /></el-icon>
                <span>Agent</span>
              </button>
              <button
                class="pill-btn"
                :class="{ active: ragEnabled }"
                @click="ragEnabled = !ragEnabled"
                title="RAG 知识库：基于文档增强回答"
              >
                <el-icon :size="14"><Collection /></el-icon>
                <span>RAG</span>
              </button>
              <!-- RAG 知识库选择器（RAG 开启时显示） -->
              <el-select
                v-if="ragEnabled && knowledgeBases.length > 0"
                v-model="selectedKbId"
                placeholder="选择知识库"
                size="small"
                style="width: 130px"
                clearable
              >
                <el-option
                  v-for="kb in knowledgeBases"
                  :key="kb.id"
                  :label="kb.name"
                  :value="kb.id"
                />
              </el-select>
            </div>
            <div class="input-send-area">
              <span class="input-hint">
                <span v-if="inputMessage.length > MAX_MESSAGE_LENGTH * 0.8"
                      :class="{ 'char-warn': inputMessage.length > MAX_MESSAGE_LENGTH }">
                  {{ inputMessage.length }}/{{ MAX_MESSAGE_LENGTH }}
                </span>
                <span v-else>Enter 发送，Shift+Enter 换行</span>
              </span>
              <button
                v-if="!isStreaming"
                class="send-btn"
                :class="{ active: inputMessage.trim() }"
                :disabled="!inputMessage.trim()"
                @click="handleSend"
              >
                <el-icon :size="16"><Promotion /></el-icon>
              </button>
              <button
                v-else
                class="stop-btn"
                @click="handleStop"
              >
                <el-icon :size="14"><VideoPause /></el-icon>
                <span>停止</span>
              </button>
            </div>
          </div>
        </div>
        <!-- 状态提示（保留在输入框下方） -->
        <div class="input-footer">
          <template v-if="agentMode">
            <span class="footer-status">
              <el-icon :size="12" color="#1677ff"><Cpu /></el-icon>
              Agent 智能体已启用（工具调用 + 推理）
            </span>
          </template>
          <template v-if="ragEnabled && selectedKbId">
            <span class="footer-status">
              <el-icon :size="12" color="#67c23a"><CircleCheckFilled /></el-icon>
              已从知识库检索上下文增强回答
            </span>
          </template>
          <span class="input-disclaimer">AI 由 DeepSeek 大模型驱动，内容仅供参考</span>
        </div>
      </div>
    </main>

  </div>

  <!-- "..." 右键菜单（Teleport 到 body，使用 fixed 定位，向下右弹出） -->
  <Teleport to="body">
    <div
      v-if="contextMenu.convId !== null"
      class="conv-menu-dropdown"
      :style="{ left: contextMenu.x + 'px', top: contextMenu.y + 'px' }"
      @click.stop
    >
      <button class="menu-item menu-item-delete" @click="handleDeleteConversation(contextMenu.convId); closeConvMenu()">
        <el-icon :size="14"><Delete /></el-icon>
        <span>删除</span>
      </button>
      <button class="menu-item" @click="enterBatchManage()">
        <el-icon :size="14"><List /></el-icon>
        <span>批量管理</span>
      </button>
    </div>
  </Teleport>
</template>

<script setup>
import { ref, nextTick, watch, onMounted, onUnmounted, computed } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  Plus, Fold, Expand, Delete, HomeFilled, ChatDotRound,
  Picture, Document,
  CopyDocument, Promotion, VideoPause, Search, ChatLineRound,
  UserFilled, RefreshLeft, Collection, CircleCheckFilled,
  // Phase 3.3: Agent 推理面板图标
  Cpu, Loading, Tools, CircleCheck, ArrowUp, ArrowDown, MoreFilled, List,
  // Phase 4: 仪表盘 + 推荐图标
  DataAnalysis,
  Upload,
} from '@element-plus/icons-vue'
import { marked } from 'marked'
import hljs from 'highlight.js'
import DOMPurify from 'dompurify'
import 'highlight.js/styles/github.css'
import katex from 'katex'
import 'katex/dist/katex.min.css'
import {
  getConversations, createConversation, getConversationMessages,
  deleteConversation, streamChat,
  getDeletedConversations, restoreConversation, permanentDeleteConversation,
  batchDeleteConversations, batchRestoreConversations, batchPermanentDeleteConversations,
} from '@/api/ai'
import { streamRagChat, listKnowledgeBases } from '@/api/rag'
// Phase 3.3: Agent SSE 流式对话
import { streamAgentChat } from '@/api/agent'
// Phase 4: 智能推荐卡片
import RecommendCard from '@/components/RecommendCard.vue'

const router = useRouter()
const messagesContainer = ref(null)
const textareaRef = ref(null)
const sidebarCollapsed = ref(false)

// 对话状态
const conversations = ref([])
const currentConversationId = ref(null)
const messages = ref([])
const inputMessage = ref('')
const isStreaming = ref(false)
const streamingContent = ref('')
const searchQuery = ref('')
let abortController = null

// ==================== 上传菜单状态 ====================
const uploadMenuOpen = ref(false)

const handleUpload = (type) => {
  uploadMenuOpen.value = false
  const label = type === 'image' ? '上传图片' : '上传文档'
  ElMessage.info(`${label}功能即将上线`)
}

// 点击外部关闭上传菜单
const handleUploadClickOutside = (e) => {
  if (!e.target.closest('.upload-btn-wrap')) {
    uploadMenuOpen.value = false
  }
}

// ==================== Phase 2: RAG 知识库状态 ====================
const ragEnabled = ref(false)
const knowledgeBases = ref([])
const selectedKbId = ref(null)

// ==================== Phase 3.3: Agent 智能体状态 ====================
const agentMode = ref(false)          // Agent 模式开关
const reasoningSteps = ref([])        // 推理步骤数组 [{type, content/tool/args, ts}]
const showReasoning = ref(true)       // 推理面板展开/折叠

/**
 * 推理步骤渲染队列 —— SSE 事件先入队，setInterval 每 800ms 出队一个
 *
 * 核心原理：setInterval 的回调是独立的宏任务，每次 push 触发独立的 Vue 渲染周期。
 * 无论 SSE 事件是瞬间全部到达还是分散到达，前端都以固定 800ms 节奏逐个显示。
 */
let pendingSteps = []
let stepTimer = null
let pendingTokens = []
let answerRevealed = false
let streamEnded = false      // SSE 流是否已结束（onDone 已到达）
let doneConversationId = null // onDone 传来的 conversationId，interval 收尾时用

// 回收站状态
const showTrash = ref(false)
const deletedConversations = ref([])

// ==================== 批量选择状态 ====================
const selectedConvIds = ref(new Set())      // 选中的对话 ID 集合
const batchManageMode = ref(false)          // 批量管理模式（显示复选框 + 全选条）
const contextMenu = ref({ convId: null, x: 0, y: 0 })  // "..." 菜单状态：打开的对话 ID + 固定定位坐标

/** 是否处于批量选择模式（有选中项） */
const batchMode = computed(() => selectedConvIds.value.size > 0)

/** 切换 "..." 右键菜单的开/关，捕获按钮坐标用于 Teleport 定位 */
const toggleConvMenu = (convId, event) => {
  if (contextMenu.value.convId === convId) {
    closeConvMenu()
    return
  }
  const rect = event.currentTarget.getBoundingClientRect()
  contextMenu.value = {
    convId,
    x: rect.right + 4,     // 按钮右边缘 + 4px 间距，向右弹出
    y: rect.bottom + 2,    // 按钮下边缘 + 2px，向下弹出
  }
}

/** 关闭 "..." 右键菜单 */
const closeConvMenu = () => {
  contextMenu.value = { convId: null, x: 0, y: 0 }
}

/** 进入批量管理模式 */
const enterBatchManage = () => {
  batchManageMode.value = true
  closeConvMenu()
}

/** 退出批量管理模式（清空选中 + 隐藏复选框） */
const exitBatchManage = () => {
  batchManageMode.value = false
  selectedConvIds.value = new Set()
}

/** 点击菜单外部时关闭 "..." 右键菜单（Teleport 到 body 后需同时检查按钮和菜单） */
const handleClickOutside = (e) => {
  if (contextMenu.value.convId !== null &&
      !e.target.closest('.conv-item-menu') &&
      !e.target.closest('.conv-menu-dropdown')) {
    closeConvMenu()
  }
}

/** 切换单个对话的选中状态 */
const toggleSelectConv = (id) => {
  const next = new Set(selectedConvIds.value)
  if (next.has(id)) {
    next.delete(id)
  } else {
    next.add(id)
  }
  selectedConvIds.value = next  // 替换整个 Set 以触发 Vue 响应式更新
}

/** 当前可见的对话 ID 列表（当前模式下） */
const visibleConvIds = computed(() => {
  if (showTrash.value) {
    return deletedConversations.value.map(c => c.id)
  }
  return groupedConversations.value.flatMap(g => g.items).map(c => c.id)
})

/** 全选当前可见的对话 */
const selectAllVisible = () => {
  selectedConvIds.value = new Set(visibleConvIds.value)
}

/** 取消全选 */
const deselectAll = () => {
  selectedConvIds.value = new Set()
}

/** 是否全选了当前可见的对话 */
const isAllSelected = computed(() => {
  const visible = visibleConvIds.value
  if (visible.length === 0) return false
  return visible.every(id => selectedConvIds.value.has(id))
})

/** 批量删除（移至回收站） */
const handleBatchDelete = async () => {
  const ids = [...selectedConvIds.value]
  if (ids.length === 0) return
  try {
    await ElMessageBox.confirm(
      `确定删除选中的 ${ids.length} 个对话？删除后可在回收站恢复。`,
      '批量删除', { confirmButtonText: '删除', cancelButtonText: '取消', type: 'warning' }
    )
    await batchDeleteConversations(ids)
    ElMessage.success(`已删除 ${ids.length} 个对话`)
    selectedConvIds.value = new Set()
    await loadConversations()
    await loadDeletedConversations()   // 同步更新回收站数目
    if (ids.includes(currentConversationId.value)) handleNewChat()
  } catch { /* 取消或失败 */ }
}

/** 批量恢复 */
const handleBatchRestore = async () => {
  const ids = [...selectedConvIds.value]
  if (ids.length === 0) return
  try {
    await ElMessageBox.confirm(
      `确定恢复选中的 ${ids.length} 个对话？`,
      '批量恢复', { confirmButtonText: '恢复', cancelButtonText: '取消', type: 'info' }
    )
    await batchRestoreConversations(ids)
    ElMessage.success(`已恢复 ${ids.length} 个对话`)
    selectedConvIds.value = new Set()
    await loadDeletedConversations()
    await loadConversations()
  } catch { /* 取消或失败 */ }
}

/** 批量永久删除 */
const handleBatchPermanentDelete = async () => {
  const ids = [...selectedConvIds.value]
  if (ids.length === 0) return
  try {
    await ElMessageBox.confirm(
      `永久删除后无法恢复，确定删除选中的 ${ids.length} 个对话？`,
      '批量永久删除', { confirmButtonText: '永久删除', cancelButtonText: '取消', type: 'error' }
    )
    await batchPermanentDeleteConversations(ids)
    ElMessage.success(`已永久删除 ${ids.length} 个对话`)
    selectedConvIds.value = new Set()
    await loadDeletedConversations()
  } catch { /* 取消或失败 */ }
}

// 欢迎页推荐问题
const suggestions = [
  '用 Java 写一个快速排序',
  'Redis 有哪些数据结构？',
  '帮我写一个 Python 爬虫',
  '解释什么是 RESTful API',
  'Vue 3 Composition API 优势',
  '如何优化 SQL 查询性能？',
]

const currentTitle = computed(() => {
  if (!currentConversationId.value) return null
  const conv = conversations.value.find((c) => c.id === currentConversationId.value)
  return conv?.title || null
})

// ==================== 对话分组 ====================

const groupedConversations = computed(() => {
  const filtered = searchQuery.value
    ? conversations.value.filter((c) =>
        c.title?.toLowerCase().includes(searchQuery.value.toLowerCase())
      )
    : conversations.value

  const groups = { today: [], yesterday: [], earlier: [] }
  const now = new Date()
  const todayStart = new Date(now.getFullYear(), now.getMonth(), now.getDate())
  const yesterdayStart = new Date(todayStart.getTime() - 86400000)

  filtered.forEach((conv) => {
    const date = new Date(conv.updatedAt)
    if (date >= todayStart) {
      groups.today.push(conv)
    } else if (date >= yesterdayStart) {
      groups.yesterday.push(conv)
    } else {
      groups.earlier.push(conv)
    }
  })

  const result = []
  if (groups.today.length) result.push({ label: '今天', items: groups.today })
  if (groups.yesterday.length) result.push({ label: '昨天', items: groups.yesterday })
  if (groups.earlier.length) result.push({ label: '更早', items: groups.earlier })
  return result
})

// ==================== Markdown 渲染（含 LaTeX 数学公式） ====================

marked.setOptions({ breaks: true, gfm: true })

// 注册数学公式扩展
// 块级：$$...$$、\[...\]、\begin{...}...\end{...}
// 行内：$...$、\(...\)  （需放在 marked.use 中，让 marked 先处理）
marked.use({
  extensions: [
    // 块级数学公式
    {
      name: 'mathBlock',
      level: 'block',
      start(src) { return src.match(/\$\$|\\\[|\\begin/)?.index },
      tokenizer(src) {
        // \begin{...} ... \end{...}
        const beginMatch = src.match(/^\\begin\{([^}]+)\}([\s\S]+?)\\end\{\1\}/)
        if (beginMatch) return { type: 'mathBlock', raw: beginMatch[0], text: beginMatch[0].trim() }
        // $$ ... $$
        let match = src.match(/^\$\$([\s\S]+?)\$\$/)
        if (match) return { type: 'mathBlock', raw: match[0], text: match[1].trim() }
        // \[ ... \]
        match = src.match(/^\\\[([\s\S]+?)\\\]/)
        if (match) return { type: 'mathBlock', raw: match[0], text: match[1].trim() }
      },
      renderer(token) {
        try {
          return katex.renderToString(token.text, { displayMode: true, throwOnError: false, trust: true })
        } catch { return `<pre>${escapeHtml(token.text)}</pre>` }
      }
    },
    // 行内数学公式（仅 \(...\)，不用 $ 避免和 markdown 加粗/货币冲突）
    {
      name: 'mathInline',
      level: 'inline',
      start(src) { return src.match(/\\\(/)?.index },
      tokenizer(src) {
        const parenMatch = src.match(/^\\\(([\s\S]+?)\\\)/)
        if (parenMatch) return { type: 'mathInline', raw: parenMatch[0], text: parenMatch[1].trim() }
      },
      renderer(token) {
        try {
          return katex.renderToString(token.text, { displayMode: false, throwOnError: false, trust: true })
        } catch { return escapeHtml(token.text) }
      }
    }
  ]
})

function escapeHtml(str) {
  return str.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;')
}

/**
 * 预处理文本，在 CJK 字符与 ** 分隔符之间插入零宽空格
 * 修复 Marked v18 GFM 规范不识别 CJK 字符旁的 ** 加粗标记的问题
 *
 * 原理：GFM 的 left-flanking delimiter run 要求 ** 前面是 ASCII 标点或空格，
 * CJK 字符不在此列。插入零宽空格（U+200B）可绕过此限制，
 * 且零宽空格在渲染时不可见，不影响显示效果。
 */
function fixCjkBold(text) {
  // GFM 规范下 CJK 字符旁 ** 的分隔符检测不可靠，
  // 直接将含 CJK 的 **...** 转为 <strong> 标签，绕过 marked 的 GFM 解析
  return text.replace(/\*\*(.+?)\*\*/g, (match, content) => {
    if (/[一-鿿]/.test(content)) {
      return '<strong>' + content + '</strong>'
    }
    return match
  })
}

const renderMarkdown = (text) => {
  if (!text) return ''
  try {
    const raw = marked.parse(fixCjkBold(text), {
      highlight: (code, lang) => {
        if (lang && hljs.getLanguage(lang)) {
          try { return hljs.highlight(code, { language: lang }).value } catch {}
        }
        try { return hljs.highlightAuto(code).value } catch {}
        return code
      },
    })
    // ADD_TAGS/ADD_ATTR: 允许 KaTeX 生成的 HTML 元素和属性通过 DOMPurify
    return DOMPurify.sanitize(raw, {
      ADD_TAGS: ['math', 'mrow', 'mi', 'mo', 'mn', 'msup', 'msub', 'mfrac', 'msqrt',
        'mover', 'munder', 'munderover', 'mspace', 'semantics', 'annotation', 'annotation-encoding'],
      ADD_ATTR: ['mathvariant', 'mathsize', 'mathcolor', 'mathbackground', 'dir', 'aria-hidden'],
    })
  } catch {
    return text
  }
}

// ==================== 对话管理 ====================

const loadConversations = async () => {
  try {
    const res = await getConversations()
    conversations.value = res.data || []
  } catch {}
}

const loadKbs = async () => {
  try {
    const res = await listKnowledgeBases()
    knowledgeBases.value = res.data || []
  } catch {}
}

const handleNewChat = () => {
  currentConversationId.value = null
  messages.value = []
  streamingContent.value = ''
  inputMessage.value = ''
}

const handleSelectConversation = async (id) => {
  if (isStreaming.value) { ElMessage.warning('请等待当前对话完成'); return }
  currentConversationId.value = id
  messages.value = []
  streamingContent.value = ''
  try {
    const res = await getConversationMessages(id)
    messages.value = res.data || []
    await scrollToBottom()
  } catch {
    ElMessage.error('加载对话失败')
  }
}

const handleDeleteConversation = async (id) => {
  try {
    await ElMessageBox.confirm('确定删除该对话？', '提示', {
      confirmButtonText: '删除',
      cancelButtonText: '取消',
      type: 'warning',
    })
    await deleteConversation(id)
    ElMessage.success('已删除')
    if (currentConversationId.value === id) handleNewChat()
    await loadConversations()
    await loadDeletedConversations()   // 同步更新回收站数目
  } catch {}
}

const handleSearch = () => {}

// ==================== 回收站 ====================

const toggleTrash = async () => {
  showTrash.value = !showTrash.value
  selectedConvIds.value = new Set()   // 切换模式时清空选中
  batchManageMode.value = false       // 退出批量管理模式
  if (showTrash.value) {
    await loadDeletedConversations()
  }
}

const loadDeletedConversations = async () => {
  try {
    const res = await getDeletedConversations()
    deletedConversations.value = res.data || []
  } catch {}
}

const handleRestore = async (id) => {
  try {
    await restoreConversation(id)
    ElMessage.success('已恢复')
    await loadDeletedConversations()
    await loadConversations()
  } catch {
    ElMessage.error('恢复失败')
  }
}

const handlePermanentDelete = async (id) => {
  try {
    await ElMessageBox.confirm('永久删除后无法恢复，确定继续？', '永久删除', {
      confirmButtonText: '永久删除',
      cancelButtonText: '取消',
      type: 'error',
    })
    await permanentDeleteConversation(id)
    ElMessage.success('已永久删除')
    await loadDeletedConversations()
  } catch {}
}

const formatDeletedTime = (dateStr) => {
  if (!dateStr) return ''
  const date = new Date(dateStr)
  const now = new Date()
  const diff = now - date
  const days = Math.floor(diff / 86400000)
  if (days === 0) return '今天'
  if (days === 1) return '昨天'
  if (days < 30) return `${days} 天前`
  return date.toLocaleDateString('zh-CN', { month: 'short', day: 'numeric' })
}

// ==================== 消息发送与流式接收 ====================

const MAX_MESSAGE_LENGTH = 4000 // P1-2: 与后端一致

const handleSend = async (e) => {
  if (e && e.isComposing) return
  if (e && e.shiftKey) return
  if (e && e.preventDefault) e.preventDefault()
  const msg = inputMessage.value.trim()
  if (!msg || isStreaming.value) return

  // P1-2: 前端消息长度校验
  if (msg.length > MAX_MESSAGE_LENGTH) {
    ElMessage.warning(`消息长度不能超过 ${MAX_MESSAGE_LENGTH} 个字符`)
    return
  }

  inputMessage.value = ''
  // 重置输入框高度（textarea 不会自动收缩）
  nextTick(() => { if (textareaRef.value) textareaRef.value.style.height = 'auto' })
  isStreaming.value = true
  streamingContent.value = ''
  // Phase 3.3: 清空上一轮的推理步骤
  reasoningSteps.value = []
  stopStepTimer()
  showReasoning.value = true
  messages.value.push({ role: 'user', content: msg })
  await scrollToBottom()

  const convId = currentConversationId.value

  // ==================== Phase 3.3: Agent 模式 — 结构化 SSE 流式对话 ====================
  if (agentMode.value) {
    startStepTimer()
    abortController = streamAgentChat(
      msg, convId,
      // onThinking → 入队，由 setInterval 每 800ms 出队一个
      (content) => {
        pendingSteps.push({ type: 'thinking', content, ts: Date.now() })
      },
      // onToolCall → 入队
      (tool, args) => {
        pendingSteps.push({ type: 'tool_call', tool, args, ts: Date.now() })
      },
      // onToolResult → 入队
      (tool, content) => {
        pendingSteps.push({ type: 'tool_result', tool, content, ts: Date.now() })
      },
      // onToken → 推理未完成时缓存，完成后直接输出
      (token) => {
        if (answerRevealed) {
          streamingContent.value += token
          if (isNearBottom()) scrollToBottom()
        } else {
          pendingTokens.push(token)
        }
      },
      // onDone → 只标记流结束，所有收尾由 interval 在队列空后处理
      async (conversationId) => {
        streamEnded = true
        // 收尾工作交给 interval：等 pendingSteps 清空 → flush token → push message → 刷新列表
        // store conversationId for interval cleanup
        doneConversationId = conversationId
      },
      // onError: 出错 → 停止定时器，刷出剩余
      (error) => {
        stopStepTimer()
        ElMessage.error(error.message || 'AI 请求失败')
        if (streamingContent.value) {
          messages.value.push({ role: 'assistant', content: streamingContent.value + '\n\n*[回复中断]*' })
        }
        streamingContent.value = ''
        isStreaming.value = false
        abortController = null
      },
    )
    return
  }

  // ==================== Phase 2: RAG / 标准模式（原有逻辑） ====================
  const kbId = ragEnabled.value ? selectedKbId.value : null
  const callChat = kbId
    ? (onT, onD, onE) => streamRagChat(msg, convId, kbId, onT, onD, onE)
    : (onT, onD, onE) => streamChat(msg, convId, onT, onD, onE)

  abortController = callChat(
    (token) => {
      streamingContent.value += token
      if (isNearBottom()) scrollToBottom()
    },
    async () => {
      if (streamingContent.value) {
        messages.value.push({ role: 'assistant', content: streamingContent.value })
      }
      streamingContent.value = ''
      isStreaming.value = false
      abortController = null
      if (!currentConversationId.value) {
        await loadConversations()
        if (conversations.value.length > 0) {
          currentConversationId.value = conversations.value[0].id
        }
      } else {
        await loadConversations()
      }
      if (isNearBottom()) await scrollToBottom()
    },
    (error) => {
      ElMessage.error(error.message || 'AI 请求失败')
      if (streamingContent.value) {
        messages.value.push({ role: 'assistant', content: streamingContent.value + '\n\n*[回复中断]*' })
      }
      streamingContent.value = ''
      isStreaming.value = false
      abortController = null
    },
  )
}

const handleStop = () => {
  if (abortController) {
    abortController.abort()
    stopStepTimer()
    if (streamingContent.value) {
      messages.value.push({ role: 'assistant', content: streamingContent.value + '\n\n*[已停止生成]*' })
    }
    streamingContent.value = ''
    isStreaming.value = false
    abortController = null
    ElMessage.info('已停止生成')
  }
}

const handleSuggestion = (text) => {
  inputMessage.value = text
  handleSend()
}

// Phase 4: 点击推荐问题 → 填入输入框并发送
const handleRecommendedQuestion = (text) => {
  inputMessage.value = text
  handleSend()
}

// ==================== 右侧时间轴（电量格导航） ====================

const timelineHovered = ref(false)

/** 当前对话中所有用户消息的索引和预览文本 */
const userMessageAnchors = computed(() => {
  const result = []
  messages.value.forEach((msg, index) => {
    if (msg.role === 'user') {
      result.push({
        index,
        preview: msg.content || '',
      })
    }
  })
  return result
})

/** 截断文本用于 tooltip 显示 */
const truncateText = (text, maxLen) => {
  if (!text) return ''
  return text.length > maxLen ? text.substring(0, maxLen) + '…' : text
}

/** 点击电量格 → 滚动到对应消息位置 */
const scrollToUserMessage = (messageIndex) => {
  const container = messagesContainer.value
  if (!container) return
  // 通过 data 属性定位用户消息元素
  const el = container.querySelector(`[data-msg-idx="${messageIndex}"]`)
  if (el) {
    el.scrollIntoView({ behavior: 'smooth', block: 'center' })
    // 高亮闪烁效果
    el.classList.add('msg-flash')
    setTimeout(() => el.classList.remove('msg-flash'), 1200)
  }
}

// ==================== 滚动到底部按钮 ====================

const showScrollBtn = ref(false)

/** 用户是否在底部附近（≤100px），用于判断是否自动跟底 */
const isNearBottom = () => {
  const el = messagesContainer.value
  if (!el) return true
  return el.scrollHeight - el.scrollTop - el.clientHeight <= 100
}

/** 检测是否离开底部超过 100px，控制 ↓ 按钮显隐 */
const checkScrollPosition = () => {
  const el = messagesContainer.value
  if (!el) return
  const distFromBottom = el.scrollHeight - el.scrollTop - el.clientHeight
  showScrollBtn.value = distFromBottom > 100
}

// ==================== 辅助功能 ====================

/**
 * 滚动到底部
 * @param smooth - true=平滑动画（用户点击按钮），false=瞬间跳转（流式自动跟底）
 */
const scrollToBottom = async (smooth = false) => {
  await nextTick()
  if (messagesContainer.value) {
    messagesContainer.value.scrollTo({
      top: messagesContainer.value.scrollHeight,
      behavior: smooth ? 'smooth' : 'instant',
    })
    if (smooth) showScrollBtn.value = false
  }
}

/** 点击 ↓ 按钮 → 平滑滚到底部 */
const handleScrollToBottom = () => scrollToBottom(true)

/**
 * 启动推理步骤定时渲染器
 * setInterval 每 800ms 从队列取出一个步骤 push 到 reasoningSteps。
 * setInterval 回调是独立宏任务 → 每次 push 触发独立 Vue 渲染 → 动画单独播放。
 */
function startStepTimer() {
  pendingSteps = []
  pendingTokens = []
  answerRevealed = false
  streamEnded = false
  doneConversationId = null

  stepTimer = setInterval(() => {
    if (pendingSteps.length > 0) {
      // 还有推理步骤没渲染 → 弹出一个，Vue 触发动画
      const step = pendingSteps.shift()
      reasoningSteps.value.push(step)
      if (isNearBottom()) scrollToBottom()
    } else if (streamEnded) {
      // 流已结束 + 推理队列已空 → flush token → push 消息 → 收尾
      clearInterval(stepTimer)
      stepTimer = null
      if (!answerRevealed) {
        answerRevealed = true
        if (pendingTokens.length > 0) {
          streamingContent.value += pendingTokens.join('')
          pendingTokens = []
        }
      }
      if (streamingContent.value) {
        messages.value.push({
          role: 'assistant',
          content: streamingContent.value,
          reasoningSteps: reasoningSteps.value.length > 0 ? [...reasoningSteps.value] : null,
          showReasoning: false,
        })
      }
      streamingContent.value = ''
      isStreaming.value = false
      abortController = null
      if (isNearBottom()) scrollToBottom()
      loadConversations().then(() => {
        if (!currentConversationId.value && conversations.value.length > 0) {
          currentConversationId.value = conversations.value[0].id
        }
      })
    }
    // 流未结束 + 队列空 → 继续等下一个 interval tick
  }, 800)
}

/**
 * 紧急停止：错误/手动中断时用，立即刷出一切
 */
function stopStepTimer() {
  if (stepTimer) {
    clearInterval(stepTimer)
    stepTimer = null
  }
  if (pendingSteps.length > 0) {
    reasoningSteps.value.push(...pendingSteps)
    pendingSteps = []
  }
  if (!answerRevealed) {
    answerRevealed = true
    if (pendingTokens.length > 0) {
      streamingContent.value += pendingTokens.join('')
      pendingTokens = []
    }
  }
  if (isNearBottom()) scrollToBottom()
}

const copyContent = (text) => {
  navigator.clipboard.writeText(text).then(() => {
    ElMessage.success('已复制')
  }).catch(() => ElMessage.error('复制失败'))
}

// ==================== Phase 3.3: Agent 推理面板工具函数 ====================

/**
 * 启动推理步骤逐帧动画渲染器
 * <p>
/**
 * 格式化工具调用参数为简洁的字符串显示
 * <p>
 * 将参数对象转为 "key=value" 格式，方便在推理面板中显示。
 * 对于嵌套对象，先尝试 JSON 序列化再截断。
 * </p>
 */
const formatToolArgs = (args) => {
  if (!args || typeof args !== 'object') return ''
  const entries = Object.entries(args)
  if (!entries.length) return ''
  return entries.map(([k, v]) => {
    const val = typeof v === 'object' ? JSON.stringify(v) : String(v)
    return `${k}=${val.length > 60 ? val.substring(0, 60) + '…' : val}`
  }).join(', ')
}

/**
 * 格式化工具返回结果为简短摘要
 * <p>
 * 工具返回内容可能很长（如搜索结果 JSON），在推理面板中只需显示摘要。
 * 截取前 120 字符，并尝试从 JSON 中提取有意义的信息。
 * </p>
 */
const formatToolResult = (content) => {
  if (!content) return '(空)'
  // 尝试作为 JSON 解析，提取摘要信息
  try {
    const obj = JSON.parse(content)
    if (obj.error) return `错误: ${obj.error}`
    if (obj.result !== undefined) return `结果: ${obj.result}`
    if (obj.expression && obj.result !== undefined) return `${obj.expression} = ${obj.result}`
    // 搜索结果
    if (obj.results && Array.isArray(obj.results)) return `找到 ${obj.results.length} 条结果`
    if (obj.answer) return String(obj.answer).substring(0, 80)
    // 代码执行结果
    if (obj.success !== undefined) {
      if (obj.stdout) return String(obj.stdout).substring(0, 80)
      return obj.success ? '执行成功' : `执行失败: ${obj.error || '未知错误'}`
    }
  } catch {}
  // 纯文本截断
  return content.length > 120 ? content.substring(0, 120) + '…' : content
}

const autoResize = () => {
  const el = textareaRef.value
  if (!el) return
  el.style.height = 'auto'
  el.style.height = Math.min(el.scrollHeight, 160) + 'px'
}

// 流式生成时：仅当用户在底部才自动跟底，否则不打扰用户阅读历史
watch(() => streamingContent.value, () => {
  if (isNearBottom()) scrollToBottom()
  checkScrollPosition()
})

onMounted(() => {
  loadConversations(); loadKbs(); loadDeletedConversations()
  document.addEventListener('click', handleClickOutside)
  document.addEventListener('click', handleUploadClickOutside)
  // 滚动检测（↓ 按钮显隐）
  if (messagesContainer.value) {
    messagesContainer.value.addEventListener('scroll', checkScrollPosition, { passive: true })
  }
})

onUnmounted(() => {
  document.removeEventListener('click', handleClickOutside)
  document.removeEventListener('click', handleUploadClickOutside)
  if (messagesContainer.value) {
    messagesContainer.value.removeEventListener('scroll', checkScrollPosition)
  }
})
</script>

<style scoped>
/* ================================================================
   腾讯元宝风格 — 极简侧边栏 + 现代对话界面
   设计语言：浅色侧边栏、微妙边框分隔、圆角卡片、克制的动效
   ================================================================ */

.chat-container {
  display: flex;
  height: 100vh;
  padding: 2px;
  background: #f5f3f0;
  font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', 'PingFang SC',
    'Hiragino Sans GB', 'Microsoft YaHei', sans-serif;
  box-sizing: border-box;
}

/* ==================== 侧边栏 ==================== */
.sidebar {
  width: 300px;
  min-width: 260px;
  background: #f2f0ec;
  display: flex;
  flex-direction: column;
  transition: width 0.2s ease, min-width 0.2s ease, border-radius 0.2s ease;
  overflow: hidden;
}
.sidebar.collapsed {
  width: 0;
  min-width: 0;
  border-radius: 0;
}

/* 侧边栏顶部 */
.sidebar-top {
  padding: 16px 12px 8px;
  flex-shrink: 0;
}
.sidebar-brand {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 0 4px 12px;
}
.brand-icon {
  width: 28px;
  height: 28px;
  border-radius: 8px;
  background: linear-gradient(130deg, #e3e3e3, #374d68);
  display: flex;
  align-items: center;
  justify-content: center;
  color: #fff;
  flex-shrink: 0;
}
.brand-text {
  font-size: 15px;
  font-weight: 600;
  color: #1e1d1a;
  flex: 1;
}
.collapse-trigger {
  width: 28px;
  height: 28px;
  border: none;
  background: transparent;
  border-radius: 6px;
  color: #959288;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  transition: all 0.15s;
}
.collapse-trigger:hover {
  background: #e1dcd8;
  color: #1e1d1a;
}

/* 新建对话按钮 */
.new-chat-btn {
  width: 100%;
  height: 40px;
  border: 1px solid #e0ddd6;
  border-radius: 10px;
  background: #eeebe5;
  color: #1e1d1a;
  font-size: 14px;
  font-weight: 500;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
  transition: all 0.15s;
}
.new-chat-btn:hover {
  background: #e1dcd8;
  border-color: #c8c4bc;
  color: #000;
}

/* 展开按钮（折叠态） */
.expand-trigger {
  width: 36px;
  height: 36px;
  border: none;
  background: transparent;
  border-radius: 8px;
  color: #959288;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  margin: 0 auto;
  transition: all 0.15s;
}
.expand-trigger:hover {
  background: #eeebe5;
  color: #1e1d1a;
}

/* 搜索框 */
.sidebar-search {
  padding: 4px 12px 8px;
  flex-shrink: 0;
}
.search-input-wrap {
  display: flex;
  align-items: center;
  gap: 8px;
  height: 34px;
  padding: 0 10px;
  background: #eeebe5;
  border-radius: 8px;
  transition: all 0.15s;
}
.search-input-wrap:focus-within {
  background: #fff;
  box-shadow: 0 0 0 1px #a2a2a2;
}
.search-icon {
  color: #959288;
  flex-shrink: 0;
}
.search-input {
  flex: 1;
  border: none;
  background: transparent;
  outline: none;
  font-size: 13px;
  color: #1e1d1a;
}
.search-input::placeholder {
  color: #b0ada5;
}

/* 对话列表 */
.sidebar-list {
  flex: 1;
  overflow-y: auto;
  padding: 0 8px;
  scrollbar-width: thin;
  scrollbar-color: #d0cdc6 transparent;
}
.sidebar-list::-webkit-scrollbar {
  width: 4px;
}
.sidebar-list::-webkit-scrollbar-thumb {
  background: #d0cdc6;
  border-radius: 4px;
}

/* 时间分组 */
.conv-group {
  margin-bottom: 4px;
}
.group-label {
  padding: 8px 8px 4px;
  font-size: 11px;
  font-weight: 600;
  color: #959288;
  text-transform: uppercase;
  letter-spacing: 0.5px;
  user-select: none;
}

/* 对话项 */
.conv-item {
  display: flex;
  align-items: center;
  gap: 4px;
  padding: 8px 10px;
  border-radius: 8px;
  cursor: pointer;
  transition: all 0.12s;
  position: relative;
  margin-bottom: 1px;
}
.conv-item:hover {
  background: #eeebe5;
}
.conv-item.active {
  background: #e1dcd8;
}
.conv-item.active .conv-item-title {
  color: #000000;
}
.conv-item-content {
  flex: 1;
  min-width: 0;
}
.conv-item-title {
  font-size: 13px;
  color: #3d3c38;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  line-height: 1.4;
}

/* "..." 菜单按钮（hover 显示） */
.conv-menu-wrap {
  flex-shrink: 0;
}
.conv-item-menu {
  width: 24px;
  height: 24px;
  border: none;
  background: transparent;
  border-radius: 6px;
  color: #b0ada5;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  opacity: 0;
  transition: all 0.12s;
}
.conv-item:hover .conv-item-menu {
  opacity: 1;
}
.conv-item-menu:hover {
  background: #eeebe5;
  color: #3d3c38;
}

/* "..." 右键菜单（Teleport 到 body，fixed 定位由 :style 驱动，向下右弹出） */
.conv-menu-dropdown {
  position: fixed;
  background: #fff;
  border: 1px solid #e5e3de;
  border-radius: 10px;
  box-shadow: 0 4px 16px rgba(0,0,0,0.12);
  padding: 4px;
  min-width: 120px;
  z-index: 9999;
}
.menu-item {
  display: flex;
  align-items: center;
  gap: 8px;
  width: 100%;
  padding: 7px 10px;
  border: none;
  background: transparent;
  border-radius: 6px;
  font-size: 13px;
  color: #3d3c38;
  cursor: pointer;
  transition: all 0.1s;
  text-align: left;
}
.menu-item:hover {
  background: #eeebe5;
}
.menu-item-delete {
  color: #ef4444;
}
.menu-item-delete:hover {
  background: #fef2f2;
}

/* 空状态 */
.empty-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 40px 20px;
  gap: 8px;
}
.empty-icon {
  color: #d0cdc6;
}
.empty-text {
  font-size: 13px;
  color: #b0ada5;
}

/* 侧边栏底部 */
.sidebar-footer {
  padding: 8px 6px 12px;
  border-top: 1px solid #e8e5e0;
  flex-shrink: 0;
}
.footer-btn {
  width: 100%;
  height: 36px;
  border: none;
  background: transparent;
  border-radius: 8px;
  color: #6b6862;
  font-size: 13px;
  cursor: pointer;
  display: flex;
  align-items: center;
  gap: 8px;
  transition: all 0.15s;
}
.footer-btn:hover {
  background: #eeebe5;
  color: #1e1d1a;
}
.footer-btn.active {
  background: #e1dcd8;
  color: #000;
}
.trash-badge {
  background: #ef4444;
  color: #fff;
  font-size: 10px;
  padding: 1px 6px;
  border-radius: 10px;
  margin-left: auto;
  line-height: 1.4;
}

/* 回收站 */
.trash-header {
  padding: 8px 8px 10px;
  display: flex;
  justify-content: space-between;
  align-items: center;
}
.trash-header-left {
  display: flex;
  align-items: baseline;
  gap: 8px;
}
.trash-title {
  font-size: 13px;
  font-weight: 600;
  color: #1e1d1a;
}
.trash-hint {
  font-size: 11px;
  color: #b0ada5;
}
.trash-batch-btn {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 4px 10px;
  border: 1px solid #d0cdc6;
  border-radius: 6px;
  background: #fff;
  color: #6b6862;
  font-size: 12px;
  cursor: pointer;
  transition: all 0.12s;
}
.trash-batch-btn:hover {
  background: #eeebe5;
  border-color: #1677ff;
  color: #1677ff;
}
.trash-item {
  opacity: 0.85;
  padding: 10px 10px;
}
.trash-item:hover {
  background: #e1dcd8;
  opacity: 1;
}
.trash-item.selected,
.trash-item.selected:hover {
  background: #e1dcd8;
  opacity: 1;
}
.trash-item.selected .conv-item-title {
  color: #000;
}
.conv-item-meta {
  font-size: 11px;
  color: #b0ada5;
  margin-top: 3px;
}
.trash-actions {
  display: flex;
  gap: 4px;
  opacity: 0;
  transition: opacity 0.12s;
  flex-shrink: 0;
}
.trash-item:hover .trash-actions {
  opacity: 1;
}
.trash-action-btn {
  width: 28px;
  height: 28px;
  border: none;
  background: transparent;
  border-radius: 6px;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  transition: all 0.12s;
}
.trash-action-btn.restore {
  color: #1677ff;
}
.trash-action-btn.restore:hover {
  background: #e8f0fe;
}
.trash-action-btn.danger {
  color: #959288;
}
.trash-action-btn.danger:hover {
  background: #fee2e2;
  color: #ef4444;
}

/* ==================== 主聊天区 ==================== */
.chat-main {
  flex: 1;
  display: flex;
  flex-direction: column;
  min-width: 0;
  border-radius: 12px;
  overflow: hidden;
}

/* 顶部栏 */
.chat-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 0 20px;
  height: 52px;
  background: transparent;
  border-bottom: 1px solid #D9D9D1;
  flex-shrink: 0;
}
.header-left {
  display: flex;
  align-items: center;
  gap: 10px;
}
.header-menu-btn {
  width: 32px;
  height: 32px;
  border: none;
  background: transparent;
  border-radius: 8px;
  color: #6b6862;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  transition: all 0.15s;
}
.header-menu-btn:hover {
  background: #eeebe5;
}
.header-title {
  font-size: 15px;
  font-weight: 600;
  color: #1e1d1a;
  margin: 0;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  max-width: 300px;
}
.header-badge {
  font-size: 11px;
  color: #959288;
  background: #eeebe5;
  padding: 3px 8px;
  border-radius: 6px;
  font-weight: 500;
}
/* ==================== 消息区域 ==================== */
.chat-messages {
  flex: 1;
  overflow-y: auto;
  padding: 24px 0;
  scroll-behavior: smooth;
}

/* 欢迎页 */
.welcome-screen {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 80px 20px 40px;
  text-align: center;
}
.welcome-logo {
  margin-bottom: 20px;
}
.logo-ring {
  width: 72px;
  height: 72px;
  border-radius: 20px;
  background: linear-gradient(130deg, #e3e3e3, #374d68);
  display: flex;
  align-items: center;
  justify-content: center;
  color: #fff;
  box-shadow: 0 8px 24px rgba(22, 119, 255, 0.2);
}
.welcome-title {
  font-size: 22px;
  font-weight: 600;
  color: #1e1d1a;
  margin: 0 0 6px;
}
.welcome-sub {
  font-size: 14px;
  color: #959288;
  margin: 0 0 28px;
}
.suggestion-grid {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 10px;
  max-width: 520px;
  width: 100%;
}
.suggestion-card {
  padding: 12px 16px;
  background: #fff;
  border: 1px solid #e5e3de;
  border-radius: 10px;
  font-size: 13px;
  color: #3d3c38;
  cursor: pointer;
  text-align: left;
  transition: all 0.15s;
  line-height: 1.5;
}
.suggestion-card:hover {
  border-color: #1677ff;
  color: #1677ff;
  background: #f0f6ff;
  box-shadow: 0 2px 8px rgba(22, 119, 255, 0.08);
}

/* 消息行 — 头像内联，内容宽度 = 输入框宽度 */
.message-row {
  display: flex;
  padding: 8px 24px;
  max-width: 820px;
  margin: 0 auto;
  width: 100%;
  box-sizing: border-box;
}
.message-row.user {
  justify-content: flex-end;
}
/* 用户消息气泡右对齐 */
.message-row.user .message-body {
  text-align: right;
}
/* 头像内联到 sender 行，不再占用单独列 */
.avatar {
  width: 28px;
  height: 28px;
  border-radius: 8px;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}
.avatar-user {
  background: #d8d5ce;
  color: #5d5a54;
}
.avatar-ai {
  background: linear-gradient(135deg, #5d5a55, #3d3c38);
  color: #fff;
}
.message-body {
  flex: 1;
  min-width: 0;
  max-width: 100%;
}
/* sender 行：头像 + 名称水平排列 */
.message-sender {
  font-size: 12px;
  color: #999;
  margin-bottom: 6px;
  font-weight: 500;
  display: flex;
  align-items: center;
  gap: 8px;
}
.message-row.user .message-sender {
  justify-content: flex-end;
}
/* AI 消息气泡：纯白 + 柔和阴影，在暖底上自然浮起 */
.message-row.assistant .message-text {
  background: none;
  border: none;
  border-radius: 2px 18px 18px 18px;
  box-shadow: 0 1px 3px rgba(0,0,0,0.05), 0 3px 8px rgba(0,0,0,0.04);
  padding: 12px 16px;
}
/* 用户消息气泡：经典靛蓝纯色，干净利落 */
.message-text-user {
  background: #ffffff;
  color: #fff;
  display: inline-block;
  max-width: 85%;
  border-radius: 18px 2px 18px 18px;
  padding: 12px 16px;
  box-shadow: 0 2px 8px rgba(76, 110, 245, 0.25);
}
/* 消息文本共用 */
.message-text {
  font-size: 14px;
  line-height: 1.7;
  color: #1e1d1a;
  word-break: break-word;
  word-break: break-word;
}

/* 打字指示器 */
.typing-indicator {
  display: inline-flex;
  gap: 3px;
  align-items: center;
}
.typing-indicator span {
  width: 4px;
  height: 4px;
  border-radius: 50%;
  background: #1677ff;
  animation: typing-bounce 1.2s infinite;
}
.typing-indicator span:nth-child(2) { animation-delay: 0.2s; }
.typing-indicator span:nth-child(3) { animation-delay: 0.4s; }
@keyframes typing-bounce {
  0%, 60%, 100% { transform: translateY(0); opacity: 0.4; }
  30% { transform: translateY(-4px); opacity: 1; }
}

/* 消息操作（hover 显示） */
.message-actions {
  margin-top: 4px;
  opacity: 0;
  transition: opacity 0.12s;
}
.message-row:hover .message-actions {
  opacity: 1;
}
.action-btn {
  width: 28px;
  height: 28px;
  border: none;
  background: transparent;
  border-radius: 6px;
  color: #959288;
  cursor: pointer;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  transition: all 0.12s;
}
.action-btn:hover {
  background: #eeebe5;
  color: #1e1d1a;
}

/* ==================== Markdown 渲染 ==================== */
.markdown-body :deep(h1),
.markdown-body :deep(h2),
.markdown-body :deep(h3) {
  margin: 16px 0 8px;
  font-weight: 600;
  color: #1e1d1a;
}
.markdown-body :deep(h1) { font-size: 1.3em; }
.markdown-body :deep(h2) { font-size: 1.15em; }
.markdown-body :deep(h3) { font-size: 1.02em; }
.markdown-body :deep(p) { margin: 8px 0; }
.markdown-body :deep(p:first-child) { margin-top: 0; }
.markdown-body :deep(p:last-child) { margin-bottom: 0; }
.markdown-body :deep(ul),
.markdown-body :deep(ol) { padding-left: 20px; margin: 8px 0; }
.markdown-body :deep(li) { margin: 4px 0; }
.markdown-body :deep(code) {
  font-family: 'SF Mono', 'Fira Code', 'Consolas', monospace;
  font-size: 0.88em;
}
.markdown-body :deep(p > code),
.markdown-body :deep(li > code) {
  background: #eeebe5;
  padding: 2px 6px;
  border-radius: 4px;
  color: #d63384;
}
.markdown-body :deep(pre) {
  background: #f6f8fa;
  border-radius: 8px;
  padding: 14px 16px;
  overflow-x: auto;
  margin: 10px 0;
  border: 1px solid #e5e3de;
}
.markdown-body :deep(pre code) {
  background: none;
  padding: 0;
  font-size: 0.84em;
  line-height: 1.6;
  color: #1e1d1a;
}
.markdown-body :deep(table) {
  border-collapse: collapse;
  margin: 10px 0;
  width: 100%;
  font-size: 13px;
}
.markdown-body :deep(th),
.markdown-body :deep(td) {
  border: 1px solid #e5e3de;
  padding: 7px 12px;
  text-align: left;
}
.markdown-body :deep(th) {
  background: #f6f8fa;
  font-weight: 600;
}
.markdown-body :deep(blockquote) {
  border-left: 3px solid #1677ff;
  margin: 8px 0;
  padding: 4px 14px;
  color: #6b6862;
  background: #f8faff;
  border-radius: 0 6px 6px 0;
}
.markdown-body :deep(a) {
  color: #1677ff;
  text-decoration: none;
}
.markdown-body :deep(a:hover) {
  text-decoration: underline;
}
.markdown-body :deep(hr) {
  border: none;
  border-top: 1px solid #e5e3de;
  margin: 14px 0;
}

/* ==================== KaTeX 数学公式样式 ==================== */
.markdown-body :deep(.katex-display) {
  margin: 12px 0;
  overflow-x: auto;
  overflow-y: hidden;
  padding: 8px 0;
}
.markdown-body :deep(.katex) {
  font-size: 1.05em;
}
.markdown-body :deep(.katex-html) {
  white-space: nowrap;
}
.markdown-body :deep(.katex-display > .katex) {
  font-size: 1.15em;
}

/* ==================== 输入区域 ==================== */
.chat-input-area {
  padding: 12px 24px 20px;
  flex-shrink: 0;
}
.input-box {
  max-width: 820px;
  margin: 0 auto;
  background: #fff;
  border: 1px solid #e5e3de;
  border-radius: 16px;
  padding: 10px 16px;
  box-shadow: 0 2px 8px rgba(0,0,0,0.04);
  transition: all 0.15s;
}
.input-box:focus-within {
  border-color: #3b82f6;
  box-shadow: 0 2px 12px rgba(59, 130, 246, 0.12);
}
.input-textarea {
  width: 100%;
  border: none;
  outline: none;
  resize: none;
  font-size: 14px;
  line-height: 1.6;
  color: #1e1d1a;
  background: transparent;
  font-family: inherit;
  min-height: 24px;
  max-height: 160px;
}
.input-textarea::placeholder {
  color: #b0ada5;
}

/* 输入框内操作行：左侧药丸按钮 + 右侧发送 */
.input-actions {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-top: 8px;
  gap: 8px;
}
.input-pills {
  display: flex;
  align-items: center;
  gap: 6px;
  flex-wrap: wrap;
  flex: 1;
  min-width: 0;
}
.input-send-area {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-shrink: 0;
}
.input-hint {
  font-size: 11px;
  color: #b0ada5;
}
.char-warn {
  color: #ef4444;
  font-weight: 500;
}
.send-btn {
  width: 32px;
  height: 32px;
  border: none;
  background: #e0ddd6;
  border-radius: 8px;
  color: #fff;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  transition: all 0.15s;
}
.send-btn.active {
  background: #3b82f6;
}
.send-btn.active:hover {
  background: #2563eb;
}
.send-btn:disabled {
  cursor: not-allowed;
}
.stop-btn {
  height: 30px;
  padding: 0 12px;
  border: 1px solid #e0ddd6;
  background: #fff;
  border-radius: 8px;
  color: #6b6862;
  font-size: 12px;
  cursor: pointer;
  display: flex;
  align-items: center;
  gap: 4px;
  transition: all 0.15s;
}
.stop-btn:hover {
  border-color: #d0cdc6;
  background: #eeebe5;
}

/* 输入框下方状态提示 */
.input-footer {
  display: flex;
  flex-wrap: wrap;
  justify-content: center;
  align-items: center;
  gap: 6px 12px;
  margin-top: 8px;
  max-width: 820px;
  margin-left: auto;
  margin-right: auto;
  font-size: 11px;
  color: #b0ada5;
}
.footer-status {
  display: inline-flex;
  align-items: center;
  gap: 3px;
  color: #6b6862;
}
.input-disclaimer {
  font-size: 11px;
  color: #b0ada5;
}

/* ==================== 药丸按钮（千问风格） ==================== */
.pill-btn {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  height: 28px;
  padding: 0 10px;
  border: 1px solid #d0cdc6;
  border-radius: 14px;
  background: #fff;
  color: #6b6862;
  font-size: 12px;
  cursor: pointer;
  transition: all 0.2s ease;
  white-space: nowrap;
  user-select: none;
}
.pill-btn:hover {
  border-color: #a2a2a2;
  background: #f5f3f0;
  color: #1e1d1a;
}
.pill-btn.active {
  background: #f0f5ff;
  border-color: #1677ff;
  color: #1677ff;
  font-weight: 500;
}
.pill-btn.active:hover {
  background: #e0edff;
  border-color: #0958d9;
}
/* 上传按钮（+ 号） */
.upload-btn-wrap {
  position: relative;
}
.pill-upload {
  width: 28px;
  padding: 0;
  justify-content: center;
  border-style: dashed;
  color: #959288;
}
.pill-upload:hover,
.pill-upload.open {
  border-color: #1677ff;
  color: #1677ff;
  background: #f0f5ff;
}

/* 上传下拉菜单 */
.upload-dropdown {
  position: absolute;
  bottom: 36px;
  left: 0;
  background: #fff;
  border: 1px solid #e5e3de;
  border-radius: 10px;
  box-shadow: 0 4px 16px rgba(0,0,0,0.12);
  padding: 4px;
  min-width: 130px;
  z-index: 100;
}
.upload-dropdown-item {
  display: flex;
  align-items: center;
  gap: 8px;
  width: 100%;
  padding: 8px 12px;
  border: none;
  background: transparent;
  border-radius: 6px;
  font-size: 13px;
  color: #3d3c38;
  cursor: pointer;
  transition: all 0.1s;
}
.upload-dropdown-item:hover {
  background: #eeebe5;
}

/* 下拉菜单动画 */
.upload-menu-enter-active {
  transition: all 0.2s cubic-bezier(0.22, 0.61, 0.36, 1);
}
.upload-menu-leave-active {
  transition: all 0.15s ease-in;
}
.upload-menu-enter-from {
  opacity: 0;
  transform: translateY(6px);
}
.upload-menu-leave-to {
  opacity: 0;
  transform: translateY(4px);
}

/* ==================== Phase 3.3: Agent 推理过程（内联于 AI 消息气泡） ==================== */

/* 推理过程容器（内联） */
.reasoning-inline {
  margin-bottom: 8px;
}

/* TransitionGroup: step-anim — 逐个飞入动画 */
.step-anim-enter-active {
  transition: all 0.5s cubic-bezier(0.22, 0.61, 0.36, 1);
}
.step-anim-leave-active {
  transition: all 0.25s ease-in;
}
.step-anim-enter-from {
  opacity: 0;
  transform: translateX(-40px);
  max-height: 0;
  padding-top: 0;
  padding-bottom: 0;
  margin-bottom: 0;
  border-left-width: 0;
}
.step-anim-enter-to {
  opacity: 1;
  transform: translateX(0);
  max-height: 60px;
}
.step-anim-leave-to {
  opacity: 0;
  transform: translateX(20px);
}

/* 展开/收起按钮 */
.reasoning-toggle {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 6px 10px;
  background: #fafbff;
  border: 1px solid #d6e4ff;
  border-radius: 8px;
  font-size: 12px;
  color: #1677ff;
  cursor: pointer;
  transition: all 0.15s;
  width: 100%;
}
.reasoning-toggle:hover {
  background: #f0f5ff;
  border-color: #1677ff;
}
.reasoning-toggle span {
  flex: 1;
  text-align: left;
}

/* 推理步骤列表（内联） */
.reasoning-steps-inline {
  padding: 6px 0 2px;
  /* max-height: 200px; */
  overflow-y: auto;
  scrollbar-width: thin;
  scrollbar-color: #d0cdc6 transparent;
}
.reasoning-steps-inline::-webkit-scrollbar { width: 4px; }
.reasoning-steps-inline::-webkit-scrollbar-thumb {
  background: #d0cdc6;
  border-radius: 4px;
}

/* 推理步骤项 — 基础样式（动画由 TransitionGroup step-anim 控制） */
.reasoning-step {
  display: flex;
  align-items: flex-start;
  gap: 6px;
  padding: 5px 8px;
  margin-bottom: 3px;
  border-radius: 6px;
  border-left: 3px solid transparent;
  font-size: 12px;
  line-height: 1.5;
  overflow: hidden;
}

/* 步骤色彩编码 */
.step-thinking {
  border-left-color: #1677ff;
  background: #f0f5ff;
}
.step-tool_call {
  border-left-color: #f59e0b;
  background: #fffbeb;
}
.step-tool_result {
  border-left-color: #10b981;
  background: #f0fdf6;
}

/* 步骤图标 */
.step-icon {
  flex-shrink: 0;
  width: 20px;
  height: 20px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  margin-top: 1px;
}
.step-icon-think { background: #e8f0fe; color: #1677ff; }
.step-icon-tool  { background: #fef3c7; color: #f59e0b; }
.step-icon-result { background: #d1fae5; color: #10b981; }

/* 流式推理中的图标动画（完成后通过 .reasoning-done 停止） */
.reasoning-steps-inline:not(.reasoning-done) .step-icon-think {
  animation: spin 1s linear infinite;
}
.reasoning-steps-inline:not(.reasoning-done) .step-icon-tool {
  animation: pulse-glow 1.2s ease-in-out infinite;
}
.reasoning-steps-inline:not(.reasoning-done) .step-icon-result {
  animation: pop-in 0.4s cubic-bezier(0.68, -0.55, 0.27, 1.55);
}
/* 已完成步骤的图标增加淡入效果 */
.reasoning-steps-inline.reasoning-done .step-icon-result {
  animation: pop-in 0.4s cubic-bezier(0.68, -0.55, 0.27, 1.55);
}

.step-text {
  color: #3d3c38;
  flex: 1;
}
.step-label {
  color: #959288;
  flex-shrink: 0;
}
.step-tool-name {
  font-weight: 600;
  color: #f59e0b;
  font-family: 'SF Mono', 'Fira Code', 'Consolas', monospace;
  font-size: 11px;
}
.step-args code {
  font-size: 11px;
  color: #6b6862;
  background: #eeebe5;
  padding: 1px 6px;
  border-radius: 4px;
  font-family: 'SF Mono', 'Fira Code', 'Consolas', monospace;
  word-break: break-all;
}
.step-result-text {
  color: #3d3c38;
  word-break: break-word;
}

/* 推理面板过渡动画 */
.reasoning-panel-enter-active,
.reasoning-panel-leave-active {
  transition: all 0.25s ease;
}
.reasoning-panel-enter-from,
.reasoning-panel-leave-to {
  opacity: 0;
  max-height: 0;
  margin-top: 0;
  margin-bottom: 0;
}
.reasoning-panel-enter-to,
.reasoning-panel-leave-from {
  opacity: 1;
  max-height: 300px;
}

/* Agent 思考标签 */
.agent-thinking-label {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  font-size: 11px;
  color: #1677ff;
  font-weight: 500;
}
.spin-icon {
  animation: spin 1.2s linear infinite;
}
@keyframes spin {
  from { transform: rotate(0deg); }
  to   { transform: rotate(360deg); }
}
@keyframes pulse-glow {
  0%, 100% { transform: scale(1);   opacity: 1;   box-shadow: 0 0 0 0 rgba(245, 158, 11, 0); }
  50%      { transform: scale(1.2); opacity: 0.85; box-shadow: 0 0 8px 2px rgba(245, 158, 11, 0.4); }
}
@keyframes pulse {
  0%, 100% { transform: scale(1); opacity: 1; }
  50%      { transform: scale(1.15); opacity: 0.8; }
}
@keyframes pop-in {
  0%   { transform: scale(0); opacity: 0; }
  60%  { transform: scale(1.3); opacity: 1; }
  100% { transform: scale(1); opacity: 1; }
}

/* Agent 思考中占位文本 */
.agent-thinking-text {
  color: #959288 !important;
  font-style: italic;
  font-size: 13px !important;
  background: #fafbff !important;
  border: 1px dashed #d6e4ff !important;
}

/* ==================== 批量选择样式 ==================== */

/* 对话项上的复选框（仅在批量管理模式显示） */
.conv-checkbox {
  flex-shrink: 0;
  margin-right: 2px;
}

/* 选中状态高亮 */
.conv-item.selected {
  background: #e1dcd8;
}

/* 全选控制条（仅在批量管理模式显示） */
.batch-control-bar {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 4px 10px 6px;
  font-size: 12px;
}
.batch-count {
  color: #1677ff;
  font-weight: 500;
  font-size: 11px;
  flex: 1;
}
.batch-exit-btn {
  padding: 3px 10px;
  border: 1px solid #d0cdc6;
  border-radius: 5px;
  background: #fff;
  color: #6b6862;
  font-size: 11px;
  cursor: pointer;
  transition: all 0.12s;
}
.batch-exit-btn:hover {
  background: #eeebe5;
  border-color: #1677ff;
  color: #1677ff;
}

/* 批量操作栏 */
.batch-action-bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 8px 12px;
  background: #fff;
  border-top: 1px solid #e0ddd6;
  box-shadow: 0 -2px 8px rgba(0,0,0,0.06);
  flex-shrink: 0;
}
.batch-action-count {
  font-size: 12px;
  color: #6b6862;
  font-weight: 500;
}
.batch-action-btns {
  display: flex;
  gap: 6px;
}
.batch-btn {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 5px 10px;
  border: 1px solid #d0cdc6;
  border-radius: 6px;
  font-size: 12px;
  cursor: pointer;
  transition: all 0.12s;
  background: #fff;
  color: #6b6862;
}
/* 纯图标批量按钮（回收站模式） */
.batch-btn-icon {
  width: 30px;
  height: 30px;
  padding: 0;
  justify-content: center;
  gap: 0;
}
.batch-btn:hover {
  background: #eeebe5;
}
.batch-btn-delete {
  color: #ef4444;
  border-color: #fecaca;
}
.batch-btn-delete:hover {
  background: #fef2f2;
  border-color: #ef4444;
}
.batch-btn-restore {
  color: #1677ff;
  border-color: #bfdbfe;
}
.batch-btn-restore:hover {
  background: #eff6ff;
  border-color: #1677ff;
}
.batch-btn-danger {
  color: #ef4444;
  border-color: #fecaca;
}
.batch-btn-danger:hover {
  background: #fef2f2;
  border-color: #ef4444;
}
.batch-btn-cancel {
  color: #959288;
}
.batch-btn-cancel:hover {
  background: #eeebe5;
  color: #6b6862;
}

/* 批量操作栏过渡动画 */
.batch-bar-enter-active,
.batch-bar-leave-active {
  transition: all 0.2s ease;
}
.batch-bar-enter-from,
.batch-bar-leave-to {
  opacity: 0;
  max-height: 0;
  padding-top: 0;
  padding-bottom: 0;
  border-top-width: 0;
}
.batch-bar-enter-to,
.batch-bar-leave-from {
  opacity: 1;
  max-height: 50px;
}

/* ==================== 右侧对话时间轴（电量格导航，始终可见） ==================== */
.chat-main {
  position: relative;  /* 为时间轴绝对定位提供参考 */
}
.conv-timeline {
  position: absolute;
  right: 20px;           /* 与滚动条隔开一点距离 */
  top: 50%;
  transform: translateY(-50%);
  width: 6px;
  display: flex;
  align-items: center;
  transition: width 0.25s ease, right 0.25s ease;
  z-index: 10;
  max-height: calc(100% - 120px);  /* 不超出消息区域 */
  overflow-y: auto;                /* 消息很多时可滚动 */
  scrollbar-width: none;           /* 隐藏时间轴自身的滚动条 */
}
.conv-timeline.expanded {
  width: 180px;
  right: 8px;
}
.timeline-track {
  display: flex;
  flex-direction: column;
  gap: 4px;
  width: 100%;
  padding: 8px 0;
}
.timeline-segment {
  display: flex;
  align-items: center;
  position: relative;
  cursor: pointer;
  height: 8px;
  transition: height 0.2s ease;
}
.conv-timeline.expanded .timeline-segment {
  height: 34px;
}
.timeline-bar {
  width: 6px;
  height: 100%;
  border-radius: 3px;
  background: linear-gradient(180deg, #e0ddd6 0%, #c8c4bc 100%);
  transition: all 0.2s ease;
  flex-shrink: 0;
  margin-left: auto;  /* 靠右对齐 */
}
.conv-timeline.expanded .timeline-bar {
  width: 8px;
  border-radius: 4px;
  background: linear-gradient(180deg, #1677ff 0%, #69b1ff 100%);
}
.timeline-segment:hover .timeline-bar {
  background: #3b82f6;
  box-shadow: 0 0 8px rgba(59, 130, 246, 0.5);
  transform: scaleX(1.3);
}
.timeline-bar.placeholder {
  background: #e8e5e0;
  opacity: 0.4;
  height: 100%;
}
.timeline-empty {
  flex: 1;
  display: flex;
  align-items: center;
  min-height: 40px;
  justify-content: flex-end;
}

/* hover 展开的气泡 tooltip（向左展开） */
.timeline-tooltip {
  position: absolute;
  right: 18px;
  top: 50%;
  transform: translateY(-50%);
  background: #1e1d1a;
  color: #fff;
  font-size: 12px;
  line-height: 1.4;
  padding: 6px 10px;
  border-radius: 8px;
  white-space: nowrap;
  max-width: 150px;
  overflow: hidden;
  text-overflow: ellipsis;
  box-shadow: 0 4px 12px rgba(0,0,0,0.18);
  pointer-events: none;
}
/* 气泡小三角（指向右侧的条形） */
.timeline-tooltip::after {
  content: '';
  position: absolute;
  right: -5px;
  top: 50%;
  transform: translateY(-50%);
  border: 5px solid transparent;
  border-left-color: #1e1d1a;
}

/* tooltip 动画 */
.timeline-tip-enter-active {
  transition: all 0.2s cubic-bezier(0.22, 0.61, 0.36, 1);
}
.timeline-tip-leave-active {
  transition: all 0.15s ease-in;
}
.timeline-tip-enter-from {
  opacity: 0;
  transform: translateX(6px);
}
.timeline-tip-leave-to {
  opacity: 0;
  transform: translateX(4px);
}

/* ==================== 滚动到底部按钮 ==================== */
.scroll-bottom-btn {
  position: sticky;
  bottom: 12px;
  left: 50%;
  transform: translateX(-50%);
  width: 32px;
  height: 32px;
  border: 1px solid #e0ddd6;
  border-radius: 50%;
  background: #fff;
  box-shadow: 0 2px 8px rgba(0,0,0,0.1);
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 20;
  margin: 0 auto;
  margin-top: -44px; /* 悬浮在底部上方，不占用流式空间 */
  transition: all 0.2s ease;
}
.scroll-bottom-btn:hover {
  border-color: #3b82f6;
  box-shadow: 0 2px 12px rgba(59,130,246,0.25);
}

/* SVG 环形进度（流式中显示） */
.scroll-btn-ring {
  position: absolute;
  width: 34px;
  height: 34px;
  opacity: 0;
  transition: opacity 0.3s;
}
.scroll-bottom-btn.streaming .scroll-btn-ring {
  opacity: 1;
}
.scroll-btn-ring.spinning {
  animation: spin 2s linear infinite;
}
.scroll-bottom-btn.streaming .ring-progress {
  stroke-dashoffset: 23.5;  /* ~75% 露出，暗示正在进行 */
  transition: stroke-dashoffset 0.6s;
}

.scroll-btn-icon {
  position: relative;
  color: #6b6862;
  transition: color 0.2s;
  z-index: 1;
}
.scroll-bottom-btn:hover .scroll-btn-icon {
  color: #3b82f6;
}
.scroll-bottom-btn.streaming .scroll-btn-icon {
  color: #3b82f6;
}

/* 按钮过渡动画 */
.scroll-btn-enter-active {
  transition: all 0.25s cubic-bezier(0.22, 0.61, 0.36, 1);
}
.scroll-btn-leave-active {
  transition: all 0.2s ease-in;
}
.scroll-btn-enter-from {
  opacity: 0;
  transform: translateX(-50%) translateY(12px);
}
.scroll-btn-leave-to {
  opacity: 0;
  transform: translateX(-50%) translateY(8px);
}

/* 消息闪烁高亮（点击跳转后） */
:deep(.msg-flash) {
  animation: msg-flash-anim 1.2s ease-out;
}
@keyframes msg-flash-anim {
  0%   { background-color: rgba(59, 130, 246, 0.15); }
  100% { background-color: transparent; }
}
</style>
