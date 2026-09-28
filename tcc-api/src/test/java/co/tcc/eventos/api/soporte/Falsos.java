package co.tcc.eventos.api.soporte;

import co.tcc.eventos.api.salud.Sonda;
import co.tcc.eventos.contratos.v1.GuiaV1;
import co.tcc.eventos.infraestructura.consultas.ConsultaGuias;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

public final class Falsos {

    private Falsos() {
    }

    public static final class ConsultaGuiasEnMemoria implements ConsultaGuias {

        public final Map<String, GuiaV1> guias = new ConcurrentHashMap<>();
        public final AtomicInteger consultas = new AtomicInteger();

        @Override
        public Optional<GuiaV1> obtener(String numeroGuia) {
            consultas.incrementAndGet();
            return Optional.ofNullable(guias.get(numeroGuia));
        }
    }

    public static final class SondaFalsa implements Sonda {

        public volatile boolean disponible = true;

        @Override
        public boolean disponible() {
            return disponible;
        }
    }
}
