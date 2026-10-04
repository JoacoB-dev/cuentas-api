package ar.cuentas.web.dto;

import ar.cuentas.dominio.Moneda;
import ar.cuentas.dominio.TipoCuenta;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;

import java.math.BigDecimal;

public record CuentaRequest(
        @NotNull(message = "El cliente es obligatorio") Long clienteId,
        @NotNull(message = "El tipo de cuenta es obligatorio (CA o CC)") TipoCuenta tipo,
        @NotNull(message = "La moneda es obligatoria (ARS o USD)") Moneda moneda,
        @NotNull(message = "El límite diario es obligatorio")
        @Positive(message = "El límite diario debe ser mayor a cero")
        @Digits(integer = 15, fraction = 2, message = "El límite diario admite hasta 2 decimales") BigDecimal limiteDiario,
        @PositiveOrZero(message = "El descubierto no puede ser negativo")
        @Digits(integer = 15, fraction = 2, message = "El descubierto admite hasta 2 decimales") BigDecimal descubiertoAutorizado) {
}
