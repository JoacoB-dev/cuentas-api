package ar.cuentas.integracion;

import ar.cuentas.dominio.Moneda;
import ar.cuentas.dominio.Rol;
import ar.cuentas.dominio.TipoCuenta;
import ar.cuentas.dominio.Usuario;
import ar.cuentas.repositorio.UsuarioRepository;
import ar.cuentas.servicio.ClienteService;
import ar.cuentas.servicio.CuentaService;
import ar.cuentas.web.dto.ClienteRequest;
import ar.cuentas.web.dto.CuentaRequest;
import ar.cuentas.web.dto.CuentaResponse;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

/**
 * Base de los tests de integración. Usan un PostgreSQL real (perfil "test",
 * base cuentas_test) y un Redis real (base 1). Antes de cada test se vacían Redis
 * y las tablas, y se cargan:
 * <ul>
 *   <li>Ana (usuario "ana", CLIENTE): CA en ARS con 50.000 y límite diario 100.000; CA en USD con 1.000.</li>
 *   <li>Bruno (usuario "bruno", CLIENTE): CC en ARS con 20.000 y descubierto de 10.000.</li>
 *   <li>Usuario "operador" (OPERADOR).</li>
 * </ul>
 */
@ActiveProfiles("test")
abstract class BaseIntegracion {

    protected static final String CLAVE = "clave-123";

    @Autowired
    protected JdbcTemplate jdbc;
    @Autowired
    protected ObjectMapper json;
    @Autowired
    protected ClienteService clienteService;
    @Autowired
    protected CuentaService cuentaService;
    @Autowired
    protected UsuarioRepository usuarios;
    @Autowired
    protected PasswordEncoder passwordEncoder;
    @Autowired
    protected StringRedisTemplate redis;

    protected Long anaId;
    protected Long brunoId;
    protected CuentaResponse anaArs;
    protected CuentaResponse anaUsd;
    protected CuentaResponse brunoArs;

    /** Cache de hashes BCrypt para no recalcularlos en cada test. */
    private static final Map<String, String> HASHES = new HashMap<>();

    @BeforeEach
    void cargarDatosBase() {
        limpiarRedis();
        jdbc.execute("TRUNCATE movimiento, transferencia, cuenta, usuario, cliente RESTART IDENTITY CASCADE");
        anaId = clienteService.crear(new ClienteRequest("Ana", "Gómez", "30111222", "ana@example.com")).id();
        brunoId = clienteService.crear(new ClienteRequest("Bruno", "Díaz", "28999888", "bruno@example.com")).id();
        anaArs = crearCuenta(anaId, TipoCuenta.CA, Moneda.ARS, "100000", null, "50000");
        anaUsd = crearCuenta(anaId, TipoCuenta.CA, Moneda.USD, "5000", null, "1000");
        brunoArs = crearCuenta(brunoId, TipoCuenta.CC, Moneda.ARS, "100000", "10000", "20000");
        crearUsuario("ana", Rol.CLIENTE, anaId);
        crearUsuario("bruno", Rol.CLIENTE, brunoId);
        crearUsuario("operador", Rol.OPERADOR, null);
    }

    /**
     * Vacía la base de Redis de los tests. Es necesario porque TRUNCATE ... RESTART IDENTITY
     * reutiliza los ids: sin esto, el caché de un test anterior aparecería en el siguiente.
     */
    protected void limpiarRedis() {
        redis.execute((RedisCallback<Void>) conexion -> {
            conexion.serverCommands().flushDb();
            return null;
        });
    }

    protected CuentaResponse crearCuenta(Long clienteId, TipoCuenta tipo, Moneda moneda, String limiteDiario,
                                         String descubierto, String depositoInicial) {
        CuentaResponse c = cuentaService.crear(new CuentaRequest(clienteId, tipo, moneda, new BigDecimal(limiteDiario),
                descubierto == null ? null : new BigDecimal(descubierto)));
        if (depositoInicial != null) {
            cuentaService.depositar(c.id(), new BigDecimal(depositoInicial), "Depósito inicial");
        }
        return c;
    }

    protected void crearUsuario(String username, Rol rol, Long clienteId) {
        String hash = HASHES.computeIfAbsent(username, u -> passwordEncoder.encode(u + "123"));
        usuarios.save(new Usuario(username, hash, rol, clienteId));
    }

    protected BigDecimal saldo(Long cuentaId) {
        return jdbc.queryForObject("select saldo from cuenta where id = ?", BigDecimal.class, cuentaId);
    }

    protected int contar(String sql, Object... args) {
        Integer n = jdbc.queryForObject(sql, Integer.class, args);
        return n == null ? 0 : n;
    }

    protected String cuerpoTransferencia(Long origenId, String cbuDestino, String importe, String concepto)
            throws Exception {
        Map<String, Object> m = new HashMap<>();
        m.put("cuentaOrigenId", origenId);
        m.put("cbuDestino", cbuDestino);
        m.put("importe", new BigDecimal(importe));
        if (concepto != null) {
            m.put("concepto", concepto);
        }
        return json.writeValueAsString(m);
    }

    protected JsonNode leer(String contenido) throws Exception {
        return json.readTree(contenido);
    }
}
