package co.tcc.eventos.infraestructura.consultas;

import co.tcc.eventos.contratos.v1.GuiaV1;
import java.util.Optional;

/**
 * Lado de lectura (CQRS): devuelve el contrato directamente, sin pasar por el dominio,
 * porque leer no cambia nada. Hoy lee PostgreSQL; a escala, una proyección en Redis.
 */
public interface ConsultaGuias {

    Optional<GuiaV1> obtener(String numeroGuia);
}
