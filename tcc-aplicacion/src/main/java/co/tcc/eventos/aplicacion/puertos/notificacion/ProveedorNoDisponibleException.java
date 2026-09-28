package co.tcc.eventos.aplicacion.puertos.notificacion;

public class ProveedorNoDisponibleException extends RuntimeException {

    public ProveedorNoDisponibleException(String mensaje) {
        super(mensaje);
    }

    public ProveedorNoDisponibleException(String mensaje, Throwable causa) {
        super(mensaje, causa);
    }
}
