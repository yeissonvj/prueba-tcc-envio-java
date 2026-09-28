package co.tcc.eventos.infraestructura.integracion;

import static co.tcc.eventos.dominio.EstadoGuia.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import co.tcc.eventos.aplicacion.Datos;
import co.tcc.eventos.aplicacion.casosuso.ProcesarEvento;
import co.tcc.eventos.aplicacion.casosuso.ResultadoProcesamiento;
import co.tcc.eventos.aplicacion.puertos.ConflictoConcurrenciaException;
import co.tcc.eventos.dominio.EstadoGuia;
import co.tcc.eventos.dominio.EventoGuia;
import co.tcc.eventos.dominio.ResultadoAplicacion;
import co.tcc.eventos.infraestructura.postgres.RepositorioGuiasJdbc;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class RepositorioGuiasJdbcPruebas {

    private static final OffsetDateTime HORA = Datos.HORA;

    private final InfraestructuraReal infra = InfraestructuraReal.obtener();
    private final RepositorioGuiasJdbc repositorio = new RepositorioGuiasJdbc(infra.jdbc(), infra.transaccion());
    private final ProcesarEvento casoUso = new ProcesarEvento(repositorio);

    @BeforeEach
    void limpiar() {
        infra.limpiar();
    }

    private static EventoGuia evento(String guia, EstadoGuia estado, OffsetDateTime cuando) {
        return Datos.evento(guia, estado, cuando);
    }

    @Test
    void cada_cambio_aplicado_deja_historial_estado_y_mensaje_en_la_bandeja_en_una_transaccion() {
        casoUso.ejecutar(evento("TCC1", EN_BODEGA_DESTINO, HORA));
        casoUso.ejecutar(evento("TCC1", EN_REPARTO, HORA.plusHours(1)));

        var guia = repositorio.obtener("TCC1").orElseThrow();
        assertThat(guia.estadoActual()).isEqualTo(EN_REPARTO);
        assertThat(guia.version()).isEqualTo(2);
        assertThat(guia.ultimoEventoEn()).isAtSameInstantAs(HORA.plusHours(1));
        assertThat(infra.contar("SELECT count(*) FROM historial_eventos WHERE numero_guia = 'TCC1'")).isEqualTo(2);
        assertThat(infra.contar("SELECT count(*) FROM bandeja_salida WHERE numero_guia = 'TCC1'")).isEqualTo(2);
    }

    @Test
    void cincuenta_procesamientos_concurrentes_del_mismo_evento_producen_un_solo_efecto() throws Exception {
        var evento = evento("TCC2", RECOGIDA, HORA);

        var resultados = new ArrayList<ResultadoProcesamiento>();
        try (var hilos = Executors.newVirtualThreadPerTaskExecutor()) {
            var tareas = new ArrayList<Future<ResultadoProcesamiento>>();
            for (var i = 0; i < 50; i++)
                tareas.add(hilos.submit(() -> procesarConReintentos(evento)));
            for (var tarea : tareas)
                resultados.add(tarea.get());
        }

        assertThat(resultados).filteredOn(r -> r == ResultadoProcesamiento.APLICADO).hasSize(1);
        assertThat(resultados).filteredOn(r -> r == ResultadoProcesamiento.DUPLICADO).hasSize(49);
        assertThat(infra.contar("SELECT count(*) FROM historial_eventos WHERE id_evento = ?", evento.idEvento())).isEqualTo(1);
        assertThat(infra.contar("SELECT count(*) FROM bandeja_salida WHERE numero_guia = 'TCC2'")).isEqualTo(1);
        assertThat(infra.contar("SELECT version FROM guias WHERE numero_guia = 'TCC2'")).isEqualTo(1);
    }

    @Test
    void una_escritura_sobre_una_version_vieja_falla_y_no_deja_nada_a_medias() {
        casoUso.ejecutar(evento("TCC3", EN_BODEGA_DESTINO, HORA));
        var copiaA = repositorio.obtener("TCC3").orElseThrow();
        var copiaB = repositorio.obtener("TCC3").orElseThrow();

        var eventoA = evento("TCC3", EN_REPARTO, HORA.plusHours(1));
        copiaA.aplicar(eventoA);
        repositorio.guardar(copiaA, eventoA, ResultadoAplicacion.APLICADO, EN_BODEGA_DESTINO);

        var eventoB = evento("TCC3", NOVEDAD, HORA.plusHours(1));
        copiaB.aplicar(eventoB);
        assertThatThrownBy(() -> repositorio.guardar(copiaB, eventoB, ResultadoAplicacion.APLICADO, EN_BODEGA_DESTINO))
                .isInstanceOf(ConflictoConcurrenciaException.class);

        // Rollback completo: ni historial ni bandeja del evento perdedor.
        assertThat(infra.contar("SELECT count(*) FROM historial_eventos WHERE id_evento = ?", eventoB.idEvento())).isZero();
        assertThat(infra.contar("SELECT count(*) FROM bandeja_salida WHERE numero_guia = 'TCC3'")).isEqualTo(2);
        assertThat(repositorio.obtener("TCC3").orElseThrow().estadoActual()).isEqualTo(EN_REPARTO);
    }

    @ParameterizedTest
    @CsvSource({"RECOGIDA, -3, TARDIO", "CREADA, 3, TRANSICION_INVALIDA"})
    void tardios_e_invalidos_quedan_en_historial_sin_cambiar_estado_ni_publicar(
            EstadoGuia estado, int horas, String resultadoEsperado) {
        casoUso.ejecutar(evento("TCC4", EN_REPARTO, HORA));

        casoUso.ejecutar(evento("TCC4", estado, HORA.plusHours(horas)));

        assertThat(infra.contar("SELECT count(*) FROM historial_eventos WHERE resultado = ?", resultadoEsperado)).isEqualTo(1);
        assertThat(infra.contar("SELECT count(*) FROM bandeja_salida")).isEqualTo(1);
        assertThat(repositorio.obtener("TCC4").orElseThrow().estadoActual()).isEqualTo(EN_REPARTO);
    }

    @Test
    void el_cambio_en_la_bandeja_conserva_el_huso_horario_original_del_evento() {
        casoUso.ejecutar(evento("TCC5", RECOGIDA, HORA));

        var carga = infra.jdbc().sql("SELECT carga->>'ocurridoEn' FROM bandeja_salida WHERE numero_guia = 'TCC5'")
                .query(String.class).single();

        assertThat(carga).isEqualTo("2026-11-30T10:00:00-05:00");
    }

    // Lo mismo que hace el consumidor real: un conflicto se reintenta y el inbox lo resuelve.
    private ResultadoProcesamiento procesarConReintentos(EventoGuia evento) {
        while (true) {
            try {
                return casoUso.ejecutar(evento);
            } catch (ConflictoConcurrenciaException reintentar) {
                // El siguiente intento relee la guía y el inbox.
            }
        }
    }
}
