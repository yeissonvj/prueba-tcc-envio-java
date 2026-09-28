package co.tcc.eventos.infraestructura.kafka;

public record MensajeRechazado(MensajeKafka original, String motivo) {
}
