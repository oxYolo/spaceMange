package com.whulri.geodata.common.utils;

import com.whulri.geodata.common.Environment;
import com.whulri.geodata.common.callback.ExeOutputCallback;
import com.whulri.geodata.common.io.Path;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.concurrent.TimeUnit;

/**
 * 外部进程（exe/jar/python）执行工具（反编译自 dev-common-1.1.8，签名与原实现保持一致）
 */
public class Marshal {

    private static final Logger log = LoggerFactory.getLogger(Marshal.class);

    public static final String JAR = "JAR";
    public static final String EXE = "EXE";
    public static final String PYTHON = "PYTHON";

    private Marshal() {
    }

    public static void outLog(InputStream is, boolean isPrint) {
        BufferedReader reader = null;
        try {
            reader = new BufferedReader(new InputStreamReader(is));
            String str = null;
            while ((str = reader.readLine()) != null) {
                if (!isPrint) {
                    continue;
                }
                log.info(str);
            }
            reader.close();
            is.close();
        } catch (Exception e) {
            log.error(e.getMessage());
        }
    }

    public static void outLog(InputStream is) {
        Marshal.outLog(is, false);
    }

    public static boolean runWithHiddenMode(String scriptPath, String scriptArgs, ExeOutputCallback callback) {
        return Marshal.runWithHiddenMode(scriptPath, scriptArgs, -1, callback);
    }

    private static String getScriptType(String scriptPath) {
        String ext = Path.getExtension(scriptPath);
        if (ext.equalsIgnoreCase("jar")) {
            return JAR;
        }
        if (ext.equalsIgnoreCase("py")) {
            return PYTHON;
        }
        return EXE;
    }

    public static boolean runWithHiddenMode(String scriptPath, String scriptArgs, int waitMs, ExeOutputCallback callback) {
        try {
            String type = Marshal.getScriptType(scriptPath);
            Process process = null;
            if (EXE.equals(type)) {
                process = Marshal.runExeWithHiddenModeAsync(scriptPath, scriptArgs);
            } else if (JAR.equals(type)) {
                process = Marshal.runJARWithHiddenModeAsync(scriptPath, scriptArgs);
            } else if (PYTHON.equals(type)) {
                process = Marshal.runPythonWithHiddenModeAsync(scriptPath, scriptArgs);
            }
            if (process == null) {
                return false;
            }
            if (null != callback) {
                InputStream is = process.getInputStream();
                InputStream es = process.getErrorStream();
                new Thread(() -> callback.infoOutput(is)).start();
                new Thread(() -> callback.errorOutput(es)).start();
            }
            if (waitMs > 0) {
                if (!process.isAlive()) {
                    return process.exitValue() == 0;
                }
                process.waitFor(waitMs, TimeUnit.MILLISECONDS);
                if (!process.isAlive()) {
                    return process.exitValue() == 0;
                }
                process.destroy();
                return false;
            }
            while (process.isAlive()) {
                process.waitFor(1000L, TimeUnit.MILLISECONDS);
            }
            return process.exitValue() == 0;
        } catch (InterruptedException e) {
            log.error("程序执行出错：" + e.getMessage());
            return false;
        }
    }

    public static Process runExeWithHiddenModeAsync(String exepath, String exeArgs) {
        return Marshal.runProcess(null, exepath, exeArgs);
    }

    public static Process runJARWithHiddenModeAsync(String jarPath, String jarArgs) {
        return Marshal.runProcess("java -jar", jarPath, jarArgs);
    }

    public static Process runPythonWithHiddenModeAsync(String pythonPath, String pythonArgs) {
        return Marshal.runProcess("python", pythonPath, pythonArgs);
    }

    public static Process runProcess(String command, String path, String args) {
        Process process = null;
        command = command == null ? "" : command + " ";
        try {
            Runtime runtime = Runtime.getRuntime();
            process = runtime.exec(command + path + " " + args);
            return process;
        } catch (IOException e) {
            log.error("程序执行出错：" + e.getMessage());
            if (process != null) {
                process.destroy();
            }
            return null;
        }
    }

    public static boolean killProcessById(String processId) {
        try {
            String cmd = "taskkill /f /pid " + processId;
            if (Environment.isLinux()) {
                cmd = "kill -9 " + processId;
            }
            Runtime.getRuntime().exec(cmd);
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}
