<template>
  <div class="login-page">
    <div class="login-container">
      <!-- 左侧：登录表单区 -->
      <div class="login-left">
        <!-- Logo 区域 -->
        <div class="logo-area">
          <div class="logo-icon">
            <el-icon :size="28"><UserFilled /></el-icon>
          </div>
          <div class="logo-text">Spring Boot Auth</div>
        </div>

        <!-- 登录方式选项卡 -->
        <div class="tab-container">
          <div
            class="tab-item"
            :class="{ active: activeTab === 'password' }"
            @click="activeTab = 'password'"
          >
            密码登录
          </div>
          <div
            class="tab-item"
            :class="{ active: activeTab === 'wechat' }"
            @click="activeTab = 'wechat'"
          >
            微信登录
          </div>
        </div>

        <!-- 密码登录 -->
        <div class="tab-content" v-show="activeTab === 'password'">
          <p class="tip-text">使用账号密码进行安全登录</p>
          <el-form
            ref="formRef"
            :model="form"
            :rules="rules"
            label-width="0"
            size="large"
            @keyup.enter="handleLogin"
          >
            <el-form-item prop="username">
              <el-input
                v-model="form.username"
                placeholder="请输入用户名"
                :prefix-icon="User"
              />
            </el-form-item>
            <el-form-item prop="password">
              <el-input
                v-model="form.password"
                type="password"
                placeholder="请输入密码"
                :prefix-icon="Lock"
                show-password
              />
            </el-form-item>
            <!-- 图形验证码 -->
            <el-form-item prop="captchaCode">
              <div class="captcha-row">
                <el-input
                  v-model="form.captchaCode"
                  placeholder="请输入验证码"
                  :prefix-icon="CircleCheck"
                  class="captcha-input"
                />
                <img
                  class="captcha-img"
                  :src="captchaImage"
                  alt="图形验证码"
                  title="点击刷新验证码"
                  @click="refreshCaptcha"
                />
              </div>
            </el-form-item>
            <el-form-item>
              <el-button
                class="btn-login"
                :loading="loading"
                @click="handleLogin"
              >
                {{ loading ? '登录中...' : '登 录' }}
              </el-button>
            </el-form-item>
          </el-form>

          <div class="links">
            <router-link to="/register" class="link-item">注册账号</router-link>
            <a href="#" class="link-item" @click.prevent="forgetPassword">找回密码</a>
          </div>

          <!-- 其他登录方式 -->
          <div class="other-login">
            <div class="divider">
              <span class="divider-text">其他登录方式</span>
            </div>
            <div class="social-icons">
              <div class="social-icon wechat" title="微信登录" @click="activeTab = 'wechat'">
                <span class="icon-placeholder">微</span>
              </div>
              <div class="social-icon qq" title="QQ登录">
                <span class="icon-placeholder">Q</span>
              </div>
            </div>
          </div>
        </div>

        <!-- 微信扫码登录（预留） -->
        <div class="tab-content wechat-tab" v-show="activeTab === 'wechat'">
          <p class="tip-text">请使用微信扫描二维码登录</p>
          <div class="qrcode-area">
            <div class="qrcode-placeholder">
              <el-icon :size="80"><PictureFilled /></el-icon>
              <p class="qrcode-hint">微信扫码功能开发中</p>
              <p class="qrcode-hint-sub">请先使用密码登录</p>
            </div>
          </div>
          <div class="back-to-password">
            <el-button type="primary" link @click="activeTab = 'password'">
              <el-icon><ArrowLeft /></el-icon> 返回密码登录
            </el-button>
          </div>
        </div>
      </div>

      <!-- 右侧：授权信息 -->
      <div class="login-right">
        <div class="permission-title">
          <el-checkbox v-model="allChecked" @change="handleCheckAll">
            登录后将获取以下权限：
          </el-checkbox>
        </div>

        <ul class="permission-list">
          <li class="permission-item" :class="{ unchecked: !allChecked }">
            使用你的账户信息（用户名、头像）
          </li>
          <li class="permission-item" :class="{ unchecked: !allChecked }">
            读取你的登录状态以保持会话
          </li>
          <li class="permission-item" :class="{ unchecked: !allChecked }">
            使用 Redis 缓存 Token 确保单设备登录
          </li>
          <li class="permission-item" :class="{ unchecked: !allChecked }">
            accessToken 30分钟有效，refreshToken 7天有效
          </li>
        </ul>

        <div class="agreement">
          登录即同意
          <a href="#" @click.prevent="showAgreement('service')">《服务协议》</a>和
          <a href="#" @click.prevent="showAgreement('privacy')">《隐私保护指引》</a>
        </div>

        <div class="tips-card">
          <el-alert
            title="安全提示"
            type="success"
            :closable="false"
            show-icon
          >
            <template #default>
              <p>本系统采用 JWT 双 Token + BCrypt 加密，保障您的账号安全</p>
            </template>
          </el-alert>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { User, Lock, UserFilled, PictureFilled, ArrowLeft, CircleCheck } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import { login, getCaptcha } from '@/api/user'
