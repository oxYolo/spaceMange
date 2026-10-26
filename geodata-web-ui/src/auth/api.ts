import { sm2 } from 'sm-crypto'
import { http } from '@/api/request'
import urlConfig from '@/config'

interface LoginResponse {
  code: number | string
  msg?: string
  data?: { access_token?: string }
}

interface ProfileResponse {
  code: number | string
  msg?: string
  user?: { userName?: string; nickName?: string }
  roles?: string[]
  permissions?: string[]
}

function loginPassword(password: string): string {
  // Flyspace 的开发模式使用明文 dronelogin；正式模式使用服务端配置的 SM2 公钥。
  if (!['production', 'test', 'cb'].includes(import.meta.env.MODE)) return password

  const configured = (urlConfig.authSm2PublicKey || '').replace(/\s+/g, '')
  const publicKey = configured.startsWith('04') ? configured : `04${configured}`
  if (!/^[0-9a-fA-F]{130}$/.test(publicKey)) {
    throw new Error('未配置有效的 SM2 登录公钥')
  }
  return sm2.doEncrypt(password, publicKey, urlConfig.authSm2CipherMode ?? 1)
}

export const authApi = {
  login(username: string, password: string, rememberMe: boolean): Promise<LoginResponse> {
    return http.post<LoginResponse>(
      '/auth/dronelogin',
      { username: username.trim(), password: loginPassword(password), rememberMe },
      undefined,
      { baseURL: urlConfig.baseApiUrl, headers: { isToken: false }, silent: true },
    )
  },
  getUserInfo(): Promise<ProfileResponse> {
    return http.get<ProfileResponse>('/system/user/getInfo', undefined, { baseURL: urlConfig.baseApiUrl, silent: true })
  },
  logout(token?: string): Promise<{ code: number; msg?: string }> {
    return http.del('/auth/logout', undefined, {
      baseURL: urlConfig.baseApiUrl,
      silent: true,
      ...(token ? { headers: { isToken: false, Authorization: `Bearer ${token}` } } : {}),
    })
  },
}
