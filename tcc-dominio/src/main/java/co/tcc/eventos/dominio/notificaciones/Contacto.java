package co.tcc.eventos.dominio.notificaciones;

/**
 * Datos de contacto del destinatario. No viajan en los eventos (Ley 1581): se consultan al enviar.
 * Cualquiera de los dos puede ser null.
 */
public record Contacto(String telefono, String correo) {
}
