<template>
  <aside class="sidebar" :class="{ collapsed: collapsed }">
    <!-- 收起态 -->
    <template v-if="collapsed">
      <div class="sidebar-top-collapsed">
        <el-tooltip content="展开侧边栏" placement="right" :show-after="400" :hide-after="0">
          <button class="expand-trigger" @click="$emit('toggle-collapse')">
            <svg xmlns="http://www.w3.org/2000/svg" width="18" height="18" fill="currentColor" viewBox="0 0 16 16" style="transform: scaleX(-1)">
              <path d="M8.5 3a4 4 0 0 0-3.8 2.745.5.5 0 1 1-.949-.313 5.002 5.002 0 0 1 9.654.595A3 3 0 0 1 13 12H4.5a.5.5 0 0 1 0-1H13a2 2 0 0 0 .001-4h-.026a.5.5 0 0 1-.5-.445A4 4 0 0 0 8.5 3M0 7.5A.5.5 0 0 1 .5 7h5a.5.5 0 0 1 0 1h-5a.5.5 0 0 1-.5-.5m2 2a.5.5 0 0 1 .5-.5h9a.5.5 0 0 1 0 1h-9a.5.5 0 0 1-.5-.5m-2 4a.5.5 0 0 1 .5-.5h9a.5.5 0 0 1 0 1h-9a.5.5 0 0 1-.5-.5"/>
            </svg>
          </button>
        </el-tooltip>
      </div>
    </template>

    <!-- 展开态 -->
    <template v-else>
      <div class="sidebar-top">
        <div class="sidebar-brand">
          <div class="brand-icon">
            <svg xmlns="http://www.w3.org/2000/svg" width="20" height="20" fill="currentColor" class="bi bi-robot" viewBox="0 0 16 16">
              <path d="M6 12.5a.5.5 0 0 1 .5-.5h3a.5.5 0 0 1 0 1h-3a.5.5 0 0 1-.5-.5M3 8.062C3 6.76 4.235 5.765 5.53 5.886a26.6 26.6 0 0 0 4.94 0C11.765 5.765 13 6.76 13 8.062v1.157a.93.93 0 0 1-.765.935c-.845.147-2.34.346-4.235.346s-3.39-.2-4.235-.346A.93.93 0 0 1 3 9.219zm4.542-.827a.25.25 0 0 0-.217.068l-.92.9a25 25 0 0 1-1.871-.183.25.25 0 0 0-.068.495c.55.076 1.232.149 2.02.193a.25.25 0 0 0 .189-.071l.754-.736.847 1.71a.25.25 0 0 0 .404.062l.932-.97a25 25 0 0 0 1.922-.188.25.25 0 0 0-.068-.495c-.538.074-1.207.145-1.98.189a.25.25 0 0 0-.166.076l-.754.785-.842-1.7a.25.25 0 0 0-.182-.135"/>
              <path d="M8.5 1.866a1 1 0 1 0-1 0V3h-2A4.5 4.5 0 0 0 1 7.5V8a1 1 0 0 0-1 1v2a1 1 0 0 0 1 1v1a2 2 0 0 0 2 2h10a2 2 0 0 0 2-2v-1a1 1 0 0 0 1-1V9a1 1 0 0 0-1-1v-.5A4.5 4.5 0 0 0 10.5 3h-2zM14 7.5V13a1 1 0 0 1-1 1H3a1 1 0 0 1-1-1V7.5A3.5 3.5 0 0 1 5.5 4h5A3.5 3.5 0 0 1 14 7.5"/>
            </svg>
          </div>
          <span class="brand-text">AI 对话</span>
          <el-tooltip content="收起侧边栏" placement="bottom" :show-after="400" :hide-after="0">
            <button class="collapse-trigger" @click="$emit('toggle-collapse')">
              <svg xmlns="http://www.w3.org/2000/svg" width="20" height="20" fill="currentColor" viewBox="0 0 16 16">
                <path d="M8.5 3a4 4 0 0 0-3.8 2.745.5.5 0 1 1-.949-.313 5.002 5.002 0 0 1 9.654.595A3 3 0 0 1 13 12H4.5a.5.5 0 0 1 0-1H13a2 2 0 0 0 .001-4h-.026a.5.5 0 0 1-.5-.445A4 4 0 0 0 8.5 3M0 7.5A.5.5 0 0 1 .5 7h5a.5.5 0 0 1 0 1h-5a.5.5 0 0 1-.5-.5m2 2a.5.5 0 0 1 .5-.5h9a.5.5 0 0 1 0 1h-9a.5.5 0 0 1-.5-.5m-2 4a.5.5 0 0 1 .5-.5h9a.5.5 0 0 1 0 1h-9a.5.5 0 0 1-.5-.5"/>
              </svg>
            </button>
          </el-tooltip>
        </div>
        <button class="new-chat-btn" @click="$emit('new-chat')">
          <el-icon :size="16"><Plus /></el-icon><span>新建对话</span>
        </button>
      </div>

      <!-- 搜索框 -->
      <div class="sidebar-search">
        <el-input v-model="searchText" placeholder="搜索对话…" size="small" :prefix-icon="Search" clearable />
      </div>

      <!-- 对话列表 -->
      <div class="conv-list">
        <div v-for="conv in filteredConversations" :key="conv.id" class="conv-item"
             :class="{ active: conv.id === currentId }"
             @click="$emit('select', conv.id)"
             @contextmenu.prevent="$emit('context-menu', { convId: conv.id, event: $event })">
          <div class="conv-title">{{ conv.title || '新对话' }}</div>
          <div class="conv-meta">{{ conv.lastMessage || '' }}</div>
        </div>
        <div v-if="filteredConversations.length === 0 && searchText" class="conv-empty">无匹配对话</div>
      </div>

      <!-- 回收站入口 -->
      <div class="sidebar-footer">
        <button class="trash-btn" @click="$emit('toggle-trash')">
          <el-icon :size="14"><Delete /></el-icon><span>回收站</span>
        </button>
      </div>
    </template>
  </aside>
