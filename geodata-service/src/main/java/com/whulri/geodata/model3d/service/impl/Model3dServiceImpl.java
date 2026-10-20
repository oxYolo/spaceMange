package com.whulri.geodata.model3d.service.impl;

import com.alibaba.fastjson.JSON;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.whulri.geodata.common.bridge.AuthUtil;
import com.whulri.geodata.common.entity.ToolHisEntity;
import com.whulri.geodata.datamanage.service.IToolHisService;
import com.whulri.geodata.datamanage.socket.SignalrClient;
import com.whulri.geodata.datamanage.util.WebSocketUtil;
import com.whulri.geodata.convert.core.ConvertCommandBuilder;
import com.whulri.geodata.convert.core.ConvertFileUtils;
import com.whulri.geodata.convert.core.ConvertProperties;
import com.whulri.geodata.convert.core.ConvertTask;
import com.whulri.geodata.convert.core.ConvertToolConfig;
import com.whulri.geodata.convert.core.ProcessExecutor;
import com.whulri.geodata.convert.core.ProgressListener;
import com.whulri.geodata.model3d.dto.Model3dPageQuery;
import com.whulri.geodata.model3d.dto.Model3dUploadInitDTO;
import com.whulri.geodata.model3d.dto.TilesetParseResult;
import com.whulri.geodata.model3d.entity.Model3dEntity;
import com.whulri.geodata.model3d.enums.Model3dStatus;
import com.whulri.geodata.model3d.enums.Model3dType;
import com.whulri.geodata.model3d.enums.SrcFormat;
import com.whulri.geodata.model3d.exception.Model3dException;
import com.whulri.geodata.model3d.mapper.Model3dMapper;
import com.whulri.geodata.model3d.publish.Model3dPublishHelper;
import com.whulri.geodata.model3d.service.IModel3dService;
import com.whulri.geodata.model3d.storage.Model3dStorage;
import com.whulri.geodata.model3d.storage.Model3dStorageProperties;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Semaphore;
import java.util.stream.Stream;

/**
 * 三维数据管理服务实现。
 *
 * <p>分层约定：本类只依赖 geodata-convert-core 的 SPI（运行期注入各工具链实现 Bean），
 * 不直接依赖 osgb/pointcloud/bim 具体模块；工具链细节全部留在各自模块内。</p>
 */
@Slf4j
@Service
public class Model3dServiceImpl implements IModel3dService {

    private static final String KEY_PREFIX = "model3d";
    private static final String MSG_TYPE = "Model3dConvert";
    private static final long MIN_PART_SIZE = 5L * 1024 * 1024;
    private static final String TOOL_HIS_ORIGIN_3D = "10";

    @Resource
    private Model3dMapper model3dMapper;

    @Resource
    private Model3dStorage storage;

    @Resource
    private Model3dStorageProperties storageProperties;

    @Resource
    private Model3dPublishHelper publishHelper;

    @Resource
    private ConvertProperties convertProperties;

    @Resource
    private ProcessExecutor processExecutor;

    @Resource
    private RabbitTemplate rabbitTemplate;

    @Resource
    private IToolHisService toolHisService;

    @Resource
    private SignalrClient signalrClient;

    /** 运行期注入全部工具链构建器（geodata-web 汇聚各模块依赖） */
    @Autowired(required = false)
    private List<ConvertCommandBuilder> builders = List.of();

    @Value("${geodata.convert.mq.exchange:Model3dConvertExchange}")
    private String mqExchange;

    @Value("${geodata.convert.mq.routing:model3d.convert}")
    private String mqRouting;

    /** 本实例标识（同机多副本部署时必须显式指定，否则重启恢复会互相误杀） */
    @Value("${geodata.convert.instance-id:}")
    private String configuredInstanceId;

    /** 转换心跳刷新间隔（毫秒） */
    @Value("${geodata.convert.heartbeat-interval-ms:30000}")
    private long heartbeatIntervalMs;

    /** 僵尸转换判定阈值（分钟，心跳超过此值未刷新 → 判定任务已死） */
    @Value("${geodata.convert.stale-minutes:15}")
    private long staleMinutes;

    /** 转换并发限流（懒初始化，取 geodata.convert.max-concurrent） */
    private volatile Semaphore permits;

    private Semaphore permits() {
        if (permits == null) {
            synchronized (this) {
                if (permits == null) {
                    permits = new Semaphore(Math.max(1, convertProperties.getMaxConcurrent()));
                }
            }
        }
        return permits;
    }

    // ==================== 上传 ====================

