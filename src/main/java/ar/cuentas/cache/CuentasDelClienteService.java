package ar.cuentas.cache;

import ar.cuentas.repositorio.CuentaRepository;
import ar.cuentas.web.dto.CuentaResponse;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Listado de cuentas de un cliente, cacheado en Redis por id de cliente.
 * Está en un bean aparte porque @Cacheable funciona a través del proxy de Spring:
 * si lo llamara un método de la misma clase, el caché no se usaría.
 *
 * <p>La invalidación la hace {@link InvalidadorDeCacheDeCuentas} cada vez que
 * cambia una cuenta (depósito, transferencia, bloqueo, alta).
 *
 * <p>Ojo: el saldo que se muestra puede venir del caché, pero las operaciones
 * (transferir, depositar) siempre leen la cuenta de la base con lock.
 */
@Service
public class CuentasDelClienteService {

    private final CuentaRepository cuentas;

    public CuentasDelClienteService(CuentaRepository cuentas) {
        this.cuentas = cuentas;
    }

    @Cacheable(cacheNames = CacheConfig.CUENTAS_POR_CLIENTE, key = "#clienteId")
    @Transactional(readOnly = true)
    public List<CuentaResponse> listar(Long clienteId) {
        return cuentas.findByClienteIdOrderByIdAsc(clienteId).stream().map(CuentaResponse::de).toList();
    }
}
