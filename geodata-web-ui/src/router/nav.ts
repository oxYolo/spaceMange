/**
 * 左侧导航（对齐 docs/低空监管数据中台-数据管理原型.html）
 * 结构：主导航 3 项（数据管理 / 地图管理 / 图层配置）+ 底部「高级功能」折叠组。
 * 收敛记录（2026-10-16）：
 *  - 「服务管理」并入数据管理（发布状态筛选 / 服务格式探测 / 复制地址 / 批量删除随迁）；
 *  - 编目管理「数据分类」下线：目录 CRUD 与图层配置页 100% 重复（同 sdms_datatype），目录维护统一在图层配置。
 */
export interface NavMainItem {
  id: string
  name: string
  route: string
  icon: string
}

export interface NavChild {
  id: string
  name: string
  route: string
  api?: string
}
export interface NavGroup {
  id: string
  title: string
  icon: string
  children: NavChild[]
}

/** 主导航：数据管理（含服务运维）/ 地图管理（Map+MapRelation 域）/ 图层配置（目录维护与挂载） */
export const NAV_MAIN: NavMainItem[] = [
  { id: 'manage', name: '数据管理', route: '/manage', icon: 'layers' },
  { id: 'maps', name: '地图管理', route: '/maps', icon: 'globe2' },
  { id: 'layers', name: '图层配置', route: '/layers', icon: 'tree' },
]

/** 高级功能（默认折叠）：与主导航不重叠的独立页面 */
export const NAV_ADVANCED: NavGroup[] = [
  {
    id: 'catalog',
    title: '编目管理',
    icon: 'tag',
    children: [
      { id: 'tag', name: '数据标签', route: '/catalog/tag', api: 'DataTag(6)' },
      { id: 'tagrel', name: '标签关系', route: '/catalog/tagrel', api: 'TagRelation(8)' },
    ],
  },
  {
    id: 'sym',
    title: '样式与符号',
    icon: 'palette',
    children: [
      { id: 'style', name: '样式库', route: '/style', api: 'DataStyle(35)' },
      { id: 'symbol', name: '符号库', route: '/symbol', api: 'Symbol(6)' },
    ],
  },
  {
    id: 'proc',
    title: '处理与分析',
    icon: 'tool',
    children: [
      { id: 'tools', name: '工具箱', route: '/tools', api: 'LayerTool(13)' },
      { id: 'topology', name: '拓扑检查', route: '/tools/topology', api: 'Topology(10)' },
      { id: 'tasks', name: '任务中心', route: '/tasks', api: 'LayerTool(13)' },
    ],
  },
  {
    id: 'sys',
    title: '系统',
    icon: 'gear',
    children: [
      { id: 'dict', name: '字典管理', route: '/system/dict', api: 'Dict(11)' },
      { id: 'log', name: '操作日志', route: '/system/log', api: 'LayerLog(2)' },
    ],
  },
]

/** 全部导航项（主 + 高级）平铺，供面包屑查找 */
export const NAV_ALL: NavChild[] = [
  ...NAV_MAIN.map((m) => ({ id: m.id, name: m.name, route: m.route })),
  ...NAV_ADVANCED.flatMap((g) => g.children),
]
