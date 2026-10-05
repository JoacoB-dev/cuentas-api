package ar.cuentas.error;

/** Se superó el máximo de logins fallidos; hay que esperar {@code segundosDeEspera}. */
public class DemasiadosIntentosException extends RuntimeException {

    private final long segundosDeEspera;

    public DemasiadosIntentosException(long segundosDeEspera) {
        super("Demasiados intentos fallidos. Probá de nuevo en " + aTexto(segundosDeEspera) + ".");
        this.segundosDeEspera = segundosDeEspera;
    }

    public long getSegundosDeEspera() {
        return segundosDeEspera;
    }

    private static String aTexto(long segundos) {
        if (segundos < 60) {
            return segundos + (segundos == 1 ? " segundo" : " segundos");
        }
        long minutos = (segundos + 59) / 60;
        return minutos + (minutos == 1 ? " minuto" : " minutos");
    }
}
