CREATE DATABASE IF NOT EXISTS `ingearq2026`
  CHARACTER SET utf8mb4
  COLLATE utf8mb4_unicode_ci;

USE  `ingearq2026`;

CREATE TABLE IF NOT EXISTS trabajadores (
  id_trabajador INT UNSIGNED NOT NULL AUTO_INCREMENT,
  nombre VARCHAR(100) NOT NULL,
  apellido VARCHAR(100) NOT NULL,
  fecha_nacimiento DATE NOT NULL,
  fecha_comienzo DATE NOT NULL,
  email VARCHAR(254) NOT NULL,
  PRIMARY KEY (id_trabajador),
  UNIQUE KEY uq_trabajadores_email (email),
  UNIQUE KEY uq_trabajadores_id_nombre (id_trabajador, nombre)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS credenciales (
  id_trabajador INT UNSIGNED NOT NULL,
  nombre_trabajador VARCHAR(100) NOT NULL,
  email VARCHAR(254) NOT NULL,
  contrasena_hash VARCHAR(255) NOT NULL,
  rol ENUM('administrador', 'vendedor') NOT NULL,
  PRIMARY KEY (id_trabajador),
  UNIQUE KEY uq_credenciales_email (email),
  CONSTRAINT fk_credenciales_email
    FOREIGN KEY (email) REFERENCES trabajadores (email),
  CONSTRAINT fk_credenciales_trabajador_nombre
    FOREIGN KEY (id_trabajador, nombre_trabajador)
    REFERENCES trabajadores (id_trabajador, nombre)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS productos (
  id_producto INT UNSIGNED NOT NULL AUTO_INCREMENT,
  nombre VARCHAR(150) NOT NULL,
  categoria VARCHAR(100) NOT NULL,
  descripcion VARCHAR(500) NOT NULL,
  precio DECIMAL(10,2) UNSIGNED NOT NULL,
  cantidad INT UNSIGNED NOT NULL DEFAULT 0,
  activo TINYINT(1) NOT NULL DEFAULT 1,
  PRIMARY KEY (id_producto),
  KEY ix_productos_nombre (nombre),
  CONSTRAINT chk_productos_precio CHECK (precio >= 0)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS ventas (
  id_comprobante CHAR(36) NOT NULL COMMENT 'UUID aleatorio generado por la aplicacion',
  id_trabajador INT UNSIGNED NULL,
  fecha_venta DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  productos JSON NOT NULL COMMENT 'Arreglo con id, nombre, cantidad, precio unitario y subtotal',
  suma_total DECIMAL(12,2) UNSIGNED NOT NULL,
  PRIMARY KEY (id_comprobante),
  KEY ix_ventas_trabajador (id_trabajador),
  CONSTRAINT fk_ventas_trabajador
    FOREIGN KEY (id_trabajador) REFERENCES trabajadores (id_trabajador),
  CONSTRAINT chk_ventas_suma_total CHECK (suma_total >= 0)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS caja (
  id_operacion BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  id_comprobante CHAR(36) NULL,
  fecha_operacion DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  descripcion VARCHAR(255) NULL,
  ingresos DECIMAL(12,2) UNSIGNED NOT NULL DEFAULT 0.00,
  egresos DECIMAL(12,2) UNSIGNED NOT NULL DEFAULT 0.00,
  ganancias_dia DECIMAL(12,2) NOT NULL DEFAULT 0.00 COMMENT 'Neto diario: ingresos menos egresos',
  total_caja DECIMAL(12,2) NOT NULL DEFAULT 0.00 COMMENT 'Saldo acumulado en caja tras la operacion',
  PRIMARY KEY (id_operacion),
  KEY ix_caja_fecha (fecha_operacion),
  KEY ix_caja_comprobante (id_comprobante),
  CONSTRAINT fk_caja_venta
    FOREIGN KEY (id_comprobante) REFERENCES ventas (id_comprobante)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS usuarios (
  id_usuario INT UNSIGNED NOT NULL AUTO_INCREMENT,
  dni CHAR(8) NOT NULL,
  nombres VARCHAR(100) NOT NULL,
  apellidos VARCHAR(100) NOT NULL,
  usuario VARCHAR(50) NOT NULL,
  clave_hash VARCHAR(255) NOT NULL,
  perfil ENUM('vendedor', 'administrador') NOT NULL DEFAULT 'vendedor',
  estado ENUM('activo', 'inactivo') NOT NULL DEFAULT 'activo',
  PRIMARY KEY (id_usuario),
  UNIQUE KEY uq_usuarios_dni (dni),
  UNIQUE KEY uq_usuarios_usuario (usuario)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS categorias (
  id_categoria INT UNSIGNED NOT NULL AUTO_INCREMENT,
  nombre VARCHAR(100) NOT NULL,
  PRIMARY KEY (id_categoria),
  UNIQUE KEY uq_categorias_nombre (nombre)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS formas_pago (
  id_forma_pago INT UNSIGNED NOT NULL AUTO_INCREMENT,
  nombre VARCHAR(50) NOT NULL,
  PRIMARY KEY (id_forma_pago),
  UNIQUE KEY uq_formas_pago_nombre (nombre)
) ENGINE=InnoDB;

INSERT IGNORE INTO categorias (nombre) VALUES ('Materiales'), ('Herramientas'), ('Maquetas'), ('Libros');
INSERT IGNORE INTO formas_pago (nombre) VALUES ('Efectivo'), ('Tarjeta'), ('Yape'), ('Transferencia');