</template>

<script setup>
import { ref, computed } from 'vue'
import { Plus, Search, Delete } from '@element-plus/icons-vue'

const props = defineProps({
  conversations: { type: Array, default: () => [] },
  currentId: { type: [Number, String], default: null },
  collapsed: { type: Boolean, default: false },
})
defineEmits(['toggle-collapse', 'new-chat', 'select', 'context-menu', 'toggle-trash'])

const searchText = ref('')

const filteredConversations = computed(() => {
  if (!searchText.value) return props.conversations
  const q = searchText.value.toLowerCase()
  return props.conversations.filter(c => (c.title || '新对话').toLowerCase().includes(q))
})
</script>

<style scoped>
.sidebar { width: 280px; display: flex; flex-direction: column; border-right: 1px solid var(--border-color, #e5e7eb); background: var(--bg-panel, #fafbfc); transition: width .2s; }
.sidebar.collapsed { width: 48px; }
.sidebar-top { display: flex; justify-content: space-between; align-items: center; padding: 14px; }
.sidebar-top-collapsed { display: flex; justify-content: center; padding-top: 14px; }
.sidebar-brand { display: flex; align-items: center; gap: 8px; }
.brand-icon { color: var(--accent, #1677ff); }
.brand-text { font-weight: 700; font-size: 15px; color: var(--text-primary, #1f2937); }
.collapse-trigger, .expand-trigger { border: none; background: transparent; color: var(--text-secondary, #9ca3af); cursor: pointer; padding: 2px; border-radius: 4px; }
.collapse-trigger:hover, .expand-trigger:hover { color: var(--accent, #1677ff); }
.new-chat-btn { display: flex; align-items: center; gap: 6px; padding: 6px 12px; border-radius: 8px; border: 1px dashed var(--border-color, #d1d5db); background: #fff; font-size: 13px; cursor: pointer; color: var(--accent, #1677ff); }
.new-chat-btn:hover { background: #f0f5ff; }
.sidebar-search { padding: 0 14px 8px; }
.conv-list { flex: 1; overflow-y: auto; padding: 4px 8px; }
.conv-item { padding: 10px 12px; border-radius: 8px; cursor: pointer; transition: background .1s; }
.conv-item:hover { background: var(--bg-hover, #f0f0f0); }
.conv-item.active { background: var(--accent-soft, #e8f0fe); }
.conv-title { font-size: 13px; font-weight: 500; color: var(--text-primary, #1f2937); white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
.conv-meta { font-size: 11px; color: var(--text-secondary, #9ca3af); margin-top: 2px; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
.conv-empty { text-align: center; padding: 20px; color: var(--text-secondary, #9ca3af); font-size: 13px; }
.sidebar-footer { padding: 8px 14px; border-top: 1px solid var(--border-color, #e5e7eb); }
.trash-btn { display: flex; align-items: center; gap: 6px; width: 100%; padding: 8px; border: none; border-radius: 6px; background: transparent; color: var(--text-secondary, #6b7280); font-size: 13px; cursor: pointer; }
.trash-btn:hover { background: var(--bg-hover, #f0f0f0); }
</style>
