# geodata-web-ui · 空间数据管理控制台前端

> 后端：`geodata-service`（GeoDataManage / Spring Boot 3.3.5 / 端口 **8982**，19 Controller / 200 端点）
> 设计依据：`../docs/prototype/sdms-console.html`（v4 原型，7 大域 / 14 页）
> 技术栈：Vue 3.5 + Vite 6 + TypeScript + Element Plus 2.10 + Pinia + vue-router(hash) + axios
> 地图：二维 **OpenLayers**（`ol`）/ 三维 **Cesium**（复用参考工程静态库，按需加载）

---

## 1. 快速开始

```bash
cd geodata-web-ui
npm install            # 依赖
npm run dev            # http://localhost:9200 （请求转发到真实后端）
npm run build          # 产物 dist/
npm run type-check     # vue-tsc -p tsconfig.app.json --noEmit
```

开发时业务路由（例如 `#/manage`）可直接访问，`#/login` 是独立页面，不参与业务路由验证。前端不再提供本地示例数据；后端未启动时，列表和操作会显示请求错误。

业务接口默认经 Vite 的 `/api` 代理转发到 `http://localhost:8982`；认证与基座接口经 `/base-api` 转发到 `http://localhost:9201`。可通过环境变量覆盖目标：

```bash
GEODATA_API=http://localhost:8982 BASE_API=http://localhost:9201 npm run dev
```

登录页目前保留独立的接口调用，但不限制业务页访问。登录参考 Flyspace 的 `/auth/dronelogin`、`/system/user/getInfo` 和 `/auth/logout`，依赖单独的 `whulri-system` 基座服务；未启动该服务时无法完成真实登录。

Flyspace 在正式模式对登录密码使用 SM2 加密。此项目没有内置其他系统的公钥；正式对接时必须在 `public/config/urlConfig.js` 填入目标认证服务的 `authSm2PublicKey`，否则登录会明确报错。只启动本仓库的 `geodata-service:8982` 无法完成真实登录，因为它没有上述认证端点。

生产部署：`public/config/urlConfig.js` 里 `production.baseUrl` 置空（与后端同域），由 nginx 把
`/layerContent`、`/model3d` 等路径反代到 geodata-service；该文件运行时可直接修改，无需重新构建。

---

## 2. 目录结构

```
geodata-web-ui/
├── public/
│   ├── config/urlConfig.js      # 运行时环境配置（baseUrl / 天地图 token / 默认视图中心）
│   └── lib/Cesium/              # Cesium 静态库（按需注入，避免拖慢首屏）
└── src/
    ├── api/                     # 请求层与全量 API 模块（按 Controller 域拆分）
    │   ├── request.ts           # axios 封装：{code,success,result,msg} 解包 + 裸返回兼容
    │   └── modules/*.ts         # layerContent / layerField / upload / model3d / catalog / style / map / tool / system
    ├── components/
    │   ├── common/              # PageHead / Icon
    │   └── map/                 # Map2D（OpenLayers）/ Map3D（Cesium）
    ├── layouts/ConsoleLayout.vue# 顶栏 + 左侧 7 域导航 + 面包屑
    ├── router/                  # 路由表（14 页）+ 导航配置 nav.ts
    ├── utils/                   # gzip（解压 getLayerTree）/ format / cesium 加载器
    └── views/                   # 页面（见下表）
```

---

## 3. 页面清单

| 路由 | 页面 | 对应 Controller |
|:---|:---|:---|
| `#/manage` | 数据管理（目录、上传入库、服务发布） | LayerContent / Model3d / ServerInterface |
| `#/manage/raster` | 栅格管理（影像详情、切片与服务地址） | LayerContent / ServerInterface |
| `#/layers` | 图层目录及已发布服务挂载 | DataType / LayerContent |
| `#/maps` | 地图编排与发布 | Map / MapRelation |
| `#/catalog/tag` | 数据标签 | DataTag |
| `#/catalog/tagrel` | 标签关系 | TagRelation |
| `#/style` | 样式库 | DataStyle |
| `#/symbol` | 符号库 | Symbol |
| `#/tools` | 工具箱 | LayerTool |
| `#/tools/topology` | 拓扑检查 | Topology |
| `#/tasks` | 任务中心 | LayerTool |
| `#/system/dict` | 字典管理 | DicDataType / DicDataCode |
| `#/system/log` | 操作日志 | LayerLog |

筛选按后端实际可区分的类型组织：矢量与白模共用矢量入库，栅格与地形共用栅格入库，因此列表合并相应筛选项，避免显示没有实际区分能力的选项。

---

## 4. 对接要点（已在代码中逐条处理）

