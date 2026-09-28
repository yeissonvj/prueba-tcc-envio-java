package co.tcc.eventos.aplicacion.puertos;

/** El evento NO quedó guardado de forma durable. Quien llama no debe confirmar la recepción. */
public class PublicacionFallidaException extends RuntimeException {

    public PublicacionFallidaException(String mensaje) {
        super(mensaje);
    }

    public PublicacionFallidaException(String mensaje, Throwable causa) {
        super(mensaje, causa);
    }
}
