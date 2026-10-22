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
npm run dev            # http://localhost:9200 （默认启用本地 Mock，无需后端）
npm run build          # 产物 dist/
npm run type-check     # vue-tsc -p tsconfig.app.json --noEmit
```

开发时默认启用本地 Mock：`/api`、`/base-api` 由 Vite 本地处理，示例数据和常见增删改在当前开发服务进程内保存，重启后恢复。矢量 SHP 文件组支持本地分片上传、合并、入库、DBF 属性查看与服务发布流程联调；Mock 仅解析基本几何类型、范围和属性，不代替 GDAL、PostGIS 或 GeoServer。GIS 分析、转换等未模拟的操作返回明确的 501 错误。顶部会显示“本地 Mock”。

矢量 Mock 联调可运行 `node scripts/test-vector-mock.mjs`。测试脚本生成点状 SHP/SHX/DBF 样本，覆盖上传、合并、入库、属性读取和 WMS 发布；需先运行 `npm run dev`。使用真实后端的导入与发布仍需相应后端服务。

现在入口为 `#/login`。Mock 登录账号 `admin`、密码 `admin123`，只供本地界面联调；Mock 不提供真实访问控制。登录后 token 保存在浏览器会话中，勾选“记住账号”只保存账号，不保存密码。退出后会清除会话。截图右上角的输入法工具条属于操作系统，不是页面元素。

连接真实后端时禁用 Mock。业务接口默认 `http://localhost:8982`；登录参考 Flyspace 的 `/auth/dronelogin`、`/system/user/getInfo` 和 `/auth/logout`，走独立的 `whulri-system` 基座服务（默认 `http://localhost:9201`），可用环境变量覆盖：

```bash
GEODATA_MOCK=0 GEODATA_API=http://localhost:8982 BASE_API=http://localhost:9201 npm run dev
```

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

## 3. 页面清单（7 大域 / 14 页）

| 路由 | 页面 | 对应 Controller |
|:---|:---|:---|
| `#/data` | 资源总览（目录树 + 四维筛选 + 6 页签详情抽屉） | LayerContent(36) / LayerField(9) / MetaData(7) |
| `#/ingest` | 数据接入（通用分片 / SHP 系列） | Upload(2) / KneeCoordinates(2) |
| `#/model3d` | 三维数据（分片上传 / 转换 / 加载地址 / Cesium 预览） | Model3d(9) |
| `#/catalog/type` | 数据分类 | DataType(8) |
| `#/catalog/tag` | 数据标签 | DataTag(6) |
| `#/catalog/tagrel` | 标签关系 | TagRelation(8) |
| `#/style` | 样式库 | DataStyle(35) |
| `#/symbol` | 符号库 | Symbol(6) |
| `#/publish` | 服务与缓存（发布向导 + 服务预览 + 切片任务） | MapRelation(19) / ServerInterface(2) |
| `#/tools` | 工具箱（10 类异步工具 + 任务进度 + 成果入库） | LayerTool(13) |
| `#/tools/topology` | 拓扑检查 | Topology(10) |
| `#/tasks` | 任务中心 | LayerTool(13) |
| `#/system/dict` | 字典管理 | DicDataType(5) / DicDataCode(6) |
| `#/system/log` | 操作日志 | LayerLog(2) |

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
