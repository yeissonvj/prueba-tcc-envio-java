package co.tcc.eventos.api.endpoints;

import co.tcc.eventos.api.errores.Problemas;
import co.tcc.eventos.api.seguridad.AutorizadorOrigen;
import co.tcc.eventos.api.validacion.ValidadorEventoGuiaV1;
import co.tcc.eventos.aplicacion.casosuso.RecibirEvento;
import co.tcc.eventos.contratos.v1.EventoGuiaV1;
import co.tcc.eventos.contratos.v1.RespuestaRecepcionV1;
import co.tcc.eventos.infraestructura.mapeo.MapeadorEventoGuia;
import co.tcc.eventos.infraestructura.observabilidad.Telemetria;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.net.URI;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/eventos-guia")
@Tag(name = "Eventos de guía")
public class EventosGuiaController {

    private final RecibirEvento recibirEvento;
    private final ValidadorEventoGuiaV1 validador;
    private final AutorizadorOrigen autorizadorOrigen;

    public EventosGuiaController(RecibirEvento recibirEvento, ValidadorEventoGuiaV1 validador, AutorizadorOrigen autorizadorOrigen) {
        this.recibirEvento = recibirEvento;
        this.validador = validador;
        this.autorizadorOrigen = autorizadorOrigen;
    }

    @PostMapping
    @Operation(summary = "Recibe un evento de estado de guía", description = """
            Requiere un token OAuth2 (client credentials) con el alcance eventos:escribir.
            202: guardado de forma durable; se procesará en segundos (consultar la URL de Location).
            200: el idEvento ya se había recibido; no tiene efecto adicional.
            400: no cumple el contrato V1; no reintentar sin corregir.
            401: sin token o token inválido (firma, emisor, audiencia o vigencia).
            403: el token no tiene eventos:escribir, o el cliente no puede reportar ese origen.
            413: el cuerpo supera 64 KB.
            429: se superó el límite de peticiones del cliente; reintentar tras Retry-After.
            503: no se pudo guardar de forma durable; reintentar con el mismo idEvento tras Retry-After.""")
    public ResponseEntity<?> recibir(@RequestBody EventoGuiaV1 evento, @AuthenticationPrincipal Jwt token) {
        var errores = validador.validar(evento);
        if (!errores.isEmpty()) {
            Telemetria.EVENTOS_RECIBIDOS.add(1, Telemetria.etiqueta("resultado", "rechazado"));
            return Problemas.validacion("El evento no cumple el contrato V1", errores);
        }

        if (!autorizadorOrigen.puedeReportar(token, evento.origen()))
            return Problemas.respuesta(HttpStatus.FORBIDDEN, "Origen no permitido",
                    "El cliente autenticado no puede reportar eventos con ese origen.");

        var resultado = recibirEvento.ejecutar(MapeadorEventoGuia.aDominio(evento));
        Telemetria.EVENTOS_RECIBIDOS.add(1, Telemetria.etiqueta("resultado", Telemetria.texto(resultado)));

        return switch (resultado) {
            case ACEPTADO -> ResponseEntity.accepted()
                    .location(URI.create("/api/v1/guias/" + evento.numeroGuia()))
                    .body(new RespuestaRecepcionV1(evento.idEvento(), RespuestaRecepcionV1.ACEPTADO));
            case DUPLICADO -> ResponseEntity.ok(new RespuestaRecepcionV1(evento.idEvento(), RespuestaRecepcionV1.DUPLICADO));
        };
    }
}
