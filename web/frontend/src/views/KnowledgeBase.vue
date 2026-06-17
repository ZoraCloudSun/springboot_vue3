<template>
  <div class="kb-container">
    <!-- 页面头部 -->
    <div class="kb-header">
      <div class="header-left">
        <el-icon :size="24"><Collection /></el-icon>
        <h1>知识库管理</h1>
        <span class="header-tip">上传文档构建知识库，让 AI 回答基于你的专属内容</span>
      </div>
      <div class="header-actions">
        <el-button type="primary" @click="openCreateDialog">
          <el-icon><Plus /></el-icon>
          新建知识库
        </el-button>
        <el-button @click="openKbRecycleBin" :type="kbRecycleBinCount > 0 ? 'warning' : 'default'">
          <el-icon><Delete /></el-icon>
          知识库回收站
          <el-badge v-if="kbRecycleBinCount > 0" :value="kbRecycleBinCount" class="recycle-badge" />
        </el-button>
        <el-button @click="$router.push('/home')">返回主页</el-button>
      </div>
    </div>

    <!-- 知识库列表（卡片式） -->
    <div class="kb-list" v-if="knowledgeBases.length > 0">
      <div
        v-for="kb in knowledgeBases"
        :key="kb.id"
        class="kb-card"
        :class="{ expanded: expandedKbId === kb.id }"
      >
        <!-- 卡片头部：可点击展开 -->
        <div class="kb-card-header" @click="toggleExpand(kb)">
          <div class="kb-card-info">
            <div class="kb-card-name">
              <el-icon :size="18" color="#409eff"><Folder /></el-icon>
              <span>{{ kb.name }}</span>
            </div>
            <div class="kb-card-meta">
              <span class="meta-doc-count">{{ kb.documentCount || 0 }} 个文档</span>
              <span class="meta-time">{{ formatTime(kb.updatedAt) }}</span>
            </div>
          </div>
          <div class="kb-card-desc" v-if="kb.description">{{ kb.description }}</div>
          <div class="kb-card-toggle">
            <el-icon :size="16">
              <ArrowDown v-if="expandedKbId !== kb.id" />
              <ArrowUp v-else />
            </el-icon>
          </div>
        </div>

        <!-- 展开区域：文档列表 + 操作 -->
        <div class="kb-card-body" v-if="expandedKbId === kb.id">
          <!-- 操作栏 -->
          <div class="doc-actions">
            <el-upload
              :before-upload="(file) => beforeUpload(file, kb.id)"
              :show-file-list="false"
              :http-request="(opt) => handleUpload(opt, kb.id)"
              accept=".pdf,.docx,.doc,.txt,.md"
            >
              <el-button size="small" type="primary" :loading="uploadingKbId === kb.id">
                <el-icon><Upload /></el-icon>
                上传文档
              </el-button>
            </el-upload>

            <el-button size="small" @click="openEditDialog(kb)">
              <el-icon><Edit /></el-icon>
              编辑
            </el-button>

            <el-popconfirm title="确定删除此知识库？所有文档将一并删除。" @confirm="handleDeleteKb(kb.id)">
              <template #reference>
                <el-button size="small" type="danger">
                  <el-icon><Delete /></el-icon>
                  删除
                </el-button>
              </template>
            </el-popconfirm>

            <!-- 检索测试面板切换 -->
            <el-button size="small" @click="showQueryPanel = showQueryPanel === kb.id ? null : kb.id">
              <el-icon><Search /></el-icon>
              {{ showQueryPanel === kb.id ? '关闭检索' : '测试检索' }}
            </el-button>

            <!-- 文档回收站 -->
            <el-button size="small" @click="openDocRecycleBin(kb.id)" style="margin-left: auto;">
              <el-icon><Delete /></el-icon>
              文档回收站
            </el-button>

            <span class="upload-tip">支持 PDF、DOCX、TXT、MD（最大 10MB）</span>
          </div>

          <!-- 检索测试面板 -->
          <div class="query-panel" v-if="showQueryPanel === kb.id">
            <el-input
              v-model="queryText"
              placeholder="输入检索关键词，测试知识库检索效果…"
              size="small"
              @keyup.enter="handleQuery(kb.id)"
            >
              <template #append>
                <el-button :icon="Search" :loading="queryLoading" @click="handleQuery(kb.id)">检索</el-button>
              </template>
            </el-input>
            <div class="query-results" v-if="queryResults.length > 0">
              <div class="query-result-item" v-for="(item, idx) in queryResults" :key="idx">
                <div class="result-header">
                  <el-tag size="small" type="success">相关度: {{ (item.score * 100).toFixed(1) }}%</el-tag>
                  <span class="result-source">来源: {{ item.filename }}</span>
                </div>
                <div class="result-content">{{ item.content }}</div>
              </div>
            </div>
            <div class="query-empty" v-else-if="querySearched">
              <el-empty description="未检索到相关内容" :image-size="60" />
            </div>
          </div>

          <!-- 文档表格 -->
          <el-table :data="documents[kb.id] || []" size="small" class="doc-table" v-loading="docLoading">
            <el-table-column prop="filename" label="文件名" min-width="200" show-overflow-tooltip />
            <el-table-column prop="fileType" label="类型" width="70" align="center">
              <template #default="{ row }">
                <el-tag size="small" type="info">{{ row.fileType?.toUpperCase() }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="fileSize" label="大小" width="90" align="center">
              <template #default="{ row }">{{ formatFileSize(row.fileSize) }}</template>
            </el-table-column>
            <el-table-column prop="status" label="状态" width="100" align="center">
              <template #default="{ row }">
                <el-tag size="small" :type="statusType(row.status)">{{ statusText(row.status) }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="chunkCount" label="块数" width="60" align="center" />
            <el-table-column label="时间" width="100" align="center">
              <template #default="{ row }">{{ formatTimeShort(row.createdAt) }}</template>
            </el-table-column>
            <el-table-column label="操作" width="80" align="center">
              <template #default="{ row }">
                <el-popconfirm title="确定删除此文档？" @confirm="handleDeleteDoc(kb.id, row.id)">
                  <template #reference>
                    <el-button size="small" type="danger" text>删除</el-button>
                  </template>
                </el-popconfirm>
              </template>
            </el-table-column>
          </el-table>
          <el-empty v-if="!documents[kb.id]?.length && !docLoading" description="暂无文档，上传一个试试" :image-size="60" />
        </div>
      </div>
    </div>

    <!-- 空状态 -->
    <div class="kb-empty" v-else-if="!loading">
      <el-empty description="还没有知识库">
        <el-button type="primary" @click="openCreateDialog">新建知识库</el-button>
      </el-empty>
    </div>

    <!-- 加载状态 -->
    <div class="kb-loading" v-if="loading">
      <el-skeleton :rows="3" animated />
    </div>

    <!-- ==================== 创建/编辑知识库对话框 ==================== -->
    <el-dialog
      v-model="dialogVisible"
      :title="isEditing ? '编辑知识库' : '新建知识库'"
      width="460px"
      :close-on-click-modal="false"
    >
      <el-form :model="form" label-position="top">
        <el-form-item label="名称" required>
          <el-input
            v-model="form.name"
            placeholder="请输入知识库名称"
            maxlength="200"
            show-word-limit
          />
        </el-form-item>
        <el-form-item label="描述（可选）">
          <el-input
            v-model="form.description"
            type="textarea"
            :rows="3"
            placeholder="简单描述这个知识库的内容…"
            maxlength="500"
            show-word-limit
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="handleSubmit">
          {{ isEditing ? '保存' : '创建' }}
        </el-button>
      </template>
    </el-dialog>

    <!-- ==================== 知识库回收站对话框 ==================== -->
    <el-dialog
      v-model="kbRecycleBinVisible"
      title="知识库回收站"
      width="70%"
      :close-on-click-modal="false"
      @closed="loadKnowledgeBases"
    >
      <el-table :data="kbRecycleBinItems" size="small" v-loading="kbRecycleBinLoading" style="width: 100%;">
        <el-table-column prop="name" label="知识库名称" min-width="160" show-overflow-tooltip />
        <el-table-column prop="description" label="描述" min-width="160" show-overflow-tooltip />
        <el-table-column prop="documentCount" label="已删除文档数" width="120" align="center" />
        <el-table-column label="删除时间" width="170" align="center">
          <template #default="{ row }">{{ formatTime(row.deletedAt) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="150" align="center">
          <template #default="{ row }">
            <el-button size="small" type="primary" text @click="handleRestoreKb(row.id)">恢复</el-button>
            <el-button size="small" type="danger" text @click="handlePermanentDeleteKb(row.id)">永久删除</el-button>
          </template>
        </el-table-column>
      </el-table>

      <el-empty v-if="!kbRecycleBinLoading && kbRecycleBinItems.length === 0" description="知识库回收站为空" :image-size="80" />
    </el-dialog>

    <!-- ==================== 文档回收站对话框（按知识库）==================== -->
    <el-dialog
      v-model="docRecycleBinVisible"
      title="文档回收站"
      width="70%"
      :close-on-click-modal="false"
      @closed="closeDocRecycleBin"
    >
      <el-table :data="docRecycleBinItems" size="small" v-loading="docRecycleBinLoading" style="width: 100%;">
        <el-table-column prop="filename" label="文件名" min-width="200" show-overflow-tooltip />
        <el-table-column prop="fileType" label="类型" width="80" align="center">
          <template #default="{ row }">
            <el-tag size="small" type="info">{{ row.fileType?.toUpperCase() }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="fileSize" label="大小" width="100" align="center">
          <template #default="{ row }">{{ formatFileSize(row.fileSize) }}</template>
        </el-table-column>
        <el-table-column label="删除时间" width="170" align="center">
          <template #default="{ row }">{{ formatTime(row.deletedAt) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="150" align="center">
          <template #default="{ row }">
            <el-button size="small" type="primary" text @click="handleRestoreDoc(docRecycleBinKbId, row.id)">恢复</el-button>
            <el-button size="small" type="danger" text @click="handlePermanentDeleteDoc(docRecycleBinKbId, row.id)">永久删除</el-button>
          </template>
        </el-table-column>
      </el-table>

      <div class="recycle-bin-footer" v-if="docRecycleBinItems.length > 0">
        <el-button type="danger" size="small" @click="handleEmptyDocRecycleBin(docRecycleBinKbId)">清空回收站</el-button>
      </div>

      <el-empty v-if="!docRecycleBinLoading && docRecycleBinItems.length === 0" description="回收站为空" :image-size="80" />
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, onMounted, onBeforeUnmount } from 'vue'
import {
  Collection, Plus, Folder, ArrowDown, ArrowUp,
  Upload, Edit, Delete, Search,
} from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  createKnowledgeBase, listKnowledgeBases, getKnowledgeBase,
  updateKnowledgeBase, deleteKnowledgeBase,
  uploadDocument, listDocuments, deleteDocument,
  queryKnowledgeBase,
  listDeletedKnowledgeBases, restoreKnowledgeBase, permanentlyDeleteKnowledgeBase,
  listDeletedDocuments, restoreDocument, permanentlyDeleteDocument, emptyDocumentRecycleBin,
} from '@/api/rag'

// ==================== 数据状态 ====================
const knowledgeBases = ref([])
const loading = ref(false)
const expandedKbId = ref(null)
const documents = ref({})
const docLoading = ref(false)
const uploadingKbId = ref(null)
// 检索测试
const showQueryPanel = ref(null)
const queryText = ref('')
const queryLoading = ref(false)
const queryResults = ref([])
const querySearched = ref(false)
// 对话框
const dialogVisible = ref(false)
const isEditing = ref(false)
const editingKbId = ref(null)
const submitting = ref(false)
const form = ref({ name: '', description: '' })

// 知识库回收站（全局）
const kbRecycleBinVisible = ref(false)
const kbRecycleBinItems = ref([])
const kbRecycleBinLoading = ref(false)
const kbRecycleBinCount = ref(0)

// 文档回收站（按知识库）
const docRecycleBinVisible = ref(false)
const docRecycleBinKbId = ref(null)
const docRecycleBinItems = ref([])
const docRecycleBinLoading = ref(false)

// 轮询定时器
let pollingTimer = null

// ==================== 生命周期 ====================
onMounted(async () => {
  await loadKnowledgeBases()
  // 获取知识库回收站数量（用于徽章显示）
  refreshKbRecycleBinCount()
  // 每 5 秒轮询：刷新有 PENDING/PROCESSING 状态文档的知识库
  pollingTimer = setInterval(() => refreshProcessingDocs(), 5000)
})

onBeforeUnmount(() => {
  if (pollingTimer) clearInterval(pollingTimer)
})

// ==================== 知识库操作 ====================
async function loadKnowledgeBases() {
  loading.value = true
  try {
    const res = await listKnowledgeBases()
    knowledgeBases.value = res.data || []
  } catch {
    // 错误由拦截器处理
  } finally {
    loading.value = false
  }
}

function toggleExpand(kb) {
  if (expandedKbId.value === kb.id) {
    expandedKbId.value = null
    showQueryPanel.value = null
  } else {
    expandedKbId.value = kb.id
    showQueryPanel.value = null
    loadDocuments(kb.id)
  }
}

function openCreateDialog() {
  isEditing.value = false
  editingKbId.value = null
  form.value = { name: '', description: '' }
  dialogVisible.value = true
}

function openEditDialog(kb) {
  isEditing.value = true
  editingKbId.value = kb.id
  form.value = { name: kb.name, description: kb.description || '' }
  dialogVisible.value = true
}

async function handleSubmit() {
  if (!form.value.name.trim()) {
    ElMessage.warning('请输入知识库名称')
    return
  }
  submitting.value = true
  try {
    if (isEditing.value) {
      await updateKnowledgeBase(editingKbId.value, form.value.name.trim(), form.value.description.trim())
      ElMessage.success('已更新')
    } else {
      await createKnowledgeBase(form.value.name.trim(), form.value.description.trim())
      ElMessage.success('知识库创建成功')
    }
    dialogVisible.value = false
    await loadKnowledgeBases()
  } catch {
    // 错误由拦截器处理
  } finally {
    submitting.value = false
  }
}

async function handleDeleteKb(kbId) {
  try {
    await deleteKnowledgeBase(kbId)
    ElMessage.success('已删除')
    if (expandedKbId.value === kbId) expandedKbId.value = null
    await loadKnowledgeBases()
  } catch {}
}

// ==================== 文档操作 ====================
async function loadDocuments(kbId) {
  docLoading.value = true
  try {
    const res = await listDocuments(kbId)
    documents.value[kbId] = res.data || []
  } catch {} finally {
    docLoading.value = false
  }
}

function beforeUpload(file) {
  const ext = file.name.split('.').pop()?.toLowerCase()
  const allowed = ['pdf', 'docx', 'doc', 'txt', 'md']
  if (!allowed.includes(ext)) {
    ElMessage.error('不支持的文件类型，支持：PDF、DOCX、DOC、TXT、MD')
    return false
  }
  if (file.size > 10 * 1024 * 1024) {
    ElMessage.error('文件大小不能超过 10MB')
    return false
  }
  return true
}

async function handleUpload(options, kbId) {
  uploadingKbId.value = kbId
  try {
    await uploadDocument(kbId, options.file)
    ElMessage.success('文档已上传，正在处理中…')
    // 立即刷新文档列表
    await loadDocuments(kbId)
    // 更新知识库列表（文档数量）
    await loadKnowledgeBases()
  } catch {} finally {
    uploadingKbId.value = null
  }
}

async function handleDeleteDoc(kbId, docId) {
  try {
    await deleteDocument(kbId, docId)
    ElMessage.success('已删除')
    await loadDocuments(kbId)
    await loadKnowledgeBases()
  } catch {}
}

// 轮询刷新：检查是否有处理中的文档，有则刷新
async function refreshProcessingDocs() {
  const kbId = expandedKbId.value
  if (!kbId) return
  const docs = documents.value[kbId] || []
  const hasProcessing = docs.some(
    d => d.status === 'PENDING' || d.status === 'PROCESSING'
  )
  if (hasProcessing) {
    await loadDocuments(kbId)
    await loadKnowledgeBases()
  }
}

// ==================== 知识库回收站操作 ====================

/** 打开知识库回收站对话框 */
async function openKbRecycleBin() {
  kbRecycleBinVisible.value = true
  await loadKbRecycleBin()
}

/** 加载已删除的知识库列表 */
async function loadKbRecycleBin() {
  kbRecycleBinLoading.value = true
  try {
    const res = await listDeletedKnowledgeBases()
    kbRecycleBinItems.value = res.data || []
    kbRecycleBinCount.value = kbRecycleBinItems.value.length
  } catch {} finally {
    kbRecycleBinLoading.value = false
  }
}

/** 静默刷新知识库回收站数量（用于徽章显示） */
async function refreshKbRecycleBinCount() {
  try {
    const res = await listDeletedKnowledgeBases()
    kbRecycleBinCount.value = (res.data || []).length
  } catch {}
}

/** 恢复知识库及其所有文档 */
async function handleRestoreKb(kbId) {
  try {
    await restoreKnowledgeBase(kbId)
    ElMessage.success('知识库已恢复')
    await loadKbRecycleBin()
    await loadKnowledgeBases()
  } catch {}
}

/** 永久删除知识库 */
async function handlePermanentDeleteKb(kbId) {
  try {
    await ElMessageBox.confirm('此操作将永久删除该知识库及其所有文档和数据，不可恢复。确定继续？', '警告', {
      confirmButtonText: '确定删除',
      cancelButtonText: '取消',
      type: 'warning',
    })
    await permanentlyDeleteKnowledgeBase(kbId)
    ElMessage.success('已永久删除知识库')
    await loadKbRecycleBin()
    await loadKnowledgeBases()
  } catch {
    // 用户取消或出错
  }
}

// ==================== 文档回收站操作（按知识库）====================

/** 打开指定知识库的文档回收站 */
async function openDocRecycleBin(kbId) {
  docRecycleBinKbId.value = kbId
  docRecycleBinVisible.value = true
  await loadDocRecycleBin(kbId)
}

/** 加载指定知识库的已删除文档列表 */
async function loadDocRecycleBin(kbId) {
  docRecycleBinLoading.value = true
  try {
    const res = await listDeletedDocuments(kbId)
    docRecycleBinItems.value = res.data || []
  } catch {} finally {
    docRecycleBinLoading.value = false
  }
}

/** 恢复文档 */
async function handleRestoreDoc(kbId, docId) {
  try {
    await restoreDocument(kbId, docId)
    ElMessage.success('文档已恢复')
    await loadDocRecycleBin(kbId)
    await loadKnowledgeBases()
    // 如果当前展开了该知识库，刷新其文档列表
    if (expandedKbId.value === kbId) {
      await loadDocuments(kbId)
    }
  } catch {}
}

/** 永久删除文档 */
async function handlePermanentDeleteDoc(kbId, docId) {
  try {
    await ElMessageBox.confirm('此操作将永久删除文档及其所有数据，不可恢复。确定继续？', '警告', {
      confirmButtonText: '确定删除',
      cancelButtonText: '取消',
      type: 'warning',
    })
    await permanentlyDeleteDocument(kbId, docId)
    ElMessage.success('已永久删除')
    await loadDocRecycleBin(kbId)
  } catch {
    // 用户取消或出错
  }
}

/** 清空指定知识库的文档回收站 */
async function handleEmptyDocRecycleBin(kbId) {
  if (docRecycleBinItems.value.length === 0) {
    ElMessage.info('回收站已为空')
    return
  }
  try {
    await ElMessageBox.confirm(
      `确定要清空回收站吗？将永久删除 ${docRecycleBinItems.value.length} 个文档，此操作不可逆。`,
      '清空回收站',
      {
        confirmButtonText: '确定清空',
        cancelButtonText: '取消',
        type: 'warning',
      }
    )
    const res = await emptyDocumentRecycleBin(kbId)
    ElMessage.success(res.msg || '回收站已清空')
    await loadDocRecycleBin(kbId)
    await loadKnowledgeBases()
    if (expandedKbId.value === kbId) {
      await loadDocuments(kbId)
    }
  } catch {
    // 用户取消或出错
  }
}

/** 关闭文档回收站时刷新 */
function closeDocRecycleBin() {
  if (docRecycleBinKbId.value) {
    loadKnowledgeBases()
    if (expandedKbId.value === docRecycleBinKbId.value) {
      loadDocuments(docRecycleBinKbId.value)
    }
  }
}

// ==================== 检索测试 ====================
async function handleQuery(kbId) {
  if (!queryText.value.trim()) return
  queryLoading.value = true
  querySearched.value = true
  try {
    const res = await queryKnowledgeBase(kbId, queryText.value.trim())
    queryResults.value = res.data || []
  } catch {} finally {
    queryLoading.value = false
  }
}

// ==================== 格式化工具 ====================
function formatTime(time) {
  if (!time) return ''
  return new Date(time).toLocaleString('zh-CN')
}

function formatTimeShort(time) {
  if (!time) return ''
  const d = new Date(time)
  return `${d.getMonth() + 1}/${d.getDate()} ${d.getHours()}:${String(d.getMinutes()).padStart(2, '0')}`
}

function formatFileSize(bytes) {
  if (!bytes) return '0 B'
  if (bytes < 1024) return bytes + ' B'
  if (bytes < 1048576) return (bytes / 1024).toFixed(1) + ' KB'
  return (bytes / 1048576).toFixed(1) + ' MB'
}

function statusType(status) {
  const map = { PENDING: 'warning', PROCESSING: '', COMPLETED: 'success', FAILED: 'danger' }
  return map[status] || 'info'
}

function statusText(status) {
  const map = { PENDING: '等待中', PROCESSING: '处理中', COMPLETED: '已完成', FAILED: '失败' }
  return map[status] || status
}
</script>

<style scoped>
.kb-container {
  max-width: 900px;
  margin: 0 auto;
  padding: 24px 20px 60px;
}

/* 头部 */
.kb-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 24px;
  flex-wrap: wrap;
  gap: 12px;
}
.header-left {
  display: flex;
  align-items: center;
  gap: 10px;
}
.header-left h1 {
  font-size: 22px;
  font-weight: 600;
  margin: 0;
}
.header-tip {
  color: #909399;
  font-size: 13px;
}
.header-actions {
  display: flex;
  gap: 8px;
}

