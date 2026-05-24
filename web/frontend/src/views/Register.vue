<template>
  <div class="register-page">
    <div class="register-container">
      <!-- 左侧：注册表单区 -->
      <div class="register-left">
        <!-- Logo 区域 -->
        <div class="logo-area">
          <div class="logo-icon">
            <el-icon :size="28"><UserFilled /></el-icon>
          </div>
          <div class="logo-text">Spring Boot Auth</div>
          <div class="logo-sub">邮箱注册新账号</div>
        </div>

        <el-form
          ref="formRef"
          :model="form"
          :rules="rules"
          label-width="0"
          size="large"
          @keyup.enter="handleRegister"
        >
          <!-- 邮箱即用户名 -->
          <el-form-item prop="email">
            <el-input
              v-model="form.email"
              placeholder="请输入邮箱地址"
              :prefix-icon="Message"
            />
          </el-form-item>

          <!-- 图形验证码 -->
          <el-form-item prop="captchaCode">
            <div class="captcha-row">
              <el-input
                v-model="form.captchaCode"
                placeholder="请输入图形验证码"
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

          <!-- 验证码 + 发送按钮 -->
          <el-form-item prop="code">
            <div class="code-row">
              <el-input
                v-model="form.code"
                placeholder="请输入验证码"
                :prefix-icon="Key"
                class="code-input"
              />
              <el-button
                class="code-btn"
                :disabled="countdown > 0"
                @click="handleSendCode"
              >
                {{ countdown > 0 ? countdown + 's 后重发' : '发送验证码' }}
              </el-button>
            </div>
          </el-form-item>

          <el-form-item prop="password">
            <el-input
              v-model="form.password"
              type="password"
              placeholder="请输入密码（至少6位）"
              :prefix-icon="Lock"
              show-password
            />
          </el-form-item>
          <el-form-item prop="confirmPassword">
            <el-input
              v-model="form.confirmPassword"
              type="password"
              placeholder="请再次输入密码"
              :prefix-icon="Lock"
              show-password
            />
          </el-form-item>
          <el-form-item>
            <el-button
              class="btn-register"
              :loading="loading"
              @click="handleRegister"
            >
              {{ loading ? '注册中...' : '注 册' }}
            </el-button>
          </el-form-item>
        </el-form>

        <div class="links">
          <span>已有账号？</span>
          <router-link to="/login" class="link-item">立即登录</router-link>
        </div>

        <!-- 微信注册入口（预留） -->
        <div class="other-register">
          <div class="divider">
            <span class="divider-text">其他注册方式</span>
          </div>
          <div class="social-icons">
            <div class="social-icon wechat" title="微信注册">
              <span class="icon-placeholder">微</span>
            </div>
            <div class="social-icon qq" title="QQ注册">
              <span class="icon-placeholder">Q</span>
            </div>
          </div>
          <p class="social-hint">微信/QQ 快捷注册功能开发中</p>
        </div>
      </div>

      <!-- 右侧：注册权益信息 -->
      <div class="register-right">
        <div class="permission-title">
          <el-icon :size="18"><Promotion /></el-icon>
          <span>注册即享以下权益：</span>
        </div>

        <ul class="feature-list">
          <li class="feature-item">
            <div class="feature-icon">
              <el-icon><Message /></el-icon>
            </div>
            <div class="feature-text">
              <strong>邮箱安全验证</strong>
              <p>采用163邮箱SMTP发送6位验证码，5分钟有效，60秒防刷</p>
            </div>
          </li>
          <li class="feature-item">
            <div class="feature-icon">
              <el-icon><Lock /></el-icon>
            </div>
            <div class="feature-text">
              <strong>安全加密存储</strong>
              <p>密码采用 BCrypt 加密算法，每次加密结果不同，抗彩虹表攻击</p>
            </div>
          </li>
          <li class="feature-item">
            <div class="feature-icon">
              <el-icon><Refresh /></el-icon>
            </div>
            <div class="feature-text">
              <strong>无感 Token 刷新</strong>
              <p>accessToken 30分钟 + refreshToken 7天，自动刷新免频繁登录</p>
            </div>
          </li>
          <li class="feature-item">
            <div class="feature-icon">
              <el-icon><Monitor /></el-icon>
            </div>
            <div class="feature-text">
              <strong>单设备登录保护</strong>
              <p>同一账号只允许一个设备在线，新登录自动踢掉旧设备</p>
            </div>
          </li>
        </ul>

        <div class="agreement">
          注册即同意
          <a href="#" @click.prevent="showAgreement('service')">《服务协议》</a>和
          <a href="#" @click.prevent="showAgreement('privacy')">《隐私保护指引》</a>
        </div>

        <div class="tips-card">
          <el-alert
            title="温馨提示"
            type="success"
            :closable="false"
            show-icon
          >
            <template #default>
              <p>请使用真实邮箱注册，后续将支持邮箱找回密码功能</p>
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
import { User, Lock, UserFilled, Promotion, Refresh, Monitor, Message, Key, CircleCheck } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import { register, sendCode, getCaptcha } from '@/api/user'

