package co.tcc.eventos.api.salud;

import org.springframework.jdbc.core.simple.JdbcClient;

/** Consulta la tabla de contingencia: confirma a la vez conexión, credenciales y que la migración se aplicó. */
public final class SondaPostgres implements Sonda {

    private final JdbcClient jdbc;

    public SondaPostgres(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public boolean disponible() {
        try {
            // El límite lo imponen connection-timeout (3 s) y query-timeout (5 s) de la configuración.
            jdbc.sql("SELECT EXISTS (SELECT 1 FROM contingencia_eventos)").query(Boolean.class).single();
            return true;
        } catch (RuntimeException ex) {
            return false;
        }
    }
}
