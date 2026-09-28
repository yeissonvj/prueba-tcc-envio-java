package co.tcc.eventos.aplicacion.puertos.notificacion;

public record MensajeNotificacion(String claveIdempotencia, String destino, String texto) {
}
