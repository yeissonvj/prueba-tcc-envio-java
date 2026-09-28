package co.tcc.eventos.infraestructura.integracion;

import static org.assertj.core.api.Assertions.assertThat;

import co.tcc.eventos.aplicacion.Datos;
import co.tcc.eventos.aplicacion.casosuso.ProcesarEvento;
import co.tcc.eventos.aplicacion.puertos.PublicacionFallidaException;
import co.tcc.eventos.aplicacion.puertos.notificacion.EstadoNotificacion;
import co.tcc.eventos.dominio.EstadoGuia;
import co.tcc.eventos.dominio.EventoGuia;
import co.tcc.eventos.dominio.notificaciones.CambioEstadoGuia;
import co.tcc.eventos.dominio.notificaciones.CanalNotificacion;
import co.tcc.eventos.infraestructura.consultas.ConsultaGuiasJdbc;
import co.tcc.eventos.infraestructura.postgres.AlmacenContingenciaJdbc;
import co.tcc.eventos.infraestructura.postgres.RegistroNotificacionesJdbc;
import co.tcc.eventos.infraestructura.postgres.RepositorioGuiasJdbc;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.IntStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class AdaptadoresJdbcPruebas {

    private final InfraestructuraReal infra = InfraestructuraReal.obtener();

    @BeforeEach
    void limpiar() {
        infra.limpiar();
    }

    @Test
    void la_contingencia_es_idempotente_y_reenvia_en_orden_conservando_los_fallidos() throws InterruptedException {
        var almacen = new AlmacenContingenciaJdbc(infra.jdbc(), infra.transaccion());
        var eventos = IntStream.range(0, 4)
                .mapToObj(i -> Datos.evento("TCC10", EstadoGuia.RECOGIDA, Datos.HORA.plusMinutes(i)))
                .toList();
        for (var evento : eventos) {
            almacen.guardar(evento);
            Thread.sleep(5); // recibido_en distinto: el orden de llegada queda definido
        }
        almacen.guardar(eventos.get(0)); // reintento del emisor durante la caída

        var publicados = new ArrayList<UUID>();
        var reenviados = almacen.reenviarPendientes(evento -> {
            if (evento.idEvento().equals(eventos.get(2).idEvento()))
                throw new PublicacionFallidaException("falla simulada");
            publicados.add(evento.idEvento());
        }, 10);

        assertThat(reenviados).isEqualTo(3);
        assertThat(publicados).containsExactly(eventos.get(0).idEvento(), eventos.get(1).idEvento(), eventos.get(3).idEvento());
        assertThat(infra.contar("SELECT count(*) FROM contingencia_eventos WHERE id_evento = ?", eventos.get(2).idEvento())).isEqualTo(1);
        assertThat(infra.contar("SELECT count(*) FROM contingencia_eventos")).isEqualTo(1);
    }

    @Test
    void la_contingencia_conserva_el_evento_completo_con_su_huso_horario() {
        var almacen = new AlmacenContingenciaJdbc(infra.jdbc(), infra.transaccion());
        var evento = new EventoGuia(UUID.randomUUID(), "TCC13", EstadoGuia.NOVEDAD, Datos.HORA, "TRANSPORTE", "Dirección incompleta");
        almacen.guardar(evento);

        var recuperados = new ArrayList<EventoGuia>();
        almacen.reenviarPendientes(recuperados::add, 10);

        assertThat(recuperados).containsExactly(evento);
    }

    @Test
    void la_consulta_devuelve_el_historial_mas_reciente_primero_y_con_limite() {
        var procesar = new ProcesarEvento(new RepositorioGuiasJdbc(infra.jdbc(), infra.transaccion()));
        procesar.ejecutar(Datos.evento("TCC11", EstadoGuia.CREADA, Datos.HORA));
        for (var i = 1; i <= 120; i++)
            procesar.ejecutar(Datos.evento("TCC11", EstadoGuia.CREADA, Datos.HORA.plusMinutes(i))); // inválidos: solo historial

        var consulta = new ConsultaGuiasJdbc(infra.jdbc());
        var guia = consulta.obtener("TCC11").orElseThrow();

        assertThat(guia.historial()).hasSize(ConsultaGuiasJdbc.MAXIMO_HISTORIAL);
        assertThat(guia.historial().get(0).ocurridoEn()).isAtSameInstantAs(Datos.HORA.plusMinutes(120));
        for (var i = 1; i < guia.historial().size(); i++)
            assertThat(guia.historial().get(i - 1).ocurridoEn()).isAfterOrEqualTo(guia.historial().get(i).ocurridoEn());
        assertThat(consulta.obtener("NOEXISTE")).isEmpty();
    }

    @Test
    void el_registro_de_notificaciones_detecta_repetidas_y_obsoletas() {
        var registro = new RegistroNotificacionesJdbc(infra.jdbc());

        assertThat(registro.consultar(cambio(6), CanalNotificacion.SMS)).isEqualTo(EstadoNotificacion.PENDIENTE);
        registro.registrarEnvio(cambio(7), CanalNotificacion.SMS);
        registro.registrarEnvio(cambio(7), CanalNotificacion.SMS); // idempotente

        assertThat(registro.consultar(cambio(7), CanalNotificacion.SMS)).isEqualTo(EstadoNotificacion.YA_ENVIADA);
        assertThat(registro.consultar(cambio(6), CanalNotificacion.CORREO)).isEqualTo(EstadoNotificacion.OBSOLETA);
        assertThat(registro.consultar(cambio(7), CanalNotificacion.CORREO)).isEqualTo(EstadoNotificacion.PENDIENTE);
        assertThat(infra.contar("SELECT count(*) FROM notificaciones_enviadas")).isEqualTo(1);
    }

    @Test
    void dos_instancias_vaciando_la_contingencia_a_la_vez_no_reenvian_el_mismo_evento() throws Exception {
        var almacen = new AlmacenContingenciaJdbc(infra.jdbc(), infra.transaccion());
        for (var i = 0; i < 40; i++)
            almacen.guardar(Datos.evento("TCC14", EstadoGuia.RECOGIDA, Datos.HORA.plusMinutes(i)));

        var publicados = java.util.Collections.synchronizedList(new ArrayList<UUID>());
        try (var hilos = java.util.concurrent.Executors.newVirtualThreadPerTaskExecutor()) {
            var tareas = List.of(
                    hilos.submit(() -> almacen.reenviarPendientes(e -> publicados.add(e.idEvento()), 40)),
                    hilos.submit(() -> almacen.reenviarPendientes(e -> publicados.add(e.idEvento()), 40)));
            for (var tarea : tareas)
                tarea.get();
        }

        assertThat(publicados).hasSize(40).doesNotHaveDuplicates(); // SKIP LOCKED
        assertThat(infra.contar("SELECT count(*) FROM contingencia_eventos")).isZero();
    }

    private static CambioEstadoGuia cambio(long version) {
        return new CambioEstadoGuia(UUID.randomUUID(), "TCC12", null, EstadoGuia.EN_REPARTO, Datos.HORA, version);
    }
}
