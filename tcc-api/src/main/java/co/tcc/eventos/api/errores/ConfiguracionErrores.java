package co.tcc.eventos.api.errores;

import co.tcc.eventos.infraestructura.mapeo.JsonContratos;
import org.springframework.boot.jackson.autoconfigure.JsonMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.json.JsonMapper;

@Configuration(proxyBeanMethods = false)
public class ConfiguracionErrores {

    /** El mismo JSON que los mensajes de Kafka y que la versión .NET (offset original, camelCase). */
    @Bean
    JsonMapperBuilderCustomizer jsonDelContrato() {
        return JsonContratos::configurar;
    }

    @Bean
    Problemas problemas(JsonMapper json) {
        return new Problemas(json);
    }
}
