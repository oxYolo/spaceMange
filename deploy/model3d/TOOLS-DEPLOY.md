# 三维转换工具链部署说明

对应 `geodata-web/src/main/resources/bootstrap-dev.yml` 中 `geodata.convert.tools.*` 的五个转换工具。
Windows 二进制统一放 `D:\tools\model3d-tools\`，Linux 统一放 `/opt/tools/model3d-tools/`（路径与 yml 一致，迁移时成对修改）。

## 工具总览

| toolId | 用途 | 语言/形态 | Windows 获取 | Linux 获取 |
|---|---|---|---|---|
| 3dtiles | OSGB → 3D Tiles | C++（fanvanzh/3dtiles） | 官方 release `3dtile.zip`，已部署 ✅ | **无官方产物**，用本目录 `3dtiles-linux.Dockerfile` 编译 |
| py3dtiles | LAS/LAZ → pnts | Python（pip 包） | venv 安装，已部署 ✅ | 服务器建 venv 后 pip 安装 |
| obj2gltf | OBJ → glb | Node | `npx --yes` 运行时自动拉取，无需预部署 | 同左（需 node + npm） |
| fbx2gltf | FBX → glb | 原生二进制（Facebook） | 官方 release `FBX2glTF-windows-x64.exe` | 官方 release `FBX2glTF-linux-x64` |
| ifcconvert | IFC → glTF | 原生二进制（IfcOpenShell） | 官方 release `ifcconvert-0.9.0-win64.zip` | 官方 release `ifcconvert-0.9.0-linux64.zip` |

## 1. 3dtiles（OSGB → 3D Tiles）

**Windows（已部署）**

```
D:\tools\model3d-tools\3dtiles\
├── 3dtile.exe        # v1.0（2021-07-29 构建），-h 验证通过
├── gdal201.dll / proj.dll / osg130-*.dll ...   # 运行时依赖 DLL，必须与 exe 同目录
├── gdal_data\        # GDAL 坐标系数据
└── osgPlugins-3.4.0\ # OSG 插件
```

CLI 实测：`3dtile.exe [FLAGS] [OPTIONS] --format <osgb,shape,gltf,b3dm> --input <FILE> --output <FILE>`，
与 yml 模板 `{bin} -f osgb -i {input} -o {output} {extra}` 一致。

**Linux（编译产出）**

```bash
cd deploy/model3d
docker build -f 3dtiles-linux.Dockerfile -t 3dtiles-builder:latest .
docker create --name tmp 3dtiles-builder:latest
docker cp tmp:/out/3dtile ./3dtile
docker rm tmp
# 拷到目标主机
scp 3dtile user@host:/opt/tools/model3d-tools/3dtiles/
ssh user@host "chmod +x /opt/tools/model3d-tools/3dtiles/3dtile && /opt/tools/model3d-tools/3dtiles/3dtile -h"
```

注意：Linux 产物**不需要**附带 Windows 的那批 DLL（Linux 动态依赖由发行版 `libopenscenegraph`/`libgdal` 运行库提供；
目标主机若报缺 so，`apt install libopenscenegraph-3.6-161 libgdal32` 或改在目标同版本系统内编译）。

## 2. py3dtiles（点云 LAS/LAZ → pnts）

py3dtiles 12.1.1（Python ≥3.10）。bin 配置指向 **console_scripts 入口**（`py3dtiles` 可执行文件），
**不是** python 解释器——12.x 包内无 `__main__.py`，`python -m py3dtiles` 会报 "cannot be directly executed"。

**Windows（已部署）**：

```
venv 路径：D:\tools\model3d-tools\venv-py3dtiles    （独立于业务代码，与其它工具同目录管理）
入口：    D:\tools\model3d-tools\venv-py3dtiles\Scripts\py3dtiles.exe
安装：    python -m venv venv-py3dtiles && venv-py3dtiles\Scripts\pip install py3dtiles laszip
依赖链：  py3dtiles → numba/llvmlite/numpy/pyproj/laszip/pygltflib 等 19 包
```

⚠️ **本机限制（WDAC）**：开发机启用了应用程序控制策略，`pyproj._context` 的原生 DLL 被
拦截（`DLL load failed while importing _context: 应用程序控制策略已阻止此文件`），
numpy/numba/llvmlite 均正常、仅 pyproj 被拦。需 IT 为 pyproj 的 DLL（或 `D:\tools\model3d-tools\` 目录）加白名单后本机才可运行；
**生产/Linux 服务器一般无此策略，不受影响**。

**Linux**：

```bash
python3 -m venv /opt/model3d-venv
/opt/model3d-venv/bin/pip install py3dtiles laszip
/opt/model3d-venv/bin/py3dtiles --help
```

yml 中 `linux-bin-path: /opt/model3d-venv/bin/py3dtiles` 与之对应。
CLI 语法：`py3dtiles convert <files...> --out <dir> [--overwrite]`（yml 模板已带 `--overwrite` 防重试残留）。

## 3. obj2gltf（OBJ → glb）

无需预部署二进制。yml 中 bin 填 `npx`，模板带 `--yes`，首次执行自动下载包（要求 PATH 里有 node/npm）。
离线环境需提前在服务运行账号下 `npm install -g obj2gltf`，并把模板改为 `{bin} obj2gltf -i {input} -o {output}`、bin 指向 `obj2gltf.cmd`/全局 bin。

## 4. FBX2glTF（FBX → glb）

Facebook 官方 release（v0.9.7），单文件免安装：

```
Windows: D:\tools\model3d-tools\FBX2glTF\FBX2glTF-windows-x64.exe
Linux:   /opt/tools/model3d-tools/FBX2glTF/FBX2glTF-linux-x64   （chmod +x）
验证:    FBX2glTF-windows-x64.exe --version
```

注意：Linux 文件名无 `.exe` 后缀，与 yml `linux-bin-path` 一致。

## 5. IfcConvert（IFC → glTF）

IfcOpenShell 官方 release（0.9.0alpha0-8c614ffa）：

```
Windows: D:\tools\model3d-tools\IfcConvert\IfcConvert.exe   （已部署验证 ✅）
Linux:   /opt/tools/model3d-tools/IfcConvert/IfcConvert     （zip 解压 + chmod +x）
验证:    IfcConvert.exe --help   （Usage: IfcConvert [options] <input.ifc> [<output>]）
```

- win64 zip 免安装，解压后 **exe 名为 `IfcConvert.exe`**，OCCT（TK*.dll）、boost、ifcopenshell_*.dll 等依赖必须与 exe 同目录（zip 内自带，整目录拷贝即可）。
- 输出格式按扩展名推断：`.glb` = Binary glTF v2.0（本项目用）、`.obj`/`.dae` 亦可。
- **编码注意**：Windows 版 stdout 为 **UTF-16LE**（实测 `--help` 输出字符间夹空字节），yml 中该工具 `output-charset: UTF-16LE`；Linux 版通常为 UTF-8，部署后如日志乱码按实际调整。
- IfcConvert 不输出百分比进度行，转换期间进度不推进属正常（完成即从转换中跳到发布）。

## 通用注意事项

- **进程树**：Java 侧 ProcessBuilder 已按进程树销毁（`ProcessHandle.descendants()`）；Windows 下 3dtile.exe 依赖 DLL 均在同目录，无需额外 PATH。
- **输出编码**：五工具默认 `output-charset: UTF-8`；若某工具在特定系统输出 GBK 导致进度解析乱码，仅调该工具的 `output-charset`。
- **目录约定**：`D:\tools\model3d-tools\`（Win）/ `/opt/tools/model3d-tools/`（Linux）成对维护；换目录时同步改 yml 的 win-bin-path/linux-bin-path。
- **离线环境**：4 个预编译二进制 + py3dtiles wheel 可提前内网镜像（pip 走私有 PyPI；obj2gltf 走私有 npm）。

## 6. 成果加载链路（公开只读 + 反代 + CORS）★ 必读

**为什么不能用预签名 URL 加载 3D Tiles**

`tileset.json` 通过**相对路径**引用子瓦片（`0/0.b3dm`、`0.json`、贴图等）。预签名 URL 的
`X-Amz-Signature` 与**单个对象**绑定，Cesium 解析相对路径后请求子瓦片时不会带签名参数 →
子瓦片返回 **403**，整套模型加载不出来。因此多文件成果必须走「公开只读前缀」。

**服务端行为（已内置，`geodata.model3d.storage.*`）**

| 配置 | 默认 | 说明 |
|---|---|---|
| `public-read` | `true` | 发布时自动为桶设置匿名只读策略（幂等，仅 `s3:GetObject`） |
| `public-read-prefix` | `model3d/` | 策略作用前缀，最小权限，不开放桶内其它数据 |
| `public-base-url` | 空 | 对外可达基地址；**留空会回退 `endpoint`（内网地址，浏览器不可达）** |
| `presign-expiry-minutes` | `120` | 仅 `public-read=false` 时生效 |

`GET /model3d/{id}/publish-url` 返回 `{public-base-url}/{bucket}/{key}`，例如
`https://gis.example.com/geodata/model3d/<id>/publish/tileset.json`。

