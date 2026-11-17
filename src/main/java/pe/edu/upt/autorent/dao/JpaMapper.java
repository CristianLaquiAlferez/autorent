package pe.edu.upt.autorent.dao;

import pe.edu.upt.autorent.modelo.*;
import pe.edu.upt.autorent.modelo.entidad.*;
/** Convierte entidades de MySQL en modelos de negocio y viceversa. */

final class JpaMapper {
    private JpaMapper() { }

    /** Transforma la entidad de persistencia al modelo de negocio. */
    static Auto toDomain(AutoEntity e) {
        if (e == null) return null;
        return new Auto(e.getId(), e.getPlaca(), e.getMarca(), e.getModelo(), e.getAnio(),
                e.getCategoria(), e.getColor(), e.getTarifaDiaria(), e.getDescripcion(), e.getEstado(), e.getImagenUrl());
    }

    static Usuario toDomain(UsuarioEntity e) {
        if (e == null) return null;
        return new Usuario(e.getId(), e.getUsername(), e.getPasswordHash(), e.getNombres(),
                e.getApellidos(), e.getDocumento(), e.getCorreo(), e.getTelefono(),
                e.getDireccion(), e.getRol(), e.isActivo());
    }

    static Reserva toDomain(ReservaEntity e) {
        if (e == null) return null;
        return new Reserva(e.getId(), toDomain(e.getAuto()), toDomain(e.getCliente()),
                e.getFechaInicio(), e.getFechaFin(), e.getDias(), e.getTarifaDiaria(),
                e.getTotal(), e.getEstado(), e.getTelefonoDevolucion(), e.getObservacion(), e.getCreadoEn());
    }

    static Pago toDomain(PagoEntity e) {
        if (e == null) return null;
        return new Pago(e.getId(), e.getReserva().getId(), e.getMonto(), e.getMetodo(), e.getTipo(),
                e.getEstado(), e.getTelefonoOrigen(), e.getCodigoOperacion(), e.getRegistradoEn(),
                e.getProcesadoEn(), e.getRegistradoPor(), e.getDetalle(), e.getProcesadoPor(), e.getObservacion());
    }

    static Devolucion toDomain(DevolucionEntity e) {
        if (e == null) return null;
        return new Devolucion(e.getId(), e.getReserva().getId(), e.getPago() == null ? null : e.getPago().getId(),
                e.getMonto(), e.getMetodo(), e.getTelefonoDestino(), e.getEstado(),
                e.getRegistradoEn(), e.getCompletadoEn(), e.getCompletadoPor(), e.getCodigoOperacion());
    }

    static Mantenimiento toDomain(MantenimientoEntity e) {
        if (e == null) return null;
        return new Mantenimiento(e.getId(), e.getAuto().getId(), e.getDescripcion(), e.getFechaInicio(),
                e.getFechaFin(), e.getEstado(), e.getRegistradoEn());
    }
    static Comprobante toDomain(ComprobanteEntity e) {
        if (e == null) return null;
        return new Comprobante(e.getId(), e.getTipo(), e.getSerie(), e.getNumero(), e.getFechaEmision(), e.getPagoId(),
                e.getReservaId(), e.getClienteId(), e.getClienteDocumento(), e.getClienteNombre(), e.getClienteDireccion(),
                e.getDescripcion(), e.getGravada(), e.getIgv(), e.getTotal(), e.getEstado(), e.getProveedor(),
                e.getEnlacePdf(), e.getHash(), e.getCadenaQr(), e.getMensaje(), e.getReferenciaId(), e.getCreadoEn());
    }

    static EventoReserva toDomain(HistorialReservaEntity e) {
        if (e == null) return null;
        return new EventoReserva(e.getId(), e.getReserva().getId(), e.getFecha(), e.getAccion(),
                e.getEstadoAnterior(), e.getEstadoNuevo(), e.getUsuario(), e.getDetalle());
    }
}
