package com.whulri.geodata.common;

import com.whulri.geodata.common.enums.OSEnum;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.net.JarURLConnection;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLConnection;
import java.security.CodeSource;
import java.security.ProtectionDomain;
import java.util.jar.JarFile;

/**
 * 运行环境探测（定位 jar/类路径、初始化应用目录，反编译自 dev-common-1.1.8，签名与原实现保持一致）
 */
public class Environment {

    private static final Logger log = LoggerFactory.getLogger(Environment.class);

    private static String homeDir;
    private static String appName;
    private static String tmpDir;
    private static String logDir;
    private static String libDir;
    private static int osCode;

    private Environment() {
    }

    public static String getHomeDir() {
        return homeDir;
    }

    public static String getAppName() {
        return appName;
    }

    public static String getTmpDir() {
        return tmpDir;
    }

    public static String getLogDir() {
        return logDir;
    }

    public static String getLibDir() {
        return libDir;
    }

    public static int getOSCode() {
        return osCode;
    }

    public static boolean isLinux() {
        return osCode == OSEnum.LINUX.getCode();
    }

    public static boolean isWindow() {
        return osCode == OSEnum.WINDOWS.getCode();
    }

    public static void initEnv(Class<?> application) {
        File sourceFile = Environment.findSource(application);
        boolean isJar = sourceFile.isFile();
        if (isJar) {
            homeDir = sourceFile.getParentFile().getAbsolutePath();
            appName = sourceFile.getName();
        } else {
            homeDir = sourceFile.getParentFile().getParentFile().getAbsolutePath();
            appName = sourceFile.getParentFile().getParentFile().getName();
        }
        tmpDir = homeDir + File.separator + "tmp";
        logDir = homeDir + File.separator + "log";
        libDir = homeDir + File.separator + "lib";
        Environment.createDir(tmpDir);
        Environment.createDir(logDir);
        System.setProperty("user.dir", homeDir);
        System.setProperty("java.io.tmpdir", tmpDir);
        System.setProperty("app.home", homeDir);
        System.setProperty("app.name", appName);
        System.setProperty("app.tmp.dir", tmpDir);
        System.setProperty("app.log.dir", logDir);
        String osStr = System.getProperty("os.name");
        log.info("OS:" + osStr);
        if (osStr.startsWith("Windows")) {
            osCode = OSEnum.WINDOWS.getCode();
        } else if (osStr.startsWith("Linux")) {
            osCode = OSEnum.LINUX.getCode();
        }
    }

    private static void createDir(String path) {
        File file = new File(path);
        if (!file.exists()) {
            file.mkdirs();
        }
    }

    private static File findSource(Class<?> sourceClass) {
        try {
            ProtectionDomain domain = sourceClass != null ? sourceClass.getProtectionDomain() : null;
            CodeSource codeSource = domain != null ? domain.getCodeSource() : null;
            URL location = codeSource != null ? codeSource.getLocation() : null;
            File source = location != null ? Environment.findSource(location) : null;
            if (source != null && source.exists()) {
                return source.getAbsoluteFile();
            }
        } catch (Exception e) {
            log.error(e.getMessage());
        }
        return null;
    }

    private static File findSource(URL location) throws IOException, URISyntaxException {
        URLConnection connection = location.openConnection();
        return connection instanceof JarURLConnection
                ? Environment.getRootJarFile(((JarURLConnection) connection).getJarFile())
                : new File(location.toURI());
    }

    private static File getRootJarFile(JarFile jarFile) {
        String name = jarFile.getName();
        int separator = name.indexOf("!/");
        if (separator > 0) {
            name = name.substring(0, separator);
        }
        return new File(name);
    }
}
