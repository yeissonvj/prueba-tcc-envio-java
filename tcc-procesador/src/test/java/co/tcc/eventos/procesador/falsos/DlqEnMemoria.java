package co.tcc.eventos.procesador.falsos;

import co.tcc.eventos.aplicacion.puertos.PublicacionFallidaException;
import co.tcc.eventos.infraestructura.kafka.MensajeRechazado;
import co.tcc.eventos.procesador.consumo.DestinoDlq;
import java.util.ArrayList;
import java.util.List;

public class DlqEnMemoria implements DestinoDlq {

    public final List<MensajeRechazado> rechazados = new ArrayList<>();
    public int fallasPendientes;

    @Override
    public void enviar(MensajeRechazado rechazado) {
        if (fallasPendientes > 0) {
            fallasPendientes--;
            throw new PublicacionFallidaException("DLQ no disponible (simulado)");
        }
        rechazados.add(rechazado);
    }
}