    @Override
    public String initUpload(Model3dUploadInitDTO dto) {
        if (dto == null || isBlank(dto.getName()) || isBlank(dto.getModelType())
                || isBlank(dto.getSrcFormat()) || isBlank(dto.getFileName())) {
            throw new Model3dException("name/modelType/srcFormat/fileName 均为必填");
        }
        Model3dType type = parseType(dto.getModelType());
        SrcFormat format = parseFormat(dto.getSrcFormat());
        if (!type.allowedSrcFormats().contains(format.name().toLowerCase())) {
            throw new Model3dException(String.format(
                    "数据类型 %s 不支持源格式 %s（允许：%s）",
                    type, format, type.allowedSrcFormats()));
        }
        int partCount = dto.getPartCount() == null || dto.getPartCount() < 1 ? 1 : dto.getPartCount();
        String fileName = sanitizeFileName(dto.getFileName());

        storage.ensureBucket();

        String id = UUID.randomUUID().toString();
        Model3dEntity entity = new Model3dEntity();
        entity.setId(id);
        entity.setName(dto.getName().trim());
        entity.setDirectoryId(dto.getDirectoryId());
        entity.setModelType(type.name());
        entity.setSrcFormat(format.name().toLowerCase());
        entity.setFileName(fileName);
        entity.setTotalSize(dto.getTotalSize());
        entity.setPartCount(partCount);
        entity.setSrs(dto.getSrs());
        entity.setProgress(0);
        entity.setStatus(Model3dStatus.UPLOADING.getCode());
        // 操作人：登录态优先；无登录态时用请求携带的 userCode 兜底（SignalR 推送目标依赖该值）
        String operator = AuthUtil.getNickName();
        if (isBlank(operator) && !isBlank(dto.getUserCode())) {
            operator = dto.getUserCode().trim();
        }
        entity.setCreateUser(operator);
        entity.setCreateTime(LocalDateTime.now());
        model3dMapper.insert(entity);
        return id;
    }

    @Override
    public void uploadPart(String id, int partNo, MultipartFile file) {
        Model3dEntity entity = requireEntity(id);
        if (!Model3dStatus.UPLOADING.getCode().equals(entity.getStatus())) {
            throw new Model3dException("当前状态不允许上传分片（status=" + entity.getStatus() + "）");
        }
        if (file == null || file.isEmpty()) {
            throw new Model3dException("分片内容为空");
        }
        int total = entity.getPartCount() == null ? 1 : entity.getPartCount();
        if (partNo < 1 || partNo > total) {
            throw new Model3dException("分片号越界：" + partNo + "，应在 1~" + total);
        }
        long size;
        try {
            size = file.getSize();
        } catch (Exception e) {
            throw new Model3dException("读取分片大小失败", e);
        }
        // 对象存储 compose 限制：除最后一个分片外必须 ≥5MiB
        if (partNo < total && size < MIN_PART_SIZE) {
            throw new Model3dException(String.format(
                    "非最后分片须 ≥5MiB（当前 %d 字节），请调整分片大小", size));
        }
        try (InputStream in = file.getInputStream()) {
            storage.putStream(partKey(id, partNo), in, size, "application/octet-stream");
        } catch (IOException e) {
            throw new Model3dException("读取分片流失败：" + partNo, e);
        }
    }

    @Override
    public Model3dEntity completeUpload(String id) {
        Model3dEntity entity = requireEntity(id);
        if (!Model3dStatus.UPLOADING.getCode().equals(entity.getStatus())) {
            throw new Model3dException("当前状态不允许完成上传（status=" + entity.getStatus() + "）");
        }
        int total = entity.getPartCount() == null ? 1 : entity.getPartCount();
        List<String> partKeys = new ArrayList<>();
        for (int i = 1; i <= total; i++) {
            String key = partKey(id, i);
            if (!storage.exists(key)) {
                throw new Model3dException("分片缺失：" + i + "，请补传后再完成上传");
            }
            partKeys.add(key);
        }

        String srcKey = srcKey(id, entity.getFileName());
        if (partKeys.size() == 1) {
            storage.copyObject(partKeys.get(0), srcKey);
        } else {
            storage.composeParts(partKeys, srcKey);
        }
        partKeys.forEach(storage::remove);

        Long size = storage.objectSize(srcKey);
        entity.setSrcObjectKey(srcKey);
        if (size != null) {
            entity.setTotalSize(size);
        }
        entity.setUpdateUser(AuthUtil.getNickName());
        entity.setUpdateTime(LocalDateTime.now());

        SrcFormat format = SrcFormat.valueOf(entity.getSrcFormat().toUpperCase());
        if (format == SrcFormat.TILESET) {
            // 现成 3D Tiles：直接走发布链路
            publishFromSrc(entity);
        } else {
            entity.setStatus(Model3dStatus.UPLOADED.getCode());
        }
        model3dMapper.updateById(entity);
        return entity;
    }

    @Override
    public List<Integer> listUploadedParts(String id) {
        Model3dEntity entity = requireEntity(id);
        if (!Model3dStatus.UPLOADING.getCode().equals(entity.getStatus())) {
            // 已合并/已发布：无待续传分片
            return List.of();
        }
        String prefix = String.format("%s/%s/parts/", KEY_PREFIX, id);
        List<Integer> parts = new ArrayList<>();
        for (String key : storage.listObjectNames(prefix)) {
            try {
                parts.add(Integer.parseInt(key.substring(prefix.length())));
            } catch (RuntimeException ignored) {
                // 非分片编号的对象忽略
            }
        }
        parts.sort(null);
        return parts;
    }

