interface SearchKey {
  key: string
  altKey: boolean
  ctrlKey: boolean
  metaKey: boolean
  shiftKey: boolean
  isComposing: boolean
  defaultPrevented: boolean
}

/** 搜索热键不能截获编辑、输入法或弹层中的键盘输入。 */
export function isGlobalSearchShortcut(event: SearchKey, editing: boolean, inDialog: boolean): boolean {
  return event.key === '/' && !event.altKey && !event.ctrlKey && !event.metaKey && !event.shiftKey
    && !event.isComposing && !event.defaultPrevented && !editing && !inDialog
}
