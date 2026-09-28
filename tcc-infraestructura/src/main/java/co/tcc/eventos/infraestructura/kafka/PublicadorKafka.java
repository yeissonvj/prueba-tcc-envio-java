package co.tcc.eventos.infraestructura.kafka;

import co.tcc.eventos.aplicacion.puertos.PublicadorEventos;
import co.tcc.eventos.contratos.v1.EventoGuiaV1;
import co.tcc.eventos.dominio.EventoGuia;
import co.tcc.eventos.infraestructura.mapeo.JsonContratos;
import co.tcc.eventos.infraestructura.mapeo.MapeadorEventoGuia;
import java.util.Map;

/**
 * Adaptador del puerto {@link PublicadorEventos}: serializa el evento al contrato V1 y lo publica
 * en guias.eventos.recibidos con el número de guía como clave (orden por guía).
 */
public final class PublicadorKafka implements PublicadorEventos {

    private final ProductorKafka productor;
    private final OpcionesKafka opciones;

    public PublicadorKafka(ProductorKafka productor, OpcionesKafka opciones) {
        this.productor = productor;
        this.opciones = opciones;
    }

    @Override
    public void publicar(EventoGuia evento) {
        productor.publicar(
                opciones.getTopicoEventosRecibidos(),
                evento.numeroGuia(),
                JsonContratos.escribir(MapeadorEventoGuia.aContrato(evento)),
                Map.of("idEvento", evento.idEvento().toString(), "contrato", EventoGuiaV1.TIPO));
    }
}
