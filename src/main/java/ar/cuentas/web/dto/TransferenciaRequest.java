package ar.cuentas.web.dto;

import ar.cuentas.web.validacion.CbuValido;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record TransferenciaRequest(
        @NotNull(message = "La cuenta de origen es obligatoria") Long cuentaOrigenId,
        @NotNull(message = "El CBU de destino es obligatorio") @CbuValido String cbuDestino,
        @NotNull(message = "El importe es obligatorio")
        @Positive(message = "El importe debe ser mayor a cero")
        @Digits(integer = 15, fraction = 2, message = "El importe admite hasta 2 decimales") BigDecimal importe,
        @Size(max = 140, message = "El concepto admite hasta 140 caracteres") String concepto) {
}
