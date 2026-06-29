import { ref, nextTick } from 'vue'

/**
 * 滚动管理组合式函数
 * 提供 isNearBottom 守卫 + scrollToBottom + 滚动按钮显示控制
 */
export function useScroll(messagesContainer) {
  const showScrollBtn = ref(false)

  const isNearBottom = () => {
    const el = messagesContainer.value
    if (!el) return true
    return el.scrollHeight - el.scrollTop - el.clientHeight <= 100
  }

  const scrollToBottom = async (smooth = false) => {
    await nextTick()
    const el = messagesContainer.value
    if (el) {
      el.scrollTo({
        top: el.scrollHeight,
        behavior: smooth ? 'smooth' : 'instant',
      })
      if (smooth) showScrollBtn.value = false
    }
  }

  const checkScrollPosition = () => {
    const el = messagesContainer.value
    if (!el) return
    showScrollBtn.value = el.scrollHeight - el.scrollTop - el.clientHeight > 100
  }

  return { showScrollBtn, isNearBottom, scrollToBottom, checkScrollPosition }
}
