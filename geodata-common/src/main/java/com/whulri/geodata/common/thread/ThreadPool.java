package com.whulri.geodata.common.thread;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.FutureTask;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * 静态共享线程池（反编译自 dev-common-1.1.8，签名与原实现保持一致）
 */
public class ThreadPool {

    private static final Logger log = LoggerFactory.getLogger(ThreadPool.class);

    private static final int CORE_POOL_SIZE = 100;

    private static final int MAX_POOL_SIZE = 500;

    private static final long KEEP_ALIVE_TIME = 300L;

    private static ExecutorService executor = new ThreadPoolExecutor(
            CORE_POOL_SIZE, MAX_POOL_SIZE, KEEP_ALIVE_TIME, TimeUnit.SECONDS, new LinkedBlockingQueue<>());

    private ThreadPool() {
    }

    public static void execute(Runnable runnable) {
        executor.execute(runnable);
    }

    public static void execute(ExceptionRunnable runnable) {
        executor.execute(() -> {
            try {
                runnable.run();
            } catch (Throwable e) {
                ExceptionProcesser.run(e);
            }
        });
    }

    public static void executeCall(ExceptionRunnable runnable) {
        ThreadPool.execute(runnable);
    }

    public static void executeCall(long waitTime, ExceptionRunnable runnable) {
        ThreadPool.execute((ExceptionRunnable) () -> {
            Thread.sleep(waitTime);
            runnable.run();
        });
    }

    public static void executeSyn(int timeout, FutureTask<?> futureTask) {
        executor.execute(futureTask);
        try {
            futureTask.get(timeout, TimeUnit.MILLISECONDS);
        } catch (InterruptedException | ExecutionException | TimeoutException e) {
            log.error(e.getMessage());
        }
    }

    public static void executeSyn(FutureTask<?> futureTask) {
        ThreadPool.executeSyn(3000, futureTask);
    }

    public static void execute(Runnable runnable, int count) {
        try {
            Runnable[] runnables = new Runnable[count];
            runnables[0] = runnable;
            Class<?> runnableClass = runnable.getClass();
            for (int i = 1; i < runnables.length; i++) {
                runnables[i] = (Runnable) runnableClass.getDeclaredConstructor().newInstance();
            }
            for (Runnable item : runnables) {
                ThreadPool.execute(item);
            }
        } catch (Throwable e) {
            ExceptionProcesser.run(e);
        }
    }

    public static void execute(ExceptionRunnable runnable, int count) {
        try {
            ExceptionRunnable[] runnables = new ExceptionRunnable[count];
            runnables[0] = runnable;
            Class<?> runnableClass = runnable.getClass();
            for (int i = 1; i < runnables.length; i++) {
                runnables[i] = (ExceptionRunnable) runnableClass.getDeclaredConstructor().newInstance();
            }
            for (ExceptionRunnable item : runnables) {
                ThreadPool.execute(item);
            }
        } catch (Throwable e) {
            ExceptionProcesser.run(e);
        }
    }

    public static void waitAllThreadFinish() {
        try {
            executor.awaitTermination(3000L, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            log.error(e.getMessage());
        }
    }

    public static void shutdown() {
        executor.shutdown();
    }
}
