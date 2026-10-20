# =====================================================================
# geodata-web 基础镜像（依赖环境层，不含业务 jar）
#
# 设计目的：把"重依赖环境"与"业务 jar"解耦。本镜像一次性固化所有依赖：
#   - 系统库（tini / curl / 中文字体 / locales / python3 / libopenscenegraph161）
#   - Node 20 + 全局 obj2gltf（经 npx 调用）
#   - py3dtiles venv（点云 LAS/LAZ → pnts）
#   - 三维转换工具链（3dtile=OSGB / FBX2glTF / IfcConvert）
#   - 工作目录 /app/geodata 及挂载点权限（uid 1000）
#   - 全部运行时 ENV（GDAL_DATA / PROJ_LIB / OSG_PLUGIN_PATH / LD_LIBRARY_PATH）
#
# 业务打包镜像 geodata-web.Dockerfile 只需 FROM 本镜像后 COPY geodata-web.jar
# 并配置启动，构建耗时从数分钟降到秒级；本镜像仅在"依赖变更"时重建：
#   更换 Node/py3dtiles/工具版本、新增系统库、调整 ENV、改动工作目录结构。
#
# 基础镜像：223.75.173.128:48082/amd/ubuntu-java17-gdal38-pg:latest（私有 Registry）
#   已内置：Java 17 运行时、GDAL 3.8（C++ 库 + GDAL_DATA/proj 数据）、
#           PostgreSQL 客户端（psql / shp2pgsql）；libgdalalljni.so 在 /usr/local/lib
#
# 构建（context = 仓库根；内网构建机需先 docker login 私有 Registry）：
#   docker build -f deploy/geodata-web-base.Dockerfile -t geodata-web-base:1.0.0 .
#
# 三维工具链安装路径约定（与 bootstrap.yml 的 linux-bin-path 严格一致，无需改配置）：
#   py3dtiles   /opt/model3d-venv/bin/py3dtiles        (venv, py3dtiles 12.1.1)
#   obj2gltf    npx                                     (Node 20 全局预装)
#   fbx2gltf    /opt/tools/model3d-tools/FBX2glTF/FBX2glTF-linux-x64
#   ifcconvert  /opt/tools/model3d-tools/IfcConvert/IfcConvert
#   3dtiles     /opt/tools/model3d-tools/3dtiles/3dtile  (fanvanzh/3dtiles v0.3 源码编译)
# =====================================================================

FROM 223.75.173.128:48082/amd/ubuntu-java17-gdal38-pg:latest

ENV DEBIAN_FRONTEND=noninteractive

