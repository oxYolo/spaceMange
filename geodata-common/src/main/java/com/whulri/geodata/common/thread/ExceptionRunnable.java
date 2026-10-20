package com.whulri.geodata.common.thread;

/**
 * 允许抛出受检异常的 Runnable（反编译自 dev-common-1.1.8）
 */
@FunctionalInterface
public interface ExceptionRunnable {

    void run() throws Exception;
}
