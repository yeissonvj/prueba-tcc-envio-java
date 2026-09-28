package co.tcc.eventos.aplicacion.casosuso;

import co.tcc.eventos.aplicacion.puertos.AlmacenContingencia;
import co.tcc.eventos.aplicacion.puertos.PublicadorEventos;

/**
 * Vacía la contingencia hacia el broker. El publicador que recibe debe ser el directo
 * (sin contingencia): si no, un evento que falla volvería a la misma tabla de la que salió.
 */
public final class ReenviarContingencia {

    private final AlmacenContingencia almacen;
    private final PublicadorEventos publicadorDirecto;

    public ReenviarContingencia(AlmacenContingencia almacen, PublicadorEventos publicadorDirecto) {
        this.almacen = almacen;
        this.publicadorDirecto = publicadorDirecto;
    }

    public int ejecutar(int maximo) {
        return almacen.reenviarPendientes(publicadorDirecto, maximo);
    }
}
