package co.tcc.eventos.infraestructura.mapeo;

import co.tcc.eventos.contratos.v1.EstadoGuiaCambiadoV1;
import co.tcc.eventos.contratos.v1.EstadosV1;
import co.tcc.eventos.contratos.v1.EventoGuiaV1;
import co.tcc.eventos.dominio.EstadoGuia;
import co.tcc.eventos.dominio.EventoGuia;
import co.tcc.eventos.dominio.Guia;
import co.tcc.eventos.dominio.ResultadoAplicacion;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;

/**
 * Traduce entre el formato externo (contrato V1) y el modelo del dominio.
 * La tabla es explícita a propósito: renombrar un valor del enum del dominio no puede cambiar el contrato.
 */
public final class MapeadorEventoGuia {

    private static final Map<EstadoGuia, String> A_TEXTO = new EnumMap<>(EstadoGuia.class);
    private static final Map<String, EstadoGuia> DESDE_TEXTO = new HashMap<>();

    static {
        A_TEXTO.put(EstadoGuia.CREADA, EstadosV1.CREADA);
        A_TEXTO.put(EstadoGuia.RECOGIDA, EstadosV1.RECOGIDA);
        A_TEXTO.put(EstadoGuia.EN_BODEGA_ORIGEN, EstadosV1.EN_BODEGA_ORIGEN);
        A_TEXTO.put(EstadoGuia.EN_TRANSITO, EstadosV1.EN_TRANSITO);
        A_TEXTO.put(EstadoGuia.EN_BODEGA_DESTINO, EstadosV1.EN_BODEGA_DESTINO);
        A_TEXTO.put(EstadoGuia.EN_REPARTO, EstadosV1.EN_REPARTO);
        A_TEXTO.put(EstadoGuia.ENTREGADA, EstadosV1.ENTREGADA);
        A_TEXTO.put(EstadoGuia.NOVEDAD, EstadosV1.NOVEDAD);
        A_TEXTO.put(EstadoGuia.REINTENTO_ENTREGA, EstadosV1.REINTENTO_ENTREGA);
        A_TEXTO.put(EstadoGuia.DEVUELTA, EstadosV1.DEVUELTA);
        A_TEXTO.forEach((estado, texto) -> DESDE_TEXTO.put(texto, estado));
    }

    private MapeadorEventoGuia() {
    }

    public static boolean esEstadoValido(String estado) {
        return estado != null && DESDE_TEXTO.containsKey(estado);
    }

    public static String estadoATexto(EstadoGuia estado) {
        return A_TEXTO.get(estado);
    }

    public static EstadoGuia estadoDesdeTexto(String estado) {
        var valor = estado == null ? null : DESDE_TEXTO.get(estado);
        if (valor == null)
            throw new IllegalArgumentException("Estado no reconocido: '" + estado + "'.");
        return valor;
    }

    public static String resultadoATexto(ResultadoAplicacion resultado) {
        return switch (resultado) {
            case APLICADO -> "APLICADO";
            case TARDIO -> "TARDIO";
            case TRANSICION_INVALIDA -> "TRANSICION_INVALIDA";
        };
    }

    /** @param estadoAnterior null cuando el evento creó la guía */
    public static EstadoGuiaCambiadoV1 aCambioEstado(Guia guia, EventoGuia causa, EstadoGuia estadoAnterior) {
        return new EstadoGuiaCambiadoV1(
                causa.idEvento(),
                guia.numeroGuia(),
                estadoAnterior == null ? null : A_TEXTO.get(estadoAnterior),
                A_TEXTO.get(guia.estadoActual()),
                causa.ocurridoEn(),
                causa.origen(),
                causa.novedad(),
                guia.version());
    }

    public static EventoGuia aDominio(EventoGuiaV1 c) {
        return new EventoGuia(c.idEvento(), c.numeroGuia(), estadoDesdeTexto(c.estado()), c.ocurridoEn(), c.origen(), c.novedad());
    }

    public static EventoGuiaV1 aContrato(EventoGuia e) {
        return new EventoGuiaV1(e.idEvento(), e.numeroGuia(), A_TEXTO.get(e.estado()), e.ocurridoEn(), e.origen(), e.novedad());
    }
}
