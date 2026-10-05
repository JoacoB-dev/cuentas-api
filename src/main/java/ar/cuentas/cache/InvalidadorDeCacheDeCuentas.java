package ar.cuentas.cache;

import ar.cuentas.dominio.Cuenta;
import jakarta.persistence.PostPersist;
import jakarta.persistence.PostRemove;
import jakarta.persistence.PostUpdate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Listener JPA de {@link Cuenta}: cuando se crea, se modifica (saldo, estado) o se
 * borra una cuenta, invalida el listado cacheado de su cliente.
 *
 * <p>Se engancha a la entidad y no a cada caso de uso para que ningún camino que
 * cambie una cuenta se olvide de invalidar (una transferencia toca dos cuentas,
 * posiblemente de dos clientes distintos, y las dos se invalidan).
 *
 * <p>El borrado en Redis se hace <b>después del commit</b>: si se hiciera antes, otro
 * pedido podría volver a cachear el saldo viejo antes de que se confirme el nuevo; y
 * si la transacción se deshace, no hay nada que invalidar. Si Redis no responde, se
 * registra el error y la operación igual se confirma (el dato viejo vive hasta el TTL).
 *
 * <p>Hibernate crea esta clase a través de Spring (SpringBeanContainer), por eso
 * puede recibir el CacheManager en el constructor.
 */
public class InvalidadorDeCacheDeCuentas {

    private static final Logger log = LoggerFactory.getLogger(InvalidadorDeCacheDeCuentas.class);

    private final CacheManager cacheManager;

    public InvalidadorDeCacheDeCuentas(CacheManager cacheManager) {
        this.cacheManager = cacheManager;
    }

    @PostPersist
    @PostUpdate
    @PostRemove
    void cuentaModificada(Cuenta cuenta) {
        if (cuenta.getCliente() == null) {
            return;
        }
        // getId() de un proxy lazy de Hibernate no dispara una consulta.
        Long clienteId = cuenta.getCliente().getId();
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    invalidar(clienteId);
                }
            });
        } else {
            invalidar(clienteId);
        }
    }

    private void invalidar(Long clienteId) {
        Cache cache = cacheManager.getCache(CacheConfig.CUENTAS_POR_CLIENTE);
        if (cache == null) {
            return;
        }
        try {
            cache.evictIfPresent(clienteId); // inmediato (ya estamos después del commit)
        } catch (RuntimeException e) {
            log.error("No se pudo invalidar el caché de cuentas del cliente {} ({}). "
                    + "Puede mostrarse un saldo viejo hasta que venza el TTL.", clienteId, e.getMessage());
        }
    }
}
