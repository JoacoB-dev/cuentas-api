package ar.cuentas.web.validacion;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** Valida largo, que sean sólo dígitos y los dos dígitos verificadores del CBU. */
@Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.RECORD_COMPONENT})
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = CbuValidoValidator.class)
public @interface CbuValido {

    String message() default "CBU inválido: debe tener 22 dígitos y dígitos verificadores correctos";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