# ---------------------------------------------------------------------
# 系统依赖补齐（基础镜像已有 java/gdal/pg 客户端，apt 对已装包自动跳过）
#   tini                 PID 1 信号转发/僵尸回收（Java ProcessBuilder 会起
#                        3dtile/py3dtiles/npx 等子进程，需随容器退出而清理）
#   curl                 HEALTHCHECK 用
#   fonts-wqy-zenhei     中文字体（Excel 导出/缩略图渲染防方块）
#   fonts-dejavu-core    基础西文字体
#   locales + C.UTF-8    工具 stdout 统一 UTF-8（yml output-charset 默认 UTF-8）
#   python3 三件套       py3dtiles venv 用（py3dtiles 12.x 需 Python ≥3.10）
#   libopenscenegraph161  3dtile(OSGB) 运行时：提供 libosg.so.161 / libosgDB.so.161
#                        / libosgUtil.so.161 / libOpenThreads.so.21 及插件目录
#                        /usr/lib/x86_64-linux-gnu/osgPlugins-3.6.5（含读取
#                        .osgb 所需的 osgdb_serializers_osg.so）。版本须与编译
#                        3dtile 时所用 OSG 3.6.5 一致（SONAME 161 → ABI 安全）
#
# 内网构建注意：若构建机 apt 不通外网，需换内网源；node/python 环境亦可请
# 运维在基础镜像中预置，删掉下方对应安装段即可。
# ---------------------------------------------------------------------
RUN apt-get update && apt-get install -y --no-install-recommends \
        tini \
        curl \
        ca-certificates \
        locales \
        fonts-wqy-zenhei \
        fonts-dejavu-core \
        python3 \
        python3-venv \
        python3-pip \
        gnupg \
        libopenscenegraph161 \
    && locale-gen C.UTF-8 \
    && rm -rf /var/lib/apt/lists/*

ENV LANG=C.UTF-8 \
    LC_ALL=C.UTF-8 \
    TZ=Asia/Shanghai

# ---------------------------------------------------------------------
# Node.js 20（obj2gltf 经 npx 调用；NodeSource 官方源）
# 全局预装 obj2gltf：npx 命中本地全局包后不再联网下载（离线环境必须），
# yml 模板 "{bin} --yes obj2gltf ..." 无需改动
# ---------------------------------------------------------------------
RUN curl -fsSL https://deb.nodesource.com/setup_20.x | bash - \
    && apt-get install -y --no-install-recommends nodejs \
    && npm install -g obj2gltf \
    && npm cache clean --force \
    && rm -rf /var/lib/apt/lists/*

# ---------------------------------------------------------------------
# py3dtiles venv（点云 LAS/LAZ → pnts）
#   - 路径 /opt/model3d-venv 与 yml linux-bin-path 一致
#   - 12.x 无 __main__.py，bin 必须指向 console_scripts 入口 py3dtiles（venv 内自带）
#   - laszip：LAZ 解压 CLI；laspy[lazrs]：LASLib 后端（实测缺它会失败）
# ---------------------------------------------------------------------
RUN python3 -m venv /opt/model3d-venv \
    && /opt/model3d-venv/bin/pip install --no-cache-dir \
        "py3dtiles==12.1.1" \
        laszip \
        "laspy[lazrs]" \
    && /opt/model3d-venv/bin/py3dtiles --help > /dev/null

# ---------------------------------------------------------------------
# 运行时环境变量（业务镜像 FROM 本镜像后自动继承，无需重复声明）
#   LD_LIBRARY_PATH  3dtile 二进制 + Java gdalalljni 所在目录
#   GDAL_DATA/PROJ_LIB  基础镜像未设 env，实测确认实际路径后显式对齐
#                       （GdalEnvInitializer 检测到 env 即复用系统 proj.db，不抽 jar 内 proj7）
#   OSG_PLUGIN_PATH   libopenscenegraph161 的 OSG 插件目录（读 .osgb 必需）
# ---------------------------------------------------------------------
ENV LD_LIBRARY_PATH=/opt/tools/model3d-tools/3dtiles:/usr/local/lib \
    GDAL_DATA=/usr/share/gdal \
    PROJ_LIB=/usr/share/proj \
    OSG_PLUGIN_PATH=/usr/lib/x86_64-linux-gnu/osgPlugins-3.6.5

# ---------------------------------------------------------------------
# 三维转换工具（路径与 yml linux-bin-path 一一对应，迁移时成对修改）
#   已纳入：py3dtiles/obj2gltf（镜像内构建）、FBX2glTF、IfcConvert、3dtile(OSGB)
#   3dtile：fanvanzh/3dtiles v0.3 源码编译（Rust 外壳 + C++/OSG 内核），
#           二进制与自带 OSG 运行时 .so 同置于 /opt/tools/model3d-tools/3dtiles/，
#           由上方 ENV 的 LD_LIBRARY_PATH 加载
# ---------------------------------------------------------------------
COPY deploy/model3d/tools/3dtiles/                    /opt/tools/model3d-tools/3dtiles/
COPY deploy/model3d/tools/FBX2glTF/FBX2glTF-linux-x64   /opt/tools/model3d-tools/FBX2glTF/FBX2glTF-linux-x64
COPY deploy/model3d/tools/IfcConvert/                 /opt/tools/model3d-tools/IfcConvert/

RUN chmod +x /opt/tools/model3d-tools/3dtiles/3dtile \
        /opt/tools/model3d-tools/FBX2glTF/FBX2glTF-linux-x64 \
        /opt/tools/model3d-tools/IfcConvert/IfcConvert

# 3dtile 在 EPSG 帧 metadata.xml 下会调用 epsg_convert，其 C++ 侧从
# <可执行文件所在目录>/gdal_data 读取 GDAL 投影数据。系统 GDAL_DATA 在
# /usr/share/gdal，故在此建软链，保证 EPSG 坐标转换可用（ENU 帧不触发此路径）
RUN ln -sfn /usr/share/gdal /opt/tools/model3d-tools/3dtiles/gdal_data \
    && ls -l /opt/tools/model3d-tools/3dtiles/gdal_data

# ---------------------------------------------------------------------
# 应用工作目录（业务镜像的相对路径 ./UploadFile 等均以此为基准，均为挂载点）
# 提前建好挂载点目录并赋权 uid 1000，业务 jar 也以 1000 运行（COPY --chown 一致）
# ---------------------------------------------------------------------
WORKDIR /app/geodata

RUN mkdir -p UploadFile Download Thumbnail Data Style logs \
        /tmp/geodata-convert \
    && chown -R 1000:1000 /app/geodata /tmp/geodata-convert

# 注意：本基础镜像**不含**业务 jar，也不定义 ENTRYPOINT/EXPOSE/HEALTHCHECK——
# 这些属于应用层，由 geodata-web.Dockerfile 在 FROM 本镜像后补齐。
# 这样基础镜像既可被业务镜像复用，也可独立用于工具链调试（如 exec 进容器跑 3dtile）。
