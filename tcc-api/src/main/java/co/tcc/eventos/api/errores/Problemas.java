package co.tcc.eventos.api.errores;

import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import tools.jackson.databind.json.JsonMapper;

/** Respuestas de error con el mismo cuerpo que la versión .NET: ProblemDetails (RFC 9457). */
public final class Problemas {

    private final JsonMapper json;

    public Problemas(JsonMapper json) {
        this.json = json;
    }

    public static ProblemDetail problema(HttpStatus estado, String titulo, String detalle) {
        var problema = ProblemDetail.forStatusAndDetail(estado, detalle);
        problema.setTitle(titulo);
        return problema;
    }

    /** 400 con los errores por campo en "errors", como el ValidationProblem de .NET. */
    public static ResponseEntity<ProblemDetail> validacion(String titulo, Map<String, List<String>> errores) {
        var problema = ProblemDetail.forStatus(HttpStatus.BAD_REQUEST);
        problema.setTitle(titulo);
        problema.setProperty("errors", errores);
        return respuesta(HttpStatus.BAD_REQUEST, problema);
    }

    public static ResponseEntity<ProblemDetail> respuesta(HttpStatus estado, ProblemDetail problema) {
        return ResponseEntity.status(estado).contentType(MediaType.APPLICATION_PROBLEM_JSON).body(problema);
    }

    public static ResponseEntity<ProblemDetail> respuesta(HttpStatus estado, String titulo, String detalle) {
        return respuesta(estado, problema(estado, titulo, detalle));
    }

    /** Para filtros e interceptores, que escriben la respuesta directamente. */
    public void escribir(HttpServletResponse respuesta, HttpStatus estado, String titulo, String detalle) throws IOException {
        respuesta.setStatus(estado.value());
        respuesta.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        respuesta.setCharacterEncoding("UTF-8");
        respuesta.getWriter().write(json.writeValueAsString(problema(estado, titulo, detalle)));
    }
}
