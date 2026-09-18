import request from './request'

/**
 * M1 用户体系接口（SRS 表53 / 概要设计 5.2.3，共 5 个）。
 *
 * <p>这一层的作用是「给每个请求起一个有意义的名字」。
 * 组件里写 {@code userApi.login(dto)} 比写
 * {@code request.post('/user/login', dto)} 更难写错路径，
 * 而且路径集中在这里，后端改了接口前缀只改一处。
 */

/** 用户注册。role 只允许 farmer / expert；验证码 Mock 固定为 111111 */
export function register(data) {
  return request.post('/user/register', data)
}

/** 用户登录。成功返回 { token, tokenType, expiresIn, user } */
export function login(data) {
  return request.post('/user/login', data)
}

/** 获取当前登录用户信息（从 Token 解身份，不需要传参） */
export function getInfo() {
  return request.get('/user/info')
}

/** 更新个人信息。部分更新语义：只传要改的字段，手机号不可改（DTO 里没有该字段） */
export function updateInfo(data) {
  return request.put('/user/info', data)
}

/**
 * 修改密码。
 * 注意：成功后服务端签发过的旧 Token 依然在有效期内（JWT 无状态，无法主动作废），
 * 所以前端收到成功响应后必须主动清登录态并跳登录页。
 */
export function changePassword(data) {
  return request.put('/user/password', data)
}

/** 健康检查（不需要登录，可用于排查前后端连通性） */
export function ping() {
  return request.get('/ping')
}

export default { register, login, getInfo, updateInfo, changePassword, ping }
