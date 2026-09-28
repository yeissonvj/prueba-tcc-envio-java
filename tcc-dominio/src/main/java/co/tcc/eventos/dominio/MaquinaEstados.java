package co.tcc.eventos.dominio;

import static co.tcc.eventos.dominio.EstadoGuia.*;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/** Define qué cambios de estado son válidos para una guía. */
public final class MaquinaEstados {

    private static final Map<EstadoGuia, Set<EstadoGuia>> TRANSICIONES = new EnumMap<>(EstadoGuia.class);

    static {
        TRANSICIONES.put(CREADA, EnumSet.of(RECOGIDA, NOVEDAD));
        TRANSICIONES.put(RECOGIDA, EnumSet.of(EN_BODEGA_ORIGEN, NOVEDAD));
        TRANSICIONES.put(EN_BODEGA_ORIGEN, EnumSet.of(EN_TRANSITO, NOVEDAD));
        TRANSICIONES.put(EN_TRANSITO, EnumSet.of(EN_BODEGA_DESTINO, NOVEDAD));
        TRANSICIONES.put(EN_BODEGA_DESTINO, EnumSet.of(EN_REPARTO, NOVEDAD));
        TRANSICIONES.put(EN_REPARTO, EnumSet.of(ENTREGADA, NOVEDAD));
        TRANSICIONES.put(NOVEDAD, EnumSet.of(REINTENTO_ENTREGA, DEVUELTA));
        TRANSICIONES.put(REINTENTO_ENTREGA, EnumSet.of(EN_REPARTO, NOVEDAD));
        TRANSICIONES.put(ENTREGADA, EnumSet.noneOf(EstadoGuia.class));
        TRANSICIONES.put(DEVUELTA, EnumSet.noneOf(EstadoGuia.class));
    }

    private MaquinaEstados() {
    }

    public static boolean puedeTransitar(EstadoGuia desde, EstadoGuia hacia) {
        return reglas(desde).contains(hacia);
    }

    public static boolean esEstadoFinal(EstadoGuia estado) {
        return reglas(estado).isEmpty();
    }

    // Un estado nuevo sin reglas es un error de programación, no una transición inválida.
    private static Set<EstadoGuia> reglas(EstadoGuia estado) {
        var reglas = TRANSICIONES.get(estado);
        if (reglas == null)
            throw new IllegalStateException("El estado " + estado + " no tiene reglas de transición.");
        return reglas;
    }
}
