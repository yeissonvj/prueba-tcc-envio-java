package co.tcc.eventos.contratos.v1;

import java.util.UUID;

public record RespuestaRecepcionV1(UUID idEvento, String resultado) {

    public static final String ACEPTADO = "ACEPTADO";
    public static final String DUPLICADO = "DUPLICADO";
}
