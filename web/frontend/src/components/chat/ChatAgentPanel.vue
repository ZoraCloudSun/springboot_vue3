<template>
  <Transition name="reasoning-panel">
    <div v-if="steps.length > 0 && show" class="agent-panel">
      <div class="panel-header" @click="show = !show">
        <div class="panel-title">
          <el-icon :size="14" class="panel-icon"><Cpu /></el-icon>
          <span>推理过程 ({{ steps.length }} 步)</span>
        </div>
        <el-icon :size="14" class="panel-arrow" :class="{ rotated: !show }"><ArrowDown /></el-icon>
      </div>
      <div v-if="show" class="panel-body">
        <div v-for="(step, idx) in steps" :key="idx" class="step-item" :class="'step-' + step.type">
          <div class="step-icon">
            <el-icon v-if="step.type === 'thinking'" :size="14"><Loading /></el-icon>
            <el-icon v-else-if="step.type === 'tool_call'" :size="14"><Tools /></el-icon>
            <el-icon v-else-if="step.type === 'tool_result'" :size="14"><CircleCheck /></el-icon>
          </div>
          <div class="step-content">
            <div class="step-type-label">{{ typeLabel(step.type) }}</div>
            <div class="step-text" v-if="step.type === 'thinking'">{{ step.content }}</div>
            <div class="step-text" v-else-if="step.type === 'tool_call'">
              🔧 <strong>{{ step.tool }}</strong>
              <span class="step-args">{{ formatArgs(step.args) }}</span>
            </div>
            <div class="step-text" v-else-if="step.type === 'tool_result'">
              {{ formatResult(step.content) }}
            </div>
          </div>
        </div>
      </div>
    </div>
  </Transition>
</template>

<script setup>
import { ref } from 'vue'
import { Cpu, ArrowDown, Loading, Tools, CircleCheck } from '@element-plus/icons-vue'

const props = defineProps({
  steps: { type: Array, default: () => [] },
})
const emit = defineEmits(['collapse'])

const show = ref(true)

function typeLabel(type) {
  return { thinking: '思考中…', tool_call: '调用工具', tool_result: '工具结果' }[type] || type
}
function formatArgs(args) {
  if (!args) return ''
  let s = typeof args === 'string' ? args : JSON.stringify(args)
  return s.length > 60 ? s.substring(0, 60) + '…' : s
}
function formatResult(content) {
  if (!content) return ''
  try { const o = JSON.parse(content); return JSON.stringify(o).substring(0, 200); } catch {}
  return content.length > 200 ? content.substring(0, 200) + '…' : content
}
</script>

<style scoped>
.agent-panel { border: 1px solid #1677ff20; border-radius: 10px; margin: 0 16px 8px; background: #f0f5ff; overflow: hidden; }
.panel-header { display: flex; justify-content: space-between; align-items: center; padding: 8px 12px; cursor: pointer; user-select: none; }
.panel-title { display: flex; align-items: center; gap: 6px; font-size: 12px; font-weight: 600; color: #1677ff; }
.panel-icon { animation: pulse 2s infinite; }
@keyframes pulse { 0%,100%{opacity:1} 50%{opacity:.5} }
.panel-arrow { color: #1677ff; transition: transform .2s; }
.panel-arrow.rotated { transform: rotate(180deg); }
.panel-body { padding: 0 12px 8px; }
.step-item { display: flex; gap: 8px; padding: 6px 0; border-bottom: 1px solid #e5e7eb; font-size: 12px; }
.step-item:last-child { border-bottom: none; }
.step-icon { flex-shrink: 0; width: 20px; display: flex; align-items: flex-start; padding-top: 1px; }
.step-thinking .step-icon { color: #1677ff; }
.step-tool_call .step-icon { color: #f59e0b; }
.step-tool_result .step-icon { color: #10b981; }
.step-content { flex: 1; min-width: 0; }
.step-type-label { font-size: 10px; color: #9ca3af; margin-bottom: 2px; text-transform: uppercase; }
.step-text { line-height: 1.5; word-break: break-word; color: #374151; }
.step-args { color: #6b7280; font-size: 11px; margin-left: 4px; }
.reasoning-panel-enter-active, .reasoning-panel-leave-active { transition: all .3s ease; }
.reasoning-panel-enter-from, .reasoning-panel-leave-to { opacity: 0; transform: translateY(-10px); }
</style>
