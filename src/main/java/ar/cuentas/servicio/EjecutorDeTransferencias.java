package ar.cuentas.servicio;

import ar.cuentas.dominio.Cuenta;
import ar.cuentas.dominio.Movimiento;
import ar.cuentas.dominio.ReglaNegocioException;
import ar.cuentas.dominio.TipoMovimiento;
import ar.cuentas.dominio.Transferencia;
import ar.cuentas.error.NoEncontradoException;
import ar.cuentas.repositorio.CuentaRepository;
import ar.cuentas.repositorio.MovimientoRepository;
import ar.cuentas.repositorio.TransferenciaRepository;
import ar.cuentas.seguridad.UsuarioActual;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

/**
 * Mueve la plata. Todo ocurre en una única transacción: o se aplican el débito,
 * el crédito, la transferencia y los dos movimientos, o no se aplica nada.
 */
@Service
public class EjecutorDeTransferencias {

    private final CuentaRepository cuentas;
    private final TransferenciaRepository transferencias;
    private final MovimientoRepository movimientos;
    private final Clock reloj;

    public EjecutorDeTransferencias(CuentaRepository cuentas, TransferenciaRepository transferencias,
                                    MovimientoRepository movimientos, Clock reloj) {
        this.cuentas = cuentas;
        this.transferencias = transferencias;
        this.movimientos = movimientos;
        this.reloj = reloj;
    }

    @Transactional
    public Transferencia ejecutar(Long origenId, String cbuDestino, BigDecimal importe, String concepto,
                                  UsuarioActual usuario, String idempotencyKey, String hash) {
        // Sólo el id del destino: si cargáramos la entidad ahora, quedaría en el contexto de
        // persistencia y el SELECT ... FOR UPDATE posterior no refrescaría su saldo.
        Long destinoId = cuentas.buscarIdPorCbu(cbuDestino)
                .orElseThrow(() -> new NoEncontradoException("No existe una cuenta con CBU " + cbuDestino + "."));
        if (destinoId.equals(origenId)) {
            throw new ReglaNegocioException("misma-cuenta", "La cuenta de origen y la de destino son la misma.");
        }

        // Orden de bloqueo consistente (id menor primero): si A->B y B->A corren a la vez,
        // las dos piden primero la misma fila, así que una espera a la otra y no hay deadlock.
        Long primero = Math.min(origenId, destinoId);
        Long segundo = Math.max(origenId, destinoId);
        Cuenta cuentaPrimera = bloquear(primero);
        Cuenta cuentaSegunda = bloquear(segundo);
        Cuenta origen = primero.equals(origenId) ? cuentaPrimera : cuentaSegunda;
        Cuenta destino = primero.equals(origenId) ? cuentaSegunda : cuentaPrimera;

        if (!usuario.esOperador() && !origen.perteneceA(usuario.clienteId())) {
            throw NoEncontradoException.cuenta(origenId);
        }
        // Un reintento con la misma clave pudo haber estado esperando el lock mientras el
        // pedido original se ejecutaba. Ahora que tenemos el lock, lo vemos ya confirmado.
        if (transferencias.findByUsuarioAndIdempotencyKey(usuario.username(), idempotencyKey).isPresent()) {
            throw new YaProcesadaException();
        }
        if (origen.getMoneda() != destino.getMoneda()) {
            throw new ReglaNegocioException("monedas-distintas",
                    "No se puede transferir entre cuentas de distinta moneda (" + origen.getMoneda() + " a "
                            + destino.getMoneda() + "). Esta API no hace conversión de moneda.");
        }
        destino.verificarActiva();
        verificarLimiteDiario(origen, importe);

        origen.debitar(importe);
        destino.acreditar(importe);

        // PostgreSQL guarda microsegundos: se trunca para que la respuesta coincida con lo guardado.
        Instant ahora = reloj.instant().truncatedTo(ChronoUnit.MICROS);
        Transferencia t = transferencias.saveAndFlush(new Transferencia(origenId, destinoId, importe,
                origen.getMoneda(), concepto, usuario.username(), idempotencyKey, hash, ahora));
        String detalle = concepto == null ? "" : " - " + concepto;
        movimientos.save(new Movimiento(origenId, TipoMovimiento.DEBITO, importe, origen.getSaldo(),
                "Transferencia enviada a CBU " + destino.getCbu() + detalle, t.getId(), ahora));
        movimientos.save(new Movimiento(destinoId, TipoMovimiento.CREDITO, importe, destino.getSaldo(),
                "Transferencia recibida de CBU " + origen.getCbu() + detalle, t.getId(), ahora));
        return t;
    }

    /**
     * El total del día se calcula con la cuenta de origen ya bloqueada: ninguna otra
     * transferencia desde esa cuenta puede estar en curso, así que la suma es exacta.
     */
    private void verificarLimiteDiario(Cuenta origen, BigDecimal importe) {
        Instant inicioDelDia = LocalDate.now(reloj).atStartOfDay(reloj.getZone()).toInstant();
        BigDecimal enviadoHoy = transferencias.totalEnviadoDesde(origen.getId(), inicioDelDia);
        if (enviadoHoy.add(importe).compareTo(origen.getLimiteDiario()) > 0) {
            BigDecimal restante = origen.getLimiteDiario().subtract(enviadoHoy).max(BigDecimal.ZERO);
            throw new ReglaNegocioException("limite-diario-excedido",
                    "La transferencia supera el límite diario de la cuenta (" + origen.getLimiteDiario() + " "
                            + origen.getMoneda() + "). Hoy todavía podés transferir " + restante + " "
                            + origen.getMoneda() + ".");
        }
    }

    /** La clave de idempotencia ya tiene una transferencia confirmada. */
    static class YaProcesadaException extends RuntimeException {
        YaProcesadaException() {
            super(null, null, false, false);
        }
    }

    private Cuenta bloquear(Long id) {
        return cuentas.buscarParaActualizar(id).orElseThrow(() -> NoEncontradoException.cuenta(id));
    }
}