    // ==================== 发布 ====================

    @Override
    public String getPublishUrl(String id) {
        Model3dEntity entity = requireEntity(id);
        if (!Model3dStatus.PUBLISHED.getCode().equals(entity.getStatus())
                || isBlank(entity.getPublishObjectKey())) {
            throw new Model3dException("数据尚未发布，无法签发加载地址");
        }
        // 多文件成果（3D Tiles）必须走公开只读前缀：预签名只能覆盖单个对象，
        // tileset.json 相对路径引用的子瓦片会丢签名导致 403。单体 glb/gltf 一并走公开前缀，
        // 前端无需处理签名过期，行为一致。
        if (storageProperties.isPublicRead()) {
            storage.ensurePublicReadPolicy();
            return storage.publicUrl(entity.getPublishObjectKey());
        }
        return storage.presignGet(entity.getPublishObjectKey(), storage.getPresignExpirySeconds());
    }

    /**
     * 从已上传的源对象发布（现成 3D Tiles 直传场景）。
     * 下载 → 解压 → 定位 tileset.json → 目录上传 → 解析登记。
     */
    private void publishFromSrc(Model3dEntity entity) {
        Path workDir = Paths.get(convertProperties.getWorkRoot(), "publish-" + entity.getId());
        try {
            Path downloadFile = workDir.resolve("download.bin");
            storage.download(entity.getSrcObjectKey(), downloadFile);

            Path srcDir = workDir.resolve("src");
            Path inputBase;
            if (entity.getFileName() != null && entity.getFileName().toLowerCase().endsWith(".zip")) {
                publishHelper.extractZip(downloadFile, srcDir);
                inputBase = srcDir;
            } else {
                Files.createDirectories(srcDir);
                inputBase = srcDir.resolve(entity.getFileName());
                Files.move(downloadFile, inputBase, StandardCopyOption.REPLACE_EXISTING);
            }

            Path tileset = publishHelper.findTileset(inputBase);
            String publishPrefix = publishPrefix(entity.getId());
            if (tileset != null) {
                publishHelper.uploadDirectory(inputBase, publishPrefix);
                String tilesetKey = publishPrefix
                        + inputBase.toAbsolutePath().normalize().relativize(tileset).toString()
                                .replace('\\', '/');
                TilesetParseResult parsed = publishHelper.parseTileset(tileset);
                applyParseResult(entity, tilesetKey, parsed);
            } else {
                Path model = ConvertFileUtils.findFirstFile(inputBase, "glb", "gltf");
                if (model == null) {
                    throw new Model3dException("成果中未找到 tileset.json 或 glb/gltf，无法发布");
                }
                String key = publishPrefix + model.getFileName();
                storage.putFile(key, model);
                entity.setPublishObjectKey(key);
            }
            ensurePublishPolicy();
            entity.setStatus(Model3dStatus.PUBLISHED.getCode());
            entity.setProgress(100);
        } catch (IOException e) {
            throw new Model3dException("发布过程 IO 失败：" + e.getMessage(), e);
        } finally {
            deleteRecursively(workDir);
        }
    }

    /** 发布前确保公开只读策略就位（publicRead=false 时跳过，走预签名） */
    private void ensurePublishPolicy() {
        if (storageProperties.isPublicRead()) {
            storage.ensurePublicReadPolicy();
        }
    }

    private void applyParseResult(Model3dEntity entity, String tilesetKey, TilesetParseResult parsed) {
        entity.setPublishObjectKey(tilesetKey);
        if (parsed == null) {
            return;
        }
        if (parsed.getBbox() != null) {
            entity.setBbox(JSON.toJSONString(parsed.getBbox()));
        }
        if (parsed.getTransform() != null) {
            entity.setTransformation(JSON.toJSONString(parsed.getTransform()));
        }
        entity.setTileCount(parsed.getTileCount());
        entity.setLodDepth(parsed.getLodDepth());
        entity.setGeometricError(parsed.getGeometricError());
    }

    // ==================== 转换 ====================

