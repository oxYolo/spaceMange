package com.whulri.geodata.convert.core;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 进程执行结果
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class ProcessResult {

    /** 退出码 */
    private int exitCode;

    /** 输出尾部（最多保留 80 行，供失败原因展示） */
    private List<String> tailLines;

    public String tailAsString(int maxLines) {
        if (tailLines == null || tailLines.isEmpty()) {
            return "";
        }
        int from = Math.max(0, tailLines.size() - maxLines);
        return String.join(System.lineSeparator(), tailLines.subList(from, tailLines.size()));
    }
}