import { setTokens } from '@/utils/token'

const router = useRouter()
const formRef = ref(null)
const loading = ref(false)
const activeTab = ref('password')
const allChecked = ref(true)
const captchaImage = ref('')
const captchaId = ref('')

const form = reactive({
  username: '',
  password: '',
  captchaCode: '',
})

const rules = {
  username: [{ required: true, message: '请输入用户名/邮箱', trigger: 'blur' }],
  password: [
    { required: true, message: '请输入密码', trigger: 'blur' },
    { min: 6, message: '密码不能少于6位', trigger: 'blur' },
  ],
  captchaCode: [{ required: true, message: '请输入图形验证码', trigger: 'blur' }],
}

// 刷新图形验证码
const refreshCaptcha = async () => {
  try {
    const res = await getCaptcha()
    captchaImage.value = res.data.captchaImage
    captchaId.value = res.data.captchaId
    form.captchaCode = ''
  } catch {
    // 错误由拦截器统一处理
  }
}

onMounted(() => {
  refreshCaptcha()
})

const handleLogin = async () => {
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) return
  loading.value = true
  try {
    const res = await login(form.username, form.password, captchaId.value, form.captchaCode)
    setTokens(res.data.accessToken, res.data.refreshToken)
    ElMessage.success('登录成功')
    router.push('/home')
  } catch {
    // 登录失败时刷新验证码
    refreshCaptcha()
  } finally {
    loading.value = false
  }
}

const handleCheckAll = (val) => {
  allChecked.value = val
}

const forgetPassword = () => {
  ElMessage.info('请联系管理员重置密码')
}

const showAgreement = (type) => {
  ElMessage.info(`${type === 'service' ? '服务协议' : '隐私保护指引'}详情页预留`)
}
</script>

<style scoped>
* {
  margin: 0;
  padding: 0;
  box-sizing: border-box;
}

.login-page {
  background-color: #f5f5f5;
  display: flex;
  justify-content: center;
  align-items: center;
  min-height: 100vh;
  font-family: "PingFang SC", "Microsoft YaHei", sans-serif;
}

.login-container {
  display: flex;
  width: 960px;
  min-height: 580px;
  background-color: #fff;
  border-radius: 8px;
  box-shadow: 0 2px 20px rgba(0, 0, 0, 0.1);
  overflow: hidden;
}

/* ======== 左侧登录区域 ======== */
.login-left {
  width: 50%;
  padding: 44px 48px;
}

/* 表单整体间距 */
.login-left :deep(.el-form-item) {
  margin-bottom: 22px;
}

.logo-area {
  text-align: center;
  margin-bottom: 28px;
}

.logo-icon {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 50px;
  height: 50px;
  background-color: #31c27c;
  border-radius: 50%;
  color: #fff;
  margin-bottom: 8px;
}

.logo-text {
  font-size: 22px;
  font-weight: bold;
  color: #333;
}

/* 选项卡 */
.tab-container {
  display: flex;
  margin-bottom: 20px;
  border-bottom: 1px solid #e5e5e5;
}

.tab-item {
  padding: 10px 24px;
  font-size: 15px;
  color: #666;
  cursor: pointer;
  position: relative;
  transition: color 0.3s;
  user-select: none;
}

.tab-item:hover {
  color: #31c27c;
}

.tab-item.active {
  color: #31c27c;
  font-weight: 500;
}

.tab-item.active::after {
  content: "";
  position: absolute;
  bottom: -1px;
  left: 12px;
  right: 12px;
  height: 2px;
  background-color: #31c27c;
  transition: all 0.3s;
}

