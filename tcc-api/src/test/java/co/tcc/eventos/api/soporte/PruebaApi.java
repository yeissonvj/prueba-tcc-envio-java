package co.tcc.eventos.api.soporte;

import co.tcc.eventos.aplicacion.falsos.PublicadorFalso;
import co.tcc.eventos.api.soporte.Falsos.ConsultaGuiasEnMemoria;
import co.tcc.eventos.api.soporte.Falsos.SondaFalsa;
import co.tcc.eventos.contratos.v1.EventoGuiaV1;
import co.tcc.eventos.infraestructura.mapeo.JsonContratos;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

/** Base de las pruebas de la API: la aplicación real en memoria, con adaptadores falsos. */
public abstract class PruebaApi {

    public static final String RUTA_EVENTOS = "/api/v1/eventos-guia";

    @Target(ElementType.TYPE)
    @Retention(RetentionPolicy.RUNTIME)
    @SpringBootTest(properties = {
            "api.relay-contingencia-habilitado=false",
            "spring.datasource.url=jdbc:postgresql://127.0.0.1:1/sin-base",
            "kafka.servidores=127.0.0.1:1",
            "seguridad.emisor=" + EmisorTokensPruebas.EMISOR
    })
    @AutoConfigureMockMvc
    @Import(ConfiguracionPruebas.class)
    public @interface ApiEnMemoria {
    }

    @Autowired
    protected MockMvc mvc;

    @Autowired
    protected PublicadorFalso publicador;

    @Autowired
    protected ConsultaGuiasEnMemoria consultaGuias;

    @Autowired
    @Qualifier("sondaKafkaFalsa")
    protected SondaFalsa sondaKafka;

    @Autowired
    @Qualifier("sondaContingenciaFalsa")
    protected SondaFalsa sondaContingencia;

    @Autowired
    @Qualifier("sondaFiltroFalsa")
    protected SondaFalsa sondaFiltro;

    @BeforeEach
    void restablecerFalsos() {
        publicador.fallar = false;
        sondaKafka.disponible = true;
        sondaContingencia.disponible = true;
        sondaFiltro.disponible = true;
    }

    protected static EventoGuiaV1 eventoValido() {
        return eventoValido("TMS");
    }

    protected static EventoGuiaV1 eventoValido(String origen) {
        return new EventoGuiaV1(UUID.randomUUID(), "TCC123456789", "EN_REPARTO",
                OffsetDateTime.now(ZoneOffset.UTC).minusMinutes(1), origen, null);
    }

    protected static MockHttpServletRequestBuilder enviar(EventoGuiaV1 evento, String token) {
        return enviarTexto(JsonContratos.escribir(evento), token);
    }

    protected static MockHttpServletRequestBuilder enviarTexto(String cuerpo, String token) {
        var peticion = MockMvcRequestBuilders.post(RUTA_EVENTOS).contentType(MediaType.APPLICATION_JSON).content(cuerpo);
        return token == null ? peticion : peticion.header("Authorization", "Bearer " + token);
    }

    protected static MockHttpServletRequestBuilder consultar(String numeroGuia, String token) {
        var peticion = MockMvcRequestBuilders.get("/api/v1/guias/{numero}", numeroGuia);
        return token == null ? peticion : peticion.header("Authorization", "Bearer " + token);
    }
}
