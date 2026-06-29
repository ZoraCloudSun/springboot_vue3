<template>
  <div class="message-bubble" :class="[message.role === 'user' ? 'msg-user' : 'msg-ai', { 'msg-streaming': isStreaming }]">
    <div class="msg-avatar" v-if="message.role !== 'user'">
      <svg xmlns="http://www.w3.org/2000/svg" width="18" height="18" fill="currentColor" class="bi bi-robot" viewBox="0 0 16 16">
        <path d="M6 12.5a.5.5 0 0 1 .5-.5h3a.5.5 0 0 1 0 1h-3a.5.5 0 0 1-.5-.5M3 8.062C3 6.76 4.235 5.765 5.53 5.886a26.6 26.6 0 0 0 4.94 0C11.765 5.765 13 6.76 13 8.062v1.157a.93.93 0 0 1-.765.935c-.845.147-2.34.346-4.235.346s-3.39-.2-4.235-.346A.93.93 0 0 1 3 9.219zm4.542-.827a.25.25 0 0 0-.217.068l-.92.9a25 25 0 0 1-1.871-.183.25.25 0 0 0-.068.495c.55.076 1.232.149 2.02.193a.25.25 0 0 0 .189-.071l.754-.736.847 1.71a.25.25 0 0 0 .404.062l.932-.97a25 25 0 0 0 1.922-.188.25.25 0 0 0-.068-.495c-.538.074-1.207.145-1.98.189a.25.25 0 0 0-.166.076l-.754.785-.842-1.7a.25.25 0 0 0-.182-.135"/>
        <path d="M8.5 1.866a1 1 0 1 0-1 0V3h-2A4.5 4.5 0 0 0 1 7.5V8a1 1 0 0 0-1 1v2a1 1 0 0 0 1 1v1a2 2 0 0 0 2 2h10a2 2 0 0 0 2-2v-1a1 1 0 0 0 1-1V9a1 1 0 0 0-1-1v-.5A4.5 4.5 0 0 0 10.5 3h-2zM14 7.5V13a1 1 0 0 1-1 1H3a1 1 0 0 1-1-1V7.5A3.5 3.5 0 0 1 5.5 4h5A3.5 3.5 0 0 1 14 7.5"/>
      </svg>
    </div>
    <div class="msg-content-wrap">
      <div class="msg-role-tag">{{ message.role === 'user' ? '你' : 'AI' }}</div>
      <div class="msg-text" v-html="renderedContent"></div>
      <div class="msg-actions" v-if="message.role !== 'user' && !isStreaming && message.id">
        <button class="msg-action-btn" title="复制" @click="$emit('copy', message.content)">
          <el-icon :size="13"><CopyDocument /></el-icon>
        </button>
        <button class="msg-action-btn" title="重新生成" @click="$emit('resend')">
          <el-icon :size="13"><Refresh /></el-icon>
        </button>
      </div>
    </div>
  </div>
</template>

<script setup>
import { computed } from 'vue'
import { CopyDocument, Refresh } from '@element-plus/icons-vue'
import DOMPurify from 'dompurify'

const props = defineProps({
  message: { type: Object, required: true },
  isStreaming: { type: Boolean, default: false },
})
defineEmits(['copy', 'resend', 'delete'])

// Markdown 渲染
function fixCjkBold(text) {
  return text.replace(/\*\*(.+?)\*\*/g, (match, content) => {
    if (/[一-鿿]/.test(content)) {
      return '<strong>' + content + '</strong>'
    }
    return match
  })
}

const renderedContent = computed(() => {
  let text = props.message.content || ''
  text = fixCjkBold(text)
  // 简易 Markdown → HTML (用 marked.js 已在 Chat.vue 中 import，此处复用简化版)
  // 实际渲染由父组件传入或在此处做 basic transform
  text = text.replace(/```(\w*)\n([\s\S]*?)```/g, '<pre><code>$2</code></pre>')
  text = text.replace(/`([^`]+)`/g, '<code>$1</code>')
  text = text.replace(/\n/g, '<br>')
  return DOMPurify.sanitize(text)
})
</script>

<style scoped>
.message-bubble { display: flex; gap: 10px; max-width: 85%; padding: 8px 0; }
.msg-user { flex-direction: row-reverse; align-self: flex-end; }
.msg-ai { align-self: flex-start; }
.msg-avatar { flex-shrink: 0; width: 32px; height: 32px; border-radius: 50%; background: var(--accent-soft, #e8f0fe); display: flex; align-items: center; justify-content: center; color: var(--accent, #1677ff); }
.msg-content-wrap { max-width: 100%; }
.msg-role-tag { font-size: 11px; color: var(--text-secondary, #9ca3af); margin-bottom: 2px; }
.msg-user .msg-role-tag { text-align: right; }
.msg-text { padding: 10px 14px; border-radius: 16px; font-size: 14px; line-height: 1.6; word-break: break-word; }
.msg-user .msg-text { background: var(--accent, #1677ff); color: #fff; border-bottom-right-radius: 4px; }
.msg-ai .msg-text { background: var(--bg-message-ai, #f3f4f6); color: var(--text-primary, #1f2937); border-bottom-left-radius: 4px; }
.msg-actions { display: flex; gap: 4px; margin-top: 4px; opacity: 0; transition: opacity .15s; }
.message-bubble:hover .msg-actions { opacity: 1; }
.msg-action-btn { border: none; background: transparent; color: var(--text-secondary, #9ca3af); cursor: pointer; padding: 2px; border-radius: 4px; }
.msg-action-btn:hover { color: var(--accent, #1677ff); background: var(--bg-hover, #f0f0f0); }
:deep(pre) { background: #282c34; color: #abb2bf; padding: 12px; border-radius: 8px; overflow-x: auto; margin: 8px 0; font-size: 13px; }
:deep(code) { font-family: 'Fira Code', monospace; font-size: 0.9em; background: rgba(0,0,0,.05); padding: 2px 5px; border-radius: 3px; }
:deep(pre code) { background: none; padding: 0; }
</style>