    @Override
    public void requestConvert(String id) {
        Model3dEntity entity = requireEntity(id);
        if (Model3dStatus.QUEUED.getCode().equals(entity.getStatus())
                || Model3dStatus.CONVERTING.getCode().equals(entity.getStatus())) {
            throw new Model3dException("转换任务进行中，请勿重复下发");
        }
        if (Model3dStatus.PUBLISHED.getCode().equals(entity.getStatus())) {
            throw new Model3dException("数据已发布，无需转换");
        }
        SrcFormat format = SrcFormat.valueOf(entity.getSrcFormat().toUpperCase());
        if (!format.needConvert()) {
            throw new Model3dException("该源格式无需转换，可直接发布");
        }
        String toolId = format.getToolId();
        ConvertToolConfig cfg = convertProperties.getTool(toolId);
        if (cfg == null || !cfg.isEnabled()) {
            throw new Model3dException(String.format(
                    "转换工具未启用：%s（请检查 geodata.convert.tools.%s 配置）", toolId, toolId));
        }

        ConvertTask task = new ConvertTask();
        task.setModel3dId(entity.getId());
        task.setName(entity.getName());
        task.setToolId(toolId);
        task.setSrcFormat(entity.getSrcFormat());
        task.setSrcObjectKey(entity.getSrcObjectKey());
        task.setFileName(entity.getFileName());
        task.setSrs(entity.getSrs());
        task.setCreateBy(entity.getCreateUser());

        // 工具历史（复用 sdms_tool_his：origin=10 表示三维转换）
        ToolHisEntity his = new ToolHisEntity();
        his.setId(UUID.randomUUID().toString());
        his.setLayerName(entity.getName());
        his.setCreateDT(LocalDateTime.now());
        his.setCreateBy(entity.getCreateUser());
        his.setStatus("0");
        his.setOrigin(TOOL_HIS_ORIGIN_3D);
        his.setStorage("1");
        Map<String, Object> detail = Map.of("model3dId", entity.getId(), "toolId", toolId);
        his.setDetailJson(JSON.toJSONString(detail));
        toolHisService.save(his);

        task.setTaskHisId(his.getId());
        rabbitTemplate.convertAndSend(mqExchange, mqRouting, JSON.toJSONString(task));

        // 用 UpdateWrapper 显式置空 fail_reason（updateById 默认忽略 null 字段，清不掉历史失败原因）
        model3dMapper.update(null, new LambdaUpdateWrapper<Model3dEntity>()
                .eq(Model3dEntity::getId, id)
                .set(Model3dEntity::getStatus, Model3dStatus.QUEUED.getCode())
                .set(Model3dEntity::getProgress, 0)
                .set(Model3dEntity::getTaskId, his.getId())
                .set(Model3dEntity::getFailReason, null)
                .set(Model3dEntity::getUpdateUser, AuthUtil.getNickName())
                .set(Model3dEntity::getUpdateTime, LocalDateTime.now()));
        log.info("三维转换任务已下发: id={}, toolId={}, taskHisId={}", id, toolId, his.getId());
        pushProgress(entity, Model3dStatus.QUEUED, 1, 0, "已进入转换队列", false, false, null, null);
    }

    @Override
    public void executeConvert(ConvertTask task) {
        if (task == null || isBlank(task.getModel3dId())) {
            throw new Model3dException("转换消息缺少 model3dId");
        }
        Model3dEntity entity = requireEntity(task.getModel3dId());
        if (Model3dStatus.PUBLISHED.getCode().equals(entity.getStatus())) {
            log.info("三维数据已发布，跳过重复转换: {}", entity.getId());
            return;
        }

        ConvertToolConfig cfg = convertProperties.toolOrEmpty(task.getToolId());
        if (!cfg.isEnabled()) {
            throw new Model3dException("转换工具未启用：" + task.getToolId());
        }
        ConvertCommandBuilder builder = builders.stream()
                .filter(b -> b.supportedSrcFormats().contains(
                        task.getSrcFormat() == null ? "" : task.getSrcFormat().toLowerCase()))
                .findFirst()
                .orElseThrow(() -> new Model3dException(
                        "无支持的转换器：srcFormat=" + task.getSrcFormat()));

        try {
            permits().acquire();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new Model3dException("等待转换许可被中断", e);
        }

        Path workDir = Paths.get(convertProperties.getWorkRoot(), "convert-" + entity.getId());
        pushProgress(entity, Model3dStatus.CONVERTING, 1, 1, "转换开始...", false, false, null, null);
        Thread heartbeat = null;
        try {
            markConverting(entity);
            heartbeat = startHeartbeat(entity.getId());

            // 0. 磁盘空间预检（下载 + 解压 + 产物按源体积 3 倍估算），不足则快速失败，避免打满磁盘拖垮实例
            ensureDiskSpace(entity.getTotalSize());

            // 1. 下载 + 解压
            Path downloadFile = workDir.resolve("download.bin");
            storage.download(task.getSrcObjectKey(), downloadFile);
            Path srcDir = workDir.resolve("src");
            Path inputBase;
            boolean isZip = task.getFileName() != null
                    && task.getFileName().toLowerCase().endsWith(".zip");
            if (isZip) {
                publishHelper.extractZip(downloadFile, srcDir);
                inputBase = srcDir;
            } else {
                Files.createDirectories(srcDir);
                inputBase = srcDir.resolve(sanitizeFileName(task.getFileName()));
                Files.move(downloadFile, inputBase, StandardCopyOption.REPLACE_EXISTING);
            }

            // 2. 提取源数据元数据（如 OSGB 的 SRS / 局部原点）
            Map<String, String> meta = builder.extractMetadata(task, inputBase);
            if (!meta.isEmpty()) {
                Model3dEntity patch = new Model3dEntity();
                patch.setId(entity.getId());
                if (meta.get("srs") != null) {
                    patch.setSrs(meta.get("srs"));
                }
                patch.setOriginX(parseDouble(meta.get("originX")));
                patch.setOriginY(parseDouble(meta.get("originY")));
                patch.setOriginZ(parseDouble(meta.get("originZ")));
                patch.setUpdateTime(LocalDateTime.now());
                model3dMapper.updateById(patch);
            }

            // 3. 构建命令 + 子进程转换（进度节流回写）
            Path outputDir = workDir.resolve("output");
            Files.createDirectories(outputDir);
            List<String> command = builder.buildCommand(task, cfg, workDir, inputBase, outputDir);
            int[] lastPercent = {0};
            ProgressListener listener = (line, ignored) -> {
                Integer p = builder.parseProgress(line);
                if (p != null && p >= lastPercent[0] + 3) {
                    lastPercent[0] = p;
                    updateProgress(entity.getId(), Math.min(p, 99));
                    pushProgress(entity, Model3dStatus.CONVERTING, 2, Math.min(p, 99),
                            "转换中：" + trimLine(line), false, false, null, null);
                }
            };
            processExecutor.run(command, workDir, cfg, listener);

            // 4. 产物校验（进程退出码 0 不代表产物可用）+ 上传 + 登记发布
            validateConvertOutput(outputDir);
            publishConvertOutput(entity, outputDir);

            updateToolHis(task.getTaskHisId(), "1");
            pushProgress(entity, Model3dStatus.PUBLISHED, 3, 100, "转换完成", false, true,
                    null, entity.getPublishObjectKey());
            log.info("三维转换完成: id={}, tool={}", entity.getId(), task.getToolId());
        } catch (Exception e) {
            String reason = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
            log.error("三维转换失败: id={}, reason={}", entity.getId(), reason, e);
            updateStatus(entity, Model3dStatus.FAILED, null, truncate(reason, 2000));
            updateToolHis(task.getTaskHisId(), "2");
            pushProgress(entity, Model3dStatus.FAILED, 0, 100, "转换失败：" + truncate(reason, 200),
                    true, true, truncate(reason, 2000), null);
            throw e instanceof RuntimeException re ? re : new Model3dException(reason, e);
        } finally {
            stopHeartbeat(heartbeat);
            deleteRecursively(workDir);
            permits().release();
        }
    }

