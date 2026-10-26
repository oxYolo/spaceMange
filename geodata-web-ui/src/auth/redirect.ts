/** 登录回跳仅允许站内路径，同时防止编码路径及反斜杠绕过。 */
export function safeLoginRedirect(value: unknown, fallback = '/manage'): string {
  if (typeof value !== 'string' || !value.startsWith('/') || value.startsWith('//') || /[\\\u0000-\u001f]/.test(value)) {
    return fallback
  }
  try {
    const decoded = decodeURIComponent(value)
    if (decoded.startsWith('//') || /[\\\u0000-\u001f]/.test(decoded) || /^\/login(?:[/?#]|$)/i.test(decoded)) return fallback
    const target = new URL(value, 'http://geodata.local')
    if (target.origin !== 'http://geodata.local' || /^\/login(?:\/|$)/i.test(target.pathname)) return fallback
    return target.pathname + target.search + target.hash
  } catch {
    return fallback
  }
}
