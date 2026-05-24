import request from './index'

export function getCaptcha() {
  return request.get('/user/captcha')
}

export function sendCode(email, captchaId, captchaCode) {
  return request.post('/user/send-code', { email, captchaId, captchaCode })
}

export function register(email, password, code, captchaId, captchaCode) {
  return request.post('/user/register', { email, password, code, captchaId, captchaCode })
}

export function login(username, password, captchaId, captchaCode) {
  return request.post('/user/login', { username, password, captchaId, captchaCode })
}

export function logout() {
  return request.post('/user/logout')
}

export function refreshToken(refreshToken) {
  return request.post('/user/refresh', { refreshToken })
}

export function getUserInfo() {
  return request.get('/user/info')
}