    // ==================== 转换存活与回收（稳定性） ====================

    /** 进入「转换中」：落库实例标识与初始心跳，供看门狗/重启恢复判定 */
    private void markConverting(Model3dEntity entity) {
        LocalDateTime now = LocalDateTime.now();
        model3dMapper.update(null, new LambdaUpdateWrapper<Model3dEntity>()
                .eq(Model3dEntity::getId, entity.getId())
                .set(Model3dEntity::getStatus, Model3dStatus.CONVERTING.getCode())
                .set(Model3dEntity::getProgress, 0)
                .set(Model3dEntity::getFailReason, null)
                .set(Model3dEntity::getConvertOwner, instanceId())
                .set(Model3dEntity::getHeartbeatTime, now)
                .set(Model3dEntity::getUpdateUser, AuthUtil.getNickName())
                .set(Model3dEntity::getUpdateTime, now));
    }

    /** 心跳连续失败次数：DB 抖动导致心跳持续失败时，看门狗会误判僵尸回收，需 WARN 可感知 */
    private volatile int heartbeatFailStreak = 0;

    /** 启动心跳线程（守护线程，转换结束后 interrupt 停止） */
    private Thread startHeartbeat(String id) {
        long interval = Math.max(5000L, heartbeatIntervalMs);
        Thread t = new Thread(() -> {
            while (!Thread.currentThread().isInterrupted()) {
                try {
                    Thread.sleep(interval);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }
                try {
                    touchHeartbeat(id);
                    heartbeatFailStreak = 0;
                } catch (Exception e) {
                    int streak = ++heartbeatFailStreak;
                    if (streak >= 3) {
                        log.warn("转换心跳连续第 {} 次刷新失败: id={}, {}（心跳持续失败将导致看门狗误判僵尸任务并回收，请检查数据库连接/连接池）",
                                streak, id, e.getMessage());
                    } else {
                        log.warn("刷新转换心跳失败: id={}, {}", id, e.getMessage());
                    }
                }
            }
        }, "model3d-heartbeat-" + id);
        t.setDaemon(true);
        t.start();
        return t;
    }

    private void stopHeartbeat(Thread heartbeat) {
        if (heartbeat != null) {
            heartbeat.interrupt();
        }
    }

    /** 刷新心跳（仅当该行仍处于「转换中」，避免覆盖已被回收的任务） */
    private void touchHeartbeat(String id) {
        Model3dEntity patch = new Model3dEntity();
        patch.setId(id);
        patch.setHeartbeatTime(LocalDateTime.now());
        patch.setUpdateTime(LocalDateTime.now());
        model3dMapper.update(patch, new LambdaQueryWrapper<Model3dEntity>()
                .eq(Model3dEntity::getId, id)
                .eq(Model3dEntity::getStatus, Model3dStatus.CONVERTING.getCode()));
    }

