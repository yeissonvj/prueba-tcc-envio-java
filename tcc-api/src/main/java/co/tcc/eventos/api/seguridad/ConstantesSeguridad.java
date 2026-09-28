package co.tcc.eventos.api.seguridad;

/** Nombres de los claims y alcances del token (los mismos que valida la versión .NET). */
public final class ConstantesSeguridad {

    public static final String RECLAMO_CLIENTE = "azp";
    public static final String ALCANCE_ESCRIBIR_EVENTOS = "eventos:escribir";
    public static final String ALCANCE_LEER_GUIAS = "guias:leer";

    private ConstantesSeguridad() {
    }
}
