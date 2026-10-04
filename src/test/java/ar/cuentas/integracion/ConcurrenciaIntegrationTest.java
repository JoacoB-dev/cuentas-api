package ar.cuentas.integracion;

import ar.cuentas.dominio.Moneda;
import ar.cuentas.dominio.TipoCuenta;
import ar.cuentas.web.dto.CuentaResponse;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;

import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pedidos HTTP reales y en paralelo contra la app levantada en un puerto aleatorio,
 * con PostgreSQL de verdad. Acá es donde se ve si los locks y la idempotencia andan.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ConcurrenciaIntegrationTest extends BaseIntegracion {

    private static final int HILOS = 20;

    @LocalServerPort
    int puerto;

    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();

    @Test
    void veintePedidosEnParaleloNuncaDejanLaCuentaEnNegativo() throws Exception {
        // 1.000 de saldo y 20 transferencias de 100 a la vez: sólo 10 pueden pasar.
        CuentaResponse origen = crearCuenta(anaId, TipoCuenta.CA, Moneda.ARS, "1000000", null, "1000");
        String token = login("ana");
        List<Callable<HttpResponse<String>>> tareas = new ArrayList<>();
        for (int i = 0; i < HILOS; i++) {
            String clave = "paralelo-" + i;
            tareas.add(() -> post(token, clave, cuerpoTransferencia(origen.id(), brunoArs.cbu(), "100", null)));
        }

        List<HttpResponse<String>> respuestas = ejecutarALaVez(tareas);

        long ok = respuestas.stream().filter(r -> r.statusCode() == 201).count();
        long rechazadas = respuestas.stream()
                .filter(r -> r.statusCode() == 422 && r.body().contains("saldo-insuficiente")).count();
        assertThat(ok).isEqualTo(10);
        assertThat(rechazadas).isEqualTo(10);
        assertThat(saldo(origen.id())).isEqualByComparingTo("0");
        assertThat(saldo(brunoArs.id())).isEqualByComparingTo("21000");
        // El historial es coherente: ningún saldo posterior negativo y 10 débitos.
        assertThat(contar("select count(*) from movimiento where cuenta_id = ? and saldo_posterior < 0", origen.id()))
                .isZero();
        assertThat(contar("select count(*) from movimiento where cuenta_id = ? and tipo = 'DEBITO'", origen.id()))
                .isEqualTo(10);
    }

    @Test
    void transferenciasCruzadasEnParaleloNoGeneranDeadlockYConservanElTotal() throws Exception {
        // A->B y B->A a la vez: sin un orden de bloqueo fijo esto produce deadlocks en PostgreSQL.
        String tokenAna = login("ana");
        String tokenBruno = login("bruno");
        BigDecimal totalAntes = saldo(anaArs.id()).add(saldo(brunoArs.id()));
        List<Callable<HttpResponse<String>>> tareas = new ArrayList<>();
        for (int i = 0; i < HILOS; i++) {
            String clave = "cruzada-" + i;
            tareas.add(() -> post(tokenAna, clave, cuerpoTransferencia(anaArs.id(), brunoArs.cbu(), "10", null)));
            tareas.add(() -> post(tokenBruno, clave, cuerpoTransferencia(brunoArs.id(), anaArs.cbu(), "7", null)));
        }

        List<HttpResponse<String>> respuestas = ejecutarALaVez(tareas);

        assertThat(respuestas).allSatisfy(r -> assertThat(r.statusCode()).as(r.body()).isEqualTo(201));
        assertThat(saldo(anaArs.id())).isEqualByComparingTo(new BigDecimal("50000").subtract(new BigDecimal(HILOS * 3)));
        assertThat(saldo(anaArs.id()).add(saldo(brunoArs.id()))).isEqualByComparingTo(totalAntes);
    }

    @Test
    void elMismoPedidoReenviadoEnParaleloSeEjecutaUnaSolaVez() throws Exception {
        String token = login("ana");
        String cuerpo = cuerpoTransferencia(anaArs.id(), brunoArs.cbu(), "250", "Reintento");
        List<Callable<HttpResponse<String>>> tareas = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            tareas.add(() -> post(token, "misma-clave", cuerpo));
        }

        List<HttpResponse<String>> respuestas = ejecutarALaVez(tareas);

        assertThat(respuestas).allSatisfy(r -> assertThat(r.statusCode()).as(r.body()).isEqualTo(201));
        assertThat(respuestas.stream().map(r -> leerId(r.body())).distinct()).hasSize(1);
        assertThat(respuestas.stream()
                .filter(r -> r.headers().firstValue("Idempotent-Replayed").orElse("").equals("false"))).hasSize(1);
        assertThat(contar("select count(*) from transferencia")).isEqualTo(1);
        assertThat(saldo(anaArs.id())).isEqualByComparingTo("49750");
    }

    // ---- helpers ----

    private List<HttpResponse<String>> ejecutarALaVez(List<Callable<HttpResponse<String>>> tareas) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(tareas.size());
        CountDownLatch largada = new CountDownLatch(1);
        try {
            List<Future<HttpResponse<String>>> futuros = new ArrayList<>();
            for (Callable<HttpResponse<String>> t : tareas) {
                futuros.add(pool.submit(() -> {
                    largada.await();
                    return t.call();
                }));
            }
            largada.countDown(); // todas salen juntas
            List<HttpResponse<String>> resultado = new ArrayList<>();
            for (Future<HttpResponse<String>> f : futuros) {
                resultado.add(f.get(60, TimeUnit.SECONDS));
            }
            return resultado;
        } finally {
            pool.shutdownNow();
        }
    }

    private HttpResponse<String> post(String token, String clave, String cuerpo) throws Exception {
        HttpRequest req = HttpRequest.newBuilder(URI.create("http://localhost:" + puerto + "/api/transferencias"))
                .header("Authorization", "Bearer " + token)
                .header("Content-Type", "application/json")
                .header("Idempotency-Key", clave)
                .timeout(Duration.ofSeconds(30))
                .POST(HttpRequest.BodyPublishers.ofString(cuerpo))
                .build();
        return http.send(req, HttpResponse.BodyHandlers.ofString());
    }

    private String login(String usuario) throws Exception {
        HttpRequest req = HttpRequest.newBuilder(URI.create("http://localhost:" + puerto + "/api/auth/login"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(
                        "{\"username\":\"" + usuario + "\",\"password\":\"" + usuario + "123\"}"))
                .build();
        return leer(http.send(req, HttpResponse.BodyHandlers.ofString()).body()).get("token").asText();
    }

    private long leerId(String cuerpo) {
        try {
            return leer(cuerpo).get("id").asLong();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