    /**
     * 服务启动后回收本实例残留的「转换中」任务：本进程刚重启，这些子进程必已随 JVM 退出而死亡。
     * 不影响其它实例在跑的任务（按 convert_owner 精确匹配，多副本安全）。
     */
    @EventListener(ApplicationReadyEvent.class)
    public void recoverOwnConversionsOnStartup() {
        try {
            String owner = instanceId();
            String reason = "服务重启导致转换中断，请重新下发转换";
            List<Model3dEntity> stale = model3dMapper.selectList(new LambdaQueryWrapper<Model3dEntity>()
                    .select(Model3dEntity::getId, Model3dEntity::getCreateUser)
                    .eq(Model3dEntity::getStatus, Model3dStatus.CONVERTING.getCode())
                    .eq(Model3dEntity::getConvertOwner, owner));
            if (stale.isEmpty()) {
                return;
            }
            Model3dEntity patch = new Model3dEntity();
            patch.setStatus(Model3dStatus.FAILED.getCode());
            patch.setFailReason(reason);
            patch.setUpdateTime(LocalDateTime.now());
            int n = model3dMapper.update(patch, new LambdaQueryWrapper<Model3dEntity>()
                    .eq(Model3dEntity::getStatus, Model3dStatus.CONVERTING.getCode())
                    .eq(Model3dEntity::getConvertOwner, owner));
            log.warn("启动恢复：本实例({})残留 {} 个转换中任务已标记失败", owner, n);
            for (Model3dEntity e : stale) {
                pushProgress(e, Model3dStatus.FAILED, 0, 100, "转换失败：" + reason, true, true, reason, null);
            }
        } catch (Exception e) {
            log.error("启动恢复残留转换任务失败（不影响启动）: {}", e.getMessage());
        }
    }

    /**
     * 看门狗：周期性回收心跳超时的「转换中」任务（进程崩溃、实例被杀、消息丢失等场景兜底）。
     * 按心跳阈值判定，覆盖所有实例，多副本安全。
     */
    @Scheduled(fixedDelayString = "${geodata.convert.watchdog-interval-ms:60000}",
            initialDelayString = "${geodata.convert.watchdog-interval-ms:60000}")
    public void recoverStaleConversions() {
        try {
            long minutes = Math.max(2L, staleMinutes);
            String reason = "转换心跳超时（>" + minutes + " 分钟无上报），任务已判定中断，请重新下发转换";
            List<Model3dEntity> stale = model3dMapper.selectList(new LambdaQueryWrapper<Model3dEntity>()
                    .select(Model3dEntity::getId, Model3dEntity::getCreateUser)
                    .eq(Model3dEntity::getStatus, Model3dStatus.CONVERTING.getCode())
                    .apply("coalesce(heartbeat_time, update_time) < {0}",
                            LocalDateTime.now().minusMinutes(minutes)));
            if (stale.isEmpty()) {
                return;
            }
            Model3dEntity patch = new Model3dEntity();
            patch.setStatus(Model3dStatus.FAILED.getCode());
            patch.setFailReason(reason);
            patch.setUpdateTime(LocalDateTime.now());
            int n = model3dMapper.update(patch, new LambdaQueryWrapper<Model3dEntity>()
                    .eq(Model3dEntity::getStatus, Model3dStatus.CONVERTING.getCode())
                    .apply("coalesce(heartbeat_time, update_time) < {0}",
                            LocalDateTime.now().minusMinutes(minutes)));
            log.warn("看门狗：回收 {} 个心跳超时的转换中任务", n);
            for (Model3dEntity e : stale) {
                pushProgress(e, Model3dStatus.FAILED, 0, 100, "转换失败：" + reason, true, true, reason, null);
            }
        } catch (Exception e) {
            log.error("转换看门狗执行失败（不影响主流程）: {}", e.getMessage());
        }
    }

    /** 转换前磁盘空间预检：按源体积 3 倍估算（下载 + 解压 + 产物），不足则快速失败 */
    private void ensureDiskSpace(Long totalSize) {
        if (totalSize == null || totalSize <= 0) {
            return;
        }
        try {
            Path root = Paths.get(convertProperties.getWorkRoot());
            Files.createDirectories(root);
            long usable = Files.getFileStore(root).getUsableSpace();
            long need = totalSize * 3L;
            if (usable < need) {
                throw new Model3dException(String.format(
                        "转换工作区磁盘空间不足：可用 %.1f GB，预计需要 %.1f GB（%s）",
                        usable / 1024.0 / 1024 / 1024, need / 1024.0 / 1024 / 1024,
                        convertProperties.getWorkRoot()));
            }
        } catch (Model3dException e) {
            throw e;
        } catch (IOException e) {
            log.warn("磁盘空间预检失败（忽略继续）: {}", e.getMessage());
        }
    }

