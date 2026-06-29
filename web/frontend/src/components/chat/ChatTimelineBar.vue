<template>
  <div class="conv-timeline" :class="{ expanded: timelineHovered }"
       @mouseenter="timelineHovered = true" @mouseleave="timelineHovered = false">
    <div class="timeline-track">
      <div v-for="(item, idx) in userMessageAnchors" :key="idx" class="timeline-segment"
           @click="$emit('scroll-to', item.index)">
        <div class="timeline-bar"></div>
        <Transition name="timeline-tip">
          <div v-if="timelineHovered" class="timeline-tooltip">{{ truncateText(item.preview, 40) }}</div>
        </Transition>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed } from 'vue'

const props = defineProps({
  messages: { type: Array, default: () => [] },
})
defineEmits(['scroll-to'])

const timelineHovered = ref(false)

const userMessageAnchors = computed(() => {
  return props.messages
    .map((m, idx) => ({ role: m.role, preview: m.content, index: idx }))
    .filter(m => m.role === 'user')
})

function truncateText(text, maxLen) {
  if (!text) return ''
  return text.length > maxLen ? text.substring(0, maxLen) + '…' : text
}
</script>

<style scoped>
.conv-timeline {
  position: absolute;
  right: 12px;
  top: 50%;
  transform: translateY(-50%);
  width: 6px;
  max-height: calc(100% - 120px);
  z-index: 10;
  transition: width .25s ease, right .25s ease;
}
.conv-timeline.expanded {
  width: 180px;
  right: 8px;
}
.timeline-track {
  display: flex;
  flex-direction: column;
  gap: 3px;
  height: 100%;
  overflow-y: auto;
  padding: 2px 0;
}
.timeline-segment {
  flex: 1;
  min-height: 4px;
  cursor: pointer;
  position: relative;
}
.timeline-bar {
  width: 100%;
  height: 100%;
  min-height: 4px;
  border-radius: 3px;
  background: var(--accent, #1677ff);
  opacity: 0.35;
  transition: opacity .15s;
}
.timeline-segment:hover .timeline-bar { opacity: 0.8; }
.timeline-tooltip {
  position: absolute;
  right: 14px;
  top: 50%;
  transform: translateY(-50%);
  background: var(--bg-tooltip, #1f2937);
  color: #fff;
  font-size: 12px;
  padding: 4px 10px;
  border-radius: 6px;
  white-space: nowrap;
  max-width: 160px;
  overflow: hidden;
  text-overflow: ellipsis;
  pointer-events: none;
  box-shadow: 0 2px 8px rgba(0,0,0,0.15);
}
.timeline-tip-enter-active, .timeline-tip-leave-active { transition: opacity .15s; }
.timeline-tip-enter-from, .timeline-tip-leave-to { opacity: 0; }
</style>
