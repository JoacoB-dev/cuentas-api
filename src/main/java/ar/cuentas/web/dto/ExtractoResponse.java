package ar.cuentas.web.dto;

import ar.cuentas.dominio.Moneda;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record ExtractoResponse(Long cuentaId, String cbu, Moneda moneda, String titular,
                               LocalDate desde, LocalDate hasta,
                               BigDecimal saldoInicial, BigDecimal totalCreditos, BigDecimal totalDebitos,
                               BigDecimal saldoFinal, List<MovimientoResponse> movimientos) {
}
