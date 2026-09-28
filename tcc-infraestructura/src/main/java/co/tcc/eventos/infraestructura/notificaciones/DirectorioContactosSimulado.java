package co.tcc.eventos.infraestructura.notificaciones;

import co.tcc.eventos.aplicacion.puertos.notificacion.DirectorioContactos;
import co.tcc.eventos.dominio.notificaciones.Contacto;
import java.util.Locale;
import java.util.Optional;

/**
 * Directorio ficticio y determinista (en producción: el servicio de clientes de TCC).
 * Las guías que terminan en 0 no tienen teléfono registrado, para ejercitar el canal alterno.
 * Mismo cálculo que en .NET: la misma guía produce el mismo teléfono en ambas versiones.
 */
public final class DirectorioContactosSimulado implements DirectorioContactos {

    @Override
    public Optional<Contacto> obtener(String numeroGuia) {
        long hash = 17L;
        for (var i = 0; i < numeroGuia.length(); i++)
            hash = (hash * 31 + numeroGuia.charAt(i)) % 1_000_000_007L;

        var sufijo = String.format("%07d", hash % 10_000_000L);
        var telefono = numeroGuia.endsWith("0") ? null : "300" + sufijo;
        return Optional.of(new Contacto(telefono, "cliente." + numeroGuia.toLowerCase(Locale.ROOT) + "@correo.test"));
    }
}
