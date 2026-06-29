<template>
  <Transition name="scroll-btn">
    <button v-if="visible" class="scroll-bottom-btn" @click="$emit('click')" :title="streaming ? '滚动到底部查看生成' : '滚动到底部'">
      <svg v-if="streaming" class="scroll-ring" viewBox="0 0 32 32" width="32" height="32">
        <circle cx="16" cy="16" r="13" fill="none" stroke="var(--border-color, #d0d5dd)" stroke-width="2.5" />
        <circle cx="16" cy="16" r="13" fill="none" stroke="var(--accent, #1677ff)" stroke-width="2.5"
                stroke-dasharray="82" stroke-dashoffset="20" stroke-linecap="round" class="ring-spin" />
      </svg>
      <svg v-else xmlns="http://www.w3.org/2000/svg" width="16" height="16" fill="currentColor" viewBox="0 0 16 16">
        <path fill-rule="evenodd" d="M8 1a.5.5 0 0 1 .5.5v11.793l3.146-3.147a.5.5 0 0 1 .708.708l-4 4a.5.5 0 0 1-.708 0l-4-4a.5.5 0 0 1 .708-.708L7.5 13.293V1.5A.5.5 0 0 1 8 1z"/>
      </svg>
    </button>
  </Transition>
</template>

<script setup>
defineProps({
  visible: { type: Boolean, default: false },
  streaming: { type: Boolean, default: false },
})
defineEmits(['click'])
</script>

<style scoped>
.scroll-bottom-btn {
  position: sticky;
  bottom: 12px;
  left: 50%;
  transform: translateX(-50%);
  width: 32px;
  height: 32px;
  border-radius: 50%;
  background: var(--bg-message-user, #fff);
  border: 1px solid var(--border-color, #e5e7eb);
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 20;
  margin-top: -44px;
  box-shadow: 0 1px 4px rgba(0,0,0,0.08);
  color: var(--text-secondary, #6b7280);
  transition: box-shadow .2s;
}
.scroll-bottom-btn:hover {
  box-shadow: 0 2px 8px rgba(0,0,0,0.12);
  color: var(--accent, #1677ff);
}

.scroll-ring { position: absolute; }
.ring-spin { animation: ring-rotate 1.5s linear infinite; transform-origin: center; }
@keyframes ring-rotate { to { transform: rotate(360deg); } }

.scroll-btn-enter-active, .scroll-btn-leave-active { transition: opacity .2s, transform .2s; }
.scroll-btn-enter-from, .scroll-btn-leave-to { opacity: 0; transform: translateX(-50%) translateY(8px); }
</style>
