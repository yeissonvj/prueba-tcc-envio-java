package co.tcc.eventos.api.seguridad;

import org.springframework.security.oauth2.jwt.Jwt;

/**
 * Autorización sobre los datos: un cliente solo puede reportar eventos de los orígenes que le pertenecen.
 * Sin esto, un sistema comprometido podría hacerse pasar por otro en el historial de la guía.
 */
public final class AutorizadorOrigen {

    private final OpcionesSeguridad opciones;

    public AutorizadorOrigen(OpcionesSeguridad opciones) {
        this.opciones = opciones;
    }

    public boolean puedeReportar(Jwt token, String origen) {
        var cliente = token.getClaimAsString(ConstantesSeguridad.RECLAMO_CLIENTE);
        if (cliente == null)
            return false;
        var permitidos = opciones.getOrigenesPorCliente().get(cliente);
        return permitidos != null && permitidos.contains(origen);
    }
}
