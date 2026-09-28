package co.tcc.eventos.infraestructura;

import static org.assertj.core.api.Assertions.assertThat;

import co.tcc.eventos.aplicacion.puertos.ConflictoConcurrenciaException;
import co.tcc.eventos.aplicacion.puertos.PublicacionFallidaException;
import co.tcc.eventos.infraestructura.resiliencia.ClasificadorErrores;
import java.net.ConnectException;
import java.sql.SQLException;
import java.util.NoSuchElementException;
import java.util.concurrent.TimeoutException;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.jdbc.CannotGetJdbcConnectionException;
import org.springframework.transaction.CannotCreateTransactionException;

class ClasificadorErroresPruebas {

    static Stream<Arguments> casos() {
        return Stream.of(
                Arguments.of(new ConflictoConcurrenciaException("TCC1"), true),
                Arguments.of(new TimeoutException(), true),
                Arguments.of(new PublicacionFallidaException("kafka"), true),
                Arguments.of(new CannotGetJdbcConnectionException("sin conexión", new SQLException("x", "08001")), true),
                Arguments.of(new CannotCreateTransactionException("base caída", new ConnectException()), true),
                Arguments.of(new SQLException("apagando", "57P01"), true),
                Arguments.of(new IllegalStateException("envuelve", new TimeoutException()), true),
                Arguments.of(new IllegalStateException("bug"), false),
                Arguments.of(new SQLException("violación de llave", "23505"), false),
                Arguments.of(new NoSuchElementException(), false));
    }

    @ParameterizedTest
    @MethodSource("casos")
    void distingue_errores_transitorios_de_los_que_no_lo_son(Throwable error, boolean esperado) {
        assertThat(ClasificadorErrores.esTransitorio(error)).isEqualTo(esperado);
    }
}