**生产部署三件事**

1. `public-base-url` 填对外域名（不要留 localhost / 内网 IP）。
2. 用 nginx 反代 MinIO，并**放行 CORS**（Cesium 跨域取子瓦片）——参考 `nginx-minio-proxy.conf`。
3. 需要鉴权时**不要在 MinIO 层做**（会重新落入「逐文件签名」的坑）；改为在 nginx 层
   用 `auth_request` 或带 token 的路径校验，MinIO 侧保持前缀只读。

> `public-read=false` 仅适用于**单体 glb/gltf**（单文件，可直接预签名）；倾斜摄影/点云/BIM 转换出的
> 3D Tiles 一律需要 `true`。

## 7. 队列与任务可靠性

- **死信队列**：主队列直接声明 DLX（`Model3dConvertDlxExchange` → `Model3dConvertDeadQueue`）作兜底；
  转换失败的消息由处理器手动转投该队列，带 `x-failure-reason` / `x-failed-at` 头。排查：
  RabbitMQ 控制台查看 `Model3dConvertDeadQueue` 消息数；重驱动走页面「再次下发转换」。
- **僵尸任务回收**：转换期间每 `heartbeat-interval-ms`（默认 30s）刷新 `sdms_model3d.heartbeat_time`；
  看门狗每 `watchdog-interval-ms`（默认 60s）把心跳超过 `stale-minutes`（默认 15min）的
  「转换中」记录回收为失败；服务启动时按 `convert_owner` 立刻回收**本实例**重启前的残留任务。
- **同机多副本**：必须为每个副本显式配置不同的 `geodata.convert.instance-id`，否则启动恢复会互相误杀。
- **数据库**：`sql/model3d_schema.sql` 已含 `convert_owner` / `heartbeat_time` 两列（新建库直接建表即可）。
- **磁盘**：转换前按源体积 3 倍预检工作区可用空间，不足直接快速失败，避免打满磁盘拖垮实例。