| 类别 | 处理方式 |
|:---|:---|
| **统一返回体** | `{code,success,result,msg}`；`api/request.ts` 的 `unwrap()` 自动取 `result`，`code!==200` 抛错 |
| **裸返回端点** | 约 20 个端点无 `Result` 外壳（如 `GetAllListByName`、`GetAttrtableByLayerId`、`Delete`），`unwrap()` 判定后原样返回 |
| **压缩串** | `getLayerTree` / `getLayerTreeByType` 返回 **Gzip+Base64**，`utils/gzip.ts` 用 pako 解压后再 `JSON.parse` |
| **字段名单数** | `ListResultOutput` 字段是 `item`，`pickList()` 已兼容 `item/items/records/list/rows/data` |
| **参数大小写混用** | `layerID` / `layerId`、`PageSize` / `pageSize`、`MapId`、`mapid` 等**逐端点按源码书写**，未做统一转换 |
| **表单 vs JSON** | `map/Insert`、`map/Update`、`map/GetAllListByName`、`layerContent/InsertImageLayer` 走 `http.postForm`（multipart/form-data） |
| **无类级前缀** | `Kneecoordinates/UploadShpFiles`、`Kneecoordinates/GetShpFileWKT` 路径自带前缀且首字母大写，已按原样书写 |
| **异步任务四段式** | 提交 → 返回 `taskId` → `/layerTool/getList?origin=N` 轮询 → `/layerTool/storageLayer` 成果入库 |
| **切片无取消端点** | 后端只有删记录没有 cancel，按钮文案用「查看」而非「停止」 |

---

## 5. 地图说明

- **二维（OpenLayers）**：天地图矢量/影像 + 注记底图；`addWms(url, layers)` 直接吃 `serverInterface` 返回的服务地址；
  支持框选 / 圈选 / 多边选 / 点选，供空间查询（`pageAttrInfoBySwitch`）使用。
- **三维（Cesium）**：`Map3D.vue` 首次挂载时动态注入 `public/lib/Cesium/Cesium.js`（约 4.4MB），
  控制台其它页面不会加载；`url` 传 `/model3d/{id}/publish-url` 返回的公开地址即可加载 3D Tiles。
- 天地图 token 取自参考工程 `spatiotemporal-intelligence-front`，可在 `public/config/urlConfig.js` 中替换。


## 开发与架构约定

- 登录页、认证接口、用户会话与登录路由集中在 `src/auth/`；业务路由位于 `src/business/routes.ts`，业务路由访问时才加载业务布局。两侧共用请求与错误处理。
- `npm run check` 执行真实应用/Node 配置类型检查、领域回归测试及生产构建。
- `npm test` 无需后端，覆盖查询分页/排序、服务缓存、认证会话、任务轮询与上传校验。
- 页面负责呈现与交互；业务状态、请求编排和操作准入放在 `src/features/`；HTTP 与认证在 `src/api/`、`src/auth/`。已有 `utils/datasets.ts` 保留兼容入口，新增代码优先引用领域模块。
- 统一使用 `showError(error, fallback)` 处理请求失败；取消和旧会话错误静默，表单校验提示单独处理。
- 写操作成功后调用 `invalidateDatasets()`；普通进度轮询只失效查询缓存，保留稳定服务地址缓存。
- 完整分层、缓存规则与产品状态流见 [架构说明](docs/architecture.md)。

## 高级操作

- **Ctrl/Cmd+K**：打开快捷导航，输入中文、英文别名或路径，使用方向键和 Enter 打开页面/上传入口；Esc 关闭。`/` 仍用于全局数据搜索。
- **快捷筛选**：数据管理中可一键筛未发布、转换中或处理失败；筛选和排序会体现在页面地址中，便于刷新后继续查看。
- **资源清单与服务地址**：列表的“清单与地址”支持导出当前页或已选资源的 CSV 清单，以及批量复制已发布服务地址。导出的是资源摘要，不是几何数据或完整属性表；不把当前页当全部数据。
- **服务接入**：在已发布资源的“预览”中选择协议，查看“服务接入”，复制真实地址及 cURL、OpenLayers、GeoJSON 或 Cesium 示例。WMTS/MVT 根据能力文档确定矩阵；WFS 请求最多 1000 条；缺少必要参数时明确说明，示例不会自动执行。
- **登录页**：仅作为独立页面保留；业务路由不验证登录状态。

接入参考：[OpenLayers WMTS API](https://openlayers.org/en/latest/apidoc/module-ol_source_WMTS.html)、[Cesium Viewer API](https://cesium.com/learn/cesiumjs/ref-doc/Viewer.html)。
