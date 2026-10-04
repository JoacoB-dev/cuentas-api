package ar.cuentas.integracion;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class MovimientoIntegrationTest extends BaseMockMvc {

    @Test
    void historialPaginadoDelMasNuevoAlMasViejo() throws Exception {
        for (int i = 1; i <= 4; i++) {
            cuentaService.depositar(anaArs.id(), new BigDecimal(i * 10), "Depósito " + i);
        }
        // 5 movimientos en total (incluye el depósito inicial)
        getCon("ana", "/api/cuentas/{id}/movimientos?pagina=0&tamanio=2", anaArs.id())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.contenido", hasSize(2)))
                .andExpect(jsonPath("$.totalElementos").value(5))
                .andExpect(jsonPath("$.totalPaginas").value(3))
                .andExpect(jsonPath("$.contenido[0].descripcion").value("Depósito 4"))
                .andExpect(jsonPath("$.contenido[0].saldoPosterior").value(50100));
        getCon("ana", "/api/cuentas/{id}/movimientos?pagina=2&tamanio=2", anaArs.id())
                .andExpect(jsonPath("$.contenido", hasSize(1)))
                .andExpect(jsonPath("$.contenido[0].descripcion").value("Depósito inicial"));
    }

    @Test
    void filtraPorFechasEnHoraArgentina() throws Exception {
        // Se mueve el depósito inicial al 15/03/2026 a las 23:30 de Argentina (= 16/03 02:30 UTC).
        jdbc.update("update movimiento set fecha = ? where cuenta_id = ?",
                Timestamp.from(Instant.parse("2026-03-16T02:30:00Z")), anaArs.id());

        getCon("ana", "/api/cuentas/{id}/movimientos?desde=2026-03-15&hasta=2026-03-15", anaArs.id())
                .andExpect(jsonPath("$.totalElementos").value(1));
        getCon("ana", "/api/cuentas/{id}/movimientos?desde=2026-03-16&hasta=2026-03-31", anaArs.id())
                .andExpect(jsonPath("$.totalElementos").value(0));
    }

    @Test
    void extractoCalculaSaldoInicialTotalesYSaldoFinal() throws Exception {
        // Movimiento viejo (fuera del período): define el saldo inicial.
        jdbc.update("update movimiento set fecha = ? where cuenta_id = ?",
                Timestamp.from(Instant.parse("2026-01-10T15:00:00Z")), anaArs.id());
        cuentaService.depositar(anaArs.id(), new BigDecimal("2500"), "Sueldo");
        transferir("ana", CLAVE, cuerpoTransferencia(anaArs.id(), brunoArs.cbu(), "700.25", "Luz"))
                .andExpect(status().isCreated());

        getCon("ana", "/api/cuentas/{id}/extracto", anaArs.id())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.saldoInicial").value(50000))
                .andExpect(jsonPath("$.totalCreditos").value(2500))
                .andExpect(jsonPath("$.totalDebitos").value(700.25))
                .andExpect(jsonPath("$.saldoFinal").value(51799.75))
                .andExpect(jsonPath("$.movimientos", hasSize(2)))
                .andExpect(jsonPath("$.movimientos[0].descripcion").value("Sueldo"))
                .andExpect(jsonPath("$.titular").value("Ana Gómez"));
    }

    @Test
    void rangoDeFechasInvalidoDevuelve400() throws Exception {
        getCon("ana", "/api/cuentas/{id}/movimientos?desde=2026-05-10&hasta=2026-05-01", anaArs.id())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("solicitud-invalida"));
        getCon("ana", "/api/cuentas/{id}/movimientos?desde=10-05-2026", anaArs.id())
                .andExpect(status().isBadRequest());
        getCon("ana", "/api/cuentas/{id}/movimientos?tamanio=500", anaArs.id())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errores[0].mensaje").value("El tamaño máximo es 100"));
    }
}
