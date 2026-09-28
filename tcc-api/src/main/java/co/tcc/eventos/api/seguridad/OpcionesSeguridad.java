package co.tcc.eventos.api.seguridad;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class OpcionesSeguridad {

    /** URL del emisor de tokens (realm de Keycloak u otro IdP OIDC): el claim iss esperado. */
    private String emisor = "";

    /**
     * Opcional: URL interna del IdP para descargar las llaves públicas cuando la pública (emisor) no es
     * alcanzable desde la red de la API (p. ej. dentro de Docker o del clúster).
     */
    private String direccionInterna;

    /** Opcional: URL exacta del JWKS. Por defecto, la de Keycloak: {emisor o interna}/protocol/openid-connect/certs. */
    private String urlLlaves;

    private String audiencia = "api-ingesta";

    /** Qué valores de "origen" puede reportar cada cliente (claim azp). Cliente ausente = no puede escribir. */
    private Map<String, List<String>> origenesPorCliente = new HashMap<>();

    private int peticionesPorSegundoPorCliente = 1000;
    private int rafagaPorCliente = 2000;

    public String urlLlavesEfectiva() {
        if (urlLlaves != null && !urlLlaves.isBlank())
            return urlLlaves;
        var base = direccionInterna != null && !direccionInterna.isBlank() ? direccionInterna : emisor;
        return base.replaceAll("/+$", "") + "/protocol/openid-connect/certs";
    }

    public String getEmisor() { return emisor; }
    public void setEmisor(String emisor) { this.emisor = emisor; }
    public String getDireccionInterna() { return direccionInterna; }
    public void setDireccionInterna(String direccion) { this.direccionInterna = direccion; }
    public String getUrlLlaves() { return urlLlaves; }
    public void setUrlLlaves(String url) { this.urlLlaves = url; }
    public String getAudiencia() { return audiencia; }
    public void setAudiencia(String audiencia) { this.audiencia = audiencia; }
    public Map<String, List<String>> getOrigenesPorCliente() { return origenesPorCliente; }
    public void setOrigenesPorCliente(Map<String, List<String>> origenes) { this.origenesPorCliente = origenes; }
    public int getPeticionesPorSegundoPorCliente() { return peticionesPorSegundoPorCliente; }
    public void setPeticionesPorSegundoPorCliente(int peticiones) { this.peticionesPorSegundoPorCliente = peticiones; }
    public int getRafagaPorCliente() { return rafagaPorCliente; }
    public void setRafagaPorCliente(int rafaga) { this.rafagaPorCliente = rafaga; }
}
