package ar.cuentas.web.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record ClienteRequest(
        @NotBlank(message = "El nombre es obligatorio")
        @Size(max = 80, message = "El nombre admite hasta 80 caracteres") String nombre,
        @NotBlank(message = "El apellido es obligatorio")
        @Size(max = 80, message = "El apellido admite hasta 80 caracteres") String apellido,
        @NotBlank(message = "El DNI es obligatorio")
        @Pattern(regexp = "\\d{7,8}", message = "El DNI debe tener 7 u 8 dígitos, sin puntos") String dni,
        @NotBlank(message = "El email es obligatorio")
        @Email(message = "El email no tiene un formato válido") String email) {
}
