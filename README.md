# GeoDataManage 空间数据管理微服务

> 空间数据管理服务，提供矢量/栅格数据接入、数据编目（分类/标签/元数据）、样式与符号库、地图服务发布与切片、GIS 异步工具与拓扑检查，以及三维模型（倾斜摄影 / 点云 / BIM）管理能力。
> 技术栈：**Spring Boot 3.3.5 + Java 17 + whulri 3.6.6 基座 + MyBatis-Plus + PostgreSQL/PostGIS + GDAL + GeoServer + MinIO + RabbitMQ + Redis**，共 19 个 Controller。

## 1. 工程结构

```
GeoDataManage/
├── pom.xml                       # 父 POM（统一依赖版本）
├── geodata-common/               # 公共层：实体/DTO/统一返回/GDAL 与 shp 读写/GeoServer REST/SLD 样式/Mapper
├── geodata-service/              # 业务层：数据管理（datamanage）+ 服务发布（datarelease）+ 三维（model3d）
├── geodata-web/                  # Web 入口：启动类 / Controller / MQ 消费者 / proj7 资源
├── geodata-convert-core/         # 三维转换核心框架：ConvertCommandBuilder SPI / 跨平台进程执行 / 工具配置
├── geodata-convert-osgb/         # C++ 工具链：OSGB 倾斜摄影 → 3D Tiles
├── geodata-convert-pointcloud/   # Python 工具链：LAS/LAZ 点云 → pnts
├── geodata-convert-bim/          # Node/原生 工具链：OBJ/FBX/IFC → glTF/glb
└── sql/
    ├── geodata_manage_schema.sql # 建库 DDL（20 张表 + postgis/postgis_topology 扩展 + 索引）
    └── model3d_schema.sql        # 三维模块建表（sdms_model3d）
```

包名统一为 `com.whulri.geodata.*`，启动类为 `com.whulri.geodata.GeodataApplication`。

## 2. 技术栈

| 类别 | 选型 |
|------|------|
| 基座/框架 | Spring Boot 3.3.5、Spring Cloud 2023.0.3、whulri-common（core/security/log/swagger/redis/datasource/datascope） |
| 持久层 | MyBatis-Plus 3.5.8、dynamic-datasource-spring-boot3-starter 4.3.1、PostgreSQL 42.7.2 + PostGIS |
| 接口文档 | springdoc-openapi 2.6.0 + knife4j openapi3-jakarta（注解 `@Tag`/`@Operation`/`@Schema`） |
| GIS | GDAL 3.8（whulri-gdal 封装）、geoserver-manager 1.7.0-pdok2（GeoServer REST）、JTS 1.19、proj7 |
| 存储 | MinIO 8.2.2（对象存储）、PostGIS 空间表、本地磁盘工作区 |
| 消息/缓存 | RabbitMQ（异步工具任务与三维转换，手动 ack）、Redis（whulri-common-redis） |
| 实时推送 | SignalR 7.0.2 客户端（导入进度实时推送） |
| 工具库 | hutool 5.8.26、zip4j 2.11.5、commons-compress/io、POI 4.1.2、fastjson 2.0.32、pinyin4j |

## 3. 组件依赖

### 3.1 Maven 依赖

- `com.whulri:whulri-gdal:1.0.0` — GDAL 封装库，包名 `com.whulri.gdal.*`
- `nl.pdok:geoserver-manager:1.7.0-pdok2` — GeoServer REST 客户端
- `org.gdal:gdal:3.8.0` — GDAL JNI 绑定

### 3.2 内置工具包（随 geodata-common 提供）

| 包 | 内容 |
| --- | --- |
| `com.whulri.geodata.datatool.*` | gis（CoordSysType / CoordTransform / GdalHelp / Rect）、marshal（BOOL）、vector（FeatAttrs / GeometryBigType / LayerField / ShpHelper / ShpReader） |
| `com.whulri.geodata.common.*` | Environment、enums/OSEnum、io/Path、model（XY / XYZ / Point3D / OutObject）、thread（ThreadPool / ExceptionRunnable / ExceptionProcesser）、utils/Marshal |

## 4. 构建 & 运行

```bash
mvn clean package -DskipTests
# 产物：geodata-web/target/geodata-web.jar
java -jar geodata-web/target/geodata-web.jar
```

- 端口：**8982**；接口文档：http://localhost:8982/doc.html
- Nacos 默认禁用（bootstrap.yml 中 enabled=false），需要注册时自行打开

## 5. 配置要点（bootstrap-dev.yml）

| 配置 | 默认值 | 说明 |
|------|--------|------|
| 数据源 | `223.75.173.128:25501/geodata_manage`（PG） | 需先执行 DDL 建库 |
| Redis | localhost:6379 db5 | whulri-common-redis 必需 |
| RabbitMQ | localhost:5672 admin/admin123 | 工具任务/拓扑异步队列，手动 ack |
| MinIO | localhost:9000 minioadmin | 导出文件上传 |
| GeoServer | app-settings.geoServerIP = localhost / port 8081 | 发布与切片目标服务 |
| PostGIS 动态表 | app-settings.PostGis* | shp2pgsql 入库用 |
| psql/shp2pgsql | app-settings.psqlBinDir / shp2pgsqlBinDir | **PG 客户端 bin 目录，按本机安装路径改** |
| SignalR | imservice.* | 实时消息服务地址（IP/端口/SSL） |
| 三维存储/转换 | geodata.model3d.* / geodata.convert.* | 见第 9 节 |

