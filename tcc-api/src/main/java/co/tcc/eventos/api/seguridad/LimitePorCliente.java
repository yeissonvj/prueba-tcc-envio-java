package co.tcc.eventos.api.seguridad;

import co.tcc.eventos.api.errores.Problemas;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * Un cubo de fichas por cliente (claim azp): si TMS entra en bucle, solo TMS recibe 429.
 * Corre después de autenticar y autorizar (interceptor de MVC). Es por instancia; el límite global
 * lo aplica el API Gateway.
 */
public final class LimitePorCliente implements HandlerInterceptor {

    private final OpcionesSeguridad opciones;
    private final Problemas problemas;
    private final Map<String, Bucket> cubos = new ConcurrentHashMap<>();

    public LimitePorCliente(OpcionesSeguridad opciones, Problemas problemas) {
        this.opciones = opciones;
        this.problemas = problemas;
    }

    @Override
    public boolean preHandle(HttpServletRequest peticion, HttpServletResponse respuesta, Object manejador) throws IOException {
        var cubo = cubos.computeIfAbsent(cliente(), c -> nuevoCubo());
        var consumo = cubo.tryConsumeAndReturnRemaining(1);
        if (consumo.isConsumed())
            return true;

        var segundos = Math.max(1, (long) Math.ceil(consumo.getNanosToWaitForRefill() / 1_000_000_000.0));
        respuesta.setHeader("Retry-After", Long.toString(segundos));
        problemas.escribir(respuesta, HttpStatus.TOO_MANY_REQUESTS, "Demasiadas peticiones",
                "Se superó el límite de peticiones del cliente. Reintente tras Retry-After.");
        return false;
    }

    private Bucket nuevoCubo() {
        return Bucket.builder()
                .addLimit(Bandwidth.builder()
                        .capacity(opciones.getRafagaPorCliente())
                        .refillGreedy(opciones.getPeticionesPorSegundoPorCliente(), Duration.ofSeconds(1))
                        .build())
                .build();
    }

    private static String cliente() {
        var autenticacion = SecurityContextHolder.getContext().getAuthentication();
        if (autenticacion instanceof JwtAuthenticationToken jwt) {
            var cliente = jwt.getToken().getClaimAsString(ConstantesSeguridad.RECLAMO_CLIENTE);
            if (cliente != null)
                return cliente;
        }
        return "anonimo";
    }
}
