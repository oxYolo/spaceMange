package com.whulri.geodata.model3d.storage;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 三维数据对象存储配置（geodata.model3d.storage.*）。
 *
 * <p>刻意独立于遗留 MinioConfig（其字段名与 bootstrap-dev.yml 键不匹配，属迁移遗留），
 * 三维模块自行管理绑定与客户端实例，互不影响。</p>
 */
@Data
@Component
@ConfigurationProperties(prefix = "geodata.model3d.storage")
public class Model3dStorageProperties {

    /** MinIO 端点 */
    private String endpoint = "http://localhost:9000";

    private String accessKey = "minioadmin";

    private String secretKey = "minioadmin";

    /** 业务桶（三维数据统一放 model3d/ 前缀下） */
    private String bucket = "geodata";

    /** 加载地址预签名有效期（分钟）——仅在 public-read=false 或单体模型场景使用 */
    private int presignExpiryMinutes = 120;

    /**
     * 是否对发布前缀开启「公开只读」（推荐 true）。
     *
     * <p>3D Tiles 的 tileset.json 会以相对路径引用子瓦片（.json/.b3dm/.pnts/贴图），
     * 而预签名 URL 的签名与单个对象绑定，相对路径解析后会丢失签名 → 子瓦片 403。
     * 因此多文件成果必须走「公开只读前缀」，不能逐文件预签名。</p>
     */
    private boolean publicRead = true;

    /**
     * 对外可访问的基础地址（浏览器可达），用于拼接公开加载地址。
     *
     * <p>留空则回退 {@link #endpoint}。生产环境通常为 nginx 反代的对外地址，
     * 例如 {@code https://gis.example.com}（反向代理把 /{bucket}/ 指向 MinIO）。</p>
     */
    private String publicBaseUrl = "";

    /** 公开只读策略作用的对象前缀（默认仅开放三维成果，最小权限） */
    private String publicReadPrefix = "model3d/";
}
