# INGEARQ

Aplicacion Java con Spring Boot, JDBC y MySQL. El servidor aloja las paginas HTML existentes y expone la API consumida por el navegador.

## Requisitos

- JDK 25 o posterior
- MySQL 8 o posterior

## Ejecutar localmente

1. Inicia MySQL. Ejecuta `database/ingearq2026.sql` si la base o sus tablas aun no existen.
2. En PowerShell, ejecuta `Copy-Item .env.example .env`. Abre `.env` y configura el usuario y la contrasena de MySQL.
3. Ejecuta `./mvnw.cmd spring-boot:run`. Las paginas y recursos estaticos se sirven desde `src/main/resources/static/`.
4. Abre `http://127.0.0.1:3000`.

Spring aplica al arrancar las migraciones aditivas (`productos.activo`, `caja.descripcion`, `ventas.id_usuario` y las tablas `usuarios`, `categorias` y `formas_pago`). Con `SEED_DEMO_DATA=true`, crea las cuentas y productos de demostracion unicamente cuando no existen. Accesos iniciales: `admin` / `Ingearq2024!` (administrador) y `vendedor` / `Vendedor2024!` (perfil vendedor).

## Acceso y usuarios

- **Login** (`index.html`): usuario y clave validados contra la tabla `usuarios`; el perfil detectado (vendedor o administrador) define el ambiente. Los usuarios dados de baja no pueden ingresar.
- **Registro** (`register.html`): solo accesible con sesion de administrador (el endpoint `/api/auth/register` tambien lo exige). Desde el login, el enlace "Para registrar un usuario como Administrador, ingrese aquí" lleva al login si no hay sesion de administrador; el administrador tambien lo encuentra en el dashboard y en Usuarios. Campos: dni, nombres, apellidos, usuario, clave, perfil y estado. Se rechaza un DNI o usuario ya existente.
- **CRUD de usuarios** (`user_manager.html`, solo administrador): crear, editar, dar de baja y reactivar. Un administrador no puede darse de baja a si mismo.

## CRUDs operativos

- **Productos:** listar, crear, editar y dar de baja/reactivar desde Inventario. La baja es logica para conservar el historial de ventas. La categoria se elige de una lista cargada desde la tabla `categorias`.
- **Ventas:** listar, crear, editar datos del comprobante y anular desde Comprobantes. La forma de pago se elige de una lista cargada desde la tabla `formas_pago`. La anulacion restaura el stock y registra el movimiento compensatorio de caja dentro de una transaccion.

## Pruebas de navegador

1. Ejecuta `npm install` y `npx playwright install chromium`.
2. Configura `.env` con acceso a MySQL y prepara un esquema aislado con `npm run test:e2e:prepare`. No apuntes estas pruebas a la base diaria.
3. En PowerShell, inicia Spring contra ese esquema:

	```powershell
	$env:PORT = "3002"
	$env:DB_NAME = "ingearq_e2e"
	.\mvnw.cmd spring-boot:run
	```

4. En otra terminal ejecuta `npm run test:e2e`. Puedes cambiar la URL con `E2E_BASE_URL`.

La aplicacion escucha solo en `127.0.0.1`; la sesion usa la sesion HTTP local de Spring. No expongas las credenciales de `.env` ni uses las cuentas de demostracion fuera del desarrollo.
