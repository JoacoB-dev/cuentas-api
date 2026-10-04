package ar.cuentas.servicio;

import ar.cuentas.dominio.Cbu;
import ar.cuentas.dominio.Cliente;
import ar.cuentas.dominio.Cuenta;
import ar.cuentas.dominio.Moneda;
import ar.cuentas.dominio.Movimiento;
import ar.cuentas.dominio.ReglaNegocioException;
import ar.cuentas.dominio.Rol;
import ar.cuentas.dominio.TipoCuenta;
import ar.cuentas.dominio.TipoMovimiento;
import ar.cuentas.dominio.Transferencia;
import ar.cuentas.error.NoEncontradoException;
import ar.cuentas.repositorio.CuentaRepository;
import ar.cuentas.repositorio.MovimientoRepository;
import ar.cuentas.repositorio.TransferenciaRepository;
import ar.cuentas.seguridad.UsuarioActual;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EjecutorDeTransferenciasTest {

    private static final ZoneId AR = ZoneId.of("America/Argentina/Buenos_Aires");
    // 4 de octubre de 2026, 15:00 hora argentina
    private static final Clock RELOJ = Clock.fixed(Instant.parse("2026-10-04T18:00:00Z"), AR);

    @Mock
    CuentaRepository cuentas;
    @Mock
    TransferenciaRepository transferencias;
    @Mock
    MovimientoRepository movimientos;

    EjecutorDeTransferencias ejecutor;

    Cliente ana;
    Cliente bruno;
    UsuarioActual usuarioAna;

    @BeforeEach
    void setUp() {
        ejecutor = new EjecutorDeTransferencias(cuentas, transferencias, movimientos, RELOJ);
        ana = cliente(1L, "Ana");
        bruno = cliente(2L, "Bruno");
        usuarioAna = new UsuarioActual("ana", Rol.CLIENTE, 1L);
        lenient().when(transferencias.saveAndFlush(any())).thenAnswer(inv -> {
            Transferencia t = inv.getArgument(0);
            ReflectionTestUtils.setField(t, "id", 99L);
            return t;
        });
        lenient().when(transferencias.totalEnviadoDesde(any(), any())).thenReturn(BigDecimal.ZERO);
        lenient().when(transferencias.findByUsuarioAndIdempotencyKey(anyString(), anyString()))
                .thenReturn(Optional.empty());
    }

    @Test
    void transfiereYRegistraUnMovimientoEnCadaCuenta() {
        Cuenta origen = cuenta(5L, ana, Moneda.ARS, "1000", "5000");
        Cuenta destino = cuenta(2L, bruno, Moneda.ARS, "0", "5000");
        preparar(origen, destino);

        Transferencia t = ejecutor.ejecutar(5L, destino.getCbu(), new BigDecimal("300.00"), "Expensas",
                usuarioAna, "k1", "hash");

        assertThat(t.getId()).isEqualTo(99L);
        assertThat(origen.getSaldo()).isEqualByComparingTo("700");
        assertThat(destino.getSaldo()).isEqualByComparingTo("300");
        ArgumentCaptor<Movimiento> mov = ArgumentCaptor.forClass(Movimiento.class);
        verify(movimientos, times(2)).save(mov.capture());
        List<Movimiento> lista = mov.getAllValues();
        assertThat(lista).extracting(Movimiento::getTipo).containsExactly(TipoMovimiento.DEBITO, TipoMovimiento.CREDITO);
        assertThat(lista).extracting(Movimiento::getCuentaId).containsExactly(5L, 2L);
        assertThat(lista.get(0).getSaldoPosterior()).isEqualByComparingTo("700");
        assertThat(lista.get(1).getSaldoPosterior()).isEqualByComparingTo("300");
    }

    @Test
    void bloqueaSiempreLaCuentaDeIdMenorPrimero() {
        Cuenta origen = cuenta(5L, ana, Moneda.ARS, "1000", "5000");
        Cuenta destino = cuenta(2L, bruno, Moneda.ARS, "0", "5000");
        preparar(origen, destino);

        ejecutor.ejecutar(5L, destino.getCbu(), BigDecimal.TEN, null, usuarioAna, "k1", "hash");

        InOrder orden = inOrder(cuentas);
        orden.verify(cuentas).buscarParaActualizar(2L);
        orden.verify(cuentas).buscarParaActualizar(5L);
    }

    @Test
    void rechazaMonedasDistintasSinTocarSaldos() {
        Cuenta origen = cuenta(1L, ana, Moneda.ARS, "1000", "5000");
        Cuenta destino = cuenta(2L, bruno, Moneda.USD, "0", "5000");
        preparar(origen, destino);

        assertThatThrownBy(() -> ejecutor.ejecutar(1L, destino.getCbu(), BigDecimal.TEN, null, usuarioAna, "k", "h"))
                .isInstanceOf(ReglaNegocioException.class)
                .extracting("codigo").isEqualTo("monedas-distintas");
        assertThat(origen.getSaldo()).isEqualByComparingTo("1000");
        verify(transferencias, never()).saveAndFlush(any());
    }

    @Test
    void rechazaSiSuperaElLimiteDiarioContandoLoYaEnviadoHoy() {
        Cuenta origen = cuenta(1L, ana, Moneda.ARS, "10000", "1000");
        Cuenta destino = cuenta(2L, bruno, Moneda.ARS, "0", "1000");
        preparar(origen, destino);
        Instant inicioDelDiaArgentino = Instant.parse("2026-10-04T03:00:00Z");
        when(transferencias.totalEnviadoDesde(1L, inicioDelDiaArgentino)).thenReturn(new BigDecimal("800"));

        assertThatThrownBy(() -> ejecutor.ejecutar(1L, destino.getCbu(), new BigDecimal("200.01"), null,
                usuarioAna, "k", "h"))
                .isInstanceOf(ReglaNegocioException.class)
                .hasMessageContaining("Hoy todavía podés transferir 200.00")
                .extracting("codigo").isEqualTo("limite-diario-excedido");
    }

    @Test
    void rechazaTransferirALaMismaCuenta() {
        Cuenta origen = cuenta(1L, ana, Moneda.ARS, "1000", "5000");
        when(cuentas.buscarIdPorCbu(origen.getCbu())).thenReturn(Optional.of(1L));

        assertThatThrownBy(() -> ejecutor.ejecutar(1L, origen.getCbu(), BigDecimal.TEN, null, usuarioAna, "k", "h"))
                .extracting("codigo").isEqualTo("misma-cuenta");
        verify(cuentas, never()).buscarParaActualizar(any());
    }

    @Test
    void unClienteNoPuedeTransferirDesdeUnaCuentaAjena() {
        Cuenta origen = cuenta(1L, bruno, Moneda.ARS, "1000", "5000");
        Cuenta destino = cuenta(2L, ana, Moneda.ARS, "0", "5000");
        preparar(origen, destino);

        assertThatThrownBy(() -> ejecutor.ejecutar(1L, destino.getCbu(), BigDecimal.TEN, null, usuarioAna, "k", "h"))
                .isInstanceOf(NoEncontradoException.class);
        assertThat(origen.getSaldo()).isEqualByComparingTo("1000");
    }

    @Test
    void siLaClaveYaSeConfirmoMientrasEsperabaElLockNoVuelveATransferir() {
        Cuenta origen = cuenta(1L, ana, Moneda.ARS, "1000", "5000");
        Cuenta destino = cuenta(2L, bruno, Moneda.ARS, "0", "5000");
        preparar(origen, destino);
        when(transferencias.findByUsuarioAndIdempotencyKey("ana", "k"))
                .thenReturn(Optional.of(org.mockito.Mockito.mock(Transferencia.class)));

        assertThatThrownBy(() -> ejecutor.ejecutar(1L, destino.getCbu(), BigDecimal.TEN, null, usuarioAna, "k", "h"))
                .isInstanceOf(EjecutorDeTransferencias.YaProcesadaException.class);
        assertThat(origen.getSaldo()).isEqualByComparingTo("1000");
    }

    // ---- helpers ----

    private void preparar(Cuenta origen, Cuenta destino) {
        when(cuentas.buscarIdPorCbu(eq(destino.getCbu()))).thenReturn(Optional.of(destino.getId()));
        when(cuentas.buscarParaActualizar(origen.getId())).thenReturn(Optional.of(origen));
        when(cuentas.buscarParaActualizar(destino.getId())).thenReturn(Optional.of(destino));
    }

    private static Cliente cliente(Long id, String nombre) {
        Cliente c = new Cliente(nombre, "Test", "1000000" + id, nombre + "@example.com");
        ReflectionTestUtils.setField(c, "id", id);
        return c;
    }

    private static Cuenta cuenta(Long id, Cliente titular, Moneda moneda, String saldo, String limite) {
        Cuenta c = new Cuenta(titular, Cbu.generar("999", "0001", 1000 + id), TipoCuenta.CA, moneda, null,
                new BigDecimal(limite));
        ReflectionTestUtils.setField(c, "id", id);
        if (new BigDecimal(saldo).signum() > 0) {
            c.acreditar(new BigDecimal(saldo));
        }
        return c;
    }
}