const router = useRouter()
const formRef = ref(null)
const loading = ref(false)
const countdown = ref(0)
let countdownTimer = null
const captchaImage = ref('')
const captchaId = ref('')

const form = reactive({
  email: '',
  captchaCode: '',
  code: '',
  password: '',
  confirmPassword: '',
})

// 邮箱格式校验
const validateEmail = (rule, value, callback) => {
  const emailReg = /^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\.[a-zA-Z]{2,}$/
  if (!emailReg.test(value)) {
    callback(new Error('请输入正确的邮箱格式'))
  } else {
    callback()
  }
}

const validateConfirmPassword = (rule, value, callback) => {
  if (value !== form.password) {
    callback(new Error('两次输入的密码不一致'))
  } else {
    callback()
  }
}

const rules = {
  email: [
    { required: true, message: '请输入邮箱地址', trigger: 'blur' },
    { validator: validateEmail, trigger: 'blur' },
  ],
  captchaCode: [{ required: true, message: '请输入图形验证码', trigger: 'blur' }],
  code: [
    { required: true, message: '请输入邮箱验证码', trigger: 'blur' },
  ],
  password: [
    { required: true, message: '请输入密码', trigger: 'blur' },
    { min: 6, message: '密码不能少于6位', trigger: 'blur' },
  ],
  confirmPassword: [
    { required: true, message: '请再次输入密码', trigger: 'blur' },
    { validator: validateConfirmPassword, trigger: 'blur' },
  ],
}

// 刷新图形验证码
const refreshCaptcha = async () => {
  try {
    const res = await getCaptcha()
    captchaImage.value = res.data.captchaImage
    captchaId.value = res.data.captchaId
    form.captchaCode = ''
  } catch { /* 拦截器统一处理 */ }
}

onMounted(() => {
  refreshCaptcha()
})

// 发送邮箱验证码（需要先通过图形验证码）
const handleSendCode = async () => {
  const emailReg = /^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\.[a-zA-Z]{2,}$/
  if (!form.email || !emailReg.test(form.email)) {
    ElMessage.warning('请先输入正确的邮箱地址')
    return
  }
  if (!form.captchaCode) {
    ElMessage.warning('请先输入图形验证码')
    return
  }
  try {
    await sendCode(form.email, captchaId.value, form.captchaCode)
    ElMessage.success('验证码已发送，请查看邮箱')
    countdown.value = 60
    countdownTimer = setInterval(() => {
      countdown.value--
      if (countdown.value <= 0) clearInterval(countdownTimer)
    }, 1000)
    // 发送成功后刷新图形验证码
    refreshCaptcha()
  } catch {
    refreshCaptcha()
  }
}

const handleRegister = async () => {
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) return
  loading.value = true
  try {
    await register(form.email, form.password, form.code, captchaId.value, form.captchaCode)
    ElMessage.success('注册成功，请登录')
    clearInterval(countdownTimer)
    router.push('/login')
  } catch {
    refreshCaptcha()
  } finally {
    loading.value = false
  }
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

