# 本机 Docker 后端环境

此配置把 Java、Maven 缓存、PostGIS、Redis、RabbitMQ、MinIO、GeoServer 和后端运行时放进 Docker。宿主机只需 Docker Desktop。数据库和文件保存在本 Compose 项目的命名卷中；停止容器不会删数据，`down --volumes` 才会删除。

## 当前构建前置条件

- 此仓库没有 `geodata-web/target/geodata-web.jar`。构建必须能获取 `com.whulri:*` 等私有 Maven 依赖。仓库 POM 使用 `192.168.10.55:48081` 的私有 Nexus；无法访问时，可以由项目方提供已经构建好的 JAR。
- 原有 `deploy/geodata-web-base.Dockerfile` 使用需登录的私有镜像仓库 `223.75.173.128:48082`。要走原有的完整 GDAL 3.8 与模型转换工具链，必须有仓库权限，或由项目方提供 `geodata-web-base:1.0.0` 镜像归档。
- 当前机器是 Apple Silicon，原有基础镜像与三维工具是 `linux/amd64`。Compose 已指定 x86 平台，运行依赖 Docker 的架构模拟。
- GeoServer 和 SignalR 是两件事：此 Compose 启动 GeoServer；仓库没有 SignalR 服务端源码，后端会按现有设计降级为轮询。

## 启动

在仓库根目录运行：

```sh
cp deploy/local/.env.example deploy/local/.env
# 按需修改 .env 中仅供本机使用的密码
docker compose --env-file deploy/local/.env -f deploy/local/compose.yaml --profile build run --rm builder
docker build --platform linux/amd64 -f deploy/geodata-web-base.Dockerfile -t geodata-web-base:1.0.0 .
docker compose --env-file deploy/local/.env -f deploy/local/compose.yaml up -d --build
docker compose --env-file deploy/local/.env -f deploy/local/compose.yaml ps
curl -fsS http://127.0.0.1:8982/actuator/health
```

首次构建基础镜像之前，须先用你自己的账号完成私有 Registry 登录。请不要把账号、令牌或 Maven 仓库凭据写入仓库文件。若项目方提供了 JAR 和基础镜像归档，可先 `docker load -i <镜像归档>`，把 JAR 放到 `geodata-web/target/geodata-web.jar`，跳过 Maven 和基础镜像构建步骤。

`bootstrap-docker.yml` 是 Docker 专用 Spring 配置，数据库、Redis、RabbitMQ、MinIO、GeoServer 均指向 Compose 服务名；它不使用 `bootstrap-dev.yml` 中的远程 PostgreSQL。只有 `127.0.0.1:8982`、`127.0.0.1:8081`、`127.0.0.1:9000` 和 `127.0.0.1:9001` 对宿主机开放。

前端当前默认使用本地 Mock。后端健康后，在 `geodata-web-ui` 目录以 `GEODATA_MOCK=0 npm run dev -- --host 127.0.0.1` 重启前端，再测试真实接口。前端另有 `/base-api` 指向独立的 `whulri-system` 基座服务，此 Compose 不包含它；依赖行政区划等基座接口的页面仍需该服务。

## 停止和清理

```sh
docker compose --env-file deploy/local/.env -f deploy/local/compose.yaml down
# 确认不再需要本地数据库、文件和 Maven 缓存后：
docker compose --env-file deploy/local/.env -f deploy/local/compose.yaml down --volumes --remove-orphans
docker image rm geodata-web:local geodata-web-base:1.0.0
```

`down --volumes` 会永久删除本地测试数据。宿主机的 Docker Desktop 需单独卸载。
