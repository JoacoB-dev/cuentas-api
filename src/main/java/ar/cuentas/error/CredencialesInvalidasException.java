package ar.cuentas.error;

public class CredencialesInvalidasException extends RuntimeException {

    public CredencialesInvalidasException() {
        super("Usuario o contraseña incorrectos.");
    }
}
