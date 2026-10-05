package ar.cuentas.seguridad;

import ar.cuentas.error.DemasiadosIntentosException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

/**
 * Límite de intentos fallidos de login, con contadores en Redis.
 *
 * <p>Por cada usuario se cuenta cuántos logins fallaron en una ventana fija
 * (por defecto 5 en 15 minutos). Al llegar al máximo, el login de ese usuario
 * responde 429 hasta que vence la ventana, <b>aunque la contraseña sea correcta</b>
 * (si no, un atacante podría seguir probando contraseñas y saber cuál es la buena).
 * Un login correcto pone el contador en cero.
 *
 * <p>Por qué Redis y no un Map en memoria: si hay varias instancias de la API,
 * todas ven el mismo contador, y el vencimiento lo maneja Redis solo (TTL).
 *
 * <p>Si Redis no responde, se deja pasar el login (fail-open) y se registra un
 * warning: se prioriza que los clientes puedan entrar. Es una decisión discutible
 * y está explicada en el README.
 */
@Component
public class LimitadorDeIntentosDeLogin {

    private static final Logger log = LoggerFactory.getLogger(LimitadorDeIntentosDeLogin.class);
    static final String PREFIJO = "cuentas-api:login-fallidos:";

    /**
     * INCR + EXPIRE en un solo paso atómico: el TTL se pone sólo con el primer fallo
     * (ventana fija). Con dos comandos sueltos, una caída en el medio podría dejar
     * un contador sin vencimiento, es decir, un usuario bloqueado para siempre.
     */
    private static final RedisScript<Long> INCREMENTAR = RedisScript.of("""
            local n = redis.call('INCR', KEYS[1])
            if n == 1 then redis.call('PEXPIRE', KEYS[1], ARGV[1]) end
            return n""", Long.class);

    private final StringRedisTemplate redis;
    private final int maxIntentos;
    private final Duration ventana;

    public LimitadorDeIntentosDeLogin(StringRedisTemplate redis,
                                      @Value("${app.login.max-intentos}") int maxIntentos,
                                      @Value("${app.login.ventana}") Duration ventana) {
        this.redis = redis;
        this.maxIntentos = maxIntentos;
        this.ventana = ventana;
    }

    /** Lanza {@link DemasiadosIntentosException} si el usuario está bloqueado. */
    public void verificar(String username) {
        String clave = clave(username);
        try {
            String valor = redis.opsForValue().get(clave);
            if (valor != null && Integer.parseInt(valor) >= maxIntentos) {
                Long ms = redis.getExpire(clave, TimeUnit.MILLISECONDS);
                long segundos = ms == null || ms < 0 ? ventana.toSeconds() : Math.max(1, (ms + 999) / 1000);
                throw new DemasiadosIntentosException(segundos);
            }
        } catch (DataAccessException e) {
            log.warn("Redis no disponible: no se controla el límite de intentos de login ({}).", e.getMessage());
        }
    }

    /** Suma un fallo. Devuelve cuántos lleva en la ventana actual (0 si Redis no respondió). */
    public long registrarFallo(String username) {
        try {
            Long n = redis.execute(INCREMENTAR, List.of(clave(username)), String.valueOf(ventana.toMillis()));
            return n == null ? 0 : n;
        } catch (DataAccessException e) {
            log.warn("Redis no disponible: no se registró el login fallido ({}).", e.getMessage());
            return 0;
        }
    }

    public void limpiar(String username) {
        try {
            redis.delete(clave(username));
        } catch (DataAccessException e) {
            log.warn("Redis no disponible: no se limpiaron los intentos de login ({}).", e.getMessage());
        }
    }

    private static String clave(String username) {
        return PREFIJO + username.trim().toLowerCase(Locale.ROOT);
    }
}
