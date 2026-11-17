-- AUTORENT - BASE DE DATOS PARA HEIDISQL + XAMPP (MySQL / MariaDB) - UNIDAD 2
-- Importar una sola vez. No elimina tablas ni registros existentes.
-- Unidad 2: agrega pagos en línea (columna pagos.detalle), comprobantes electrónicos (tabla comprobantes)
-- y trazabilidad: quién confirmó o rechazó cada pago y por qué (pagos.procesado_por, pagos.observacion),
-- evidencia de cada devolución (devoluciones.codigo_operacion) y la bitácora de cada reserva (tabla historial_reservas).
-- Si ya tenía la base de la unidad 1, NO hace falta importar nada: Spring Boot (ddl-auto=update)
-- agrega las columnas y tablas nuevas al arrancar. Este script sirve para una instalación nueva.
-- Las tablas de negocio empiezan vacias; Spring Boot registra usuarios y autos
-- de demostracion solo si todavia no existen. Los datos nuevos se guardan solos.
-- Compatible con XAMPP / MariaDB 10.4+ y MySQL 8.0+.

CREATE DATABASE IF NOT EXISTS autorent_db
  CHARACTER SET utf8mb4
  COLLATE utf8mb4_unicode_ci;

USE autorent_db;

CREATE TABLE IF NOT EXISTS usuarios (
    id BIGINT NOT NULL AUTO_INCREMENT,
    username VARCHAR(60) NOT NULL,
    password_hash VARCHAR(100) NOT NULL,
    nombres VARCHAR(80) NOT NULL,
    apellidos VARCHAR(80) NOT NULL,
    documento VARCHAR(20) NOT NULL,
    correo VARCHAR(120) NOT NULL,
    telefono VARCHAR(20) NULL,
    direccion VARCHAR(200) NULL,
    rol VARCHAR(20) NOT NULL,
    activo TINYINT(1) NOT NULL DEFAULT 1,
    creado_en DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_usuario_username UNIQUE (username),
    CONSTRAINT uk_usuario_documento UNIQUE (documento),
    CONSTRAINT uk_usuario_correo UNIQUE (correo)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS autos (
    id BIGINT NOT NULL AUTO_INCREMENT,
    placa VARCHAR(15) NOT NULL,
    marca VARCHAR(60) NOT NULL,
    modelo VARCHAR(60) NOT NULL,
    anio INT NOT NULL,
    categoria VARCHAR(40) NOT NULL,
    color VARCHAR(40) NULL,
    tarifa_diaria DECIMAL(10,2) NOT NULL,
    descripcion VARCHAR(500) NULL,
    estado VARCHAR(20) NOT NULL,
    imagen_url VARCHAR(1000) NULL,
    creado_en DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_auto_placa UNIQUE (placa)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS reservas (
    id BIGINT NOT NULL AUTO_INCREMENT,
    auto_id BIGINT NOT NULL,
    cliente_id BIGINT NOT NULL,
    fecha_inicio DATE NOT NULL,
    fecha_fin DATE NOT NULL,
    dias INT NOT NULL,
    tarifa_diaria DECIMAL(10,2) NOT NULL,
    total DECIMAL(12,2) NOT NULL,
    estado VARCHAR(30) NOT NULL,
    telefono_devolucion VARCHAR(20) NULL,
    observacion VARCHAR(500) NULL,
    creado_en DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    KEY idx_reserva_auto_fechas (auto_id, fecha_inicio, fecha_fin),
    KEY idx_reserva_auto_estado (auto_id, estado),
    KEY idx_reserva_cliente (cliente_id),
    CONSTRAINT fk_reservas_auto FOREIGN KEY (auto_id) REFERENCES autos (id),
    CONSTRAINT fk_reservas_cliente FOREIGN KEY (cliente_id) REFERENCES usuarios (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS pagos (
    id BIGINT NOT NULL AUTO_INCREMENT,
    reserva_id BIGINT NOT NULL,
    monto DECIMAL(12,2) NOT NULL,
    metodo VARCHAR(20) NOT NULL,
    tipo VARCHAR(20) NOT NULL,
    estado VARCHAR(20) NOT NULL,
    telefono_origen VARCHAR(20) NULL,
    codigo_operacion VARCHAR(40) NULL,
    registrado_en DATETIME(6) NOT NULL,
    confirmado_en DATETIME(6) NULL,     -- fecha en que se confirmó o rechazó
    registrado_por VARCHAR(60) NULL,    -- quién registró el pago (cliente o trabajador)
    detalle VARCHAR(120) NULL,          -- pago en línea: "VISA **** 1111" o "PayPal correo" (nunca el número completo)
    procesado_por VARCHAR(60) NULL,     -- quién lo confirmó o rechazó (trabajador, cliente vía pasarela o SISTEMA)
    observacion VARCHAR(200) NULL,      -- motivo del rechazo o forma de verificación
    PRIMARY KEY (id),
    KEY idx_pagos_reserva (reserva_id),
    KEY idx_pago_reserva_estado (reserva_id, estado),
    CONSTRAINT uk_pagos_codigo_operacion UNIQUE (codigo_operacion),
    CONSTRAINT fk_pagos_reserva FOREIGN KEY (reserva_id) REFERENCES reservas (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS devoluciones (
    id BIGINT NOT NULL AUTO_INCREMENT,
    reserva_id BIGINT NOT NULL,
    pago_id BIGINT NULL,
    monto DECIMAL(12,2) NOT NULL,
    metodo VARCHAR(20) NOT NULL,
    telefono_destino VARCHAR(20) NOT NULL,
    estado VARCHAR(20) NOT NULL,
    registrado_en DATETIME(6) NOT NULL,
    completado_en DATETIME(6) NULL,
    completado_por VARCHAR(60) NULL,
    codigo_operacion VARCHAR(60) NULL,  -- evidencia: código del Yape enviado o del reembolso en la pasarela
    PRIMARY KEY (id),
    KEY idx_devoluciones_reserva (reserva_id),
    KEY idx_devoluciones_pago (pago_id),
    CONSTRAINT fk_devoluciones_reserva FOREIGN KEY (reserva_id) REFERENCES reservas (id),
    CONSTRAINT fk_devoluciones_pago FOREIGN KEY (pago_id) REFERENCES pagos (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS mantenimientos (
    id BIGINT NOT NULL AUTO_INCREMENT,
    auto_id BIGINT NOT NULL,
    descripcion VARCHAR(500) NOT NULL,
    fecha_inicio DATE NOT NULL,
    fecha_fin DATE NULL,
    estado VARCHAR(20) NOT NULL,
    registrado_en DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    KEY idx_mantenimiento_auto (auto_id),
    CONSTRAINT fk_mantenimiento_auto FOREIGN KEY (auto_id) REFERENCES autos (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Comprobantes electrónicos: una boleta por cada pago confirmado y una nota de crédito por cada devolución.
CREATE TABLE IF NOT EXISTS comprobantes (
    id BIGINT NOT NULL AUTO_INCREMENT,
    tipo VARCHAR(20) NOT NULL,                 -- BOLETA o NOTA_CREDITO
    serie VARCHAR(4) NOT NULL,                 -- B001 / BC01 (prueba) o la serie de Nubefact
    numero INT NOT NULL,                       -- correlativo sin saltos dentro de la serie
    fecha_emision DATE NOT NULL,
    pago_id BIGINT NOT NULL,
    reserva_id BIGINT NOT NULL,
    cliente_id BIGINT NOT NULL,
    cliente_documento VARCHAR(15) NOT NULL,
    cliente_nombre VARCHAR(160) NOT NULL,
    cliente_direccion VARCHAR(200) NULL,
    descripcion VARCHAR(250) NOT NULL,
    gravada DECIMAL(12,2) NOT NULL,            -- total / 1.18
    igv DECIMAL(12,2) NOT NULL,                -- total - gravada
    total DECIMAL(12,2) NOT NULL,
    estado VARCHAR(20) NOT NULL,               -- PENDIENTE, EMITIDO, ACEPTADO, RECHAZADO, ERROR
    proveedor VARCHAR(30) NOT NULL,            -- PRUEBA o NUBEFACT
    enlace_pdf VARCHAR(500) NULL,
    hash VARCHAR(100) NULL,
    cadena_qr VARCHAR(500) NULL,
    mensaje VARCHAR(500) NULL,
    referencia_id BIGINT NULL,                 -- en una nota de crédito: la boleta que anula
    creado_en DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_comprobante_serie_numero UNIQUE (tipo, serie, numero),
    KEY idx_comprobante_pago (pago_id),
    KEY idx_comprobante_cliente (cliente_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Bitácora de cada reserva: qué pasó, cuándo, quién lo hizo y cómo cambió el estado. Solo se insertan filas.
CREATE TABLE IF NOT EXISTS historial_reservas (
    id BIGINT NOT NULL AUTO_INCREMENT,
    reserva_id BIGINT NOT NULL,
    fecha DATETIME(6) NOT NULL,
    accion VARCHAR(30) NOT NULL,
    estado_anterior VARCHAR(30) NULL,
    estado_nuevo VARCHAR(30) NULL,
    usuario VARCHAR(60) NOT NULL,
    detalle VARCHAR(300) NULL,
    PRIMARY KEY (id),
    KEY idx_historial_reserva (reserva_id, fecha),
    CONSTRAINT fk_historial_reserva FOREIGN KEY (reserva_id) REFERENCES reservas (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Comprobacion: al importar por primera vez, debe mostrar 8 tablas.
SHOW TABLES;
