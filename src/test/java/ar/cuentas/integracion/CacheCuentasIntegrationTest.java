package ar.cuentas.integracion;

import ar.cuentas.cache.CacheConfig;
import ar.cuentas.dominio.EstadoCuenta;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Caché del listado de cuentas en un Redis real: que se llene, que se use y,
 * sobre todo, que se invalide cuando cambia el saldo o el estado de una cuenta.
 */
class CacheCuentasIntegrationTest extends BaseMockMvc {

    private static final String CLAVE_ANA = "cuentas-api:" + CacheConfig.CUENTAS_POR_CLIENTE + "::";

    @Autowired
    CacheManager cacheManager;

    private Cache cache() {
        return cacheManager.getCache(CacheConfig.CUENTAS_POR_CLIENTE);
    }

    private boolean enRedis(Long clienteId) {
        return Boolean.TRUE.equals(redis.hasKey(CLAVE_ANA + clienteId));
    }

    @Test
    void elListadoDelClienteQuedaEnRedisComoJsonConTtl() throws Exception {
        assertThat(enRedis(anaId)).isFalse();

        getCon("ana", "/api/cuentas").andExpect(status().isOk());

        assertThat(enRedis(anaId)).isTrue();
        String json = redis.opsForValue().get(CLAVE_ANA + anaId);
        assertThat(json).contains("\"cbu\":\"" + anaArs.cbu() + "\"").doesNotContain("java.util");
        Long ttl = redis.getExpire(CLAVE_ANA + anaId);
        assertThat(ttl).isPositive().isLessThanOrEqualTo(300);
    }

    @Test
    void laSegundaConsultaSaleDelCacheSinIrALaBase() throws Exception {
        getCon("ana", "/api/cuentas").andExpect(status().isOk());
        // Se cambia el saldo "por atrás" (sin JPA, así no se invalida): si la respuesta
        // sigue mostrando el saldo viejo, es que salió de Redis. Es justamente el riesgo
        // de datos viejos que se explica en el README.
        jdbc.update("update cuenta set saldo = 1 where id = ?", anaArs.id());

        getCon("ana", "/api/cuentas")
                .andExpect(jsonPath("$[0].saldo").value(50000));
    }

    @Test
    void unaTransferenciaInvalidaElCacheDeLosDosClientes() throws Exception {
        getCon("ana", "/api/cuentas").andExpect(jsonPath("$[0].saldo").value(50000));
        getCon("bruno", "/api/cuentas").andExpect(jsonPath("$[0].saldo").value(20000));
        assertThat(enRedis(anaId)).isTrue();
        assertThat(enRedis(brunoId)).isTrue();

        transferir("ana", CLAVE, cuerpoTransferencia(anaArs.id(), brunoArs.cbu(), "1500", "Cena"))
                .andExpect(status().isCreated());

        assertThat(enRedis(anaId)).isFalse();
        assertThat(enRedis(brunoId)).isFalse();
        getCon("ana", "/api/cuentas").andExpect(jsonPath("$[0].saldo").value(48500));
        getCon("bruno", "/api/cuentas").andExpect(jsonPath("$[0].saldo").value(21500));
    }

    @Test
    void unaTransferenciaRechazadaNoInvalidaNada() throws Exception {
        getCon("ana", "/api/cuentas").andExpect(status().isOk());

        transferir("ana", CLAVE, cuerpoTransferencia(anaArs.id(), brunoArs.cbu(), "999999", null))
                .andExpect(status().isUnprocessableEntity());

        // La transacción se deshizo: el listado cacheado sigue siendo correcto.
        assertThat(enRedis(anaId)).isTrue();
    }

    @Test
    void depositoYBloqueoDelOperadorInvalidanElCache() throws Exception {
        getCon("ana", "/api/cuentas").andExpect(status().isOk());
        assertThat(enRedis(anaId)).isTrue();

        postCon("operador", "/api/cuentas/{id}/depositos".replace("{id}", anaArs.id().toString()),
                "{\"importe\": 250.50}").andExpect(status().isCreated());
        assertThat(enRedis(anaId)).isFalse();
        getCon("ana", "/api/cuentas").andExpect(jsonPath("$[0].saldo").value(50250.50));

        cuentaService.cambiarEstado(anaArs.id(), EstadoCuenta.BLOQUEADA);
        assertThat(enRedis(anaId)).isFalse();
        getCon("ana", "/api/cuentas").andExpect(jsonPath("$[0].estado").value("BLOQUEADA"));
    }

    @Test
    void abrirUnaCuentaNuevaLaMuestraEnElListado() throws Exception {
        getCon("ana", "/api/cuentas").andExpect(jsonPath("$.length()").value(2));

        postCon("operador", "/api/cuentas", "{\"clienteId\":" + anaId
                + ",\"tipo\":\"CA\",\"moneda\":\"ARS\",\"limiteDiario\":1000}").andExpect(status().isCreated());

        getCon("ana", "/api/cuentas").andExpect(jsonPath("$.length()").value(3));
    }

    @Test
    void elOperadorFiltrandoPorClienteUsaElMismoCache() throws Exception {
        getCon("operador", "/api/cuentas?clienteId=" + brunoId).andExpect(jsonPath("$.length()").value(1));
        assertThat(cache().get(brunoId)).isNotNull();
        // El listado completo del operador no se cachea.
        getCon("operador", "/api/cuentas").andExpect(jsonPath("$.length()").value(3));
        assertThat(redis.keys("cuentas-api:*")).hasSize(1);
    }

    @Test
    void elSaldoCacheadoNoAfectaLasReglasDeLaTransferencia() throws Exception {
        // El caché muestra 50.000, pero en la base Ana tiene 100: la transferencia lee la base con lock.
        getCon("ana", "/api/cuentas").andExpect(jsonPath("$[0].saldo").value(50000));
        jdbc.update("update cuenta set saldo = 100 where id = ?", anaArs.id());

        transferir("ana", CLAVE, cuerpoTransferencia(anaArs.id(), brunoArs.cbu(), "5000", null))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.codigo").value("saldo-insuficiente"));
        assertThat(saldo(anaArs.id())).isEqualByComparingTo(new BigDecimal("100"));
    }
}
