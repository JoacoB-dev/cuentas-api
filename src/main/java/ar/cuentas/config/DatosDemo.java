package ar.cuentas.config;

import ar.cuentas.dominio.Moneda;
import ar.cuentas.dominio.Rol;
import ar.cuentas.dominio.TipoCuenta;
import ar.cuentas.dominio.Usuario;
import ar.cuentas.repositorio.UsuarioRepository;
import ar.cuentas.servicio.ClienteService;
import ar.cuentas.servicio.CuentaService;
import ar.cuentas.web.dto.ClienteRequest;
import ar.cuentas.web.dto.ClienteResponse;
import ar.cuentas.web.dto.CuentaRequest;
import ar.cuentas.web.dto.CuentaResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

/**
 * Carga datos de ejemplo la primera vez (si no hay usuarios). Las contraseñas son
 * de demo y están a la vista a propósito: no usar este componente fuera de desarrollo
 * (se apaga con CARGAR_DATOS_DEMO=false).
 */
@Component
@ConditionalOnProperty(name = "app.demo.cargar-datos", havingValue = "true")
public class DatosDemo implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DatosDemo.class);

    private final UsuarioRepository usuarios;
    private final ClienteService clientes;
    private final CuentaService cuentas;
    private final PasswordEncoder passwordEncoder;

    public DatosDemo(UsuarioRepository usuarios, ClienteService clientes, CuentaService cuentas,
                     PasswordEncoder passwordEncoder) {
        this.usuarios = usuarios;
        this.clientes = clientes;
        this.cuentas = cuentas;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (usuarios.count() > 0) {
            return;
        }
        ClienteResponse ana = clientes.crear(new ClienteRequest("Ana", "Gómez", "30111222", "ana@example.com"));
        ClienteResponse bruno = clientes.crear(new ClienteRequest("Bruno", "Díaz", "28999888", "bruno@example.com"));

        CuentaResponse anaArs = cuentas.crear(new CuentaRequest(ana.id(), TipoCuenta.CA, Moneda.ARS,
                new BigDecimal("1000000"), null));
        CuentaResponse anaUsd = cuentas.crear(new CuentaRequest(ana.id(), TipoCuenta.CA, Moneda.USD,
                new BigDecimal("5000"), null));
        CuentaResponse brunoArs = cuentas.crear(new CuentaRequest(bruno.id(), TipoCuenta.CC, Moneda.ARS,
                new BigDecimal("2000000"), new BigDecimal("50000")));
        CuentaResponse brunoUsd = cuentas.crear(new CuentaRequest(bruno.id(), TipoCuenta.CA, Moneda.USD,
                new BigDecimal("5000"), null));

        cuentas.depositar(anaArs.id(), new BigDecimal("500000"), "Depósito inicial");
        cuentas.depositar(anaUsd.id(), new BigDecimal("1200"), "Depósito inicial");
        cuentas.depositar(brunoArs.id(), new BigDecimal("150000"), "Depósito inicial");
        cuentas.depositar(brunoUsd.id(), new BigDecimal("300"), "Depósito inicial");

        usuarios.save(new Usuario("ana", passwordEncoder.encode("ana123"), Rol.CLIENTE, ana.id()));
        usuarios.save(new Usuario("bruno", passwordEncoder.encode("bruno123"), Rol.CLIENTE, bruno.id()));
        usuarios.save(new Usuario("operador", passwordEncoder.encode("operador123"), Rol.OPERADOR, null));

        log.info("Datos demo cargados. Usuarios: ana/ana123, bruno/bruno123 (CLIENTE), operador/operador123 (OPERADOR)");
        log.info("CBU Ana ARS: {} | Ana USD: {} | Bruno ARS: {} | Bruno USD: {}",
                anaArs.cbu(), anaUsd.cbu(), brunoArs.cbu(), brunoUsd.cbu());
    }
}
