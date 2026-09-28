package co.tcc.eventos.aplicacion;

import co.tcc.eventos.dominio.EstadoGuia;
import co.tcc.eventos.dominio.EventoGuia;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

/** Datos de prueba compartidos. */
public final class Datos {

    public static final OffsetDateTime EPOCA = OffsetDateTime.of(1970, 1, 1, 0, 0, 0, 0, ZoneOffset.UTC);
    public static final OffsetDateTime HORA = OffsetDateTime.of(2026, 11, 30, 10, 0, 0, 0, ZoneOffset.ofHours(-5));

    private Datos() {
    }

    public static EventoGuia evento(String guia, EstadoGuia estado, OffsetDateTime cuando) {
        return new EventoGuia(UUID.randomUUID(), guia, estado, cuando, "TMS");
    }

    public static EventoGuia nuevoEvento() {
        return evento("TCC123", EstadoGuia.RECOGIDA, EPOCA);
    }
}
