package ar.cuentas.web.dto;

import ar.cuentas.dominio.Cuenta;
import ar.cuentas.dominio.EstadoCuenta;
import ar.cuentas.dominio.Moneda;
import ar.cuentas.dominio.TipoCuenta;

import java.math.BigDecimal;

public record CuentaResponse(Long id, String cbu, TipoCuenta tipo, Moneda moneda, EstadoCuenta estado,
                             BigDecimal saldo, BigDecimal disponible, BigDecimal descubiertoAutorizado,
                             BigDecimal limiteDiario, Long clienteId, String titular) {

    /** Debe llamarse dentro de una transacción (el cliente se carga en forma lazy). */
    public static CuentaResponse de(Cuenta c) {
        return new CuentaResponse(c.getId(), c.getCbu(), c.getTipo(), c.getMoneda(), c.getEstado(),
                c.getSaldo(), c.disponible(), c.getDescubiertoAutorizado(), c.getLimiteDiario(),
                c.getCliente().getId(), c.getCliente().nombreCompleto());
    }
}
