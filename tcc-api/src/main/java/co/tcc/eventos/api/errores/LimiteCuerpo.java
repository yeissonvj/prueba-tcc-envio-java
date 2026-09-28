package co.tcc.eventos.api.errores;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.http.HttpStatus;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Un evento pesa ~1 KB; 64 KB evita que un cuerpo gigante agote la memoria.
 * Con Content-Length se rechaza de inmediato (413); sin él (chunked), se corta al leer.
 */
public final class LimiteCuerpo extends OncePerRequestFilter {

    public static final int MAXIMO_BYTES = 64 * 1024;

    private final Problemas problemas;

    public LimiteCuerpo(Problemas problemas) {
        this.problemas = problemas;
    }

    /** true si el error de lectura se debe a que el cuerpo superó el límite. */
    public static boolean esExceso(Throwable error) {
        for (var actual = error; actual != null; actual = actual.getCause())
            if (actual instanceof CuerpoDemasiadoGrandeException)
                return true;
        return false;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest peticion, HttpServletResponse respuesta, FilterChain cadena)
            throws ServletException, IOException {
        if (peticion.getContentLengthLong() > MAXIMO_BYTES) {
            problemas.escribir(respuesta, HttpStatus.CONTENT_TOO_LARGE, "Cuerpo demasiado grande",
                    "El cuerpo supera el tamaño máximo permitido.");
            return;
        }
        cadena.doFilter(new PeticionLimitada(peticion), respuesta);
    }

    static final class CuerpoDemasiadoGrandeException extends IOException {
        CuerpoDemasiadoGrandeException() {
            super("El cuerpo supera " + MAXIMO_BYTES + " bytes.");
        }
    }

    private static final class PeticionLimitada extends HttpServletRequestWrapper {

        private ServletInputStream entrada;

        PeticionLimitada(HttpServletRequest peticion) {
            super(peticion);
        }

        @Override
        public ServletInputStream getInputStream() throws IOException {
            if (entrada == null)
                entrada = new EntradaLimitada(super.getInputStream());
            return entrada;
        }
    }

    private static final class EntradaLimitada extends ServletInputStream {

        private final ServletInputStream interna;
        private long leidos;

        EntradaLimitada(ServletInputStream interna) {
            this.interna = interna;
        }

        @Override
        public int read() throws IOException {
            var b = interna.read();
            if (b >= 0)
                contar(1);
            return b;
        }

        @Override
        public int read(byte[] destino, int desde, int cantidad) throws IOException {
            var n = interna.read(destino, desde, cantidad);
            if (n > 0)
                contar(n);
            return n;
        }

        private void contar(int n) throws IOException {
            leidos += n;
            if (leidos > MAXIMO_BYTES)
                throw new CuerpoDemasiadoGrandeException();
        }

        @Override
        public boolean isFinished() {
            return interna.isFinished();
        }

        @Override
        public boolean isReady() {
            return interna.isReady();
        }

        @Override
        public void setReadListener(ReadListener oyente) {
            interna.setReadListener(oyente);
        }
    }
}
