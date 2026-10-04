package ar.cuentas.web.validacion;

import ar.cuentas.dominio.Cbu;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

public class CbuValidoValidator implements ConstraintValidator<CbuValido, String> {

    @Override
    public boolean isValid(String valor, ConstraintValidatorContext contexto) {
        // null lo resuelve @NotNull
        return valor == null || Cbu.esValido(valor);
    }
}
