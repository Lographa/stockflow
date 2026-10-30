package com.stockFlow.stockflow_api.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.stockFlow.stockflow_api.integration.support.Concurrently;
import com.stockFlow.stockflow_api.integration.support.Concurrently.Outcome;
import com.stockFlow.stockflow_api.movements.StockMovementService;
import com.stockFlow.stockflow_api.movements.dto.CreateMovementRequest;
import com.stockFlow.stockflow_api.movements.enums.MovementTypeEnum;
import com.stockFlow.stockflow_api.movements.exception.InsufficientStockException;
import com.stockFlow.stockflow_api.products.ProductService;
import com.stockFlow.stockflow_api.products.exception.ProductNotFoundException;

/**
 * QA-03 — concorrência com lock pessimista (T-04) contra MySQL real
 * ({@code SELECT ... FOR UPDATE}). Cada cenário é repetido 3 vezes para reduzir
 * falso-positivo (uma corrida "sem lock" pode passar por sorte em uma execução).
 * Todas as threads são liberadas ao mesmo tempo por um {@code CountDownLatch}.
 */
@Tag("integration")
class StockConcurrencyIntegrationTest extends AbstractMySqlIntegrationTest {

    private static final int THREADS = 30;

    @Autowired
    private StockMovementService stockMovementService;

    @Autowired
    private ProductService productService;

    @Autowired
    private PlatformTransactionManager transactionManager;

    // ------------------------------------------------------------------ helpers

    private static String kindOf(Outcome<?> outcome) {
        if (!outcome.failed()) {
            return "OK";
        }
        Throwable error = outcome.error();
        if (error instanceof InsufficientStockException) {
            return "INSUFFICIENT";
        }
        if (error instanceof ProductNotFoundException) {
            return "NOT_FOUND";
        }
        return "OTHER(" + error + ")";
    }

    private static Map<String, Long> kinds(List<? extends Outcome<?>> outcomes) {
        return outcomes.stream().collect(Collectors.groupingBy(o -> kindOf(o), Collectors.counting()));
    }

    private static Map<Integer, Long> statuses(List<Outcome<Integer>> outcomes) {
        return outcomes.stream().collect(Collectors.groupingBy(
                o -> o.failed() ? -1 : o.value(), Collectors.counting()));
    }

    private Object register(long productId, MovementTypeEnum type, int quantity) {
        return stockMovementService.register(new CreateMovementRequest(productId, type, quantity, null));
    }

    // ------------------------------------------------------------------ saídas simultâneas

    @RepeatedTest(3)
    void saidasConcorrentes_viaService_exatamenteOSaldoSucede_oRestoRecebeInsufficientStock() throws Exception {
        int stock = 10;
        long id = createProduct(stock, 0);

        List<Outcome<Object>> outcomes = Concurrently.run(THREADS,
                i -> () -> register(id, MovementTypeEnum.EXIT, 1));

        assertThat(kinds(outcomes)).isEqualTo(Map.of("OK", (long) stock, "INSUFFICIENT", (long) (THREADS - stock)));
        assertThat(quantityOf(id)).isZero();
        assertThat(sumOf(id, "EXIT")).isEqualTo(stock);
        assertThat(movementCount(id, "EXIT")).isEqualTo(stock);
        assertThat(movementCount(id)).isEqualTo(1 + stock); // ENTRY inicial + S saídas aceitas
        assertBalanceMatchesHistory(id);
    }

    @RepeatedTest(3)
    void saidasConcorrentes_viaHttp_exatamenteOSaldoRetorna201_oRestoRetorna409() throws Exception {
        int stock = 10;
        long id = createProduct(stock, 0);

        List<Outcome<Integer>> outcomes = Concurrently.run(THREADS,
                i -> () -> httpPost("/movements", movementJson(id, "EXIT", 1)).status());

        assertThat(statuses(outcomes)).isEqualTo(Map.of(201, (long) stock, 409, (long) (THREADS - stock)));
        assertThat(quantityOf(id)).isZero();
        assertThat(sumOf(id, "EXIT")).isEqualTo(stock);
        assertThat(movementCount(id)).isEqualTo(1 + stock);
        assertBalanceMatchesHistory(id);
    }

