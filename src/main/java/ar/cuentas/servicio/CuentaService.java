package ar.cuentas.servicio;

import ar.cuentas.config.CbuProperties;
import ar.cuentas.dominio.Cbu;
import ar.cuentas.dominio.Cliente;
import ar.cuentas.dominio.Cuenta;
import ar.cuentas.dominio.EstadoCuenta;
import ar.cuentas.dominio.Movimiento;
import ar.cuentas.dominio.TipoMovimiento;
import ar.cuentas.error.NoEncontradoException;
import ar.cuentas.repositorio.ClienteRepository;
import ar.cuentas.repositorio.CuentaRepository;
import ar.cuentas.repositorio.MovimientoRepository;
import ar.cuentas.seguridad.UsuarioActual;
import ar.cuentas.web.dto.CuentaRequest;
import ar.cuentas.web.dto.CuentaResponse;
import ar.cuentas.web.dto.MovimientoResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Service
public class CuentaService {

    private final CuentaRepository cuentas;
    private final ClienteRepository clientes;
    private final MovimientoRepository movimientos;
    private final CbuProperties cbu;
    private final Clock reloj;

    public CuentaService(CuentaRepository cuentas, ClienteRepository clientes, MovimientoRepository movimientos,
                         CbuProperties cbu, Clock reloj) {
        this.cuentas = cuentas;
        this.clientes = clientes;
        this.movimientos = movimientos;
        this.cbu = cbu;
        this.reloj = reloj;
    }

    /** OPERADOR ve todas; CLIENTE sólo las propias. */
    @Transactional(readOnly = true)
    public List<CuentaResponse> listar(UsuarioActual usuario, Long clienteId) {
        List<Cuenta> resultado;
        if (usuario.esOperador()) {
            resultado = clienteId == null ? cuentas.findAllByOrderByIdAsc() : cuentas.findByClienteIdOrderByIdAsc(clienteId);
        } else {
            resultado = cuentas.findByClienteIdOrderByIdAsc(usuario.clienteId());
        }
        return resultado.stream().map(CuentaResponse::de).toList();
    }

    @Transactional(readOnly = true)
    public CuentaResponse obtener(Long id, UsuarioActual usuario) {
        return CuentaResponse.de(buscarAccesible(id, usuario));
    }

    @Transactional
    public CuentaResponse crear(CuentaRequest req) {
        Cliente cliente = clientes.findById(req.clienteId())
                .orElseThrow(() -> new NoEncontradoException("No existe el cliente " + req.clienteId() + "."));
        String nuevoCbu = Cbu.generar(cbu.entidad(), cbu.sucursal(), cuentas.siguienteNumeroDeCuenta());
        Cuenta cuenta = new Cuenta(cliente, nuevoCbu, req.tipo(), req.moneda(), req.descubiertoAutorizado(),
                req.limiteDiario());
        return CuentaResponse.de(cuentas.save(cuenta));
    }

    @Transactional
    public CuentaResponse cambiarEstado(Long id, EstadoCuenta estado) {
        Cuenta cuenta = cuentas.buscarParaActualizar(id).orElseThrow(() -> NoEncontradoException.cuenta(id));
        cuenta.cambiarEstado(estado);
        return CuentaResponse.de(cuenta);
    }

    /** Depósito por ventanilla (lo hace un OPERADOR). Toma el mismo lock que las transferencias. */
    @Transactional
    public MovimientoResponse depositar(Long id, BigDecimal importe, String descripcion) {
        Cuenta cuenta = cuentas.buscarParaActualizar(id).orElseThrow(() -> NoEncontradoException.cuenta(id));
        BigDecimal monto = importe.setScale(2, RoundingMode.UNNECESSARY);
        cuenta.acreditar(monto);
        String texto = descripcion == null || descripcion.isBlank() ? "Depósito por ventanilla" : descripcion.trim();
        Movimiento mov = movimientos.save(new Movimiento(cuenta.getId(), TipoMovimiento.CREDITO, monto,
                cuenta.getSaldo(), texto, null, reloj.instant().truncatedTo(ChronoUnit.MICROS)));
        return MovimientoResponse.de(mov);
    }

    /**
     * Devuelve la cuenta si el usuario puede verla. Para un CLIENTE, una cuenta ajena
     * se responde igual que una inexistente (404) para no revelar qué ids existen.
     */
    Cuenta buscarAccesible(Long id, UsuarioActual usuario) {
        Cuenta cuenta = cuentas.findWithClienteById(id).orElseThrow(() -> NoEncontradoException.cuenta(id));
        if (!usuario.esOperador() && !cuenta.perteneceA(usuario.clienteId())) {
            throw NoEncontradoException.cuenta(id);
        }
        return cuenta;
    }
}
