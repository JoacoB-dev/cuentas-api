package ar.cuentas.web.dto;

import ar.cuentas.dominio.Movimiento;
import ar.cuentas.dominio.TipoMovimiento;

import java.math.BigDecimal;
import java.time.Instant;

public record MovimientoResponse(Long id, Instant fecha, TipoMovimiento tipo, BigDecimal importe,
                                 BigDecimal saldoPosterior, String descripcion, Long transferenciaId) {

    public static MovimientoResponse de(Movimiento m) {
        return new MovimientoResponse(m.getId(), m.getFecha(), m.getTipo(), m.getImporte(),
                m.getSaldoPosterior(), m.getDescripcion(), m.getTransferenciaId());
    }
}
