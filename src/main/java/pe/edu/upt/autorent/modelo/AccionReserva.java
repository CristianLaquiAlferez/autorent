package pe.edu.upt.autorent.modelo;

/** Tipos de acciones que se anotan en la bitácora de una reserva. */
public enum AccionReserva {
    CREADA("Reserva creada"),
    PAGO_REGISTRADO("Pago registrado"),
    PAGO_CONFIRMADO("Pago confirmado"),
    PAGO_RECHAZADO("Pago rechazado"),
    PAGO_EN_LINEA_FALLIDO("Intento de pago en línea rechazado"),
    COMPROBANTE_EMITIDO("Comprobante emitido"),
    COMPROBANTE_CON_ERROR("Comprobante con error"),
    ENTREGA("Vehículo entregado"),
    RECEPCION("Vehículo recibido"),
    CARGO_ADICIONAL("Cargo adicional"),
    CANCELADA("Reserva cancelada"),
    ANULADA("Reserva anulada por el personal"),
    VENCIDA("Reserva vencida"),
    NO_PRESENTADO("Cliente no se presentó"),
    DEVOLUCION_REGISTRADA("Devolución de dinero registrada"),
    DEVOLUCION_COMPLETADA("Devolución de dinero completada");

    private final String etiqueta;

    AccionReserva(String etiqueta) { this.etiqueta = etiqueta; }

    public String getEtiqueta() { return etiqueta; }
}
