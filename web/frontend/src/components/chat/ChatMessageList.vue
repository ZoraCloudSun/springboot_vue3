<template>
  <div ref="messagesContainer" class="chat-messages" @scroll="checkScrollPosition">
    <!-- 空状态 -->
    <div v-if="!loading && messages.length === 0" class="empty-state">
      <div class="empty-icon">
        <svg xmlns="http://www.w3.org/2000/svg" width="40" height="40" fill="currentColor" class="bi bi-chat-dots" viewBox="0 0 16 16">
          <path d="M5 8a1 1 0 1 1-2 0 1 1 0 0 1 2 0m4 0a1 1 0 1 1-2 0 1 1 0 0 1 2 0m3 1a1 1 0 1 0 0-2 1 1 0 0 0 0 2"/>
          <path d="m2.165 15.803.02-.004c1.83-.363 2.948-.842 3.468-1.105A9 9 0 0 0 8 15c4.418 0 8-3.134 8-7s-3.582-7-8-7-8 3.134-8 7c0 1.76.743 3.37 1.97 4.6a10.4 10.4 0 0 1-.524 2.318l-.003.011a11 11 0 0 1-.244.637c-.079.186.074.394.273.362a21.7 21.7 0 0 0 .693-.125"/>
        </svg>
      </div>
      <p class="empty-text">开始一段新的对话吧</p>
    </div>

    <!-- Agent 推理面板 -->
    <slot name="agent-panel"></slot>

    <!-- 消息列表 -->
    <slot name="messages"></slot>

    <!-- 流式生成指示器 -->
    <div v-if="streaming" class="streaming-indicator">
      <span class="streaming-dot"></span>
      <span class="streaming-dot"></span>
      <span class="streaming-dot"></span>
    </div>

    <!-- 滚动按钮 -->
    <ChatScrollButton :visible="showScrollBtn" :streaming="streaming" @click="handleScrollToBottom" />
  </div>
</template>

<script setup>
import { ref, watch } from 'vue'
import ChatScrollButton from './ChatScrollButton.vue'

const props = defineProps({
  messages: { type: Array, default: () => [] },
  streaming: { type: Boolean, default: false },
  loading: { type: Boolean, default: false },
})
const emit = defineEmits(['scroll-to-message'])

const messagesContainer = ref(null)
const showScrollBtn = ref(false)

function isNearBottom() {
  const el = messagesContainer.value
  if (!el) return true
  return el.scrollHeight - el.scrollTop - el.clientHeight <= 100
}

function scrollToBottom(smooth = false) {
  const el = messagesContainer.value
  if (!el) return
  el.scrollTo({
    top: el.scrollHeight,
    behavior: smooth ? 'smooth' : 'instant',
  })
  if (smooth) showScrollBtn.value = false
}

function checkScrollPosition() {
  showScrollBtn.value = !isNearBottom()
}

function handleScrollToBottom() {
  scrollToBottom(true)
}

// 消息变化时自动滚动
watch(() => props.messages.length, () => {
  if (isNearBottom()) scrollToBottom()
})

defineExpose({ scrollToBottom, messagesContainer })
</script>

<style scoped>
.chat-messages {
  flex: 1;
  overflow-y: auto;
  padding: 16px;
  position: relative;
}
.empty-state { display: flex; flex-direction: column; align-items: center; justify-content: center; height: 100%; gap: 12px; color: var(--text-secondary, #9ca3af); }
.empty-icon { opacity: .4; }
.empty-text { font-size: 14px; }
.streaming-indicator { display: flex; gap: 4px; padding: 8px 16px; }
.streaming-dot { width: 6px; height: 6px; border-radius: 50%; background: var(--accent, #1677ff); animation: dot-bounce 1.4s infinite ease-in-out both; }
.streaming-dot:nth-child(1) { animation-delay: 0s; }
.streaming-dot:nth-child(2) { animation-delay: .16s; }
.streaming-dot:nth-child(3) { animation-delay: .32s; }
@keyframes dot-bounce { 0%,80%,100%{transform:scale(0);opacity:.3} 40%{transform:scale(1);opacity:1} }
</style>
