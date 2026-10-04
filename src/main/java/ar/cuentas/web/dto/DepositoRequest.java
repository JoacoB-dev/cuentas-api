package ar.cuentas.web.dto;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record DepositoRequest(
        @NotNull(message = "El importe es obligatorio")
        @Positive(message = "El importe debe ser mayor a cero")
        @Digits(integer = 15, fraction = 2, message = "El importe admite hasta 2 decimales") BigDecimal importe,
        @Size(max = 120, message = "La descripción admite hasta 120 caracteres") String descripcion) {
}