    /** 产物校验：进程退出码 0 不代表产物可用，至少校验存在性与基本格式 */
    private void validateConvertOutput(Path outputDir) {
        Path tileset = publishHelper.findTileset(outputDir);
        if (tileset != null) {
            try {
                if (Files.size(tileset) <= 0) {
                    throw new Model3dException("转换产物 tileset.json 为空：" + tileset);
                }
            } catch (IOException e) {
                throw new Model3dException("校验 tileset.json 失败：" + e.getMessage(), e);
            }
            return;
        }
        Path model = ConvertFileUtils.findFirstFile(outputDir, "glb", "gltf");
        if (model == null) {
            throw new Model3dException("转换产物中未找到 tileset.json 或 glb/gltf");
        }
        try {
            long size = Files.size(model);
            if (size < 12) {
                throw new Model3dException("转换产物过小，疑似损坏：" + model.getFileName() + "（" + size + " 字节）");
            }
            if (model.getFileName().toString().toLowerCase().endsWith(".glb")) {
                byte[] magic = new byte[4];
                try (InputStream in = Files.newInputStream(model)) {
                    if (in.read(magic) != 4
                            || magic[0] != 'g' || magic[1] != 'l'
                            || magic[2] != 'T' || magic[3] != 'F') {
                        throw new Model3dException("转换产物不是合法 glb（魔数校验失败）：" + model.getFileName());
                    }
                }
            }
        } catch (IOException e) {
            throw new Model3dException("校验转换产物失败：" + e.getMessage(), e);
        }
    }

    /** 本实例标识：优先配置，缺省取主机名（同机多副本务必显式配置 instance-id） */
    private volatile String cachedInstanceId;

    private String instanceId() {
        if (cachedInstanceId == null) {
            synchronized (this) {
                if (cachedInstanceId == null) {
                    cachedInstanceId = resolveInstanceId();
                }
            }
        }
        return cachedInstanceId;
    }

    private String resolveInstanceId() {
        if (configuredInstanceId != null && !configuredInstanceId.isBlank()) {
            return configuredInstanceId.trim();
        }
        try {
            return InetAddress.getLocalHost().getHostName();
        } catch (Exception e) {
            return "unknown-host";
        }
    }

    /** 更新工具历史状态（复用 sdms_tool_his，失败不抛出以免掩盖主异常） */
    private void updateToolHis(String taskHisId, String status) {
        if (isBlank(taskHisId)) {
            return;
        }
        try {
            ToolHisEntity his = toolHisService.getById(taskHisId);
            if (his != null) {
                his.setStatus(status);
                toolHisService.updateById(his);
            }
        } catch (Exception e) {
            log.error("更新工具历史状态失败: taskId={}, {}", taskHisId, e.getMessage());
        }
    }

    /** 转换产物上传与登记：优先 tileset.json（整套瓦片），否则取单个 glb/gltf */
    private void publishConvertOutput(Model3dEntity entity, Path outputDir) throws IOException {
        String publishPrefix = publishPrefix(entity.getId());
        Path tileset = publishHelper.findTileset(outputDir);
        if (tileset != null) {
            publishHelper.uploadDirectory(outputDir, publishPrefix);
            String tilesetKey = publishPrefix
                    + outputDir.toAbsolutePath().normalize().relativize(tileset).toString()
                            .replace('\\', '/');
            TilesetParseResult parsed = publishHelper.parseTileset(tileset);
            applyParseResult(entity, tilesetKey, parsed);
        } else {
            Path model = ConvertFileUtils.findFirstFile(outputDir, "glb", "gltf");
            if (model == null) {
                throw new Model3dException("转换产物中未找到 tileset.json 或 glb/gltf");
            }
            String key = publishPrefix + model.getFileName();
            storage.putFile(key, model);
            entity.setPublishObjectKey(key);
        }
        ensurePublishPolicy();
        entity.setStatus(Model3dStatus.PUBLISHED.getCode());
        entity.setProgress(100);
        entity.setFailReason(null);
        entity.setUpdateUser(AuthUtil.getNickName());
        entity.setUpdateTime(LocalDateTime.now());
        model3dMapper.updateById(entity);
    }

    // ==================== 查询 / 删除 ====================

    @Override
    public Model3dEntity getById(String id) {
        return model3dMapper.selectById(id);
    }

    @Override
    public Page<Model3dEntity> page(Model3dPageQuery query) {
        LambdaQueryWrapper<Model3dEntity> wrapper = new LambdaQueryWrapper<>();
        if (query != null) {
            if (!isBlank(query.getKeyword())) {
                wrapper.like(Model3dEntity::getName, query.getKeyword().trim());
            }
            if (!isBlank(query.getDirectoryId())) {
                wrapper.eq(Model3dEntity::getDirectoryId, query.getDirectoryId());
            }
            if (!isBlank(query.getModelType())) {
                wrapper.eq(Model3dEntity::getModelType, query.getModelType());
            }
            if (!isBlank(query.getStatus())) {
                wrapper.eq(Model3dEntity::getStatus, query.getStatus());
            }
            long pageNum = Math.max(1, query.getPageNum());
            long pageSize = Math.min(200, Math.max(1, query.getPageSize()));
            Page<Model3dEntity> page = Page.of(pageNum, pageSize);
            wrapper.orderByDesc(Model3dEntity::getCreateTime);
            return model3dMapper.selectPage(page, wrapper);
        }
        return model3dMapper.selectPage(Page.of(1, 10),
                new LambdaQueryWrapper<Model3dEntity>().orderByDesc(Model3dEntity::getCreateTime));
    }

