# =====================================================================
# geodata-web 业务打包镜像（依赖环境已由基础镜像提供，本文件只打包业务）
#
# 设计目的：与 geodata-web-base.Dockerfile 解耦。本文件仅负责把业务 jar 拷入
#   并配置运行用户/参数/健康检查/启动命令。业务代码改动后只需重建本镜像，
#   无需重跑 apt / Node / pip / COPY 工具链（基础镜像层被 Docker 缓存复用），
#   构建耗时从数分钟降到秒级。
#
# 前置：必须先构建基础镜像（含全部依赖环境）：
#   docker build -f deploy/geodata-web-base.Dockerfile -t geodata-web-base:1.0.0 .
#
# 本文件构建（context = 仓库根）：
#   docker build -f deploy/geodata-web.Dockerfile -t geodata-web:1.0.0 .
#
# 运行示例：
#   docker run -d --name geodata-web \
#     -p 8982:8982 \
#     -e SPRING_PROFILES_ACTIVE=prod \
#     -v /data/geodata/UploadFile:/app/geodata/UploadFile \
#     -v /data/geodata/Thumbnail:/app/geodata/Thumbnail \
#     -v /data/geodata/logs:/app/geodata/logs \
#     -v /data/geodata/convert:/tmp/geodata-convert \
#     geodata-web:1.0.0
#
# 从基础镜像继承的内容（FROM 自动生效，本文件无需重复）：
#   - 系统库 / Node20+obj2gltf / py3dtiles venv / 三维工具链
#   - ENV：LANG/LC_ALL/TZ/LD_LIBRARY_PATH/GDAL_DATA/PROJ_LIB/OSG_PLUGIN_PATH
#   - WORKDIR /app/geodata（含 UploadFile/Download/Thumbnail/Data/Style/logs 挂载点，uid 1000）
# =====================================================================

FROM geodata-web-base:1.0.0

# ---------------------------------------------------------------------
# 业务 jar（Spring Boot 3.3.5 fat jar，Java 17，finalName=geodata-web）
#   外部依赖（PostgreSQL/Redis/RabbitMQ/MinIO/GeoServer/SignalR）均为既有服务，
#   运行时按环境注入地址，镜像不含。
# ---------------------------------------------------------------------
COPY --chown=1000:1000 geodata-web/target/geodata-web.jar /app/geodata/geodata-web.jar

USER 1000

ENV JAVA_OPTS="-XX:MaxRAMPercentage=75.0 -Dfile.encoding=UTF-8 -Duser.timezone=Asia/Shanghai"

EXPOSE 8982

HEALTHCHECK --interval=30s --timeout=5s --start-period=90s --retries=3 \
    CMD curl -fsS http://localhost:8982/actuator/health || exit 1

# tini 作 PID 1：转发 SIGTERM、回收 ProcessBuilder 转换子进程
ENTRYPOINT ["/usr/bin/tini", "--", "sh", "-c", \
    "exec java $JAVA_OPTS -Djava.library.path=/usr/local/lib -jar /app/geodata/geodata-web.jar"]

# =====================================================================
# 附：关键环境变量覆盖（Spring relaxed binding，运行时 -e 注入）：
#   SPRING_DATASOURCE_DYNAMIC_DATASOURCE_MASTER_URL      PostgreSQL 连接
#   SPRING_DATASOURCE_DYNAMIC_DATASOURCE_MASTER_PASSWORD
#   SPRING_DATA_REDIS_HOST / SPRING_DATA_REDIS_PASSWORD
#   SPRING_RABBITMQ_HOST / SPRING_RABBITMQ_PASSWORD
#   MINIO_URL / MINIO_ACCESS_KEY / MINIO_SECRET_KEY
#   GEODATA_MODEL3D_STORAGE_PUBLIC-BASE-URL              3D Tiles 对外基地址
#   APPSETTINGS_GEOSERVERIP / APPSETTINGS_PUBLISHADDRESS GeoServer 地址
#   APPSETTINGS_PSQLBINDDIR=/usr/bin                     psql/shp2pgsql 目录
#   GEODATA_CONVERT_INSTANCE-ID                          多副本时必填且各不相同
# =====================================================================
