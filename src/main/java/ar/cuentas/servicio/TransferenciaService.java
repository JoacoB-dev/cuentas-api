package ar.cuentas.servicio;

import ar.cuentas.dominio.ReglaNegocioException;
import ar.cuentas.dominio.Transferencia;
import ar.cuentas.error.NoEncontradoException;
import ar.cuentas.error.SolicitudInvalidaException;
import ar.cuentas.repositorio.CuentaRepository;
import ar.cuentas.repositorio.TransferenciaRepository;
import ar.cuentas.seguridad.UsuarioActual;
import ar.cuentas.web.dto.TransferenciaRequest;
import ar.cuentas.web.dto.TransferenciaResponse;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Optional;

/**
 * Punto de entrada de las transferencias. Resuelve la idempotencia y delega el
 * movimiento de plata en {@link EjecutorDeTransferencias}, que corre en su propia
 * transacción.
 *
 * <p>Este servicio NO es transaccional a propósito: si dos pedidos con la misma
 * Idempotency-Key llegan a la vez, el segundo choca contra la constraint UNIQUE
 * al hacer commit; su transacción se deshace entera (incluido el débito) y acá se
 * vuelve a buscar la transferencia que guardó el primero, en una lectura nueva.
 */
@Service
public class TransferenciaService {

    public static final int LARGO_MAXIMO_CLAVE = 100;

    private final EjecutorDeTransferencias ejecutor;
    private final TransferenciaRepository transferencias;
    private final CuentaRepository cuentas;

    public TransferenciaService(EjecutorDeTransferencias ejecutor, TransferenciaRepository transferencias,
                                CuentaRepository cuentas) {
        this.ejecutor = ejecutor;
        this.transferencias = transferencias;
        this.cuentas = cuentas;
    }

    public Resultado transferir(TransferenciaRequest req, String idempotencyKey, UsuarioActual usuario) {
        String clave = validarClave(idempotencyKey);
        BigDecimal importe = req.importe().setScale(2, RoundingMode.UNNECESSARY);
        String concepto = req.concepto() == null || req.concepto().isBlank() ? null : req.concepto().trim();
        String hash = hashDeSolicitud(req.cuentaOrigenId(), req.cbuDestino(), importe, concepto);

        Optional<Resultado> previa = buscarPrevia(usuario.username(), clave, hash);
        if (previa.isPresent()) {
            return previa.get();
        }
        try {
            Transferencia t = ejecutor.ejecutar(req.cuentaOrigenId(), req.cbuDestino(), importe, concepto,
                    usuario, clave, hash);
            return new Resultado(TransferenciaResponse.de(t), false);
        } catch (EjecutorDeTransferencias.YaProcesadaException e) {
            // Otro pedido con la misma clave terminó mientras este esperaba el lock.
            return buscarPrevia(usuario.username(), clave, hash).orElseThrow();
        } catch (DataIntegrityViolationException e) {
            // Carrera con otro pedido con la misma clave (desde otra cuenta): el otro ganó.
            return buscarPrevia(usuario.username(), clave, hash).orElseThrow(() -> e);
        }
    }

    @Transactional(readOnly = true)
    public TransferenciaResponse obtener(Long id, UsuarioActual usuario) {
        Transferencia t = transferencias.findById(id)
                .orElseThrow(() -> new NoEncontradoException("No existe la transferencia " + id + "."));
        if (!usuario.esOperador() && !participa(t, usuario)) {
            throw new NoEncontradoException("No existe la transferencia " + id + " o no tenés acceso a ella.");
        }
        return TransferenciaResponse.de(t);
    }

    private boolean participa(Transferencia t, UsuarioActual usuario) {
        return cuentas.findById(t.getCuentaOrigenId()).map(c -> c.perteneceA(usuario.clienteId())).orElse(false)
                || cuentas.findById(t.getCuentaDestinoId()).map(c -> c.perteneceA(usuario.clienteId())).orElse(false);
    }

    private Optional<Resultado> buscarPrevia(String username, String clave, String hash) {
        return transferencias.findByUsuarioAndIdempotencyKey(username, clave).map(t -> {
            if (!t.getHashSolicitud().equals(hash)) {
                throw new ReglaNegocioException("clave-idempotencia-reutilizada",
                        "La Idempotency-Key '" + clave + "' ya se usó para una transferencia con otros datos. "
                                + "Usá una clave nueva para cada transferencia distinta.");
            }
            return new Resultado(TransferenciaResponse.de(t), true);
        });
    }

    private static String validarClave(String clave) {
        if (clave == null || clave.isBlank()) {
            throw new SolicitudInvalidaException("El header Idempotency-Key no puede estar vacío.");
        }
        if (clave.length() > LARGO_MAXIMO_CLAVE) {
            throw new SolicitudInvalidaException(
                    "El header Idempotency-Key admite hasta " + LARGO_MAXIMO_CLAVE + " caracteres.");
        }
        return clave.trim();
    }

    /** Huella de los datos del pedido: si se reusa la clave con otros datos, no coincide. */
    static String hashDeSolicitud(Long origenId, String cbuDestino, BigDecimal importe, String concepto) {
        String canonico = origenId + "|" + cbuDestino + "|" + importe.toPlainString() + "|"
                + (concepto == null ? "" : concepto);
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(canonico.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 no disponible", e);
        }
    }

    /** @param repetida true si la respuesta corresponde a un reintento (no se movió plata de nuevo). */
    public record Resultado(TransferenciaResponse transferencia, boolean repetida) {
    }
}
