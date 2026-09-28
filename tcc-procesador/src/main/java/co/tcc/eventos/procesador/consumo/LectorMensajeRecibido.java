package co.tcc.eventos.procesador.consumo;

import co.tcc.eventos.contratos.v1.EventoGuiaV1;
import co.tcc.eventos.dominio.EventoGuia;
import co.tcc.eventos.infraestructura.mapeo.JsonContratos;
import co.tcc.eventos.infraestructura.mapeo.MapeadorEventoGuia;
import tools.jackson.core.JacksonException;

/**
 * Convierte el mensaje en un evento del dominio o explica por qué no se puede (poison pill).
 * Un mensaje ilegible nunca será legible: reintentarlo solo bloquearía la partición.
 */
public final class LectorMensajeRecibido {

    /** Exactamente uno de los dos es null. */
    public record Lectura(EventoGuia evento, String motivo) {

        static Lectura valida(EventoGuia evento) {
            return new Lectura(evento, null);
        }

        static Lectura ilegible(String motivo) {
            return new Lectura(null, motivo);
        }
    }

    private LectorMensajeRecibido() {
    }

    public static Lectura leer(String valor) {
        if (valor == null || valor.isBlank())
            return Lectura.ilegible("Mensaje vacío.");

        EventoGuiaV1 contrato;
        try {
            contrato = JsonContratos.leer(valor, EventoGuiaV1.class);
        } catch (JacksonException ex) {
            return Lectura.ilegible("JSON inválido: " + ex.getOriginalMessage());
        }

        if (contrato == null)
            return Lectura.ilegible("El mensaje es null.");
        if (contrato.idEvento() == null || (contrato.idEvento().getMostSignificantBits() == 0 && contrato.idEvento().getLeastSignificantBits() == 0))
            return Lectura.ilegible("idEvento vacío.");
        if (contrato.numeroGuia() == null || contrato.numeroGuia().isBlank())
            return Lectura.ilegible("numeroGuia vacío.");
        if (!MapeadorEventoGuia.esEstadoValido(contrato.estado()))
            return Lectura.ilegible("Estado no reconocido: '" + contrato.estado() + "'.");
        if (contrato.ocurridoEn() == null)
            return Lectura.ilegible("ocurridoEn vacío.");
        if (contrato.origen() == null || contrato.origen().isBlank())
            return Lectura.ilegible("origen vacío.");

        return Lectura.valida(MapeadorEventoGuia.aDominio(contrato));
    }
}
