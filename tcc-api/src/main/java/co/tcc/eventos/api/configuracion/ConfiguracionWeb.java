package co.tcc.eventos.api.configuracion;

import co.tcc.eventos.api.errores.LimiteCuerpo;
import co.tcc.eventos.api.errores.Problemas;
import co.tcc.eventos.api.seguridad.LimitePorCliente;
import co.tcc.eventos.api.seguridad.OpcionesSeguridad;
import io.lettuce.core.ClientOptions;
import io.lettuce.core.ClientOptions.DisconnectedBehavior;
import org.springframework.boot.data.redis.autoconfigure.LettuceClientConfigurationBuilderCustomizer;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/** Detalles del borde HTTP: límites y comportamiento de Redis ante fallas. */
@Configuration(proxyBeanMethods = false)
public class ConfiguracionWeb implements WebMvcConfigurer {

    private final LimitePorCliente limitePorCliente;

    public ConfiguracionWeb(OpcionesSeguridad opciones, Problemas problemas) {
        this.limitePorCliente = new LimitePorCliente(opciones, problemas);
    }

    /** Primero que todo: un cuerpo gigante no debe llegar ni a la autenticación. */
    @Bean
    FilterRegistrationBean<LimiteCuerpo> limiteCuerpo(Problemas problemas) {
        var registro = new FilterRegistrationBean<>(new LimiteCuerpo(problemas));
        registro.setOrder(Ordered.HIGHEST_PRECEDENCE);
        registro.addUrlPatterns("/api/*");
        return registro;
    }

    /** Después de autenticar y autorizar: el límite se parte por cliente. */
    @Override
    public void addInterceptors(InterceptorRegistry registro) {
        registro.addInterceptor(limitePorCliente).addPathPatterns("/api/**");
    }

    /**
     * Redis caído = fallar rápido, no encolar comandos en memoria: el filtro de duplicados es una optimización
     * y la recepción no puede esperar por él (equivale a BacklogPolicy.FailFast en .NET).
     */
    @Bean
    LettuceClientConfigurationBuilderCustomizer redisFallaRapido() {
        return constructor -> constructor.clientOptions(ClientOptions.builder()
                .disconnectedBehavior(DisconnectedBehavior.REJECT_COMMANDS)
                .autoReconnect(true)
                .build());
    }
}
