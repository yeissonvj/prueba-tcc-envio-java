package co.tcc.eventos.notificador.enrutamiento;

import co.tcc.eventos.aplicacion.casosuso.NotificarCambioEstado;
import co.tcc.eventos.aplicacion.casosuso.ResultadoNotificacion;
import co.tcc.eventos.aplicacion.puertos.notificacion.DestinoRechazadoException;
import co.tcc.eventos.contratos.v1.CanalesV1;
import co.tcc.eventos.contratos.v1.EstadoGuiaCambiadoV1;
import co.tcc.eventos.contratos.v1.NotificacionPendienteV1;
import co.tcc.eventos.dominio.notificaciones.CambioEstadoGuia;
import co.tcc.eventos.dominio.notificaciones.CanalNotificacion;
import co.tcc.eventos.dominio.notificaciones.PoliticaNotificacion;
import co.tcc.eventos.infraestructura.ciclovida.SenalApagado;
import co.tcc.eventos.infraestructura.ciclovida.SenalApagado.ServicioDeteniendoseException;
import co.tcc.eventos.infraestructura.kafka.MensajeKafka;
import co.tcc.eventos.infraestructura.mapeo.JsonContratos;
import co.tcc.eventos.infraestructura.mapeo.MapeadorNotificacion;
import co.tcc.eventos.infraestructura.observabilidad.Telemetria;
import co.tcc.eventos.infraestructura.resiliencia.ClasificadorErrores;
import java.time.Clock;
import java.time.Duration;
import java.util.Locale;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.jackson.core.JacksonException;

/**
 * Política de enrutamiento del notificador. NUNCA bloquea: un proveedor caído no puede frenar
 * las notificaciones de las demás guías (al revés que el procesador, donde el orden obliga a esperar).
 * <pre>
 *   éxito                          → listo
 *   proveedor no disponible        → siguiente etapa de reintento (1 min → 10 min → 1 h)
 *   reintentos agotados / destino
 *   rechazado / sin destino        → canal alterno (con su propia escalera) y, si no hay, DLQ
 *   ilegible / error inesperado    → DLQ
 * </pre>
 */
public final class ManejadorNotificacion {

    private static final Logger log = LoggerFactory.getLogger(ManejadorNotificacion.class);

    private final NotificarCambioEstado notificar;
    private final EnrutadorNotificaciones enrutador;
    private final OpcionesNotificador opciones;
    private final SenalApagado apagado;
    private final Clock reloj;

    public ManejadorNotificacion(
            NotificarCambioEstado notificar, EnrutadorNotificaciones enrutador, OpcionesNotificador opciones,
            SenalApagado apagado, Clock reloj) {
        this.notificar = notificar;
        this.enrutador = enrutador;
        this.opciones = opciones;
        this.apagado = apagado;
        this.reloj = reloj;
    }

    /** Desde guias.estados.cambiados: primer intento por el canal principal. */
    public void manejarCambio(MensajeKafka mensaje) {
        var cambio = leer(mensaje.valor(), EstadoGuiaCambiadoV1.class);
        if (cambio == null) {
            insistir(() -> enrutador.enviarADlq(clave(mensaje), mensaje.valor(), "Mensaje ilegible"));
            return;
        }
        intentar(new NotificacionPendienteV1(cambio, CanalesV1.SMS, 0, mensaje.marca()));
    }

    /** Desde notificaciones.reintento.*: el consumidor ya esperó a que venciera. */
    public void manejarReintento(MensajeKafka mensaje) {
        var pendiente = leer(mensaje.valor(), NotificacionPendienteV1.class);
        if (pendiente == null || pendiente.cambio() == null) {
            insistir(() -> enrutador.enviarADlq(clave(mensaje), mensaje.valor(), "Mensaje ilegible"));
            return;
        }
        intentar(pendiente);
    }

