package co.tcc.eventos.dominio;

/** Qué ocurrió al intentar aplicar un evento a una guía. */
public enum ResultadoAplicacion {
    /** El estado de la guía cambió. */
    APLICADO,

    /** El evento es anterior al último aplicado: se guarda en historial, no cambia el estado. */
    TARDIO,

    /** El cambio de estado no está permitido. */
    TRANSICION_INVALIDA
}
