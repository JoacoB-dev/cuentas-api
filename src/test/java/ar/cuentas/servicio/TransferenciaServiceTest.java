package ar.cuentas.servicio;

import ar.cuentas.dominio.Moneda;
import ar.cuentas.dominio.ReglaNegocioException;
import ar.cuentas.dominio.Rol;
import ar.cuentas.dominio.Transferencia;
import ar.cuentas.error.SolicitudInvalidaException;
import ar.cuentas.repositorio.CuentaRepository;
import ar.cuentas.repositorio.TransferenciaRepository;
import ar.cuentas.seguridad.UsuarioActual;
import ar.cuentas.web.dto.TransferenciaRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TransferenciaServiceTest {

    private static final String CBU_DESTINO = "9990001800000000010025";

    @Mock
    EjecutorDeTransferencias ejecutor;
    @Mock
    TransferenciaRepository transferencias;
    @Mock
    CuentaRepository cuentas;

    TransferenciaService servicio;
    final UsuarioActual ana = new UsuarioActual("ana", Rol.CLIENTE, 1L);
    final TransferenciaRequest pedido = new TransferenciaRequest(1L, CBU_DESTINO, new BigDecimal("150"), "Cena");

    @BeforeEach
    void setUp() {
        servicio = new TransferenciaService(ejecutor, transferencias, cuentas);
    }

    @Test
    void primeraVezEjecutaLaTransferencia() {
        when(transferencias.findByUsuarioAndIdempotencyKey("ana", "k1")).thenReturn(Optional.empty());
        when(ejecutor.ejecutar(any(), any(), any(), any(), any(), any(), any())).thenReturn(transferencia(hashDe(pedido)));

        TransferenciaService.Resultado r = servicio.transferir(pedido, "k1", ana);

        assertThat(r.repetida()).isFalse();
        assertThat(r.transferencia().id()).isEqualTo(7L);
    }

    @Test
    void conLaMismaClaveYLosMismosDatosDevuelveLaOriginalSinTransferirDeNuevo() {
        when(transferencias.findByUsuarioAndIdempotencyKey("ana", "k1"))
                .thenReturn(Optional.of(transferencia(hashDe(pedido))));

        TransferenciaService.Resultado r = servicio.transferir(pedido, "k1", ana);

        assertThat(r.repetida()).isTrue();
        assertThat(r.transferencia().id()).isEqualTo(7L);
        verifyNoInteractions(ejecutor);
    }

    @Test
    void conLaMismaClavePeroOtrosDatosRechaza() {
        when(transferencias.findByUsuarioAndIdempotencyKey("ana", "k1"))
                .thenReturn(Optional.of(transferencia(hashDe(pedido))));
        TransferenciaRequest otroImporte = new TransferenciaRequest(1L, CBU_DESTINO, new BigDecimal("151"), "Cena");

        assertThatThrownBy(() -> servicio.transferir(otroImporte, "k1", ana))
                .isInstanceOf(ReglaNegocioException.class)
                .extracting("codigo").isEqualTo("clave-idempotencia-reutilizada");
        verifyNoInteractions(ejecutor);
    }

    @Test
    void siPierdeLaCarreraContraOtroPedidoConLaMismaClaveDevuelveElDelGanador() {
        when(transferencias.findByUsuarioAndIdempotencyKey("ana", "k1"))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.of(transferencia(hashDe(pedido))));
        when(ejecutor.ejecutar(any(), any(), any(), any(), any(), any(), any()))
                .thenThrow(new DataIntegrityViolationException("uq_transferencia_idempotencia"));

        TransferenciaService.Resultado r = servicio.transferir(pedido, "k1", ana);

        assertThat(r.repetida()).isTrue();
        assertThat(r.transferencia().id()).isEqualTo(7L);
    }

    @Test
    void claveVaciaOMuyLargaEsUnaSolicitudInvalida() {
        assertThatThrownBy(() -> servicio.transferir(pedido, "  ", ana)).isInstanceOf(SolicitudInvalidaException.class);
        assertThatThrownBy(() -> servicio.transferir(pedido, "x".repeat(101), ana))
                .isInstanceOf(SolicitudInvalidaException.class);
        verify(ejecutor, never()).ejecutar(any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void elHashNoDependeDeLaEscalaDelImporte() {
        // 150 y 150.00 son el mismo pedido: el servicio normaliza a 2 decimales antes de calcular el hash.
        String h1 = TransferenciaService.hashDeSolicitud(1L, CBU_DESTINO, new BigDecimal("150").setScale(2), "Cena");
        String h2 = TransferenciaService.hashDeSolicitud(1L, CBU_DESTINO, new BigDecimal("150.00"), "Cena");
        String h3 = TransferenciaService.hashDeSolicitud(1L, CBU_DESTINO, new BigDecimal("150.00"), "Otra cosa");
        assertThat(h1).isEqualTo(h2).hasSize(64);
        assertThat(h3).isNotEqualTo(h1);
    }

    private static String hashDe(TransferenciaRequest r) {
        return TransferenciaService.hashDeSolicitud(r.cuentaOrigenId(), r.cbuDestino(),
                r.importe().setScale(2), r.concepto());
    }

    private static Transferencia transferencia(String hash) {
        Transferencia t = new Transferencia(1L, 3L, new BigDecimal("150.00"), Moneda.ARS, "Cena", "ana", "k1",
                hash, Instant.parse("2026-10-04T18:00:00Z"));
        ReflectionTestUtils.setField(t, "id", 7L);
        return t;
    }
}