    @RepeatedTest(3)
    void saidasConcorrentes_comSaldoNaoDivisivel_nuncaFicaNegativo() throws Exception {
        // saldo 7, saídas de 2: no máximo 3 aceitas (6), saldo final 1; as demais recusadas
        long id = createProduct(7, 0);

        List<Outcome<Object>> outcomes = Concurrently.run(20,
                i -> () -> register(id, MovementTypeEnum.EXIT, 2));

        assertThat(kinds(outcomes)).isEqualTo(Map.of("OK", 3L, "INSUFFICIENT", 17L));
        assertThat(quantityOf(id)).isEqualTo(1);
        assertThat(sumOf(id, "EXIT")).isEqualTo(6);
        assertBalanceMatchesHistory(id);
    }

    // ------------------------------------------------------------------ entradas + saídas

    @RepeatedTest(3)
    void entradasESaidasSimultaneas_saldoFinalEIgualAoInicialMaisEntradasMenosSaidasAceitas() throws Exception {
        int initial = 10;
        long id = createProduct(initial, 0);
        int entryTasks = 15; // quantidades 1,2,3 repetidas → Σ = 30 (sempre aceitas)
        int exitTasks = 25; // saídas de 2 → demanda 50 > oferta máxima 40 → ao menos 5 recusadas

        List<Outcome<Object>> outcomes = Concurrently.run(entryTasks + exitTasks, i -> i < entryTasks
                ? () -> register(id, MovementTypeEnum.ENTRY, (i % 3) + 1)
                : () -> register(id, MovementTypeEnum.EXIT, 2));

        List<Outcome<Object>> entries = outcomes.subList(0, entryTasks);
        List<Outcome<Object>> exits = outcomes.subList(entryTasks, entryTasks + exitTasks);
        assertThat(kinds(entries)).isEqualTo(Map.of("OK", (long) entryTasks));

        long acceptedExits = exits.stream().filter(o -> !o.failed()).count();
        long rejectedExits = exits.stream().filter(o -> "INSUFFICIENT".equals(kindOf(o))).count();
        assertThat(acceptedExits + rejectedExits).as("saídas só podem ser aceitas ou recusadas por saldo")
                .isEqualTo(exitTasks);
        assertThat(rejectedExits).isGreaterThanOrEqualTo(5);

        int expectedEntries = 30;
        int expectedBalance = initial + expectedEntries - (int) acceptedExits * 2;
        assertThat(quantityOf(id)).isEqualTo(expectedBalance).isGreaterThanOrEqualTo(0);
        assertThat(sumOf(id, "ENTRY")).isEqualTo(initial + expectedEntries);
        assertThat(sumOf(id, "EXIT")).isEqualTo((int) acceptedExits * 2);
        assertThat(movementCount(id)).isEqualTo(1 + entryTasks + (int) acceptedExits);
        assertBalanceMatchesHistory(id);
    }

    // ------------------------------------------------------------------ PUT quantity × movimentações (D-1)

    @RepeatedTest(3)
    void putQuantityConcorrenteComMovimentacoes_viaHttp_mantemSaldoConsistenteComHistorico() throws Exception {
        long id = createProduct(50, 0);
        int[] putTargets = { 20, 30, 40, 60, 70 };
        int movementTasks = 20; // 10 ENTRY + 10 EXIT de 1

        List<Outcome<Integer>> outcomes = Concurrently.run(movementTasks + putTargets.length, i -> {
            if (i < 10) {
                return () -> httpPost("/movements", movementJson(id, "ENTRY", 1)).status();
            }
            if (i < movementTasks) {
                return () -> httpPost("/movements", movementJson(id, "EXIT", 1)).status();
            }
            int target = putTargets[i - movementTasks];
            return () -> httpPut("/product/" + id, "{\"quantity\":" + target + "}").status();
        });

        List<Outcome<Integer>> movements = outcomes.subList(0, movementTasks);
        List<Outcome<Integer>> puts = outcomes.subList(movementTasks, outcomes.size());
        assertThat(movements).allSatisfy(o -> assertThat(o.failed() ? -1 : o.value()).isIn(201, 409));
        assertThat(statuses(puts)).isEqualTo(Map.of(200, (long) putTargets.length));

        // qualquer ordem de execução: saldo == Σ ENTRY − Σ EXIT, nunca negativo (nenhum ajuste "perdido")
        assertBalanceMatchesHistory(id);
        // saldo final é um valor alcançável: último PUT ± movimentações posteriores (limite superior/inferior)
        assertThat(quantityOf(id)).isBetween(0, 70 + 10);
    }

    // ------------------------------------------------------------------ exclusão × saídas

