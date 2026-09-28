package co.tcc.eventos.api.salud;

import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;

public final class SondaRedis implements Sonda {

    private final StringRedisTemplate redis;

    public SondaRedis(StringRedisTemplate redis) {
        this.redis = redis;
    }

    @Override
    public boolean disponible() {
        try {
            // El límite lo impone el timeout de comandos de la conexión (250 ms).
            var respuesta = redis.execute((RedisCallback<String>) conexion -> conexion.ping());
            return "PONG".equalsIgnoreCase(respuesta);
        } catch (RuntimeException ex) {
            return false;
        }
    }
}
