package com.whulri.geodata.datamanage.config;

import lombok.extern.slf4j.Slf4j;
import org.gdal.gdal.gdal;
import org.gdal.osr.osr;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.URL;
import java.net.URLDecoder;
import java.util.Enumeration;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * GDAL 运行环境初始化。
 *
 * <p>问题背景：whulri-gdal 的 {@code GDALConfig.Configuration()} 通过
 * {@code ResourceUtility.GetResourcePath("/proj7")} 解析打包在 jar 内的 GDAL 数据目录，
 * 但 Spring Boot 3 的 fat-jar 使用 {@code jar:nested:} 协议，该方法无法解析，会抛出
 * "获取资源路径失败: /proj7"，导致 {@code GDAL_DATA} / {@code PROJ_LIB} 永远不被设置，
 * 进而坐标转换、投影、栅格缩略图等依赖 GDAL 数据的功能全部静默降级。</p>
 *
 * <p>本组件在应用启动后将 {@code proj7} 资源目录从 fat-jar 抽取到真实文件系统
 * （{@code <user.home>/.geodata/proj7}），并正确设置 {@code GDAL_DATA} 与
 * {@code PROJ_LIB}（含 PROJ 搜索路径）。抽取只需一次，跨 jar 重建稳定。</p>
 *
 * <p>注意：GDAL 原生库（gdalalljni.dll / libgdalalljni.so）必须经 {@code java.library.path}
 * 加载，否则任意 {@code gdal.*} 调用抛 {@code UnsatisfiedLinkError}。本组件在原生就绪后通过
 * {@code gdal.SetConfigOption} / {@code osr.SetPROJSearchPath} 直接配置（覆盖环境变量），
 * 同时在启动脚本中设置真实环境变量 {@code GDAL_DATA} / {@code PROJ_LIB} 作为兜底。</p>
 */
@Slf4j
@Component
public class GdalEnvInitializer {

    /** 抽取目标：固定路径，与启动脚本中设置的环境变量保持一致。 */
    private static final String EXTRACT_BASE = ".geodata/proj7";
    private static volatile boolean initialized = false;

    @EventListener(ApplicationReadyEvent.class)
    public void init() {
        ensureConfigured();
    }

    /** 幂等：供业务代码在调用 GDAL 前主动确保环境已配置（替代 GDALConfig.Configuration()）。 */
    public static synchronized void ensureConfigured() {
        if (initialized) {
            return;
        }
        try {
            File proj7Dir = extractProj7();
            if (proj7Dir == null || !proj7Dir.exists()) {
                log.warn("GDAL proj7 资源未找到，跳过 GDAL_DATA/PROJ_LIB 配置（GDAL 相关功能降级）");
                initialized = true;
                return;
            }
            File dataDir = new File(proj7Dir, "data");
            File shareDir = new File(proj7Dir, "share");
            // gdal-data 在 data/；proj.db 在 share/
            String gdalData = dataDir.exists() ? dataDir.getAbsolutePath() : proj7Dir.getAbsolutePath();
            String projLib = shareDir.exists() ? shareDir.getAbsolutePath() : proj7Dir.getAbsolutePath();

            boolean configuredByNative = false;
            try {
                // 必须先触发原生库加载（懒加载），否则 gdal.* 全部抛 UnsatisfiedLinkError
                gdal.AllRegister();
                // 原生库已加载：直接设置 GDAL / PROJ 配置项（覆盖环境变量，优先级最高）
                gdal.SetConfigOption("GDAL_DATA", gdalData);
                gdal.SetConfigOption("GDAL_CACHEMAX", "3072");
                gdal.SetConfigOption("GDAL_NUM_THREADS", "4");
                gdal.SetConfigOption("OSR_USE_NON_DEPRECATED", "NO");
                osr.SetPROJSearchPath(projLib);
                configuredByNative = true;
                log.info("GDAL 环境已初始化（原生）: GDAL_DATA={}, PROJ_LIB={}", gdalData, projLib);
            } catch (Throwable t) {
                // 原生库未加载（如目标机器未安装 GDAL 原生库）时降级
                log.warn("GDAL 原生库尚未加载，坐标转换/投影功能降级（仅抽取数据备用）: {}", t.getMessage());
            }

            // 环境变量兜底：即使本进程尚未触发原生加载，原生库后续加载时也会从环境变量读取
            System.setProperty("GDAL_DATA", gdalData);
            System.setProperty("PROJ_LIB", projLib);
            log.info("GDAL 数据目录已就绪: {} (原生直接配置={})", proj7Dir.getAbsolutePath(), configuredByNative);
            initialized = true;
        } catch (Throwable t) {
            log.warn("GDAL 环境初始化失败（GDAL 相关功能降级）: {}", t.getMessage());
            initialized = true;
        }
    }

