import { NAV_ADVANCED, NAV_MAIN } from '@/router/nav'

export interface ShellCommand {
  id: string
  label: string
  description: string
  icon: string
  to: { path: string; query?: Record<string, string> }
  keywords: string[]
}

const aliases: Record<string, string[]> = {
  manage: ['资源', '全部数据', 'data', 'resources'],
  raster: ['影像', '地形', 'raster', 'tiff'],
  maps: ['地图', 'map'], layers: ['目录', 'layer'],
  tasks: ['进度', '任务', 'task', 'jobs'], tools: ['分析', 'tool'],
  topology: ['拓扑', 'topology'], style: ['渲染', 'style'], symbol: ['symbol'],
  tag: ['tag'], tagrel: ['tag relation'], dict: ['dictionary'], log: ['log', 'history'],
}

/** 只提供已有页面和上传入口，执行命令不会直接修改数据。 */
export function createShellCommands(): ShellCommand[] {
  const navigation: ShellCommand[] = [
    ...NAV_MAIN.map(item => ({ id: `page:${item.id}`, label: item.name, description: '数据工作台', icon: item.icon,
      to: { path: item.route }, keywords: aliases[item.id] || [] })),
    ...NAV_ADVANCED.flatMap(group => group.children.map(item => ({ id: `page:${item.id}`, label: item.name,
      description: group.title, icon: group.icon, to: { path: item.route }, keywords: aliases[item.id] || [] }))),
  ]
  return [...navigation,
    { id: 'upload:vector', label: '上传矢量数据', description: '打开上传窗口 · SHP / GeoJSON', icon: 'upload',
      to: { path: '/manage', query: { upload: 'vector' } }, keywords: ['上传', 'upload', 'vector', 'shp', 'geojson', '导入'] },
    { id: 'upload:raster', label: '上传栅格数据', description: '打开上传窗口 · GeoTIFF', icon: 'upload',
      to: { path: '/manage', query: { upload: 'raster' } }, keywords: ['上传', 'upload', 'raster', 'tif', 'tiff', '影像', '导入'] },
  ]
}

const commonIds = ['page:manage', 'upload:vector', 'upload:raster', 'page:tasks', 'page:maps', 'page:layers']
const normalize = (value: string) => value.normalize('NFKC').toLocaleLowerCase('zh-CN').trim()

/** 多词须全部命中；优先名称，其次别名、分组与路径。 */
export function searchShellCommands(commands: ShellCommand[], query: string): ShellCommand[] {
  const normalized = normalize(query).slice(0, 200)
  if (!normalized) return commonIds.map(id => commands.find(command => command.id === id)).filter((command): command is ShellCommand => Boolean(command))
  const words = normalized.split(/\s+/)
  return commands.map((command, index) => {
    const label = normalize(command.label)
    const keywords = normalize(command.keywords.join(' '))
    const description = normalize(command.description)
    const path = normalize(command.to.path)
    const haystack = `${label} ${keywords} ${description} ${path}`
    const score = label === normalized ? 0 : label.startsWith(normalized) ? 1 : label.includes(normalized) ? 2
      : keywords.includes(normalized) ? 3 : description.includes(normalized) ? 4 : 5
    return { command, index, score, matches: words.every(word => haystack.includes(word)) }
  }).filter(item => item.matches).sort((a, b) => a.score - b.score || a.index - b.index).map(item => item.command)
}

interface CommandKey {
  key: string
  altKey?: boolean
  ctrlKey?: boolean
  metaKey?: boolean
  shiftKey?: boolean
  isComposing?: boolean
  keyCode?: number
  repeat?: boolean
  defaultPrevented?: boolean
}
const composing = (event: CommandKey) => event.isComposing || event.keyCode === 229

export function isCommandShortcut(event: CommandKey, inDialog = false): boolean {
  return event.key.toLowerCase() === 'k' && Boolean(event.ctrlKey || event.metaKey) && !event.altKey && !event.shiftKey
    && !composing(event) && !event.repeat && !event.defaultPrevented && !inDialog
}

export function paletteKeyAction(event: CommandKey): 'next' | 'previous' | 'execute' | 'close' | null {
  if (composing(event) || event.defaultPrevented) return null
  if (event.key === 'Escape' || isCommandShortcut(event)) return 'close'
  if (event.altKey || event.ctrlKey || event.metaKey || event.shiftKey) return null
  if (event.key === 'ArrowDown') return 'next'
  if (event.key === 'ArrowUp') return 'previous'
  if (event.key === 'Enter') return 'execute'
  return null
}

export function moveCommandSelection(index: number, direction: 1 | -1, length: number): number {
  if (length <= 0) return -1
  if (index < 0 || index >= length) return direction === 1 ? 0 : length - 1
  return (index + direction + length) % length
}

export const commandShortcutLabel = (platform: string): string => /mac|iphone|ipad|ipod/i.test(platform) ? '⌘ K' : 'Ctrl K'