    @Override
    public void delete(String id) {
        Model3dEntity entity = requireEntity(id);
        storage.removeByPrefix(KEY_PREFIX + "/" + id + "/");
        model3dMapper.deleteById(id);
        log.info("三维数据已删除: id={}, name={}", id, entity.getName());
    }

    // ==================== 私有辅助 ====================

    private Model3dEntity requireEntity(String id) {
        if (isBlank(id)) {
            throw new Model3dException("id 不能为空");
        }
        Model3dEntity entity = model3dMapper.selectById(id);
        if (entity == null) {
            throw new Model3dException("三维数据不存在：" + id);
        }
        return entity;
    }

    private void updateStatus(Model3dEntity entity, Model3dStatus status, Integer progress, String failReason) {
        Model3dEntity patch = new Model3dEntity();
        patch.setId(entity.getId());
        patch.setStatus(status.getCode());
        if (progress != null) {
            patch.setProgress(progress);
        }
        patch.setFailReason(failReason);
        patch.setUpdateUser(AuthUtil.getNickName());
        patch.setUpdateTime(LocalDateTime.now());
        model3dMapper.updateById(patch);
    }

    private void updateProgress(String id, int percent) {
        Model3dEntity patch = new Model3dEntity();
        patch.setId(id);
        patch.setProgress(percent);
        // 顺带刷新心跳：有进度输出的工具无需依赖独立心跳线程
        patch.setHeartbeatTime(LocalDateTime.now());
        model3dMapper.updateById(patch);
    }

    private void pushProgress(Model3dEntity entity, Model3dStatus status, int step, int percent,
                              String msg, boolean isError, boolean isFinish,
                              String failReason, String result) {
        String target = entity.getCreateUser();
        if (isBlank(target)) {
            log.warn("推送目标缺失（createUser 为空），实时通知不可达: model3dId={}", entity.getId());
            return;
        }
        WebSocketUtil.sendMsg(signalrClient, target, MSG_TYPE,
                entity.getId(), status == null ? null : Integer.valueOf(status.getCode()), step, percent, msg,
                isError, isFinish, failReason, result);
    }

    private String partKey(String id, int partNo) {
        return String.format("%s/%s/parts/%06d", KEY_PREFIX, id, partNo);
    }

    private String srcKey(String id, String fileName) {
        return String.format("%s/%s/src/%s", KEY_PREFIX, id, sanitizeFileName(fileName));
    }

    private String publishPrefix(String id) {
        return String.format("%s/%s/publish/", KEY_PREFIX, id);
    }

    private String sanitizeFileName(String name) {
        if (name == null || name.isBlank()) {
            throw new Model3dException("文件名不能为空");
        }
        String cleaned = name.replace('\\', '/');
        cleaned = cleaned.substring(cleaned.lastIndexOf('/') + 1).trim();
        cleaned = cleaned.replaceAll("[\\r\\n\\t\"*?<>|]", "_");
        if (cleaned.length() > 200) {
            cleaned = cleaned.substring(cleaned.length() - 200);
        }
        if (cleaned.isBlank() || ".".equals(cleaned) || "..".equals(cleaned)) {
            throw new Model3dException("文件名非法：" + name);
        }
        return cleaned;
    }

    private Model3dType parseType(String v) {
        try {
            return Model3dType.valueOf(v.trim().toUpperCase());
        } catch (Exception e) {
            throw new Model3dException("非法数据类型：" + v);
        }
    }

    private SrcFormat parseFormat(String v) {
        try {
            return SrcFormat.valueOf(v.trim().toUpperCase());
        } catch (Exception e) {
            throw new Model3dException("非法源格式：" + v);
        }
    }

    private Double parseDouble(String v) {
        if (v == null || v.isBlank()) {
            return null;
        }
        try {
            return Double.parseDouble(v.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private String trimLine(String line) {
        if (line == null) {
            return "";
        }
        String t = line.trim();
        return t.length() > 100 ? t.substring(0, 100) : t;
    }

    private String truncate(String s, int max) {
        if (s == null) {
            return null;
        }
        return s.length() <= max ? s : s.substring(0, max);
    }

    private boolean isBlank(String s) {
        return s == null || s.isBlank();
    }

    private void deleteRecursively(Path dir) {
        if (dir == null || !Files.exists(dir)) {
            return;
        }
        try (Stream<Path> stream = Files.walk(dir)) {
            stream.sorted(Comparator.reverseOrder()).forEach(p -> {
                try {
                    Files.deleteIfExists(p);
                } catch (IOException ignored) {
                }
            });
        } catch (IOException ignored) {
        }
    }
}
