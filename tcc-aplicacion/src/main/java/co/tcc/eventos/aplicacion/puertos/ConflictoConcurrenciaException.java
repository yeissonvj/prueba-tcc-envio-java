package co.tcc.eventos.aplicacion.puertos;

/**
 * Otra instancia modificó la guía entre la lectura y la escritura (p. ej. durante un rebalanceo).
 * Es transitoria: al reintentar se relee la guía y el inbox evita el doble efecto.
 */
public class ConflictoConcurrenciaException extends RuntimeException {

    public ConflictoConcurrenciaException(String numeroGuia) {
        super("La guía " + numeroGuia + " cambió durante el procesamiento.");
    }
}
