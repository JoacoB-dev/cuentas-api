package ar.cuentas.dominio;

/**
 * Violación de una regla de negocio (saldo insuficiente, monedas distintas, etc.).
 * El código se usa para armar el "type" del ProblemDetail y para que el cliente
 * de la API pueda distinguir casos sin parsear el mensaje.
 */
public class ReglaNegocioException extends RuntimeException {

    private final String codigo;

    public ReglaNegocioException(String codigo, String mensaje) {
        super(mensaje);
        this.codigo = codigo;
    }

    public String getCodigo() {
        return codigo;
    }
}
