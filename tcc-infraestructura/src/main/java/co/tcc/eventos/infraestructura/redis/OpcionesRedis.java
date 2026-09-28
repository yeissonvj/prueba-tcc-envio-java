package co.tcc.eventos.infraestructura.redis;

import java.time.Duration;

/** Opciones del filtro de duplicados (la conexión la configura Spring con spring.data.redis.*). */
public class OpcionesRedis {

    private String prefijoClave = "tcc:eventos:recibidos:";
    private Duration vigencia = Duration.ofHours(72);

    public String getPrefijoClave() { return prefijoClave; }
    public void setPrefijoClave(String prefijo) { this.prefijoClave = prefijo; }
    public Duration getVigencia() { return vigencia; }
    public void setVigencia(Duration vigencia) { this.vigencia = vigencia; }
}
