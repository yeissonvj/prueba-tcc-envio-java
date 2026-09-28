package co.tcc.eventos.infraestructura.notificaciones;

public class OpcionesProveedores {

    private OpcionesProveedorSimulado sms = new OpcionesProveedorSimulado(30);
    private OpcionesProveedorSimulado correo = new OpcionesProveedorSimulado(50);

    // Circuito por proveedor: se abre si en la ventana falla al menos la mitad de un mínimo de envíos.
    private int circuitoMinimoEnvios = 10;
    private int circuitoVentanaSegundos = 30;
    private int circuitoSegundosAbierto = 30;

    public OpcionesProveedorSimulado getSms() { return sms; }
    public void setSms(OpcionesProveedorSimulado sms) { this.sms = sms; }
    public OpcionesProveedorSimulado getCorreo() { return correo; }
    public void setCorreo(OpcionesProveedorSimulado correo) { this.correo = correo; }
    public int getCircuitoMinimoEnvios() { return circuitoMinimoEnvios; }
    public void setCircuitoMinimoEnvios(int envios) { this.circuitoMinimoEnvios = envios; }
    public int getCircuitoVentanaSegundos() { return circuitoVentanaSegundos; }
    public void setCircuitoVentanaSegundos(int segundos) { this.circuitoVentanaSegundos = segundos; }
    public int getCircuitoSegundosAbierto() { return circuitoSegundosAbierto; }
    public void setCircuitoSegundosAbierto(int segundos) { this.circuitoSegundosAbierto = segundos; }
}
