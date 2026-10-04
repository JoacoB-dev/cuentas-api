package ar.cuentas.error;

public class NoEncontradoException extends RuntimeException {

    public NoEncontradoException(String mensaje) {
        super(mensaje);
    }

    public static NoEncontradoException cuenta(Long id) {
        return new NoEncontradoException("No existe la cuenta " + id + " o no tenés acceso a ella.");
    }
}
