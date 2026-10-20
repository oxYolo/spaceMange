# =====================================================================
# 3dtiles (fanvanzh/3dtiles) Linux 二进制编译镜像
#
# 背景：fanvanzh/3dtiles 官方只提供 Windows 产物（3dtile.exe），
#       Linux 无预编译二进制，需从源码编译（依赖 OpenSceneGraph + GDAL）。
# 用途：本镜像仅用于"产出" Linux 可执行文件，不是运行时容器。
#       编译完成后按下方命令把 3dtile 拷贝到宿主机部署目录即可。
#
# 构建：  docker build -f 3dtiles-linux.Dockerfile -t 3dtiles-builder:latest .
# 取出：  docker create --name tmp 3dtiles-builder:latest
#         docker cp tmp:/out/3dtile ./3dtile
#         docker rm tmp
# 部署：  scp 3dtile user@linux-host:/opt/tools/model3d-tools/3dtiles/
#         chmod +x /opt/tools/model3d-tools/3dtiles/3dtile
# 验证：  ./3dtile -h   （应打印 USAGE: 3dtile [FLAGS] [OPTIONS] --format ...）
# =====================================================================

FROM ubuntu:22.04

ENV DEBIAN_FRONTEND=noninteractive

# 编译依赖：
#   libopenscenegraph-dev -> OSG 3.6.5（osg/osgDB/osgUtil/OpenThreads）
#   libgdal-dev           -> GDAL 3.4.x（SpatialReference / 坐标转换）
RUN apt-get update && apt-get install -y --no-install-recommends \
        ca-certificates \
        git \
        cmake \
        g++ \
        make \
        libopenscenegraph-dev \
        libgdal-dev \
    && rm -rf /var/lib/apt/lists/*

WORKDIR /src

# 浅克隆即可（不需要历史）；如需固定版本，改为
#   git clone --branch <tag> --depth 1 https://github.com/fanvanzh/3dtiles.git .
RUN git clone --depth 1 https://github.com/fanvanzh/3dtiles.git .

# CMake 需要 C++14；-j 按容器可用核数并行
RUN mkdir -p build /out \
    && cmake -S . -B build -DCMAKE_BUILD_TYPE=Release -DCMAKE_CXX_STANDARD=14 \
    && make -C build -j"$(nproc)"

# 编译产物名以仓库 CMakeLists 为准（Windows 侧为 3dtile.exe，Linux 侧通常为 3dtile）。
# 用 find 兜底拷贝，避免产物名差异导致 COPY 失败。
RUN find /src/build -maxdepth 3 -type f -name "3dtile*" ! -name "*.o" ! -name "*.a" \
        -exec cp -f {} /out/3dtile \; \
    && chmod +x /out/3dtile \
    && /out/3dtile -h > /out/3dtile-help.txt 2>&1 || true

# 产物在 /out：3dtile（可执行） + 3dtile-help.txt（验证输出）
CMD ["cat", "/out/3dtile-help.txt"]