    /**
     * 查找机器上已存在的 GDAL 原生数据目录（以 {@code <dir>/share/proj.db} 为准）。
     * 依次检查：{@code gdal.home} 系统属性 → {@code PROJ_LIB} 环境变量 →
     * {@code GDAL_DATA} 环境变量 → 本机常见安装路径。
     *
     * @return 找到的 proj7 目录；未找到返回 {@code null}
     */
    private static File findExistingProj7() {
        java.util.List<File> candidates = new java.util.ArrayList<>();

        String gdalHome = System.getProperty("gdal.home");
        if (gdalHome != null && !gdalHome.isEmpty()) {
            candidates.add(new File(gdalHome, "bin/proj7"));
            candidates.add(new File(gdalHome, "proj7"));
            candidates.add(new File(gdalHome));
        }

        String projLib = System.getenv("PROJ_LIB");
        if (projLib != null && !projLib.isEmpty()) {
            candidates.add(new File(projLib));
            File p = new File(projLib).getParentFile();
            if (p != null) {
                candidates.add(p);
            }
        }

        String gdalData = System.getenv("GDAL_DATA");
        if (gdalData != null && !gdalData.isEmpty()) {
            File p = new File(gdalData).getParentFile();
            if (p != null) {
                candidates.add(p);
            }
        }

        candidates.add(new File("D:/tools/release-1916-x64-gdal-3-8-5-mapserver-8-0-1/bin/proj7"));
        candidates.add(new File("C:/Program Files/GDAL/proj7"));
        candidates.add(new File("C:/OSGeo4W/share/proj"));

        for (File c : candidates) {
            if (c == null) {
                continue;
            }
            if (new File(c, "share/proj.db").exists() || new File(c, "proj.db").exists()) {
                return c;
            }
        }
        return null;
    }

    /**
     * 将 fat-jar 内的 proj7 资源抽取到真实文件系统。
     * 通过 {@code getResource("/proj7")} 解析资源真实位置，可正确处理
     * {@code jar:nested:}（Spring Boot 3 fat-jar）与 {@code jar:file:} 形态，
     * 取最外层 jar 作为 ZipFile 来源、取资源在 jar 内的条目前缀进行抽取。
     */
    private static File extractProj7() throws Exception {
        File target = new File(System.getProperty("user.home"), EXTRACT_BASE);
        if (target.exists() && new File(target, "data").exists()) {
            return target; // 已抽取，跳过
        }

        // 优先复用机器上已安装的 GDAL 原生数据目录（含 proj.db），避免 jar 内资源解析失败
        File existing = findExistingProj7();
        if (existing != null) {
            log.info("复用已存在的 GDAL 数据目录: {}", existing.getAbsolutePath());
            return existing;
        }

        target.mkdirs();

        URL resUrl = GdalEnvInitializer.class.getResource("/proj7");
        if (resUrl == null) {
            resUrl = GdalEnvInitializer.class.getClassLoader().getResource("proj7");
        }
        if (resUrl == null) {
            log.warn("无法定位 /proj7 资源（getResource 返回 null），GDAL 数据目录抽取跳过");
            return target;
        }

        String spec = resUrl.toString();
        String proto = spec.startsWith("jar:nested:") ? "jar:nested:"
                : (spec.startsWith("jar:file:") ? "jar:file:" : null);

        if (proto == null) {
            // file: 形式（IDE / 解压目录运行）：直接拷贝目录
            if (spec.startsWith("file:")) {
                String p = URLDecoder.decode(spec.substring("file:".length()), "UTF-8");
                File proj7File = new File(p);
                if (proj7File.exists() && proj7File.isDirectory()) {
                    copyDir(proj7File, target);
                }
            }
            return target;
        }

        // jar:* 形态：解析最外层 jar 与资源在 jar 内的条目前缀
        String s = spec.substring(proto.length()); // file:/X/geodata-web.jar!/.../proj7/
        int idx = s.indexOf("!/");
        if (idx < 0) {
            return target;
        }
        String outerJar = s.substring(0, idx);
        if (outerJar.startsWith("file:")) {
            outerJar = outerJar.substring("file:".length());
        }
        outerJar = URLDecoder.decode(outerJar, "UTF-8");

        // 资源在 jar 内的条目前缀（可能为 BOOT-INF/lib/xxx.jar!/BOOT-INF/classes/proj7 嵌套形态）
        String inner = s.substring(idx + 2);
        if (inner.startsWith("/")) {
            inner = inner.substring(1);
        }
        int nestedIdx = inner.indexOf(".jar!/");
        String entryPrefix = nestedIdx >= 0 ? inner.substring(nestedIdx + ".jar!/".length()) : inner;
        // 去掉末尾斜杠
        if (entryPrefix.endsWith("/")) {
            entryPrefix = entryPrefix.substring(0, entryPrefix.length() - 1);
        }

        try (ZipFile zf = new ZipFile(outerJar)) {
            Enumeration<? extends ZipEntry> e = zf.entries();
            int count = 0;
            while (e.hasMoreElements()) {
                ZipEntry ze = e.nextElement();
                String name = ze.getName();
                if (name.startsWith(entryPrefix + "/") && !ze.isDirectory()) {
                    String rel = name.substring(entryPrefix.length() + 1);
                    File out = new File(target, rel);
                    out.getParentFile().mkdirs();
                    try (InputStream in = zf.getInputStream(ze);
                         FileOutputStream fos = new FileOutputStream(out)) {
                        byte[] buf = new byte[8192];
                        int n;
                        while ((n = in.read(buf)) > 0) {
                            fos.write(buf, 0, n);
                        }
                    }
                    count++;
                }
            }
            log.info("GDAL proj7 抽取完成：来源={}，条目数={}", outerJar, count);
        }
        return target;
    }

    private static void copyDir(File src, File dst) {
        File[] files = src.listFiles();
        if (files == null) {
            return;
        }
        for (File f : files) {
            File to = new File(dst, f.getName());
            if (f.isDirectory()) {
                to.mkdirs();
                copyDir(f, to);
            } else {
                try (InputStream in = new java.io.FileInputStream(f);
                     FileOutputStream fos = new FileOutputStream(to)) {
                    byte[] buf = new byte[8192];
                    int n;
                    while ((n = in.read(buf)) > 0) {
                        fos.write(buf, 0, n);
                    }
                } catch (Exception ignored) {
                    // 单个文件失败不影响整体
                }
            }
        }
    }
}
