package co.tcc.eventos.api.salud;

/**
 * Revisa si una dependencia externa está disponible. Nunca lanza: una falla es "false".
 * Cada implementación limita su propio tiempo para que la sonda responda rápido.
 */
@FunctionalInterface
public interface Sonda {

    boolean disponible();
}
