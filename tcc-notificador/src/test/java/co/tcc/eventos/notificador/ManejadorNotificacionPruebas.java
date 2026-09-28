package co.tcc.eventos.notificador;

import static org.assertj.core.api.Assertions.assertThat;

import co.tcc.eventos.aplicacion.casosuso.NotificarCambioEstado;
import co.tcc.eventos.aplicacion.falsos.FalsosNotificacion.DirectorioFijo;
import co.tcc.eventos.aplicacion.falsos.FalsosNotificacion.ProveedorEnMemoria;
import co.tcc.eventos.aplicacion.falsos.FalsosNotificacion.RegistroEnMemoria;
import co.tcc.eventos.aplicacion.puertos.notificacion.DestinoRechazadoException;
import co.tcc.eventos.aplicacion.puertos.notificacion.ProveedorNoDisponibleException;
import co.tcc.eventos.contratos.v1.CanalesV1;
import co.tcc.eventos.contratos.v1.EstadoGuiaCambiadoV1;
import co.tcc.eventos.contratos.v1.EstadosV1;
import co.tcc.eventos.contratos.v1.NotificacionPendienteV1;
import co.tcc.eventos.dominio.notificaciones.CanalNotificacion;
import co.tcc.eventos.dominio.notificaciones.Contacto;
import co.tcc.eventos.infraestructura.ciclovida.SenalApagado;
import co.tcc.eventos.infraestructura.kafka.MensajeKafka;
import co.tcc.eventos.infraestructura.mapeo.JsonContratos;
import co.tcc.eventos.notificador.enrutamiento.ManejadorNotificacion;
import co.tcc.eventos.notificador.enrutamiento.OpcionesNotificador;
import co.tcc.eventos.notificador.enrutamiento.OpcionesNotificador.EtapaReintento;
import java.time.Clock;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;

class ManejadorNotificacionPruebas {

    private static final OffsetDateTime EPOCA = OffsetDateTime.of(1970, 1, 1, 0, 0, 0, 0, ZoneOffset.UTC);

    private final RegistroEnMemoria registro = new RegistroEnMemoria();
    private final ProveedorEnMemoria sms = new ProveedorEnMemoria(CanalNotificacion.SMS);
    private final ProveedorEnMemoria correo = new ProveedorEnMemoria(CanalNotificacion.CORREO);
    private final EnrutadorEnMemoria enrutador = new EnrutadorEnMemoria();

    static OpcionesNotificador opciones() {
        var opciones = new OpcionesNotificador();
        opciones.setReintentos(List.of(
                new EtapaReintento("r1", Duration.ofMinutes(1)),
                new EtapaReintento("r2", Duration.ofMinutes(10)),
                new EtapaReintento("r3", Duration.ofHours(1))));
        return opciones;
    }

    private ManejadorNotificacion manejador() {
        return manejador(new Contacto("3001234567", "c@correo.co"));
    }

    private ManejadorNotificacion manejador(Contacto contacto) {
        var notificar = new NotificarCambioEstado(registro, new DirectorioFijo(contacto), List.of(sms, correo));
        return new ManejadorNotificacion(notificar, enrutador, opciones(), new SenalApagado(), Clock.systemUTC());
    }

    private static EstadoGuiaCambiadoV1 cambio(String estado) {
        return new EstadoGuiaCambiadoV1(UUID.randomUUID(), "TCC123", EstadosV1.EN_BODEGA_DESTINO, estado, EPOCA, "TRANSPORTE", null, 3);
    }

    private static EstadoGuiaCambiadoV1 cambio() {
        return cambio(EstadosV1.EN_REPARTO);
    }

    private static MensajeKafka deCambio(EstadoGuiaCambiadoV1 cambio) {
        return new MensajeKafka("guias.estados.cambiados", 0, 1, cambio.numeroGuia(), JsonContratos.escribir(cambio));
    }

    private static MensajeKafka deReintento(NotificacionPendienteV1 pendiente) {
        return new MensajeKafka("r", 0, 1, pendiente.cambio().numeroGuia(), JsonContratos.escribir(pendiente));
    }

