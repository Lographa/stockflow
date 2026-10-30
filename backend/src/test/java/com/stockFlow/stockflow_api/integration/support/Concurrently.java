package com.stockFlow.stockflow_api.integration.support;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.function.IntFunction;

/**
 * Dispara N tarefas ao mesmo tempo (uma thread por tarefa, liberadas por um
 * {@link CountDownLatch} de largada). Exceções de cada tarefa são devolvidas
 * como resultado ({@link Outcome}) — o teste decide o que é aceitável.
 */
public final class Concurrently {

    private Concurrently() {
    }

    /** Resultado de uma tarefa: valor ou erro, com o instante de início (System.nanoTime). */
    public record Outcome<T>(int index, long startedAtNanos, T value, Throwable error) {
        public boolean failed() {
            return error != null;
        }
    }

    public static <T> List<Outcome<T>> run(int tasks, IntFunction<Callable<T>> factory) {
        ExecutorService pool = Executors.newFixedThreadPool(tasks);
        try {
            CountDownLatch ready = new CountDownLatch(tasks);
            CountDownLatch start = new CountDownLatch(1);
            List<Future<Outcome<T>>> futures = new ArrayList<>();
            for (int i = 0; i < tasks; i++) {
                final int index = i;
                final Callable<T> task = factory.apply(i);
                futures.add(pool.submit(() -> {
                    ready.countDown();
                    start.await();
                    long startedAt = System.nanoTime();
                    try {
                        return new Outcome<>(index, startedAt, task.call(), null);
                    } catch (Throwable ex) {
                        return new Outcome<>(index, startedAt, null, ex);
                    }
                }));
            }
            if (!ready.await(30, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Threads não ficaram prontas a tempo");
            }
            start.countDown();
            return collect(futures);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(ex);
        } finally {
            pool.shutdownNow();
        }
    }

    /** Aguarda futures já submetidas por um cenário customizado. */
    public static <T> List<T> await(List<Future<T>> futures, long timeoutSeconds) {
        List<T> results = new ArrayList<>();
        try {
            for (Future<T> future : futures) {
                results.add(future.get(timeoutSeconds, TimeUnit.SECONDS));
            }
        } catch (Exception ex) {
            throw new IllegalStateException("Tarefa concorrente não concluiu: " + ex, ex);
        }
        return results;
    }

    private static <T> List<Outcome<T>> collect(List<Future<Outcome<T>>> futures) {
        return await(futures, 120);
    }
}
