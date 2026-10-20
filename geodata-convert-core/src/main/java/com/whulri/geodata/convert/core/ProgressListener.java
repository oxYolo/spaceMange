package com.whulri.geodata.convert.core;

/**
 * 子进程输出行监听器（进度解析 / 日志采集）。
 */
@FunctionalInterface
public interface ProgressListener {

    /**
     * @param line    子进程输出的一行（已按配置字符集解码）
     * @param percent 解析出的进度（0-100），无法解析时为 null
     */
    void onLine(String line, Integer percent);
}
