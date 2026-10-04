package ar.cuentas.web.dto;

import ar.cuentas.dominio.Moneda;
import ar.cuentas.dominio.Transferencia;

import java.math.BigDecimal;
import java.time.Instant;

public record TransferenciaResponse(Long id, Long cuentaOrigenId, Long cuentaDestinoId, BigDecimal importe,
                                    Moneda moneda, String concepto, Instant fecha) {

    public static TransferenciaResponse de(Transferencia t) {
        return new TransferenciaResponse(t.getId(), t.getCuentaOrigenId(), t.getCuentaDestinoId(),
                t.getImporte(), t.getMoneda(), t.getConcepto(), t.getFecha());
    }
}
