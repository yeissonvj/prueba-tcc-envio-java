package co.tcc.eventos.procesador.consumo;

import co.tcc.eventos.infraestructura.kafka.MensajeRechazado;

/** Solo termina con éxito si el mensaje rechazado quedó guardado: si no, el offset no puede avanzar. */
public interface DestinoDlq {

    void enviar(MensajeRechazado rechazado);
}
