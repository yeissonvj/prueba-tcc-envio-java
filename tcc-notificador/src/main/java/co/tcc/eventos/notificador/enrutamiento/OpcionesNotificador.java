package co.tcc.eventos.notificador.enrutamiento;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

public class OpcionesNotificador {

    public static class EtapaReintento {

        private String topico = "";
        private Duration espera = Duration.ZERO;

        public EtapaReintento() {
        }

        public EtapaReintento(String topico, Duration espera) {
            this.topico = topico;
            this.espera = espera;
        }

        public String getTopico() { return topico; }
        public void setTopico(String topico) { this.topico = topico; }
        public Duration getEspera() { return espera; }
        public void setEspera(Duration espera) { this.espera = espera; }
    }

    private String grupoConsumo = "notificador";
    private String topicoDlq = "notificaciones.dlq";

    /** Hilos del consumidor de guias.estados.cambiados (uno por partición asignada). */
    private int hilos = 24;

    /** Escalera de reintentos no bloqueantes; el orden importa (intento 1 → primera etapa). */
    private List<EtapaReintento> reintentos = new ArrayList<>();

    /**
     * Usar solo las primeras N etapas (null = todas). Una lista de configuración no se acorta fácilmente
     * sobrescribiéndola por variables de entorno; esto sí (p. ej. Aiven gratuito solo admite 5 tópicos).
     */
    private Integer etapasActivas;

    public OpcionesNotificador conEtapasActivas() {
        if (etapasActivas == null)
            return this;
        var recortadas = new OpcionesNotificador();
        recortadas.grupoConsumo = grupoConsumo;
        recortadas.topicoDlq = topicoDlq;
        recortadas.hilos = hilos;
        recortadas.reintentos = List.copyOf(reintentos.subList(0, Math.min(etapasActivas, reintentos.size())));
        return recortadas;
    }

    public String getGrupoConsumo() { return grupoConsumo; }
    public void setGrupoConsumo(String grupo) { this.grupoConsumo = grupo; }
    public String getTopicoDlq() { return topicoDlq; }
    public void setTopicoDlq(String topico) { this.topicoDlq = topico; }
    public int getHilos() { return hilos; }
    public void setHilos(int hilos) { this.hilos = hilos; }
    public List<EtapaReintento> getReintentos() { return reintentos; }
    public void setReintentos(List<EtapaReintento> reintentos) { this.reintentos = reintentos; }
    public Integer getEtapasActivas() { return etapasActivas; }
    public void setEtapasActivas(Integer etapas) { this.etapasActivas = etapas; }
}
