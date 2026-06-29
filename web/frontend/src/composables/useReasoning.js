import { ref } from 'vue'

/**
 * Agent 推理步骤管理
 * 管理 Agent 推理面板的思考步骤、工具调用、工具结果的时序流
 */
export function useReasoning() {
  const reasoningSteps = ref([])
  const reasoningActive = ref(false)
  const showReasoning = ref(true)

  const addReasoningStep = (step) => {
    reasoningSteps.value.push({ ...step, id: Date.now() + Math.random() })
  }

  const updateLastReasoningStep = (updates) => {
    const steps = reasoningSteps.value
    if (steps.length > 0) {
      const last = steps[steps.length - 1]
      Object.assign(last, updates)
      // vue 3 ref 响应式: 替换数组最后一个元素触发更新
      steps[steps.length - 1] = { ...last }
    }
  }

  const clearReasoning = () => {
    reasoningSteps.value = []
    reasoningActive.value = false
    showReasoning.value = true
  }

  return {
    reasoningSteps, reasoningActive, showReasoning,
    addReasoningStep, updateLastReasoningStep, clearReasoning
  }
}
