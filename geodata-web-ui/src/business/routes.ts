import type { RouteRecordRaw } from 'vue-router'

/** Workspace pages; each view remains a dynamic import. */
export const businessRoutes: RouteRecordRaw[] = [
  { path: '/', redirect: '/manage' },

  // ★ 主导航 · 数据管理（合并 layerContent 矢量/栅格 + model3d 三维 + 服务管理，上传/发布/服务运维全链路）
  {
    path: '/manage',
    name: 'DataManage',
    component: () => import('@/views/manage/index.vue'),
    meta: { title: '数据管理', domain: '数据管理' },
  },
  {
    path: '/manage/raster',
    name: 'RasterManage',
    component: () => import('@/views/raster/index.vue'),
    meta: { title: '栅格数据管理', domain: '数据管理' },
  },
  // ★ 主导航 · 图层配置（目录树 + 已发布服务挂载）
  {
    path: '/layers',
    name: 'LayerConfig',
    component: () => import('@/views/layers/index.vue'),
    meta: { title: '图层配置', domain: '图层配置' },
  },
  // ★ 主导航 · 地图管理（MapController + MapRelationController：地图编排 / 发布 / 切片缓存）
  {
    path: '/maps',
    name: 'MapManage',
    component: () => import('@/views/maps/index.vue'),
    meta: { title: '地图管理', domain: '地图管理' },
  },

  // ① 编目管理 —— DataTag(6) + TagRelation(8)
  //    （数据分类页已下线：与图层配置的目录 CRUD 同源重复；数据资源/三维资源/服务与缓存三域此前已并入主导航）
  {
    path: '/catalog/tag',
    name: 'CatalogTag',
    component: () => import('@/views/catalog/tag.vue'),
    meta: { title: '数据标签', domain: '编目管理' },
  },
  {
    path: '/catalog/tagrel',
    name: 'CatalogTagRel',
    component: () => import('@/views/catalog/tagrel.vue'),
    meta: { title: '标签关系', domain: '编目管理' },
  },

  // ② 样式与符号 —— DataStyle(35) + Symbol(6)
  {
    path: '/style',
    name: 'StyleLibrary',
    component: () => import('@/views/style/index.vue'),
    meta: { title: '样式库', domain: '样式与符号' },
  },
  {
    path: '/symbol',
    name: 'SymbolLibrary',
    component: () => import('@/views/symbol/index.vue'),
    meta: { title: '符号库', domain: '样式与符号' },
  },

  // ③ 处理与分析 —— LayerTool(13) + Topology(10)
  {
    path: '/tools',
    name: 'Toolbox',
    component: () => import('@/views/tools/index.vue'),
    meta: { title: '工具箱', domain: '处理与分析' },
  },
  {
    path: '/tools/topology',
    name: 'TopologyCheck',
    component: () => import('@/views/tools/topology.vue'),
    meta: { title: '拓扑检查', domain: '处理与分析' },
  },
  {
    path: '/tasks',
    name: 'TaskCenter',
    component: () => import('@/views/tasks/index.vue'),
    meta: { title: '任务中心', domain: '处理与分析' },
  },

  // ④ 系统 —— DicDataType(5) + DicDataCode(6) + LayerLog(2)
  {
    path: '/system/dict',
    name: 'SystemDict',
    component: () => import('@/views/system/dict.vue'),
    meta: { title: '字典管理', domain: '系统' },
  },
  {
    path: '/system/log',
    name: 'SystemLog',
    component: () => import('@/views/system/log.vue'),
    meta: { title: '操作日志', domain: '系统' },
  },
]
