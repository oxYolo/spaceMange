/** 剪贴板复制（含 execCommand 降级） */
export async function copyText(text: string): Promise<boolean> {
  if (!text) return false
  try {
    if (navigator.clipboard && window.isSecureContext) {
      await navigator.clipboard.writeText(text)
      return true
    }
  } catch {
    /* 降级 */
  }
  let textarea: HTMLTextAreaElement | undefined
  const previous = document.activeElement instanceof HTMLElement ? document.activeElement : null
  try {
    const ta = textarea = document.createElement('textarea')
    ta.value = text
    ta.style.position = 'fixed'
    ta.style.opacity = '0'
    ta.tabIndex = -1
    document.body.appendChild(ta)
    ta.focus({ preventScroll: true })
    ta.select()
    ta.setSelectionRange(0, text.length)
    return document.execCommand('copy')
  } catch {
    return false
  } finally {
    textarea?.remove()
    if (previous?.isConnected) previous.focus({ preventScroll: true })
  }
}
