package co.tcc.eventos.contratos.v1;

import java.util.List;

/** Textos de estado acordados con los sistemas de TCC en la versión 1 del contrato. */
public final class EstadosV1 {

    public static final String CREADA = "CREADA";
    public static final String RECOGIDA = "RECOGIDA";
    public static final String EN_BODEGA_ORIGEN = "EN_BODEGA_ORIGEN";
    public static final String EN_TRANSITO = "EN_TRANSITO";
    public static final String EN_BODEGA_DESTINO = "EN_BODEGA_DESTINO";
    public static final String EN_REPARTO = "EN_REPARTO";
    public static final String ENTREGADA = "ENTREGADA";
    public static final String NOVEDAD = "NOVEDAD";
    public static final String REINTENTO_ENTREGA = "REINTENTO_ENTREGA";
    public static final String DEVUELTA = "DEVUELTA";

    public static final List<String> TODOS = List.of(
            CREADA, RECOGIDA, EN_BODEGA_ORIGEN, EN_TRANSITO, EN_BODEGA_DESTINO,
            EN_REPARTO, ENTREGADA, NOVEDAD, REINTENTO_ENTREGA, DEVUELTA);

    private EstadosV1() {
    }
}
