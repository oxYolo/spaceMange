package com.whulri.geodata.model3d.storage;

import com.whulri.geodata.model3d.exception.Model3dException;
import io.minio.BucketExistsArgs;
import io.minio.ComposeObjectArgs;
import io.minio.ComposeSource;
import io.minio.GetObjectArgs;
import io.minio.GetPresignedObjectUrlArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.RemoveObjectArgs;
import io.minio.Result;
import io.minio.SetBucketPolicyArgs;
import io.minio.http.Method;
import io.minio.messages.Item;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;

/**
 * 三维数据 MinIO 存储封装（分片对象 / compose 合并 / 目录上传 / 预签名 / 前缀清理）。
 */
@Slf4j
@Component
public class Model3dStorage {

    private final Model3dStorageProperties props;

    private volatile MinioClient client;

    public Model3dStorage(Model3dStorageProperties props) {
        this.props = props;
    }

    private MinioClient client() {
        if (client == null) {
            synchronized (this) {
                if (client == null) {
                    client = MinioClient.builder()
                            .endpoint(props.getEndpoint())
                            .credentials(props.getAccessKey(), props.getSecretKey())
                            .build();
                }
            }
        }
        return client;
    }

    /** 确保桶存在（不存在时创建） */
    public void ensureBucket() {
        try {
            boolean exists = client().bucketExists(
                    BucketExistsArgs.builder().bucket(props.getBucket()).build());
            if (!exists) {
                client().makeBucket(MakeBucketArgs.builder().bucket(props.getBucket()).build());
                log.info("已创建对象存储桶: {}", props.getBucket());
            }
        } catch (Exception e) {
            throw new Model3dException("对象存储桶检查/创建失败：" + e.getMessage(), e);
        }
    }

    /** 公开只读策略是否已设置（进程内幂等，避免每次发布都调 MinIO） */
    private volatile boolean publicPolicyReady = false;

    /**
     * 为发布前缀设置「匿名只读」桶策略（幂等）。
     *
     * <p>3D Tiles 是多文件成果，tileset.json 通过相对路径引用子瓦片；预签名 URL 只能覆盖
     * 单个对象，无法覆盖相对路径解析出的子瓦片，故必须开放前缀的匿名 GET 权限。
     * 策略只授予 {@code GetObject}（不含 ListBucket/写权限），作用范围限 {@code publicReadPrefix}，最小权限。</p>
     */
    public synchronized void ensurePublicReadPolicy() {
        if (publicPolicyReady) {
            return;
        }
        String prefix = props.getPublicReadPrefix() == null ? "" : props.getPublicReadPrefix();
        String resource = "arn:aws:s3:::" + props.getBucket() + "/" + prefix + "*";
        String policy = "{\"Version\":\"2012-10-17\",\"Statement\":[{"
                + "\"Effect\":\"Allow\",\"Principal\":{\"AWS\":[\"*\"]},"
                + "\"Action\":[\"s3:GetObject\"],"
                + "\"Resource\":[\"" + resource + "\"]}]}";
        try {
            ensureBucket();
            client().setBucketPolicy(SetBucketPolicyArgs.builder()
                    .bucket(props.getBucket())
                    .config(policy)
                    .build());
            publicPolicyReady = true;
            log.info("已设置公开只读策略: bucket={}, prefix={}", props.getBucket(), prefix);
        } catch (Exception e) {
            // 设置失败不阻断发布：可能是权限受限或 MinIO 已由运维统一配置；回退预签名由调用方决定
            log.warn("设置公开只读策略失败（不影响本地上传，加载地址可能不可用）: {}", e.getMessage());
        }
    }

