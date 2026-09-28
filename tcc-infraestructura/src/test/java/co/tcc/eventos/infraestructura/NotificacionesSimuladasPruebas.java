package co.tcc.eventos.infraestructura;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import co.tcc.eventos.aplicacion.puertos.notificacion.DestinoRechazadoException;
import co.tcc.eventos.aplicacion.puertos.notificacion.MensajeNotificacion;
import co.tcc.eventos.aplicacion.puertos.notificacion.ProveedorNoDisponibleException;
import co.tcc.eventos.dominio.notificaciones.CanalNotificacion;
import co.tcc.eventos.infraestructura.notificaciones.DirectorioContactosSimulado;
import co.tcc.eventos.infraestructura.notificaciones.OpcionesProveedorSimulado;
import co.tcc.eventos.infraestructura.notificaciones.ProveedorSimulado;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class NotificacionesSimuladasPruebas {

    @ParameterizedTest
    @CsvSource({
            "3001234567, 300****567",
            "cliente@correo.co, c***@correo.co",
            "12345, ***"
    })
    void los_destinos_se_enmascaran_en_los_logs(String destino, String esperado) {
        assertThat(ProveedorSimulado.enmascarar(destino)).isEqualTo(esperado);
    }

    @Test
    void el_directorio_es_determinista_y_sin_telefono_para_guias_terminadas_en_0() {
        var directorio = new DirectorioContactosSimulado();

        assertThat(directorio.obtener("TCC123")).isEqualTo(directorio.obtener("TCC123"));
        assertThat(directorio.obtener("TCC123").orElseThrow().telefono()).startsWith("300").hasSize(10);
        assertThat(directorio.obtener("TCC120").orElseThrow().telefono()).isNull();
        assertThat(directorio.obtener("TCC120").orElseThrow().correo()).isEqualTo("cliente.tcc120@correo.test");
    }

    @Test
    void el_proveedor_caido_falla_como_no_disponible() {
        var opciones = new OpcionesProveedorSimulado(0);
        opciones.setCaido(true);

        assertThatThrownBy(() -> new ProveedorSimulado(CanalNotificacion.SMS, opciones).enviar(mensaje("3001234567")))
                .isInstanceOf(ProveedorNoDisponibleException.class);
    }

    @Test
    void un_destino_invalido_se_rechaza_sin_reintento_posible() {
        assertThatThrownBy(() -> new ProveedorSimulado(CanalNotificacion.SMS, new OpcionesProveedorSimulado(0)).enviar(mensaje("0001234567")))
                .isInstanceOf(DestinoRechazadoException.class);
    }

    private static MensajeNotificacion mensaje(String destino) {
        return new MensajeNotificacion("TCC1:1:SMS", destino, "texto");
    }
}
