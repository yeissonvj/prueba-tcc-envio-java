package co.tcc.eventos.dominio;

import java.time.OffsetDateTime;

/**
 * Una guía de envío y su estado actual.
 * Es la única que decide si un evento cambia su estado. Sin setters: el estado solo cambia con {@link #aplicar}.
 */
public final class Guia {

    private final String numeroGuia;
    private EstadoGuia estadoActual;
    private OffsetDateTime ultimoEventoEn;
    private long version;

    private Guia(String numeroGuia, EstadoGuia estado, OffsetDateTime ultimoEventoEn, long version) {
        this.numeroGuia = numeroGuia;
        this.estadoActual = estado;
        this.ultimoEventoEn = ultimoEventoEn;
        this.version = version;
    }

    /** Crea una guía a partir del primer evento que se recibe de ella. */
    public static Guia crear(EventoGuia primerEvento) {
        return new Guia(primerEvento.numeroGuia(), primerEvento.estado(), primerEvento.ocurridoEn(), 1);
    }

    /** Reconstruye una guía guardada en la base de datos. */
    public static Guia reconstruir(String numeroGuia, EstadoGuia estado, OffsetDateTime ultimoEventoEn, long version) {
        return new Guia(numeroGuia, estado, ultimoEventoEn, version);
    }

    public ResultadoAplicacion aplicar(EventoGuia evento) {
        if (!evento.numeroGuia().equals(numeroGuia))
            throw new IllegalArgumentException(
                    "El evento es de la guía " + evento.numeroGuia() + ", no de " + numeroGuia + ".");

        if (evento.ocurridoEn().isBefore(ultimoEventoEn))
            return ResultadoAplicacion.TARDIO;

        if (!MaquinaEstados.puedeTransitar(estadoActual, evento.estado()))
            return ResultadoAplicacion.TRANSICION_INVALIDA;

        estadoActual = evento.estado();
        ultimoEventoEn = evento.ocurridoEn();
        version++;
        return ResultadoAplicacion.APLICADO;
    }

    public String numeroGuia() {
        return numeroGuia;
    }

    public EstadoGuia estadoActual() {
        return estadoActual;
    }

    public OffsetDateTime ultimoEventoEn() {
        return ultimoEventoEn;
    }

    public long version() {
        return version;
    }
}