## 6. 建库

```sql
-- psql 执行 sql/geodata_manage_schema.sql（20 张表 + postgis/postgis_topology 扩展 + 常用索引）
psql -h 223.75.173.128 -p 25501 -U postgres -d geodata_manage -f geodata_manage_schema.sql
-- 三维模块
psql -h 223.75.173.128 -p 25501 -U postgres -d geodata_manage -f sql/model3d_schema.sql
```

矢量图层入库时按 `sdms_layer.layerspatialtable` 动态建 PostGIS 空间表（运行时由 shp2pgsql 生成），无需预建。

## 7. 运行时环境依赖（非 Maven）

| 依赖 | 说明 |
|------|------|
| GDAL 3.8 native 库 | `org.gdal:gdal:3.8.0` 是 JNI 绑定，需 OS 级 GDAL（Windows 装 GDAL，Linux 可用含 GDAL 的镜像）。缺失时栅格功能不可用，纯矢量+发布功能不受影响 |
| PostgreSQL 客户端 | `psql` / `shp2pgsql`（AppSettingConfig 可配 bin 目录） |
| GeoServer | 发布/切片功能目标服务（REST 调用） |
| PROJ 数据 | proj7 数据文件已打包在 jar 内（geodata-web resources） |
| 三维转换工具 | 按需部署各语言工具链二进制并在 yml 配置路径，见第 9 节 |

## 8. 说明与注意事项

1. **进度推送**走 SignalR → 实时消息服务；无该服务时导入进度无实时推送（功能本身可用）。
2. `AuthUtil.getNickName()` 取 whulri 登录用户名；无登录上下文时返回空串。
3. `JacksonConfig` 自定义了全局 ObjectMapper（LocalDateTime 格式 yyyy-MM-dd HH:mm:ss），以本工程配置为准。
4. `sdms_metadata` 等表的 `"大写带引号"` 列名保留原样（PG 大小写敏感，勿手工改小写）。
5. 依赖私有 Nexus 仓库；若仓库不可达，需先本地安装第 3 节的 jar 依赖后再构建。

## 9. 三维数据管理（geodata-convert-* 模块）

以 3D Tiles 为统一成果格式，MinIO 对象托管，转换任务经 RabbitMQ 异步执行，工具调用按语言拆分独立 Maven 模块：

```
geodata-convert-core        # 核心框架：ConvertCommandBuilder SPI / 跨平台 ProcessExecutor / 工具配置
geodata-convert-osgb        # C++ 工具链：OSGB → 3D Tiles（fanvanzh/3dtiles，含 metadata.xml 解析）
geodata-convert-pointcloud  # Python 工具链：LAS/LAZ → pnts（py3dtiles）
geodata-convert-bim         # Node/原生 工具链：OBJ→glb(obj2gltf)、FBX→glb(FBX2glTF)、IFC→glTF(IfcConvert)
```

- 业务代码在 `geodata-service` 的 `com.whulri.geodata.model3d` 包（entity/mapper/service/publish/storage），Controller/消费者在 `geodata-web` 同名包；与 2D 业务（`com.whulri.geodata.datamanage` / `datarelease`）隔离。
- 接口前缀 `/model3d`：`init` / `{id}/part` / `{id}/complete` / `{id}/parts` / `{id}/convert` / `{id}` / `page` / `{id}/publish-url` / `DELETE {id}`（共 9 个）。
- 建表：`sql/model3d_schema.sql`（sdms_model3d，含分片数/包围盒/transform/状态机/心跳字段）。
- 配置：`geodata.model3d.storage.*`（独立于 `minio.*` 的 MinioConfig）与 `geodata.convert.tools.*`（每个工具 win-bin-path/linux-bin-path + command-template，按操作系统自动选择，模板支持 {bin}/{input}/{output}/{extra} 占位符，含空格路径无需转义）。
- 关键设计：海量小文件走「zip 整包上传 + 服务端解压」（zip4j，含 zip-slip 防护）；OSGB 局部原点自动解析 metadata.xml 并入库；产物统一为 MinIO `model3d/{id}/publish/` 前缀。
- **加载链路**：3D Tiles 子瓦片走相对路径，预签名 URL 无法覆盖 → 发布前缀匿名只读（`public-read` + `public-read-prefix`），`publish-url` 返回 `{public-base-url}/{bucket}/{key}` 公开地址；生产需配 nginx 反代 + CORS（`deploy/model3d/nginx-minio-proxy.conf`）。
- **稳定性**：转换心跳 + 看门狗回收僵尸「转换中」任务 + 启动按实例标识回收本实例残留；失败消息转投死信队列；上传分片断点续传（`GET /{id}/parts`）；转换前磁盘空间预检；产物格式校验。详见 `deploy/model3d/TOOLS-DEPLOY.md` 第 6、7 节。
- 运行前提：部署对应平台转换工具二进制并在 yml 中配置路径；未配置的工具下发转换时会明确报错，不影响其余功能。
