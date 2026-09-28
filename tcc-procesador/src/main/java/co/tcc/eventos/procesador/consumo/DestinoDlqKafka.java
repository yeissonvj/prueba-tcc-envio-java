package co.tcc.eventos.procesador.consumo;

import co.tcc.eventos.infraestructura.kafka.MensajeRechazado;
import co.tcc.eventos.infraestructura.kafka.OpcionesKafka;
import co.tcc.eventos.infraestructura.kafka.ProductorKafka;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.Map;

/**
 * Publica el mensaje original, intacto, en guias.eventos.dlq con encabezados para diagnosticarlo
 * y reinyectarlo (redrive) después de corregir la causa. Mismos encabezados que la versión .NET.
 */
public final class DestinoDlqKafka implements DestinoDlq {

    private final ProductorKafka productor;
    private final OpcionesKafka opciones;
    private final Clock reloj;

    public DestinoDlqKafka(ProductorKafka productor, OpcionesKafka opciones, Clock reloj) {
        this.productor = productor;
        this.opciones = opciones;
        this.reloj = reloj;
    }

    @Override
    public void enviar(MensajeRechazado rechazado) {
        var original = rechazado.original();
        productor.publicar(
                opciones.getTopicoEventosDlq(),
                original.clave() == null ? "" : original.clave(),
                original.valor() == null ? "" : original.valor(),
                Map.of(
                        "dlq-motivo", rechazado.motivo(),
                        "dlq-topico-origen", original.topico(),
                        "dlq-particion-origen", Integer.toString(original.particion()),
                        "dlq-offset-origen", Long.toString(original.offset()),
                        "dlq-rechazado-en", OffsetDateTime.now(reloj).toString()));
    }
}
