package co.tcc.eventos.api.errores;

import co.tcc.eventos.aplicacion.puertos.PublicacionFallidaException;
import co.tcc.eventos.infraestructura.observabilidad.Telemetria;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Un manejador por tipo de error (equivale a la cadena de IExceptionHandler de .NET).
 * El detalle técnico va al log, nunca al cliente.
 */
@RestControllerAdvice
public class ManejadorErrores {

    public static final int SEGUNDOS_PARA_REINTENTAR = 5;

    private static final Logger log = LoggerFactory.getLogger(ManejadorErrores.class);

    /** "El evento no quedó durable" → 503 + Retry-After: el emisor conserva el evento y reintenta. */
    @ExceptionHandler(PublicacionFallidaException.class)
    ResponseEntity<ProblemDetail> sinDurabilidad(PublicacionFallidaException ex) {
        log.error("Evento no durable; se responde 503", ex);
        Telemetria.EVENTOS_RECIBIDOS.add(1, Telemetria.etiqueta("resultado", "no_durable"));

        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .header("Retry-After", Integer.toString(SEGUNDOS_PARA_REINTENTAR))
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .body(Problemas.problema(HttpStatus.SERVICE_UNAVAILABLE, "Servicio temporalmente no disponible",
                        "El evento no pudo guardarse de forma durable. Reintente con el mismo idEvento."));
    }

    /**
     * JSON mal formado, tipos incorrectos (p. ej. "idEvento": 123) o cuerpo demasiado grande → 4xx, no 500.
     * No se devuelve el mensaje del parser: puede revelar detalles internos.
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    ResponseEntity<ProblemDetail> peticionInvalida(HttpMessageNotReadableException ex) {
        if (LimiteCuerpo.esExceso(ex))
            return Problemas.respuesta(HttpStatus.CONTENT_TOO_LARGE, "Cuerpo demasiado grande",
                    "El cuerpo supera el tamaño máximo permitido.");

        // Error del emisor, no del sistema: info, para no inundar el log si alguien envía basura.
        log.info("Petición rechazada con 400: {}", ex.getMostSpecificCause().getClass().getSimpleName());
        Telemetria.EVENTOS_RECIBIDOS.add(1, Telemetria.etiqueta("resultado", "rechazado"));
        return Problemas.respuesta(HttpStatus.BAD_REQUEST, "Petición inválida",
                "El cuerpo no es un JSON válido para el contrato EventoGuiaV1.");
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    ResponseEntity<ProblemDetail> tipoNoSoportado(HttpMediaTypeNotSupportedException ex) {
        return Problemas.respuesta(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "Tipo de contenido no soportado",
                "Enviar el evento como application/json.");
    }
}