    /** 公开加载地址（浏览器直连，可覆盖相对路径引用的子瓦片） */
    public String publicUrl(String key) {
        String base = props.getPublicBaseUrl() == null || props.getPublicBaseUrl().isBlank()
                ? props.getEndpoint()
                : props.getPublicBaseUrl();
        while (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        return base + "/" + props.getBucket() + "/" + encodeKeyPath(key);
    }

    /** 对象键路径编码（按段编码，保留 / 分隔符） */
    private static String encodeKeyPath(String key) {
        StringBuilder sb = new StringBuilder(key.length() + 16);
        String[] segs = key.split("/");
        for (int i = 0; i < segs.length; i++) {
            if (i > 0) {
                sb.append('/');
            }
            try {
                // URLEncoder 会把空格编成 +，需还原为 %20（路径语义）
                sb.append(java.net.URLEncoder.encode(segs[i], java.nio.charset.StandardCharsets.UTF_8)
                        .replace("+", "%20"));
            } catch (Exception e) {
                sb.append(segs[i]);
            }
        }
        return sb.toString();
    }

    /** 指定前缀下全部对象名（供上传断点续传/清理使用） */
    public List<String> listObjectNames(String prefix) {
        List<String> names = new ArrayList<>();
        try {
            Iterable<Result<Item>> results = client().listObjects(io.minio.ListObjectsArgs.builder()
                    .bucket(props.getBucket())
                    .prefix(prefix)
                    .recursive(true)
                    .build());
            for (Result<Item> r : results) {
                Item item = r.get();
                if (item != null && !item.isDir()) {
                    names.add(item.objectName());
                }
            }
        } catch (Exception e) {
            throw new Model3dException("列举对象失败：" + prefix + "，" + e.getMessage(), e);
        }
        return names;
    }

    /** 上传流（覆盖写） */
    public void putStream(String key, InputStream in, long size, String contentType) {
        try {
            client().putObject(io.minio.PutObjectArgs.builder()
                    .bucket(props.getBucket())
                    .object(key)
                    .stream(in, size, -1)
                    .contentType(contentType == null ? "application/octet-stream" : contentType)
                    .build());
        } catch (Exception e) {
            throw new Model3dException("上传对象失败：" + key + "，" + e.getMessage(), e);
        }
    }

    /** 上传本地文件 */
    public void putFile(String key, Path file) {
        try (InputStream in = Files.newInputStream(file)) {
            putStream(key, in, Files.size(file), guessContentType(key));
        } catch (Exception e) {
            throw new Model3dException("上传文件失败：" + file + " → " + key + "，" + e.getMessage(), e);
        }
    }

    /** 将若干分片对象合并为一个对象（分片按传入顺序拼接） */
    public void composeParts(List<String> partKeys, String targetKey) {
        try {
            List<ComposeSource> sources = new ArrayList<>();
            for (String k : partKeys) {
                sources.add(ComposeSource.builder()
                        .bucket(props.getBucket())
                        .object(k)
                        .build());
            }
            client().composeObject(ComposeObjectArgs.builder()
                    .bucket(props.getBucket())
                    .object(targetKey)
                    .sources(sources)
                    .build());
        } catch (Exception e) {
            throw new Model3dException("分片合并失败：" + targetKey + "，" + e.getMessage(), e);
        }
    }

    /** 下载对象到本地文件（覆盖） */
    public void download(String key, Path targetFile) {
        try (InputStream in = client().getObject(GetObjectArgs.builder()
                .bucket(props.getBucket()).object(key).build())) {
            Files.createDirectories(targetFile.getParent());
            Files.copy(in, targetFile, StandardCopyOption.REPLACE_EXISTING);
        } catch (Exception e) {
            throw new Model3dException("下载对象失败：" + key + "，" + e.getMessage(), e);
        }
    }

    /** 判断对象是否存在 */
    public boolean exists(String key) {
        try {
            client().statObject(io.minio.StatObjectArgs.builder()
                    .bucket(props.getBucket()).object(key).build());
            return true;
        } catch (io.minio.errors.ErrorResponseException e) {
            return false;
        } catch (Exception e) {
            throw new Model3dException("检查对象存在性失败：" + key + "，" + e.getMessage(), e);
        }
    }

    /** 对象字节数（不存在时返回 null） */
    public Long objectSize(String key) {
        try {
            return client().statObject(io.minio.StatObjectArgs.builder()
                    .bucket(props.getBucket()).object(key).build()).size();
        } catch (io.minio.errors.ErrorResponseException e) {
            return null;
        } catch (Exception e) {
            throw new Model3dException("读取对象大小失败：" + key + "，" + e.getMessage(), e);
        }
    }

    /** 同桶内对象拷贝（单分片场景等价于 compose） */
    public void copyObject(String srcKey, String targetKey) {
        try {
            client().copyObject(io.minio.CopyObjectArgs.builder()
                    .source(io.minio.CopySource.builder()
                            .bucket(props.getBucket()).object(srcKey).build())
                    .bucket(props.getBucket())
                    .object(targetKey)
                    .build());
        } catch (Exception e) {
            throw new Model3dException("对象拷贝失败：" + srcKey + " → " + targetKey + "，" + e.getMessage(), e);
        }
    }

    /** 删除单个对象（不存在时忽略） */
    public void remove(String key) {
        try {
            client().removeObject(RemoveObjectArgs.builder()
                    .bucket(props.getBucket()).object(key).build());
        } catch (Exception e) {
            log.warn("删除对象失败（忽略）: {}，{}", key, e.getMessage());
        }
    }

    /** 删除指定前缀下全部对象，返回删除数量 */
    public int removeByPrefix(String prefix) {
        int count = 0;
        try {
            Iterable<Result<Item>> results = client().listObjects(io.minio.ListObjectsArgs.builder()
                    .bucket(props.getBucket())
                    .prefix(prefix)
                    .recursive(true)
                    .build());
            List<String> keys = new ArrayList<>();
            for (Result<Item> r : results) {
                Item item = r.get();
                if (item != null && !item.isDir()) {
                    keys.add(item.objectName());
                }
            }
            for (String k : keys) {
                remove(k);
                count++;
            }
        } catch (Exception e) {
            log.warn("按前缀删除对象失败（忽略）: {}，{}", prefix, e.getMessage());
        }
        return count;
    }

    /** 预签名 GET 加载地址 */
    public String presignGet(String key, int expirySeconds) {
        try {
            return client().getPresignedObjectUrl(GetPresignedObjectUrlArgs.builder()
                    .method(Method.GET)
                    .bucket(props.getBucket())
                    .object(key)
                    .expiry(expirySeconds)
                    .build());
        } catch (Exception e) {
            throw new Model3dException("签发加载地址失败：" + key + "，" + e.getMessage(), e);
        }
    }

    public String getBucket() {
        return props.getBucket();
    }

    public int getPresignExpirySeconds() {
        return Math.max(60, props.getPresignExpiryMinutes() * 60);
    }

    /** 按扩展名猜测 ContentType（Cesium 加载依赖正确的 MIME） */
    public static String guessContentType(String name) {
        String n = name.toLowerCase();
        if (n.endsWith(".json")) {
            return "application/json";
        }
        if (n.endsWith(".b3dm")) {
            return "model/gltf.binary";
        }
        if (n.endsWith(".glb")) {
            return "model/gltf-binary";
        }
        if (n.endsWith(".gltf")) {
            return "model/gltf+json";
        }
        if (n.endsWith(".i3dm") || n.endsWith(".pnts") || n.endsWith(".cmpt")) {
            return "model/gltf.binary";
        }
        if (n.endsWith(".png")) {
            return "image/png";
        }
        if (n.endsWith(".jpg") || n.endsWith(".jpeg")) {
            return "image/jpeg";
        }
        if (n.endsWith(".ktx2")) {
            return "image/ktx2";
        }
        if (n.endsWith(".zip")) {
            return "application/zip";
        }
        return "application/octet-stream";
    }
}
