package ar.cuentas.dominio;

public enum Rol {
    /** Titular: sólo ve y opera sus propias cuentas. */
    CLIENTE,
    /** Empleado: da de alta clientes y cuentas, deposita, bloquea y consulta todo. */
    OPERADOR
}
