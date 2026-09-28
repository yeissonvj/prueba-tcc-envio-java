package co.tcc.eventos.api.salud;

import io.swagger.v3.oas.annotations.Hidden;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/salud")
@Hidden
public class SaludController {

    private final VerificacionesSalud verificaciones;

    public SaludController(VerificacionesSalud verificaciones) {
        this.verificaciones = verificaciones;
    }

    /** Vida: sin dependencias externas. Si revisara Kafka, una caída de Kafka reiniciaría todos los pods. */
    @GetMapping("/viva")
    public ResponseEntity<String> viva() {
        return ResponseEntity.ok().contentType(MediaType.TEXT_PLAIN).body(EstadoSalud.HEALTHY.texto());
    }

    /**
     * Lista: Degraded sigue recibiendo tráfico (200); solo Unhealthy lo saca del balanceador (503).
     * La respuesta es solo el estado: qué dependencia falló no se expone.
     */
    @GetMapping("/lista")
    public ResponseEntity<String> lista() {
        var estado = verificaciones.lista();
        return ResponseEntity.status(estado == EstadoSalud.UNHEALTHY ? 503 : 200)
                .contentType(MediaType.TEXT_PLAIN)
                .body(estado.texto());
    }
}
