package co.tcc.eventos.notificador.enrutamiento;

import co.tcc.eventos.contratos.v1.NotificacionPendienteV1;
import co.tcc.eventos.infraestructura.kafka.MensajeKafka;
import co.tcc.eventos.infraestructura.kafka.ProductorKafka;
import co.tcc.eventos.infraestructura.mapeo.JsonContratos;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.Map;

public final class EnrutadorNotificacionesKafka implements EnrutadorNotificaciones {

    /** Cuándo se puede procesar el reintento (ISO-8601). Mismo encabezado que la versión .NET. */
    public static final String ENCABEZADO_REINTENTAR_DESPUES = "reintentar-despues";

    private final ProductorKafka productor;
    private final OpcionesNotificador opciones;
    private final Clock reloj;

    public EnrutadorNotificacionesKafka(ProductorKafka productor, OpcionesNotificador opciones, Clock reloj) {
        this.productor = productor;
        this.opciones = opciones;
        this.reloj = reloj;
    }

    @Override
    public void programarReintento(NotificacionPendienteV1 pendiente) {
        var etapa = opciones.getReintentos().get(pendiente.intento() - 1);
        var vence = OffsetDateTime.now(reloj).plus(etapa.getEspera());

        productor.publicar(
                etapa.getTopico(),
                pendiente.cambio().numeroGuia(),
                JsonContratos.escribir(pendiente),
                Map.of(ENCABEZADO_REINTENTAR_DESPUES, vence.toString(), "contrato", NotificacionPendienteV1.TIPO));
    }

    @Override
    public void enviarADlq(String clave, String contenido, String motivo) {
        productor.publicar(
                opciones.getTopicoDlq(),
                clave,
                contenido == null ? "" : contenido,
                Map.of("dlq-motivo", motivo, "dlq-rechazado-en", OffsetDateTime.now(reloj).toString()));
    }

    /** Cuándo se puede procesar un mensaje de reintento; null si no trae la marca (se procesa ya). */
    public static OffsetDateTime vencimiento(MensajeKafka mensaje) {
        var texto = mensaje.encabezado(ENCABEZADO_REINTENTAR_DESPUES);
        if (texto == null)
            return null;
        try {
            return OffsetDateTime.parse(texto);
        } catch (DateTimeParseException ex) {
            return null;
        }
    }
}