/* 知识库卡片 */
.kb-list { display: flex; flex-direction: column; gap: 12px; }
.kb-card {
  border: 1px solid #e4e7ed;
  border-radius: 10px;
  background: #fff;
  transition: box-shadow 0.2s;
}
.kb-card:hover { box-shadow: 0 2px 12px rgba(0,0,0,0.06); }
.kb-card.expanded { border-color: #409eff; }
.kb-card-header {
  padding: 16px 20px;
  cursor: pointer;
  display: flex;
  align-items: center;
  gap: 16px;
}
.kb-card-info { flex: 1; min-width: 0; }
.kb-card-name {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 15px;
  font-weight: 500;
}
.kb-card-meta {
  margin-top: 4px;
  font-size: 12px;
  color: #909399;
  display: flex;
  gap: 16px;
}
.kb-card-desc {
  flex: 1;
  color: #606266;
  font-size: 13px;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
.kb-card-toggle { color: #909399; }

/* 展开区域 */
.kb-card-body {
  padding: 0 20px 16px;
  border-top: 1px solid #ebeef5;
}
.doc-actions {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 12px 0;
  flex-wrap: wrap;
}
.upload-tip { font-size: 12px; color: #c0c4cc; margin-left: auto; }

/* 检索面板 */
.query-panel {
  margin-bottom: 12px;
  padding: 12px;
  background: #f5f7fa;
  border-radius: 8px;
}
.query-results { margin-top: 10px; display: flex; flex-direction: column; gap: 8px; }
.query-result-item {
  padding: 10px 12px;
  background: #fff;
  border-radius: 6px;
  border: 1px solid #e4e7ed;
}
.result-header { display: flex; align-items: center; gap: 10px; margin-bottom: 6px; }
.result-source { font-size: 12px; color: #909399; }
.result-content {
  font-size: 13px;
  color: #303133;
  line-height: 1.6;
  white-space: pre-wrap;
  max-height: 120px;
  overflow-y: auto;
}
.query-empty { margin-top: 10px; }

/* 文档表格 */
.doc-table { margin-top: 4px; }

/* 空/加载 */
.kb-empty, .kb-loading { margin-top: 60px; }

/* 回收站 */
.recycle-badge { margin-left: 4px; }
.recycle-bin-footer {
  margin-top: 16px;
  text-align: right;
}
</style>
