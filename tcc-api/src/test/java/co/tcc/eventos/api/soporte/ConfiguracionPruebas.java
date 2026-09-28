package co.tcc.eventos.api.soporte;

import co.tcc.eventos.api.salud.VerificacionesSalud;
import co.tcc.eventos.api.seguridad.ConfiguracionSeguridad;
import co.tcc.eventos.api.seguridad.OpcionesSeguridad;
import co.tcc.eventos.api.soporte.Falsos.ConsultaGuiasEnMemoria;
import co.tcc.eventos.api.soporte.Falsos.SondaFalsa;
import co.tcc.eventos.aplicacion.falsos.FiltroDuplicadosEnMemoria;
import co.tcc.eventos.aplicacion.falsos.PublicadorFalso;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;

/**
 * Reemplaza los adaptadores externos por falsos escritos a mano (como la FabricaApi de .NET): cada falso es
 * {@code @Primary}, así gana sobre el adaptador real al inyectarse por tipo. Todo lo demás —seguridad,
 * validación, errores, límites, controladores— es el código real.
 */
@TestConfiguration(proxyBeanMethods = false)
public class ConfiguracionPruebas {

    @Bean
    @Primary
    PublicadorFalso publicadorFalso() {
        return new PublicadorFalso();
    }

    @Bean
    @Primary
    FiltroDuplicadosEnMemoria filtroDuplicadosEnMemoria() {
        return new FiltroDuplicadosEnMemoria();
    }

    @Bean
    @Primary
    ConsultaGuiasEnMemoria consultaGuiasEnMemoria() {
        return new ConsultaGuiasEnMemoria();
    }

    @Bean
    SondaFalsa sondaKafkaFalsa() {
        return new SondaFalsa();
    }

    @Bean
    SondaFalsa sondaContingenciaFalsa() {
        return new SondaFalsa();
    }

    @Bean
    SondaFalsa sondaFiltroFalsa() {
        return new SondaFalsa();
    }

    @Bean
    @Primary
    VerificacionesSalud verificacionesSaludFalsas(
            @Qualifier("sondaKafkaFalsa") SondaFalsa kafka,
            @Qualifier("sondaContingenciaFalsa") SondaFalsa contingencia,
            @Qualifier("sondaFiltroFalsa") SondaFalsa filtro) {
        return new VerificacionesSalud(kafka, contingencia, filtro);
    }

    /** Mismas validaciones que producción; solo cambia de dónde salen las llaves (una local en vez del JWKS). */
    @Bean
    @Primary
    JwtDecoder jwtDecoderPruebas(OpcionesSeguridad opciones) {
        var decodificador = NimbusJwtDecoder.withPublicKey(EmisorTokensPruebas.llavePublica())
                .signatureAlgorithm(SignatureAlgorithm.RS256)
                .build();
        return ConfiguracionSeguridad.conValidaciones(decodificador, opciones);
    }
}
