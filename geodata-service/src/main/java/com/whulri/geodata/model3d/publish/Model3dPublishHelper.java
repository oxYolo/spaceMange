package com.whulri.geodata.model3d.publish;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.whulri.geodata.model3d.dto.TilesetParseResult;
import com.whulri.geodata.model3d.exception.Model3dException;
import com.whulri.geodata.model3d.storage.Model3dStorage;
import lombok.extern.slf4j.Slf4j;
import net.lingala.zip4j.ZipFile;
import net.lingala.zip4j.model.FileHeader;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

/**
 * 三维发布辅助：zip 安全解压 / tileset.json 定位与解析 / 目录上传对象存储。
 *
 * <p>纯 JDK + zip4j + Jackson，跨平台路径一律走 {@link Path}。</p>
 */
@Slf4j
@Component
public class Model3dPublishHelper {

    private final Model3dStorage storage;

    private final ObjectMapper mapper = new ObjectMapper();

    public Model3dPublishHelper(Model3dStorage storage) {
        this.storage = storage;
    }

    // ==================== zip 解压（含 zip-slip 防护） ====================

    /**
     * 解压 zip 到目标目录（覆盖已存在文件）。
     * 逐条目校验「归一化后不得逃逸出目标目录」，防御恶意路径（../）。
     */
    public Path extractZip(Path zipFile, Path destDir) {
        try {
            Files.createDirectories(destDir);
            ZipFile zip = new ZipFile(zipFile.toFile());
            List<FileHeader> headers = zip.getFileHeaders();
            if (headers == null || headers.isEmpty()) {
                throw new Model3dException("zip 包为空或损坏：" + zipFile);
            }
            String destRoot = destDir.toAbsolutePath().normalize().toString();
            for (FileHeader h : headers) {
                String name = h.getFileName();
                if (name == null) {
                    continue;
                }
                // 统一分隔符后做逃逸校验（zip 内条目分隔符固定为 /）
                String rel = name.replace('\\', '/');
                Path target = destDir.resolve(rel).normalize();
                if (!target.toAbsolutePath().normalize().toString().startsWith(destRoot)) {
                    throw new Model3dException("zip 内含非法路径条目（疑似路径穿越攻击）：" + rel);
                }
            }
            zip.extractAll(destDir.toAbsolutePath().toString());
            return destDir;
        } catch (Model3dException e) {
            throw e;
        } catch (Exception e) {
            throw new Model3dException("zip 解压失败：" + zipFile + "，" + e.getMessage(), e);
        }
    }

    // ==================== tileset.json 定位 ====================

    /**
     * 在目录中定位 tileset.json：优先根目录，其次逐层下探（最多 4 层，取最浅者）。
     */
    public Path findTileset(Path dir) {
        try (Stream<Path> stream = Files.walk(dir, 4)) {
            return stream
                    .filter(Files::isRegularFile)
                    .filter(p -> p.getFileName().toString().equalsIgnoreCase("tileset.json"))
                    .sorted(Comparator.comparingInt(p -> p.getNameCount()))
                    .findFirst()
                    .orElse(null);
        } catch (IOException e) {
            throw new Model3dException("扫描 tileset.json 失败：" + dir, e);
        }
    }

    // ==================== tileset.json 解析 ====================