/* 提示文字 */
.tip-text {
  font-size: 12px;
  color: #999;
  margin-bottom: 16px;
}

/* 图形验证码 */
.captcha-row {
  display: flex;
  align-items: center;
  gap: 10px;
}

.captcha-input {
  flex: 1;
}

.captcha-img {
  width: 150px;
  height: 30px;
  border-radius: 4px;
  cursor: pointer;
  border: 1px solid #e5e5e5;
  flex-shrink: 0;
}

.captcha-img:hover {
  border-color: #31c27c;
}

/* 登录按钮 */
.btn-login {
  width: 100%;
  height: 42px;
  background-color: #31c27c;
  border-color: #31c27c;
  color: #fff;
  font-size: 16px;
  border-radius: 4px;
}

.btn-login:hover,
.btn-login:focus {
  background-color: #2aae6a;
  border-color: #2aae6a;
}

/* 链接 */
.links {
  display: flex;
  justify-content: space-between;
  margin-bottom: 24px;
}

.link-item {
  font-size: 12px;
  color: #666;
  text-decoration: none;
}

.link-item:hover {
  color: #31c27c;
}

/* 其他登录方式 */
.other-login {
  margin-top: 8px;
}

.divider {
  display: flex;
  align-items: center;
  margin-bottom: 16px;
}

.divider::before,
.divider::after {
  content: "";
  flex: 1;
  height: 1px;
  background-color: #e5e5e5;
}

.divider-text {
  padding: 0 16px;
  font-size: 12px;
  color: #999;
}

.social-icons {
  display: flex;
  justify-content: center;
  gap: 24px;
}

.social-icon {
  width: 40px;
  height: 40px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  transition: all 0.3s;
}

.social-icon:hover {
  transform: scale(1.1);
}

.social-icon.wechat {
  background-color: #07c160;
  color: #fff;
}

.social-icon.qq {
  background-color: #12b7f5;
  color: #fff;
}

.icon-placeholder {
  font-size: 18px;
  font-weight: bold;
}

/* 微信扫码区域（预留） */
.wechat-tab {
  text-align: center;
}

.qrcode-area {
  display: flex;
  justify-content: center;
  margin-bottom: 20px;
}

.qrcode-placeholder {
  width: 180px;
  height: 180px;
  border: 2px dashed #d9d9d9;
  border-radius: 8px;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  color: #bbb;
  background-color: #fafafa;
}

.qrcode-hint {
  font-size: 14px;
  color: #999;
  margin-top: 8px;
  margin-bottom: 4px;
}

.qrcode-hint-sub {
  font-size: 12px;
  color: #bbb;
}

.back-to-password {
  margin-top: 12px;
}

/* ======== 右侧授权区域 ======== */
.login-right {
  width: 50%;
  background-color: #f9fbf9;
  padding: 44px 48px;
  border-left: 1px solid #e8e8e8;
  display: flex;
  flex-direction: column;
}

.permission-title {
  font-size: 14px;
  color: #333;
  margin-bottom: 20px;
}

.permission-list {
  list-style: none;
  margin-bottom: 24px;
  flex: 1;
}

.permission-item {
  font-size: 13px;
  color: #555;
  margin-bottom: 12px;
  padding-left: 20px;
  position: relative;
  transition: all 0.3s;
}

.permission-item::before {
  content: "";
  position: absolute;
  left: 0;
  top: 6px;
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background-color: #31c27c;
}

.permission-item.unchecked {
  color: #bbb;
  text-decoration: line-through;
}

.permission-item.unchecked::before {
  background-color: #ddd;
}

.agreement {
  font-size: 12px;
  color: #999;
  line-height: 1.6;
  margin-bottom: 20px;
}

.agreement a {
  color: #31c27c;
  text-decoration: none;
}

.agreement a:hover {
  text-decoration: underline;
}

.tips-card {
  margin-top: auto;
}

/* ======== 响应式 ======== */
@media (max-width: 768px) {
  .login-container {
    flex-direction: column;
    width: 92%;
    min-height: auto;
  }

  .login-left,
  .login-right {
    width: 100%;
  }

  .login-right {
    border-left: none;
    border-top: 1px solid #e8e8e8;
  }

  .login-left {
    padding: 28px 24px;
  }

  .login-right {
    padding: 28px 24px;
  }
}
</style>
