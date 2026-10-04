package ar.cuentas.servicio;

import ar.cuentas.dominio.Cuenta;
import ar.cuentas.dominio.Movimiento;
import ar.cuentas.dominio.TipoMovimiento;
import ar.cuentas.error.SolicitudInvalidaException;
import ar.cuentas.repositorio.MovimientoRepository;
import ar.cuentas.seguridad.UsuarioActual;
import ar.cuentas.web.dto.ExtractoResponse;
import ar.cuentas.web.dto.MovimientoResponse;
import ar.cuentas.web.dto.PaginaResponse;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

@Service
public class MovimientoService {

    static final int DIAS_POR_DEFECTO = 30;
    static final int DIAS_MAXIMOS_EXTRACTO = 366;

    private final CuentaService cuentaService;
    private final MovimientoRepository movimientos;
    private final Clock reloj;

    public MovimientoService(CuentaService cuentaService, MovimientoRepository movimientos, Clock reloj) {
        this.cuentaService = cuentaService;
        this.movimientos = movimientos;
        this.reloj = reloj;
    }

    /** Historial paginado, del más nuevo al más viejo. Fechas inclusivas, en hora argentina. */
    @Transactional(readOnly = true)
    public PaginaResponse<MovimientoResponse> historial(Long cuentaId, LocalDate desde, LocalDate hasta,
                                                        int pagina, int tamanio, UsuarioActual usuario) {
        Cuenta cuenta = cuentaService.buscarAccesible(cuentaId, usuario);
        Rango r = rango(desde, hasta, Integer.MAX_VALUE);
        return PaginaResponse.de(
                movimientos.findByCuentaIdAndFechaGreaterThanEqualAndFechaLessThanOrderByFechaDescIdDesc(
                        cuenta.getId(), r.inicio(), r.fin(), PageRequest.of(pagina, tamanio)),
                MovimientoResponse::de);
    }

    /**
     * Extracto de un período: saldo inicial, créditos, débitos y saldo final.
     * El saldo inicial sale del último movimiento anterior al período (toda cuenta
     * arranca en 0 y todo cambio de saldo deja un movimiento).
     */
    @Transactional(readOnly = true)
    public ExtractoResponse extracto(Long cuentaId, LocalDate desde, LocalDate hasta, UsuarioActual usuario) {
        Cuenta cuenta = cuentaService.buscarAccesible(cuentaId, usuario);
        Rango r = rango(desde, hasta, DIAS_MAXIMOS_EXTRACTO);
        BigDecimal saldoInicial = movimientos
                .findFirstByCuentaIdAndFechaLessThanOrderByFechaDescIdDesc(cuenta.getId(), r.inicio())
                .map(Movimiento::getSaldoPosterior)
                .orElse(BigDecimal.ZERO.setScale(2));
        BigDecimal creditos = movimientos.sumarPorTipo(cuenta.getId(), TipoMovimiento.CREDITO, r.inicio(), r.fin());
        BigDecimal debitos = movimientos.sumarPorTipo(cuenta.getId(), TipoMovimiento.DEBITO, r.inicio(), r.fin());
        List<MovimientoResponse> lista = movimientos
                .findByCuentaIdAndFechaGreaterThanEqualAndFechaLessThanOrderByFechaAscIdAsc(cuenta.getId(), r.inicio(), r.fin())
                .stream().map(MovimientoResponse::de).toList();
        return new ExtractoResponse(cuenta.getId(), cuenta.getCbu(), cuenta.getMoneda(),
                cuenta.getCliente().nombreCompleto(), r.desde(), r.hasta(),
                saldoInicial, creditos.setScale(2), debitos.setScale(2),
                saldoInicial.add(creditos).subtract(debitos).setScale(2), lista);
    }

    private Rango rango(LocalDate desde, LocalDate hasta, int maxDias) {
        LocalDate h = hasta != null ? hasta : LocalDate.now(reloj);
        LocalDate d = desde != null ? desde : h.minusDays(DIAS_POR_DEFECTO);
        if (d.isAfter(h)) {
            throw new SolicitudInvalidaException("La fecha 'desde' (" + d + ") es posterior a 'hasta' (" + h + ").");
        }
        if (d.plusDays(maxDias).isBefore(h)) {
            throw new SolicitudInvalidaException("El período no puede superar " + maxDias + " días.");
        }
        Instant inicio = d.atStartOfDay(reloj.getZone()).toInstant();
        Instant fin = h.plusDays(1).atStartOfDay(reloj.getZone()).toInstant();
        return new Rango(d, h, inicio, fin);
    }

    private record Rango(LocalDate desde, LocalDate hasta, Instant inicio, Instant fin) {
    }
}