    private void intentar(NotificacionPendienteV1 pendiente) {
        CanalNotificacion canal;
        CambioEstadoGuia cambio;
        try {
            canal = MapeadorNotificacion.canalDesdeTexto(pendiente.canal());
            cambio = MapeadorNotificacion.aDominio(pendiente.cambio());
        } catch (IllegalArgumentException ex) {
            enviarADlq(pendiente, "Mensaje inválido: " + ex.getMessage());
            return;
        }

        try {
            var resultado = notificar.ejecutar(cambio, canal);
            log.debug("Notificación {} v{} por {}: {}", cambio.numeroGuia(), cambio.version(), canal, resultado);
            medir(canal, Telemetria.texto(resultado));
            if (resultado == ResultadoNotificacion.ENVIADA && pendiente.recibidoEn() != null)
                Telemetria.LATENCIA_NOTIFICACION.record(
                        Duration.between(pendiente.recibidoEn().toInstant(), reloj.instant()).toNanos() / 1e9,
                        Telemetria.etiqueta("canal", Telemetria.texto(canal)));

            if (resultado == ResultadoNotificacion.SIN_DESTINO)
                canalAlternoODlq(pendiente, canal, "Sin destino para " + canal);
        } catch (DestinoRechazadoException ex) {
            canalAlternoODlq(pendiente, canal, ex.getMessage());
        } catch (ServicioDeteniendoseException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            if (!ClasificadorErrores.esTransitorio(ex)) {
                log.error("Error inesperado notificando {} v{}", cambio.numeroGuia(), cambio.version(), ex);
                enviarADlq(pendiente, "Error inesperado: " + ex.getClass().getSimpleName() + ": " + ex.getMessage());
            } else if (pendiente.intento() < opciones.getReintentos().size()) {
                var siguiente = pendiente.conIntento(pendiente.intento() + 1);
                log.warn("Notificación {} v{} por {} falló ({}); reintento {} en {}",
                        cambio.numeroGuia(), cambio.version(), canal, ex.getMessage(), siguiente.intento(),
                        opciones.getReintentos().get(pendiente.intento()).getEspera());
                insistir(() -> enrutador.programarReintento(siguiente));
                medir(canal, "reintento");
            } else {
                canalAlternoODlq(pendiente, canal, "Reintentos agotados: " + ex.getMessage());
            }
        }
    }

    private void canalAlternoODlq(NotificacionPendienteV1 pendiente, CanalNotificacion canal, String motivo) {
        var alterno = PoliticaNotificacion.canalAlterno(canal);
        if (alterno.isPresent()) {
            log.warn("Notificación {} v{}: {}; se intenta por {}",
                    pendiente.cambio().numeroGuia(), pendiente.cambio().version(), motivo, alterno.get());
            intentar(pendiente.porCanal(MapeadorNotificacion.canalATexto(alterno.get())));
            return;
        }
        enviarADlq(pendiente, motivo);
    }

    private void enviarADlq(NotificacionPendienteV1 pendiente, String motivo) {
        log.error("Notificación {} v{} va a DLQ: {}", pendiente.cambio().numeroGuia(), pendiente.cambio().version(), motivo);
        insistir(() -> enrutador.enviarADlq(pendiente.cambio().numeroGuia(), JsonContratos.escribir(pendiente), motivo));
        medir(pendiente.canal(), "fallida");
        Telemetria.MENSAJES_DLQ.add(1, Telemetria.etiqueta("origen", "notificador"));
    }

    private static void medir(CanalNotificacion canal, String resultado) {
        medir(MapeadorNotificacion.canalATexto(canal), resultado);
    }

    private static void medir(String canal, String resultado) {
        Telemetria.NOTIFICACIONES.add(1, Telemetria.etiquetas("canal", canal.toLowerCase(Locale.ROOT), "resultado", resultado));
    }

    // Publicar el reintento o la DLQ DEBE lograrse antes de avanzar el offset; si Kafka no responde, se insiste.
    private void insistir(Runnable publicar) {
        for (var intento = 1; ; intento++) {
            try {
                publicar.run();
                return;
            } catch (ServicioDeteniendoseException ex) {
                throw ex;
            } catch (RuntimeException ex) {
                var espera = Duration.ofMillis((long) Math.min(30_000, 200 * Math.pow(2, Math.min(intento - 1, 10))));
                log.warn("No se pudo publicar la etapa siguiente (intento {}); se insiste en {} ms", intento, espera.toMillis(), ex);
                apagado.esperar(espera);
            }
        }
    }

    private static String clave(MensajeKafka mensaje) {
        return mensaje.clave() == null ? "" : mensaje.clave();
    }

    private static <T> T leer(String valor, Class<T> tipo) {
        if (valor == null || valor.isBlank())
            return null;
        try {
            return JsonContratos.leer(valor, tipo);
        } catch (JacksonException ex) {
            return null;
        }
    }
}
