package co.tcc.eventos.procesador;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import co.tcc.eventos.aplicacion.casosuso.ProcesarEvento;
import co.tcc.eventos.aplicacion.puertos.ConflictoConcurrenciaException;
import co.tcc.eventos.infraestructura.kafka.MensajeKafka;
import co.tcc.eventos.procesador.consumo.ManejadorMensajeRecibido;
import co.tcc.eventos.procesador.consumo.OpcionesConsumidor;
import co.tcc.eventos.infraestructura.ciclovida.SenalApagado;
import co.tcc.eventos.infraestructura.ciclovida.SenalApagado.ServicioDeteniendoseException;
import co.tcc.eventos.procesador.falsos.DlqEnMemoria;
import co.tcc.eventos.procesador.falsos.RepositorioGuiasProgramable;
import java.sql.SQLTransientConnectionException;
import java.time.Clock;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class ManejadorMensajeRecibidoPruebas {

    private final RepositorioGuiasProgramable repositorio = new RepositorioGuiasProgramable();
    private final DlqEnMemoria dlq = new DlqEnMemoria();
    private final SenalApagado apagado = new SenalApagado();
    private final ManejadorMensajeRecibido manejador =
            new ManejadorMensajeRecibido(new ProcesarEvento(repositorio), dlq, opciones(), apagado, Clock.systemUTC());

    // Esperas de 1 ms: se prueba la política, no el reloj.
    private static OpcionesConsumidor opciones() {
        var opciones = new OpcionesConsumidor();
        opciones.setIntentosErrorInesperado(3);
        opciones.setEsperaMinimaMs(1);
        opciones.setEsperaMaximaMs(1);
        return opciones;
    }

    private static MensajeKafka mensaje(String valor) {
        return new MensajeKafka("guias.eventos.recibidos", 7, 42, "TCC123", valor);
    }

    private static String eventoJson() {
        return """
                {"idEvento":"%s","numeroGuia":"TCC123","estado":"EN_REPARTO","ocurridoEn":"2026-09-26T10:15:00-05:00","origen":"TMS"}"""
                .formatted(UUID.randomUUID());
    }

    @Test
    void un_evento_valido_se_procesa_y_no_va_a_la_dlq() {
        manejador.manejar(mensaje(eventoJson()));

        assertThat(repositorio.guardados).hasSize(1);
        assertThat(dlq.rechazados).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "esto no es json",
            "{\"idEvento\":\"0199a1b2-7c3d-7e4f-8a9b-0c1d2e3f4a5b\",\"numeroGuia\":\"TCC1\",\"estado\":\"PERDIDA\",\"ocurridoEn\":\"2026-09-26T10:15:00-05:00\",\"origen\":\"TMS\"}",
            ""
    })
    void un_mensaje_ilegible_va_a_la_dlq_sin_reintentos_y_conserva_su_origen(String valor) {
        manejador.manejar(mensaje(valor));

        assertThat(dlq.rechazados).singleElement().satisfies(rechazado -> {
            assertThat(rechazado.original().particion()).isEqualTo(7);
            assertThat(rechazado.original().offset()).isEqualTo(42);
            assertThat(rechazado.original().valor()).isEqualTo(valor);
        });
        assertThat(repositorio.intentosDeGuardar).isZero();
    }

    @Test
    void un_error_transitorio_se_reintenta_hasta_que_funciona_sin_saltar_el_evento() {
        repositorio.fallarProximas(10, () -> new SQLTransientConnectionExceptionNoVerificada("PostgreSQL no responde"));

        manejador.manejar(mensaje(eventoJson()));

        assertThat(repositorio.intentosDeGuardar).isEqualTo(11);
        assertThat(repositorio.guardados).hasSize(1);
        assertThat(dlq.rechazados).isEmpty();
    }

    @Test
    void un_conflicto_de_concurrencia_se_reintenta() {
        repositorio.fallarProximas(2, () -> new ConflictoConcurrenciaException("TCC123"));

        manejador.manejar(mensaje(eventoJson()));

        assertThat(repositorio.guardados).hasSize(1);
    }

    @Test
    void un_error_inesperado_va_a_la_dlq_tras_los_intentos_configurados() {
        repositorio.fallarProximas(100, () -> new IllegalStateException("bug"));

        manejador.manejar(mensaje(eventoJson()));

        assertThat(repositorio.intentosDeGuardar).isEqualTo(3);
        assertThat(dlq.rechazados).singleElement().satisfies(r -> assertThat(r.motivo()).contains("IllegalStateException"));
    }

    @Test
    void si_la_dlq_falla_se_insiste_hasta_guardar_el_rechazado() {
        dlq.fallasPendientes = 3;

        manejador.manejar(mensaje("basura"));

        assertThat(dlq.rechazados).hasSize(1);
    }

    @Test
    void al_detener_el_servicio_se_interrumpe_el_reintento_sin_marcar_el_mensaje() {
        repositorio.fallarProximas(Integer.MAX_VALUE, () -> new SQLTransientConnectionExceptionNoVerificada("sin base"));
        CompletableFuture.delayedExecutor(50, TimeUnit.MILLISECONDS).execute(apagado::apagar);

        assertThatThrownBy(() -> manejador.manejar(mensaje(eventoJson()))).isInstanceOf(ServicioDeteniendoseException.class);
        assertThat(dlq.rechazados).isEmpty();
    }

    /** Así llega una base caída a través de Spring JDBC: una excepción no verificada que envuelve la de SQL. */
    static final class SQLTransientConnectionExceptionNoVerificada extends RuntimeException {
        SQLTransientConnectionExceptionNoVerificada(String mensaje) {
            super(mensaje, new SQLTransientConnectionException(mensaje));
        }
    }
}