    /**
     * Determinístico: a transação da exclusão segura o lock da linha (T-04) enquanto
     * N saídas tentam entrar — todas bloqueiam. Após o COMMIT da exclusão, o
     * {@code SELECT ... FOR UPDATE ... deleted_at IS NULL} das saídas enxerga o produto
     * excluído e NENHUMA é aceita (404).
     */
    @RepeatedTest(2)
    void exclusaoVsSaidas_deterministico_nenhumaSaidaEAceitaAposOCommitDaExclusao() throws Exception {
        long id = createProduct(50, 0);
        int exits = 8;
        ExecutorService pool = Executors.newFixedThreadPool(exits);
        List<Future<Object>> futures = new ArrayList<>();
        CountDownLatch launched = new CountDownLatch(exits);
        try {
            new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
                productService.deleteProduct(id); // trava a linha e marca deleted_at (flush no commit)
                for (int i = 0; i < exits; i++) {
                    futures.add(pool.submit(() -> {
                        launched.countDown();
                        return register(id, MovementTypeEnum.EXIT, 1);
                    }));
                }
                try {
                    assertThat(launched.await(30, TimeUnit.SECONDS)).isTrue();
                    Thread.sleep(1500); // dá tempo de todas chegarem ao SELECT ... FOR UPDATE e bloquearem
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException(ex);
                }
                // enquanto a exclusão não é confirmada, nenhuma saída pode ter concluído
                assertThat(futures).allSatisfy(f -> assertThat(f.isDone())
                        .as("saída deveria estar bloqueada pelo lock da exclusão").isFalse());
            }); // commit da exclusão

            for (Future<Object> future : futures) {
                assertThat(catchThrowable(() -> future.get(60, TimeUnit.SECONDS)))
                        .isInstanceOf(ExecutionException.class)
                        .hasCauseInstanceOf(ProductNotFoundException.class);
            }
        } finally {
            pool.shutdownNow();
        }

        assertThat(jdbc.queryForObject("SELECT deleted_at IS NOT NULL FROM products WHERE id = ?", Boolean.class, id))
                .isTrue();
        assertThat(quantityOf(id)).isEqualTo(50);
        assertThat(movementCount(id)).isEqualTo(1);
        assertBalanceMatchesHistory(id);
    }

    /**
     * Corrida real (sem sincronização artificial): uma exclusão e 30 saídas via HTTP.
     * Toda saída INICIADA depois que o DELETE retornou deve ser 404; o saldo final
     * reflete exatamente as saídas aceitas antes da exclusão.
     */
    @RepeatedTest(3)
    void exclusaoVsSaidas_corrida_viaHttp_saldoConsistente_eSaidasPosterioresRecebem404() throws Exception {
        int stock = 50;
        int exitTasks = 30;
        long id = createProduct(stock, 0);
        AtomicLong deleteReturnedAt = new AtomicLong(Long.MAX_VALUE);

        List<Outcome<Integer>> outcomes = Concurrently.run(exitTasks + 1, i -> {
            if (i == 0) {
                return () -> {
                    int statusCode = httpDelete("/product/" + id).status();
                    deleteReturnedAt.set(System.nanoTime());
                    return statusCode;
                };
            }
            return () -> httpPost("/movements", movementJson(id, "EXIT", 1)).status();
        });

        Outcome<Integer> deletion = outcomes.get(0);
        List<Outcome<Integer>> exits = outcomes.subList(1, outcomes.size());
        assertThat(deletion.failed() ? -1 : deletion.value()).isEqualTo(204);
        assertThat(exits).allSatisfy(o -> assertThat(o.failed() ? -1 : o.value()).isIn(201, 404));

        long accepted = exits.stream().filter(o -> o.value() == 201).count();
        assertThat(exits.stream()
                .filter(o -> o.startedAtNanos() > deleteReturnedAt.get())
                .map(o -> o.value()))
                .as("saídas iniciadas depois do retorno do DELETE")
                .allMatch(code -> code == 404);

        assertThat(jdbc.queryForObject("SELECT deleted_at IS NOT NULL FROM products WHERE id = ?", Boolean.class, id))
                .isTrue();
        assertThat(quantityOf(id)).isEqualTo(stock - (int) accepted);
        assertThat(sumOf(id, "EXIT")).isEqualTo((int) accepted);
        assertThat(movementCount(id)).isEqualTo(1 + (int) accepted);
        assertBalanceMatchesHistory(id);
    }
}
