package co.tcc.eventos.infraestructura.resiliencia;

import co.tcc.eventos.aplicacion.puertos.ConflictoConcurrenciaException;
import co.tcc.eventos.aplicacion.puertos.PublicacionFallidaException;
import co.tcc.eventos.aplicacion.puertos.notificacion.ProveedorNoDisponibleException;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.sql.SQLException;
import java.sql.SQLRecoverableException;
import java.sql.SQLTransientException;
import java.util.concurrent.TimeoutException;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.RecoverableDataAccessException;
import org.springframework.dao.TransientDataAccessException;
import org.springframework.transaction.CannotCreateTransactionException;

/**
 * Transitorio = se arregla solo si se espera (base caída, red, broker, proveedor, conflicto de concurrencia).
 * Cada consumidor decide qué hacer con eso: el procesador espera (bloqueante), el notificador reprograma.
 */
public final class ClasificadorErrores {

    private ClasificadorErrores() {
    }

    public static boolean esTransitorio(Throwable error) {
        for (var actual = error; actual != null; actual = actual.getCause()) {
            if (esTransitorioPorSiMismo(actual))
                return true;
            if (actual.getCause() == actual)
                break;
        }
        return false;
    }

    private static boolean esTransitorioPorSiMismo(Throwable error) {
        return error instanceof ConflictoConcurrenciaException
                || error instanceof PublicacionFallidaException
                || error instanceof ProveedorNoDisponibleException
                || error instanceof TimeoutException
                || error instanceof SocketTimeoutException
                || error instanceof SocketException
                || error instanceof SQLTransientException
                || error instanceof SQLRecoverableException
                || error instanceof TransientDataAccessException
                || error instanceof RecoverableDataAccessException
                // Base caída: no se obtiene conexión o no se puede abrir la transacción.
                || error instanceof DataAccessResourceFailureException
                || error instanceof CannotCreateTransactionException
                || (error instanceof SQLException sql && esEstadoSqlTransitorio(sql.getSQLState()));
    }

    /**
     * Clases de SQLSTATE de PostgreSQL que se resuelven reintentando:
     * 08 conexión, 40001 serialización, 40P01 deadlock, 53 recursos insuficientes, 57P0x servidor apagándose.
     */
    private static boolean esEstadoSqlTransitorio(String estado) {
        if (estado == null)
            return false;
        return estado.startsWith("08")
                || estado.equals("40001")
                || estado.equals("40P01")
                || estado.startsWith("53")
                || estado.startsWith("57P0");
    }
}