    private static Supplier<RuntimeException> caido() {
        return () -> new ProveedorNoDisponibleException("503");
    }

    @Test
    void con_el_proveedor_disponible_notifica_por_sms_sin_reintentos() {
        manejador().manejarCambio(deCambio(cambio()));

        assertThat(sms.enviados).hasSize(1);
        assertThat(enrutador.reintentos).isEmpty();
        assertThat(enrutador.dlq).isEmpty();
    }

    @Test
    void un_estado_interno_no_genera_nada() {
        manejador().manejarCambio(deCambio(cambio(EstadosV1.EN_TRANSITO)));

        assertThat(sms.enviados).isEmpty();
        assertThat(enrutador.reintentos).isEmpty();
    }

    @Test
    void con_el_sms_caido_programa_el_primer_reintento_sin_bloquear() {
        sms.falla = caido();

        manejador().manejarCambio(deCambio(cambio()));

        assertThat(enrutador.reintentos).singleElement().satisfies(r -> {
            assertThat(r.canal()).isEqualTo(CanalesV1.SMS);
            assertThat(r.intento()).isEqualTo(1);
        });
    }

    @Test
    void cada_reintento_fallido_avanza_a_la_siguiente_etapa() {
        sms.falla = caido();

        manejador().manejarReintento(deReintento(new NotificacionPendienteV1(cambio(), CanalesV1.SMS, 1, null)));

        assertThat(enrutador.reintentos).singleElement().satisfies(r -> assertThat(r.intento()).isEqualTo(2));
    }

    @Test
    void agotados_los_reintentos_del_sms_se_usa_el_correo() {
        sms.falla = caido();

        manejador().manejarReintento(deReintento(new NotificacionPendienteV1(cambio(), CanalesV1.SMS, 3, null)));

        assertThat(correo.enviados).hasSize(1);
        assertThat(enrutador.dlq).isEmpty();
    }

    @Test
    void si_el_correo_tambien_falla_tiene_su_propia_escalera_y_al_final_va_a_la_dlq() {
        sms.falla = caido();
        correo.falla = caido();

        manejador().manejarReintento(deReintento(new NotificacionPendienteV1(cambio(), CanalesV1.SMS, 3, null)));
        manejador().manejarReintento(deReintento(new NotificacionPendienteV1(cambio(), CanalesV1.CORREO, 3, null)));

        assertThat(enrutador.reintentos.get(0).canal()).isEqualTo(CanalesV1.CORREO);
        assertThat(enrutador.reintentos.get(0).intento()).isEqualTo(1);
        assertThat(enrutador.dlq).singleElement().satisfies(r -> assertThat(r.motivo()).contains("Reintentos agotados"));
    }

    @Test
    void sin_telefono_va_directo_al_correo() {
        manejador(new Contacto(null, "c@correo.co")).manejarCambio(deCambio(cambio()));

        assertThat(sms.enviados).isEmpty();
        assertThat(correo.enviados).hasSize(1);
    }

    @Test
    void un_destino_rechazado_no_se_reintenta_por_el_mismo_canal() {
        sms.falla = () -> new DestinoRechazadoException("número inválido");

        manejador().manejarCambio(deCambio(cambio()));

        assertThat(enrutador.reintentos).isEmpty();
        assertThat(correo.enviados).hasSize(1);
    }

    @Test
    void un_mensaje_ilegible_va_a_la_dlq() {
        manejador().manejarCambio(new MensajeKafka("t", 0, 1, "TCC1", "{roto"));

        assertThat(enrutador.dlq).singleElement().satisfies(r -> assertThat(r.contenido()).isEqualTo("{roto"));
    }

    @Test
    void si_kafka_no_acepta_el_reintento_se_insiste_hasta_lograrlo() {
        sms.falla = caido();
        enrutador.fallasPendientes = 2;

        manejador().manejarCambio(deCambio(cambio()));

        assertThat(enrutador.reintentos).hasSize(1);
    }
}
