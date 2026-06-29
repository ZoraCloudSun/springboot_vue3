<template>
  <div class="chat-input-area">
    <!-- 上传菜单 -->
    <div class="upload-btn-wrap">
      <button class="pill-btn pill-upload" :class="{ open: menuOpen }" @click.stop="menuOpen = !menuOpen">
        <el-icon :size="14"><Plus /></el-icon>
      </button>
      <Transition name="upload-menu">
        <div v-if="menuOpen" class="upload-dropdown">
          <button class="upload-dropdown-item" @click.stop="handleUpload('image')">
            <el-icon :size="14"><Picture /></el-icon><span>上传图片</span>
          </button>
          <button class="upload-dropdown-item" @click.stop="handleUpload('document')">
            <el-icon :size="14"><Document /></el-icon><span>上传文档</span>
          </button>
        </div>
      </Transition>
    </div>

    <!-- 消息输入 -->
    <div class="textarea-wrapper">
      <textarea
        ref="textareaRef"
        v-model="inputText"
        class="chat-textarea"
        :placeholder="placeholder"
        rows="1"
        @keydown.enter.exact.prevent="handleSend"
        @input="autoResize"
      ></textarea>
    </div>

    <!-- 药丸按钮行 -->
    <div class="input-actions">
      <button class="pill-btn" :class="{ active: agentEnabled }" @click="$emit('toggle-agent')" title="Agent 模式">
        <el-icon :size="13"><Cpu /></el-icon>
        <span>Agent</span>
      </button>
      <button class="pill-btn" :class="{ active: ragEnabled && kbId }" @click="$emit('toggle-rag')" title="RAG 知识库">
        <el-icon :size="13"><Collection /></el-icon>
        <span>RAG</span>
      </button>
      <button class="pill-btn pill-send" @click="handleSend" :disabled="!canSend" title="发送">
        <el-icon :size="14"><Promotion /></el-icon>
      </button>
    </div>
  </div>
</template>

<script setup>
import { ref, computed } from 'vue'
import { Plus, Picture, Document, Cpu, Collection, Promotion } from '@element-plus/icons-vue'

const props = defineProps({
  agentEnabled: { type: Boolean, default: false },
  ragEnabled: { type: Boolean, default: false },
  kbId: { type: Number, default: null },
  disabled: { type: Boolean, default: false },
})
const emit = defineEmits(['send', 'toggle-agent', 'toggle-rag', 'upload'])

const inputText = ref('')
const textareaRef = ref(null)
const menuOpen = ref(false)

const placeholder = computed(() => {
  if (props.agentEnabled) return '输入问题，Agent 会自动调用工具…'
  if (props.ragEnabled && props.kbId) return '输入问题，AI 将基于知识库回答…'
  return '输入消息… (Enter 发送)'
})

const canSend = computed(() => inputText.value.trim() && !props.disabled)

function handleSend() {
  if (!canSend.value) return
  emit('send', inputText.value.trim())
  inputText.value = ''
  if (textareaRef.value) {
    textareaRef.value.style.height = 'auto'
  }
}

function handleUpload(type) {
  menuOpen.value = false
  emit('upload', type)
}

function autoResize() {
  const el = textareaRef.value
  if (!el) return
  el.style.height = 'auto'
  el.style.height = Math.min(el.scrollHeight, 150) + 'px'
}
</script>

<style scoped>
.chat-input-area {
  display: flex;
  align-items: flex-end;
  gap: 8px;
  padding: 12px 16px;
  border-top: 1px solid var(--border-color, #e5e7eb);
  background: var(--bg-panel, #fff);
}
.textarea-wrapper { flex: 1; }
.chat-textarea {
  width: 100%;
  border: 1px solid var(--border-color, #d1d5db);
  border-radius: 12px;
  padding: 10px 14px;
  font-size: 14px;
  line-height: 1.5;
  resize: none;
  outline: none;
  background: var(--bg-input, #f9fafb);
  color: var(--text-primary, #1f2937);
  transition: border-color .2s;
  font-family: inherit;
}
.chat-textarea:focus { border-color: var(--accent, #1677ff); background: #fff; }
.input-actions { display: flex; gap: 6px; align-items: center; flex-shrink: 0; }
.pill-btn {
  display: flex; align-items: center; gap: 4px;
  padding: 6px 10px; border-radius: 20px; border: 1px solid var(--border-color, #d1d5db);
  background: #fff; font-size: 12px; cursor: pointer; color: var(--text-secondary, #6b7280);
  transition: all .15s; white-space: nowrap;
}
.pill-btn:hover { border-color: var(--accent, #1677ff); color: var(--accent, #1677ff); }
.pill-btn.active { background: var(--accent, #1677ff); border-color: var(--accent, #1677ff); color: #fff; }
.pill-send { width: 34px; height: 34px; border-radius: 50%; justify-content: center; padding: 0; }
.pill-send:disabled { opacity: .4; cursor: not-allowed; }
.pill-send:not(:disabled):hover { background: var(--accent, #1677ff); color: #fff; }
.upload-btn-wrap { position: relative; flex-shrink: 0; }
.pill-upload { width: 34px; height: 34px; border-radius: 50%; justify-content: center; padding: 0; }
.pill-upload.open { border-color: var(--accent, #1677ff); }
.upload-dropdown { position: absolute; bottom: 42px; left: 0; background: #fff; border: 1px solid #e5e7eb; border-radius: 10px; box-shadow: 0 4px 16px rgba(0,0,0,.1); overflow: hidden; z-index: 30; min-width: 130px; }
.upload-dropdown-item { display: flex; align-items: center; gap: 8px; width: 100%; padding: 10px 14px; border: none; background: #fff; cursor: pointer; font-size: 13px; color: #374151; }
.upload-dropdown-item:hover { background: #f3f4f6; }
.upload-menu-enter-active, .upload-menu-leave-active { transition: all .15s ease; }
.upload-menu-enter-from, .upload-menu-leave-to { opacity: 0; transform: translateY(4px); }
</style>
