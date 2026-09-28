package co.tcc.eventos.api.seguridad;

import co.tcc.eventos.api.errores.Problemas;
import java.time.Duration;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwtClaimValidator;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtIssuerValidator;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.web.SecurityFilterChain;

/**
 * OAuth2 client credentials por sistema emisor. Se valida la firma (solo RS256: sin "alg confusion"),
 * el emisor, la audiencia y la vigencia; después, el alcance de cada ruta. La seguridad vive aquí,
 * fuera de los controladores y del negocio (ADR-0008).
 */
@Configuration(proxyBeanMethods = false)
public class ConfiguracionSeguridad {

    private static final String AUTORIDAD_ESCRIBIR = "SCOPE_" + ConstantesSeguridad.ALCANCE_ESCRIBIR_EVENTOS;
    private static final String AUTORIDAD_LEER = "SCOPE_" + ConstantesSeguridad.ALCANCE_LEER_GUIAS;

    @Bean
    @ConfigurationProperties("seguridad")
    OpcionesSeguridad opcionesSeguridad() {
        return new OpcionesSeguridad();
    }

    @Bean
    SecurityFilterChain cadenaSeguridad(HttpSecurity http, Problemas problemas) throws Exception {
        http
                .csrf(csrf -> csrf.disable())              // API sin cookies ni sesiones: solo tokens Bearer
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(rutas -> rutas
                        .requestMatchers("/salud/**").permitAll()
                        .requestMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/v1/eventos-guia").hasAuthority(AUTORIDAD_ESCRIBIR)
                        .requestMatchers(HttpMethod.GET, "/api/v1/guias/*").hasAuthority(AUTORIDAD_LEER)
                        .requestMatchers("/error").permitAll()
                        .anyRequest().denyAll())
                .oauth2ResourceServer(recurso -> recurso.jwt(jwt -> { }))
                .exceptionHandling(errores -> errores
                        .accessDeniedHandler((peticion, respuesta, ex) -> problemas.escribir(respuesta, HttpStatus.FORBIDDEN,
                                "Sin permiso", "El token no tiene el alcance necesario para esta operación.")));
        return http.build();
    }

    /** Llaves públicas del IdP (JWKS), descargadas en la primera petición y renovadas cuando rota la firma. */
    @Bean
    JwtDecoder jwtDecoder(OpcionesSeguridad opciones) {
        if (opciones.getEmisor() == null || opciones.getEmisor().isBlank())
            throw new IllegalStateException("Falta la configuración 'seguridad.emisor'.");
        var decodificador = NimbusJwtDecoder.withJwkSetUri(opciones.urlLlavesEfectiva())
                .jwsAlgorithm(SignatureAlgorithm.RS256)
                .build();
        return conValidaciones(decodificador, opciones);
    }

    /** Las mismas reglas para el decodificador real y el de las pruebas (llave local). */
    public static NimbusJwtDecoder conValidaciones(NimbusJwtDecoder decodificador, OpcionesSeguridad opciones) {
        decodificador.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
                new JwtTimestampValidator(Duration.ofSeconds(30)),
                new JwtIssuerValidator(opciones.getEmisor()),
                // Rechaza tokens emitidos para otras APIs.
                new JwtClaimValidator<List<String>>("aud", aud -> aud != null && aud.contains(opciones.getAudiencia())),
                token -> token.getExpiresAt() != null
                        ? OAuth2TokenValidatorResult.success()
                        : OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token", "El token no tiene vencimiento.", null))));
        return decodificador;
    }
}
