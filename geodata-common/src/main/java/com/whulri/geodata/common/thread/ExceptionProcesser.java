package com.whulri.geodata.common.thread;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 异常处理器（反编译自 dev-common-1.1.8）
 */
public class ExceptionProcesser {

    private static final Logger log = LoggerFactory.getLogger(ExceptionProcesser.class);

    private ExceptionProcesser() {
    }

    public static void run(Throwable throwable) {
        StringBuilder buff = new StringBuilder();
        buff.append("程序异常");
        buff.append(ExceptionProcesser.getInfo(throwable));
        log.error(buff.toString());
    }

    public static String getInfo(Throwable throwable) {
        Throwable cause = throwable;
        Throwable childCause;
        while ((childCause = cause.getCause()) != null) {
            cause = childCause;
        }
        StackTraceElement[] elements = cause.getStackTrace();
        StringBuilder buff = new StringBuilder();
        buff.append(String.format("%n           [异常详情：%s]", cause.getMessage()));
        buff.append("\n           [堆栈追踪：");
        int iLast = elements.length - 1;
        for (int i = 0; i <= iLast; i++) {
            StackTraceElement element = elements[i];
            buff.append(String.format("%n           %s(%s行)", element.getClassName(), "" + element.getLineNumber()));
            if (i == iLast) {
                continue;
            }
            buff.append(", ");
        }
        buff.append("]");
        return buff.toString();
    }
}
