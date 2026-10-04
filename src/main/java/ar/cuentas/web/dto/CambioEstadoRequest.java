package ar.cuentas.web.dto;

import ar.cuentas.dominio.EstadoCuenta;
import jakarta.validation.constraints.NotNull;

public record CambioEstadoRequest(
        @NotNull(message = "El estado es obligatorio (ACTIVA o BLOQUEADA)") EstadoCuenta estado) {
}
