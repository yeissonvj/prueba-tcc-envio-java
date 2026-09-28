package co.tcc.eventos.infraestructura.redis;

import co.tcc.eventos.aplicacion.puertos.FiltroDuplicados;
import java.util.UUID;
import org.springframework.data.redis.core.StringRedisTemplate;

/**
 * Adaptador: implementa el puerto {@link FiltroDuplicados} con Redis. Solo habla con Redis;
 * la tolerancia a fallas la agrega el decorador FiltroDuplicadosTolerante.
 * Las claves son las mismas que usa la versión .NET (idEvento sin guiones), así ambas pueden convivir.
 */
public final class FiltroDuplicadosRedis implements FiltroDuplicados {

    private final StringRedisTemplate redis;
    private final OpcionesRedis opciones;

    public FiltroDuplicadosRedis(StringRedisTemplate redis, OpcionesRedis opciones) {
        this.redis = redis;
        this.opciones = opciones;
    }

    @Override
    public boolean yaRecibido(UUID idEvento) {
        return Boolean.TRUE.equals(redis.hasKey(clave(idEvento)));
    }

    @Override
    public void marcarRecibido(UUID idEvento) {
        redis.opsForValue().set(clave(idEvento), "1", opciones.getVigencia());
    }

    private String clave(UUID idEvento) {
        return opciones.getPrefijoClave() + idEvento.toString().replace("-", "");
    }
}
