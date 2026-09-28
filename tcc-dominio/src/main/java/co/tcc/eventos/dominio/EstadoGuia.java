package co.tcc.eventos.dominio;

/** Estados por los que puede pasar una guía de TCC. */
public enum EstadoGuia {
    CREADA,
    RECOGIDA,
    EN_BODEGA_ORIGEN,
    EN_TRANSITO,
    EN_BODEGA_DESTINO,
    EN_REPARTO,
    ENTREGADA,
    NOVEDAD,
    REINTENTO_ENTREGA,
    DEVUELTA
}
