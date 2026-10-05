package ar.cuentas.cache;

import ar.cuentas.web.dto.CuentaResponse;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.Cache;
import org.springframework.cache.annotation.CachingConfigurer;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.interceptor.CacheErrorHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.Jackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext.SerializationPair;

import java.time.Duration;
import java.util.List;

/**
 * Caché en Redis con Spring Cache.
 *
 * <p>Se cachea una sola cosa: el listado de cuentas de un cliente (la pantalla de
 * inicio del home banking, que se consulta en cada navegación). Decisiones:
 * <ul>
 *   <li><b>Invalidación después del commit</b> (ver {@link InvalidadorDeCacheDeCuentas}).
 *       No se usa el modo "transactionAware" del manager porque, con Redis caído, la
 *       escritura diferida fallaría fuera del {@link CacheErrorHandler} y la consulta
 *       terminaría en 500 (lo detectó {@code SinRedisIntegrationTest}).</li>
 *   <li><b>JSON tipado</b> (no serialización Java ni tipos polimórficos): en Redis
 *       queda un JSON legible y no se deserializan clases arbitrarias.</li>
 *   <li><b>TTL</b>: tope de vida por si alguna invalidación se pierde.</li>
 *   <li><b>Si Redis se cae, la app sigue</b>: el {@link CacheErrorHandler} registra
 *       el error y la consulta va directo a PostgreSQL.</li>
 * </ul>
 */
@Configuration
@EnableCaching
public class CacheConfig implements CachingConfigurer {

    public static final String CUENTAS_POR_CLIENTE = "cuentas-por-cliente";

    private static final Logger log = LoggerFactory.getLogger(CacheConfig.class);

    @Bean
    public RedisCacheManager cacheManager(RedisConnectionFactory conexion, ObjectMapper mapper,
                                          @Value("${app.cache.ttl-cuentas}") Duration ttlCuentas) {
        JavaType listaDeCuentas = mapper.getTypeFactory().constructCollectionType(List.class, CuentaResponse.class);
        RedisCacheConfiguration cuentas = RedisCacheConfiguration.defaultCacheConfig()
                .prefixCacheNameWith("cuentas-api:")
                .entryTtl(ttlCuentas)
                .disableCachingNullValues()
                .serializeValuesWith(SerializationPair.fromSerializer(
                        new Jackson2JsonRedisSerializer<>(mapper, listaDeCuentas)));
        return RedisCacheManager.builder(conexion)
                .withCacheConfiguration(CUENTAS_POR_CLIENTE, cuentas)
                .disableCreateOnMissingCache() // sólo existen los cachés declarados acá
                .build();
    }

    /** Un error de Redis no rompe la operación: se registra y se sigue sin caché. */
    @Override
    public CacheErrorHandler errorHandler() {
        return new CacheErrorHandler() {
            @Override
            public void handleCacheGetError(RuntimeException e, Cache cache, Object key) {
                log.warn("Redis no disponible al leer {}::{} ({}). Se consulta la base.", cache.getName(), key,
                        e.getMessage());
            }

            @Override
            public void handleCachePutError(RuntimeException e, Cache cache, Object key, Object value) {
                log.warn("No se pudo guardar {}::{} en Redis ({}).", cache.getName(), key, e.getMessage());
            }

            @Override
            public void handleCacheEvictError(RuntimeException e, Cache cache, Object key) {
                // Es el caso delicado: si no se pudo invalidar, el dato viejo vive hasta el TTL.
                log.error("No se pudo invalidar {}::{} en Redis ({}). Puede quedar un saldo viejo hasta el TTL.",
                        cache.getName(), key, e.getMessage());
            }

            @Override
            public void handleCacheClearError(RuntimeException e, Cache cache) {
                log.error("No se pudo limpiar el caché {} ({}).", cache.getName(), e.getMessage());
            }
        };
    }
}
