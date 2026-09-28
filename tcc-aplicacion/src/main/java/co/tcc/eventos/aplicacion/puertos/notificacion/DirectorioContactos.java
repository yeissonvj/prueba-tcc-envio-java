package co.tcc.eventos.aplicacion.puertos.notificacion;

import co.tcc.eventos.dominio.notificaciones.Contacto;
import java.util.Optional;

public interface DirectorioContactos {

    Optional<Contacto> obtener(String numeroGuia);
}
