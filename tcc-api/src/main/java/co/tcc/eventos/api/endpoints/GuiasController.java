package co.tcc.eventos.api.endpoints;

import co.tcc.eventos.api.errores.Problemas;
import co.tcc.eventos.api.validacion.ValidadorEventoGuiaV1;
import co.tcc.eventos.infraestructura.consultas.ConsultaGuias;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import java.util.Map;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/guias")
@Tag(name = "Guías")
public class GuiasController {

    private final ConsultaGuias consulta;

    public GuiasController(ConsultaGuias consulta) {
        this.consulta = consulta;
    }

    @GetMapping("/{numeroGuia}")
    @Operation(summary = "Consulta el estado actual y el historial de una guía", description = """
            Requiere un token OAuth2 con el alcance guias:leer.
            Consistencia eventual: tras un 202 de la ingesta, el cambio aparece en segundos (SLO p95 < 5 s);
            mientras tanto puede responder 404 o el estado anterior.
            El historial incluye los 100 eventos más recientes, también los tardíos y los inválidos.""")
    public ResponseEntity<?> obtener(@PathVariable String numeroGuia) {
        if (!ValidadorEventoGuiaV1.esNumeroGuiaValido(numeroGuia))
            return Problemas.validacion("Número de guía inválido",
                    Map.of("numeroGuia", List.of("Solo letras y números, máximo 30 caracteres.")));

        // Datos de envíos de clientes: ningún proxy o navegador intermedio debe guardarlos.
        return consulta.obtener(numeroGuia)
                .<ResponseEntity<?>>map(guia -> ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(guia))
                .orElseGet(() -> Problemas.respuesta(HttpStatus.NOT_FOUND, "Guía no encontrada",
                        "No hay eventos procesados para esa guía. Si se acaba de reportar, reintente en unos segundos."));
    }
}
