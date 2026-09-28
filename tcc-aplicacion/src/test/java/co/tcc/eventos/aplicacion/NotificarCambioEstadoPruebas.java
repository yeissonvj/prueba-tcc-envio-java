package co.tcc.eventos.aplicacion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import co.tcc.eventos.aplicacion.casosuso.NotificarCambioEstado;
import co.tcc.eventos.aplicacion.casosuso.ResultadoNotificacion;
import co.tcc.eventos.aplicacion.falsos.FalsosNotificacion.DirectorioFijo;
import co.tcc.eventos.aplicacion.falsos.FalsosNotificacion.ProveedorEnMemoria;
import co.tcc.eventos.aplicacion.falsos.FalsosNotificacion.RegistroEnMemoria;
import co.tcc.eventos.aplicacion.puertos.notificacion.ProveedorNoDisponibleException;
import co.tcc.eventos.dominio.EstadoGuia;
import co.tcc.eventos.dominio.notificaciones.CambioEstadoGuia;
import co.tcc.eventos.dominio.notificaciones.CanalNotificacion;
import co.tcc.eventos.dominio.notificaciones.Contacto;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class NotificarCambioEstadoPruebas {

    private final RegistroEnMemoria registro = new RegistroEnMemoria();
    private final ProveedorEnMemoria sms = new ProveedorEnMemoria(CanalNotificacion.SMS);
    private final ProveedorEnMemoria correo = new ProveedorEnMemoria(CanalNotificacion.CORREO);

    private NotificarCambioEstado casoUso() {
        return casoUso(new Contacto("3001234567", "cliente@correo.co"));
    }

    private NotificarCambioEstado casoUso(Contacto contacto) {
        return new NotificarCambioEstado(registro, new DirectorioFijo(contacto), List.of(sms, correo));
    }

    private static CambioEstadoGuia cambio(EstadoGuia estado) {
        return cambio(estado, 1);
    }

    private static CambioEstadoGuia cambio(EstadoGuia estado, long version) {
        return new CambioEstadoGuia(UUID.randomUUID(), "TCC123", null, estado, Datos.EPOCA, version);
    }

    @Test
    void envia_por_el_canal_pedido_con_la_clave_de_idempotencia_y_lo_registra() {
        var resultado = casoUso().ejecutar(cambio(EstadoGuia.EN_REPARTO), CanalNotificacion.SMS);

        assertThat(resultado).isEqualTo(ResultadoNotificacion.ENVIADA);
        assertThat(sms.enviados).singleElement().satisfies(mensaje -> {
            assertThat(mensaje.claveIdempotencia()).isEqualTo("TCC123:1:SMS");
            assertThat(mensaje.destino()).isEqualTo("3001234567");
        });
        assertThat(registro.enviadas).hasSize(1);
    }

    @Test
    void un_estado_interno_no_se_notifica() {
        var resultado = casoUso().ejecutar(cambio(EstadoGuia.EN_TRANSITO), CanalNotificacion.SMS);

        assertThat(resultado).isEqualTo(ResultadoNotificacion.NO_APLICA);
        assertThat(sms.enviados).isEmpty();
    }

    @Test
    void la_misma_notificacion_no_se_envia_dos_veces() {
        var cambio = cambio(EstadoGuia.ENTREGADA);
        casoUso().ejecutar(cambio, CanalNotificacion.SMS);

        var resultado = casoUso().ejecutar(cambio, CanalNotificacion.SMS);

        assertThat(resultado).isEqualTo(ResultadoNotificacion.YA_ENVIADA);
        assertThat(sms.enviados).hasSize(1);
    }

    @Test
    void un_reintento_viejo_no_se_envia_si_ya_se_notifico_algo_mas_reciente() {
        casoUso().ejecutar(cambio(EstadoGuia.ENTREGADA, 7), CanalNotificacion.SMS);

        var resultado = casoUso().ejecutar(cambio(EstadoGuia.EN_REPARTO, 6), CanalNotificacion.SMS);

        assertThat(resultado).isEqualTo(ResultadoNotificacion.OBSOLETA);
        assertThat(sms.enviados).hasSize(1);
    }

    @Test
    void sin_telefono_no_intenta_el_sms() {
        var resultado = casoUso(new Contacto(null, "cliente@correo.co"))
                .ejecutar(cambio(EstadoGuia.EN_REPARTO), CanalNotificacion.SMS);

        assertThat(resultado).isEqualTo(ResultadoNotificacion.SIN_DESTINO);
        assertThat(sms.enviados).isEmpty();
    }

    @Test
    void si_el_proveedor_falla_no_se_registra_como_enviada() {
        sms.falla = () -> new ProveedorNoDisponibleException("SMS caído");

        assertThatThrownBy(() -> casoUso().ejecutar(cambio(EstadoGuia.EN_REPARTO), CanalNotificacion.SMS))
                .isInstanceOf(ProveedorNoDisponibleException.class);
        assertThat(registro.enviadas).isEmpty();
    }
}