.register-page {
  background-color: #f5f5f5;
  display: flex;
  justify-content: center;
  align-items: center;
  min-height: 100vh;
  font-family: "PingFang SC", "Microsoft YaHei", sans-serif;
}

.register-container {
  display: flex;
  width: 980px;
  min-height: 680px;
  background-color: #fff;
  border-radius: 8px;
  box-shadow: 0 2px 20px rgba(0, 0, 0, 0.1);
  overflow: hidden;
}

/* ======== 左侧注册区域 ======== */
.register-left {
  width: 50%;
  padding: 36px 48px;
}

/* 表单整体间距 */
.register-left :deep(.el-form-item) {
  margin-bottom: 20px;
}

.logo-area {
  text-align: center;
  margin-bottom: 22px;
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

.logo-sub {
  font-size: 13px;
  color: #999;
  margin-top: 4px;
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

/* 邮箱验证码行 */
.code-row {
  display: flex;
  gap: 10px;
}

.code-input {
  flex: 1;
}

.code-btn {
  width: 130px;
  height: 42px;
  background-color: #fff;
  border: 1px solid #31c27c;
  color: #31c27c;
  font-size: 13px;
  border-radius: 4px;
  white-space: nowrap;
  flex-shrink: 0;
}

.code-btn:hover:not(:disabled) {
  background-color: #e8f8ef;
}

.code-btn:disabled {
  color: #bbb;
  border-color: #e5e5e5;
}

/* 注册按钮 */
.btn-register {
  width: 100%;
  height: 42px;
  background-color: #31c27c;
  border-color: #31c27c;
  color: #fff;
  font-size: 16px;
  border-radius: 4px;
}

.btn-register:hover,
.btn-register:focus {
  background-color: #2aae6a;
  border-color: #2aae6a;
}

/* 链接 */
.links {
  text-align: center;
  font-size: 13px;
  color: #666;
  margin-bottom: 20px;
}

.link-item {
  color: #31c27c;
  text-decoration: none;
  font-weight: 500;
}

.link-item:hover {
  text-decoration: underline;
}

/* 其他注册方式 */
.other-register {
  margin-top: 4px;
}

.divider {
  display: flex;
  align-items: center;
  margin-bottom: 14px;
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
  margin-bottom: 10px;
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

.social-hint {
  text-align: center;
  font-size: 11px;
  color: #bbb;
}

/* ======== 右侧权益区域 ======== */
.register-right {
  width: 50%;
  background-color: #f9fbf9;
  padding: 36px 48px;
  border-left: 1px solid #e8e8e8;
  display: flex;
  flex-direction: column;
}

.permission-title {
  font-size: 16px;
  color: #333;
  margin-bottom: 24px;
  display: flex;
  align-items: center;
  gap: 8px;
  font-weight: 500;
}

.feature-list {
  list-style: none;
  flex: 1;
}

.feature-item {
  display: flex;
  gap: 12px;
  margin-bottom: 20px;
  padding-bottom: 16px;
  border-bottom: 1px solid #f0f0f0;
}

.feature-item:last-child {
  border-bottom: none;
}

.feature-icon {
  width: 36px;
  height: 36px;
  border-radius: 50%;
  background-color: #e8f8ef;
  color: #31c27c;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.feature-text strong {
  display: block;
  font-size: 14px;
  color: #333;
  margin-bottom: 4px;
}

.feature-text p {
  font-size: 12px;
  color: #999;
  line-height: 1.5;
  margin: 0;
}

.agreement {
  font-size: 12px;
  color: #999;
  line-height: 1.6;
  margin-bottom: 16px;
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
  .register-container {
    flex-direction: column;
    width: 92%;
    min-height: auto;
  }

  .register-left,
  .register-right {
    width: 100%;
  }

  .register-right {
    border-left: none;
    border-top: 1px solid #e8e8e8;
  }

  .register-left {
    padding: 28px 24px;
  }

  .register-right {
    padding: 24px;
  }

  .code-btn {
    width: 110px;
    font-size: 12px;
  }
}
</style>