    /**
     * 解析 tileset.json：包围盒 / transform / 瓦片数 / LOD 深度 / 根几何误差。
     * 兼容 box / sphere / region 三种 boundingVolume。
     */
    public TilesetParseResult parseTileset(Path tilesetJson) {
        try {
            JsonNode root = mapper.readTree(tilesetJson.toFile());
            TilesetParseResult r = new TilesetParseResult();

            JsonNode asset = root.get("asset");
            if (asset != null && asset.hasNonNull("version")) {
                r.setVersion(asset.get("version").asText());
            }

            JsonNode rootTile = root.get("root");
            if (rootTile == null) {
                throw new Model3dException("tileset.json 缺少 root 节点：" + tilesetJson);
            }

            JsonNode bv = rootTile.get("boundingVolume");
            if (bv != null) {
                r.setBbox(computeBbox(bv));
            }

            JsonNode transform = rootTile.get("transform");
            if (transform != null && transform.isArray() && transform.size() == 16) {
                List<Double> t = new ArrayList<>(16);
                transform.forEach(n -> t.add(n.asDouble()));
                r.setTransform(t);
            }

            JsonNode ge = rootTile.get("geometricError");
            if (ge != null && ge.isNumber()) {
                r.setGeometricError(ge.asDouble());
            }

            int[] counter = new int[]{0};
            int[] depth = new int[]{0};
            countTiles(rootTile, 1, counter, depth);
            r.setTileCount((long) counter[0]);
            r.setLodDepth(depth[0]);
            return r;
        } catch (Model3dException e) {
            throw e;
        } catch (Exception e) {
            throw new Model3dException("解析 tileset.json 失败：" + tilesetJson + "，" + e.getMessage(), e);
        }
    }

    /** 递归统计瓦片节点与深度 */
    private void countTiles(JsonNode tile, int level, int[] counter, int[] depth) {
        counter[0]++;
        depth[0] = Math.max(depth[0], level);
        JsonNode children = tile.get("children");
        if (children != null && children.isArray()) {
            for (JsonNode child : children) {
                countTiles(child, level + 1, counter, depth);
            }
        }
    }

    /** boundingVolume → [xmin,ymin,zmin,xmax,ymax,zmax] */
    private List<Double> computeBbox(JsonNode bv) {
        JsonNode box = bv.get("box");
        if (box != null && box.isArray() && box.size() == 12) {
            double cx = box.get(0).asDouble(), cy = box.get(1).asDouble(), cz = box.get(2).asDouble();
            double ex = 0, ey = 0, ez = 0;
            for (int i = 0; i < 3; i++) {
                ex += Math.abs(box.get(3 + i * 3).asDouble());
                ey += Math.abs(box.get(4 + i * 3).asDouble());
                ez += Math.abs(box.get(5 + i * 3).asDouble());
            }
            return List.of(cx - ex, cy - ey, cz - ez, cx + ex, cy + ey, cz + ez);
        }
        JsonNode sphere = bv.get("sphere");
        if (sphere != null && sphere.isArray() && sphere.size() == 4) {
            double cx = sphere.get(0).asDouble(), cy = sphere.get(1).asDouble(),
                    cz = sphere.get(2).asDouble(), r = sphere.get(3).asDouble();
            return List.of(cx - r, cy - r, cz - r, cx + r, cy + r, cz + r);
        }
        JsonNode region = bv.get("region");
        if (region != null && region.isArray() && region.size() == 6) {
            // region 为弧度 [west,south,east,north,minHeight,maxHeight]，换算为度
            double w = Math.toDegrees(region.get(0).asDouble());
            double s = Math.toDegrees(region.get(1).asDouble());
            double e = Math.toDegrees(region.get(2).asDouble());
            double n = Math.toDegrees(region.get(3).asDouble());
            double minH = region.get(4).asDouble();
            double maxH = region.get(5).asDouble();
            return List.of(w, s, minH, e, n, maxH);
        }
        return null;
    }

    // ==================== 目录上传 ====================

    /**
     * 上传目录到指定对象前缀（保留相对路径，分隔符统一为 /），返回上传文件数。
     */
    public int uploadDirectory(Path dir, String keyPrefix) {
        String prefix = keyPrefix.endsWith("/") ? keyPrefix : keyPrefix + "/";
        String base = dir.toAbsolutePath().normalize().toString();
        final int[] count = {0};
        try (Stream<Path> stream = Files.walk(dir)) {
            stream.filter(Files::isRegularFile).forEach(file -> {
                String rel = dir.toAbsolutePath().normalize().relativize(file).toString()
                        .replace('\\', '/');
                storage.putFile(prefix + rel, file);
                count[0]++;
            });
            log.info("目录上传完成: {} → {}（{} 个文件）", base, prefix, count[0]);
        } catch (IOException e) {
            throw new Model3dException("目录上传失败：" + dir, e);
        }
        return count[0];
    }
}
