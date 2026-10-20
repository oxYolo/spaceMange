package com.whulri.geodata.convert.core;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.TimeUnit;

/**
 * 跨平台子进程执行器。
 *
 * <ul>
 *   <li>参数列表方式启动（ProcessBuilder），不经 shell，Windows/Linux 行为一致；</li>
 *   <li>合并 stderr 到 stdout，统一按工具配置字符集解码（Windows 下可配 GBK）；</li>
 *   <li>逐行回调进度；超时或取消时销毁整个进程树（descendants），不留孤儿进程；</li>
 *   <li>输出尾部留存 80 行用于失败原因展示。</li>
 * </ul>
 */
@Slf4j
@Component
public class ProcessExecutor {

    private static final int TAIL_LIMIT = 80;

    public ProcessResult run(List<String> command, Path workDir, ConvertToolConfig tool,
                             ProgressListener listener) {
        if (command == null || command.isEmpty()) {
            throw new ConvertException("命令为空，无法执行转换");
        }
        try {
            Files.createDirectories(workDir);
        } catch (IOException e) {
            throw new ConvertException("创建工作目录失败：" + workDir, e);
        }

        log.info("执行转换命令: {} (workDir={})", String.join(" ", command), workDir);

        ProcessBuilder pb = new ProcessBuilder(command);
        pb.directory(workDir.toFile());
        pb.redirectErrorStream(true);

        Process process;
        try {
            process = pb.start();
        } catch (IOException e) {
            throw new ConvertException("启动转换进程失败：" + e.getMessage(), e);
        }

        Charset charset = tool.charset();
        ConcurrentLinkedQueue<String> tail = new ConcurrentLinkedQueue<>();
        Thread reader = new Thread(() -> consume(process.getInputStream(), charset, tail, listener),
                "convert-output-reader");
        reader.setDaemon(true);
        reader.start();

        boolean finished;
        try {
            finished = process.waitFor(tool.timeout().toMinutes(), TimeUnit.MINUTES);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            destroyTree(process);
            throw new ConvertException("转换等待被中断", e);
        }

        if (!finished) {
            destroyTree(process);
            throw new ConvertException(String.format(
                    "转换超时（>%d 分钟），已终止进程树", tool.timeout().toMinutes()));
        }

        try {
            reader.join(3000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        int exit = process.exitValue();
        List<String> tailList = new ArrayList<>(tail);
        if (exit != 0) {
            throw new ConvertException(String.format(
                    "转换进程退出码 %d，输出尾部：%n%s", exit, tailAsString(tailList, 20)));
        }
        return new ProcessResult(exit, tailList);
    }

    private void consume(InputStream in, Charset charset,
                         ConcurrentLinkedQueue<String> tail, ProgressListener listener) {
        try (BufferedReader br = new BufferedReader(new InputStreamReader(in, charset))) {
            String line;
            while ((line = br.readLine()) != null) {
                pushTail(tail, line);
                if (listener != null) {
                    try {
                        // 百分比解析由监听器实现方负责（可调用 ConvertCommandBuilder.parseProgress）
                        listener.onLine(line, null);
                    } catch (Exception e) {
                        log.debug("进度监听器异常: {}", e.getMessage());
                    }
                }
            }
        } catch (IOException ignored) {
            // 进程被销毁时流关闭属正常现象
        }
    }

    private void pushTail(ConcurrentLinkedQueue<String> tail, String line) {
        tail.add(line);
        while (tail.size() > TAIL_LIMIT) {
            tail.poll();
        }
    }

    private String tailAsString(List<String> tail, int maxLines) {
        if (tail.isEmpty()) {
            return "(无输出)";
        }
        int from = Math.max(0, tail.size() - maxLines);
        return String.join(System.lineSeparator(), tail.subList(from, tail.size()));
    }

    /** 销毁整棵进程树（Windows/Linux 通用：JDK9+ ProcessHandle） */
    private void destroyTree(Process process) {
        try {
            process.toHandle().descendants().forEach(ProcessHandle::destroyForcibly);
        } catch (Exception e) {
            log.debug("枚举子进程失败（忽略，继续销毁主进程）: {}", e.getMessage());
        }
        process.destroy();
        try {
            if (!process.waitFor(5, TimeUnit.SECONDS)) {
                process.destroyForcibly();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            process.destroyForcibly();
        }
    }
}
