[English version](README.en.md)

# Cuentas y transferencias: API Spring Boot + web Angular

API REST hecha con Java 21 y Spring Boot 3.5 que modela lo básico de un banco: clientes, cuentas (caja de ahorro y cuenta corriente, en pesos y dólares), movimientos y transferencias entre cuentas. Tiene además un **frontend en Angular** (home banking para el cliente y pantallas para el operador), **Redis** para caché y límite de intentos de login, y un **pipeline de CI** en GitHub Actions. Es un proyecto de portfolio, con datos de ejemplo: no lo usa ningún banco. Apunta a los avisos de **desarrollador Full Stack Java + Angular** (Java, Spring, Angular, TypeScript, SQL, Redis, Git, Docker, CI/CD, Linux, REST, testing) y cubre lo que suelen pedir los bancos, las fintechs y las consultoras en el día a día, con foco en las partes que en este tipo de sistemas no pueden fallar: que una transferencia no se ejecute dos veces, que dos transferencias simultáneas no dejen una cuenta en negativo y que los errores se entiendan.

**Stack backend:** Java 21 · Spring Boot 3.5.16 · Spring Web · Spring Data JPA / Hibernate · PostgreSQL 16 · Flyway · Spring Security (resource server JWT) · Spring Cache + Spring Data Redis (Redis 7) · Bean Validation · springdoc-openapi (Swagger UI) · JUnit 5 · Mockito · MockMvc · Maven.

**Stack frontend:** Angular 22 (componentes standalone, signals, TypeScript estricto, Reactive Forms, carga diferida de rutas) · Vitest · Playwright · ESLint · nginx.

**Infra:** Docker · Docker Compose (Postgres + Redis + API + web) · GitHub Actions.

| Mis cuentas (cliente) | Confirmación de transferencia |
|---|---|
| ![Mis cuentas](docs/capturas/05-web-mis-cuentas.png) | ![Confirmar transferencia](docs/capturas/06-web-confirmar-transferencia.png) |

## Qué hace

- **Clientes y usuarios.** Hay dos roles. `CLIENTE` ve y opera sólo sus cuentas. `OPERADOR` da de alta clientes y cuentas, registra depósitos, bloquea cuentas y puede ver todo. Las contraseñas se guardan con BCrypt.
- **Cuentas.** Pueden ser `CA` (caja de ahorro) o `CC` (cuenta corriente), en `ARS` o `USD`. Cada una tiene un CBU de 22 dígitos generado con el algoritmo real de dígitos verificadores, un límite diario de transferencias y, en el caso de la CC, un descubierto acordado opcional.
- **Transferencias** entre cuentas de este banco:
  - sólo entre cuentas de la misma moneda (si no coinciden, se rechazan explícitamente: no hay conversión);
  - son atómicas: se aplican el débito, el crédito, la transferencia y los dos movimientos, o no se aplica nada;
  - usan locks pesimistas con un orden fijo, así no hay sobregiros ni deadlocks;
  - el header `Idempotency-Key` es obligatorio, así un reintento no transfiere dos veces;
  - controlan el límite diario por cuenta, con el día calculado en hora argentina.
- **Movimientos.** El historial está paginado y se puede filtrar por fechas. El **extracto** de un período trae saldo inicial, total de créditos, total de débitos y saldo final.
- **Errores** en formato RFC 7807 (`ProblemDetail`), con mensajes en castellano y un `codigo` estable para que lo lea un programa.
- **OpenAPI** en `/swagger-ui.html`.
- **Caché en Redis** del listado de cuentas de cada cliente, invalidado cuando cambia cualquier cuenta, y **límite de intentos de login** (5 fallos en 15 minutos → 429) con contadores en Redis. Ver [Redis](#redis-caché-y-límite-de-login).
- **Web en Angular** (carpeta [`web/`](web/)): ver [Pantallas](#pantallas-web).

### Pantallas (web)

| Pantalla | Quién | Qué hace |
|---|---|---|
| Login | público | Reactive Form con validación; muestra el error de la API (credenciales, 429 por demasiados intentos, sesión vencida) |
| Mis cuentas | CLIENTE | Tarjetas con saldo, disponible (con descubierto), CBU y límite diario |
| Movimientos | ambos | Tabla paginada con filtro de fechas `desde`/`hasta` (validado: `desde` ≤ `hasta`) |
| Extracto | ambos | Saldo inicial, créditos, débitos y saldo final de un período |
| Transferir | CLIENTE | Tres pasos: datos → **confirmación** → comprobante. Valida el CBU (22 dígitos) y el importe (máx. 2 decimales) antes de llamar a la API. La `Idempotency-Key` se genera en el navegador (UUID) |
| Cuentas (operador) | OPERADOR | Todas las cuentas, **depósito por ventanilla**, **bloqueo / desbloqueo**, **alta de cuenta** |
| Clientes (operador) | OPERADOR | Lista y **alta de cliente** (nombre, apellido, DNI, email) |

![Operador](docs/capturas/08-web-operador.png)

### Endpoints

| Método | Ruta | Quién | Qué hace |
|---|---|---|---|
| POST | `/api/auth/login` | público | Devuelve un JWT (429 + `Retry-After` después de 5 fallos seguidos) |
| GET | `/api/cuentas` | ambos | CLIENTE: sus cuentas. OPERADOR: todas (`?clienteId=` para filtrar) |
| GET | `/api/cuentas/{id}` | ambos | Detalle (si la cuenta es ajena, 404) |
| POST | `/api/cuentas` | OPERADOR | Abre una cuenta y genera el CBU |
| PATCH | `/api/cuentas/{id}/estado` | OPERADOR | `ACTIVA` / `BLOQUEADA` |
| POST | `/api/cuentas/{id}/depositos` | OPERADOR | Depósito por ventanilla |
| GET | `/api/cuentas/{id}/movimientos` | ambos | `?desde=AAAA-MM-DD&hasta=…&pagina=0&tamanio=20` |
| GET | `/api/cuentas/{id}/extracto` | ambos | `?desde=…&hasta=…` (por defecto, los últimos 30 días) |
| POST | `/api/transferencias` | ambos | Requiere el header `Idempotency-Key` |
| GET | `/api/transferencias/{id}` | ambos | CLIENTE: sólo si es origen o destino |
| POST | `/api/clientes` | OPERADOR | Alta de cliente |
| GET | `/api/clientes` | OPERADOR | Lista de clientes |
| GET | `/api/clientes/{id}` | ambos | CLIENTE: sólo sus propios datos |

Usuarios demo (sólo se cargan si `CARGAR_DATOS_DEMO=true` y la base está vacía): `ana / ana123` y `bruno / bruno123` (CLIENTE), `operador / operador123` (OPERADOR).

## Decisiones de diseño

### Concurrencia: lock pesimista con orden fijo

Una transferencia lee el saldo, lo valida y lo modifica. Si dos transferencias desde la misma cuenta hacen eso a la vez sin protección, las dos ven el mismo saldo y las dos pasan (el clásico *lost update*). Está comprobado: sin el lock, el test de concurrencia muestra 20 transferencias aprobadas de 10 posibles.

- `CuentaRepository.buscarParaActualizar` usa `@Lock(PESSIMISTIC_WRITE)`. En PostgreSQL, Hibernate lo traduce como `SELECT … FOR NO KEY UPDATE`: la fila queda bloqueada hasta el commit y cualquier otra transferencia que toque esa cuenta espera.
- **Orden de bloqueo:** siempre se bloquea primero la cuenta de id menor. Si A→B y B→A corren al mismo tiempo, las dos piden primero la misma fila, una espera a la otra y no hay deadlock. Si se quita el orden, el test de transferencias cruzadas termina con `ERROR: deadlock detected` de PostgreSQL (también está comprobado).
- **¿Por qué no un lock optimista (`@Version`)?** En una cuenta con mucho movimiento (por ejemplo, una que recibe muchos pagos) un lock optimista genera muchos conflictos, y cada uno obliga a reintentar. El pesimista, en cambio, pone las operaciones en fila. Las transacciones son cortas (unas pocas queries), así que la espera es chica.
- Un detalle de JPA: antes de tomar el lock no se carga la entidad de la cuenta destino, sólo su id (`buscarIdPorCbu`). Si estuviera cargada en el contexto de persistencia, el `SELECT … FOR UPDATE` posterior devolvería la instancia ya cargada, con un saldo viejo.
- El límite diario se calcula **con la cuenta de origen ya bloqueada**, así que la suma de lo enviado en el día es exacta.
- Como última defensa, la base tiene un `CHECK (saldo >= -descubierto_autorizado)`.

### Idempotencia (`Idempotency-Key`)

Si el cliente manda una transferencia y se corta la conexión antes de recibir la respuesta, no sabe si se hizo o no. Con este header puede reintentar sin miedo:

- La clave se guarda en la tabla `transferencia` con `UNIQUE (usuario, idempotency_key)`, junto con un hash SHA-256 de los datos del pedido.
- Si llega la misma clave con **los mismos datos**, se devuelve la transferencia original (mismo id) con `Idempotent-Replayed: true` y no se mueve plata.
- Si llega la misma clave con **otros datos**, se responde `422 clave-idempotencia-reutilizada`.
- **Dos pedidos iguales al mismo tiempo:** el segundo espera el lock de la cuenta. Cuando lo obtiene, encuentra la clave ya confirmada y devuelve la original. Si la carrera se da por otro camino, choca contra la constraint UNIQUE, su transacción se deshace entera (incluido el débito) y el servicio busca la transferencia ganadora en una lectura nueva. Por eso `TransferenciaService` no es transaccional y delega en `EjecutorDeTransferencias`, que sí lo es.
- Sólo quedan registradas las transferencias exitosas. Si una falla (por ejemplo, por saldo insuficiente) no deja rastro, y se puede reintentar con la misma clave.

### CBU con dígitos verificadores

El CBU tiene 22 dígitos en dos bloques, y cada bloque termina en un dígito verificador:

| Bloque | Contenido | Ponderadores |
|---|---|---|
| 1 (8 dígitos) | entidad (3) + sucursal (4) + verificador | 7, 1, 3, 9, 7, 1, 3 |
| 2 (14 dígitos) | número de cuenta (13) + verificador | 3, 9, 7, 1, 3, 9, 7, 1, 3, 9, 7, 1, 3 |

En cada bloque se multiplica cada dígito por su ponderador y se suman los resultados. El verificador es `(10 - suma % 10) % 10`. Está implementado en [`Cbu.java`](src/main/java/ar/cuentas/dominio/Cbu.java) y probado contra un CBU real publicado como ejemplo (`2850590940090418135201`). Los CBU que genera la API usan una entidad ficticia (`999`) y la sucursal `0001`. El número de cuenta sale de una secuencia de PostgreSQL. Un CBU de destino con un verificador incorrecto se rechaza con 400 antes de tocar la base.

### Errores

Todas las respuestas de error son `application/problem+json`:

```json
{
  "type": "urn:cuentas-api:error:saldo-insuficiente",
  "title": "Operación rechazada",
  "status": 422,
  "detail": "Saldo insuficiente en la cuenta 9990001800000000010001: disponible 100.00 ARS, se intentó debitar 150.00 ARS.",
  "instance": "/api/transferencias",
  "codigo": "saldo-insuficiente"
}
```

| Status | Cuándo |
|---|---|
| 400 | Validación (con lista `errores` campo por campo), JSON ilegible, falta `Idempotency-Key`, fechas inválidas |
| 401 | Sin token, token vencido o adulterado, credenciales incorrectas |
| 403 | El rol no alcanza (por ejemplo, un CLIENTE que quiere abrir una cuenta) |
| 404 | No existe, **o es de otro cliente**: no se distingue, para no revelar qué ids existen |
| 409 | Conflicto de unicidad en la base |
| 429 | Demasiados intentos de login fallidos para ese usuario (`demasiados-intentos`, con header `Retry-After`) |
| 422 | Regla de negocio: `saldo-insuficiente`, `monedas-distintas`, `limite-diario-excedido`, `cuenta-bloqueada`, `misma-cuenta`, `clave-idempotencia-reutilizada`, `dni-duplicado`… |
| 503 | La base abortó por un problema de lock (no debería pasar; se puede reintentar con la misma clave) |

Los 401 y 403 se generan en el filtro de seguridad, antes del controller, así que tienen su propio handler ([`ProblemaSeguridadHandler`](src/main/java/ar/cuentas/seguridad/ProblemaSeguridadHandler.java)) con el mismo formato.

### Seguridad

- JWT firmado con HS256. En vez de escribir un filtro a mano, se usa el soporte de *resource server* de Spring Security, que valida la firma, el vencimiento y el emisor. El token lleva `sub`, `rol` y `clienteId`.
- **El secreto viene de la variable `JWT_SECRET`.** El `application.yml` trae un valor por defecto que empieza con `solo-desarrollo-local…`, pensado sólo para correrlo en la máquina propia. Si se usa ese valor, la app lo avisa en el log. Si el secreto tiene menos de 32 bytes, la app no arranca. En `docker-compose.yml` la variable es obligatoria.
- BCrypt para las contraseñas. En el login se hace la comparación BCrypt aunque el usuario no exista, para que el tiempo de respuesta no revele qué usuarios existen.
- Hay roles con `@PreAuthorize` para lo que es sólo de OPERADOR, y un control de titularidad en los servicios para lo que depende del dueño de la cuenta.
- La API es stateless y sin cookies, por eso CSRF está desactivado.

### Otras decisiones

- Importes como `BigDecimal` y `NUMERIC(19,2)`, nunca `double`. Se aceptan como máximo 2 decimales.
- Fechas guardadas en UTC (`TIMESTAMPTZ`). El "día" del límite diario y los filtros `desde`/`hasta` se interpretan en `America/Argentina/Buenos_Aires`. Se usa un `Clock` inyectable, lo que permite testear con la fecha fija.
- Las reglas de saldo viven en la entidad `Cuenta` (`debitar`, `acreditar`), así que se prueban sin base de datos.
- Los DTO son `record`s y el mapeo es a mano: con pocas clases no hacía falta MapStruct.
- `spring.jpa.open-in-view=false` y `ddl-auto=validate`: el esquema lo maneja sólo Flyway.

### Redis: caché y límite de login

Redis se usa para **dos cosas**, ninguna imprescindible: si Redis se cae, la API sigue funcionando contra PostgreSQL (lo prueba `SinRedisIntegrationTest`).

**1. Caché del listado de cuentas de un cliente** (`GET /api/cuentas`, la pantalla de inicio del home banking, que se pide en cada navegación).

- Spring Cache con `@Cacheable` en [`CuentasDelClienteService`](src/main/java/ar/cuentas/cache/CuentasDelClienteService.java), clave = id de cliente. En Redis queda como JSON (`cuentas-api:cuentas-por-cliente::1`), con un TTL de 5 minutos (`CACHE_TTL_CUENTAS`).
- **Invalidación:** un listener JPA en la entidad `Cuenta` ([`InvalidadorDeCacheDeCuentas`](src/main/java/ar/cuentas/cache/InvalidadorDeCacheDeCuentas.java)) borra la entrada del cliente cada vez que una cuenta se crea o cambia (transferencia, depósito, bloqueo, alta). Está en la entidad y no en cada caso de uso para que ningún camino se olvide: una transferencia toca dos cuentas, posiblemente de dos clientes, y se invalidan las dos.
- **Después del commit:** el borrado se registra con `TransactionSynchronization.afterCommit()`. Si se borrara antes del commit, otro pedido podría volver a cachear el saldo viejo en el medio; y si la transacción se deshace (saldo insuficiente, por ejemplo), no hay nada que invalidar.
- **Qué no se cachea:** el listado completo del operador (cambia con cualquier operación), los movimientos y el extracto. Y lo más importante: **las operaciones nunca leen el caché**. La transferencia y el depósito leen la cuenta de PostgreSQL con lock, así que un saldo cacheado nunca puede aprobar una transferencia sin fondos (lo prueba `elSaldoCacheadoNoAfectaLasReglasDeLaTransferencia`).

**Riesgo de datos viejos (y por qué es aceptable acá).** Hay dos casos en los que el listado puede mostrar un saldo desactualizado: (a) si Redis no responde justo al invalidar, la entrada vieja vive hasta que vence el TTL (se registra un `ERROR` en el log); (b) si alguien cambia una cuenta por fuera de JPA (un `UPDATE` a mano en la base). En los dos casos el peor efecto es **mostrar** un saldo viejo como máximo 5 minutos; no se pierde ni se duplica plata, porque las reglas se validan contra la base. En un banco real esto se discutiría con negocio: quizá se cachearía sólo lo que no es saldo, o se bajaría el TTL.

**2. Límite de intentos de login** ([`LimitadorDeIntentosDeLogin`](src/main/java/ar/cuentas/seguridad/LimitadorDeIntentosDeLogin.java)).

- Un contador por usuario (`INCR` + `PEXPIRE` en un script Lua, atómico): al quinto fallo en 15 minutos, el login de ese usuario responde **429** con `Retry-After`, **aunque la contraseña sea correcta** (si no, un atacante seguiría probando y sabría cuándo acertó). Un login correcto reinicia el contador.
- Por qué Redis y no un `Map` en memoria: con varias instancias de la API todas ven el mismo contador, y el vencimiento lo maneja Redis solo.
- **Fail-open:** si Redis no responde, se deja pasar el login (con un warning en el log). Se prioriza que los clientes puedan entrar; la alternativa (fail-closed) es más segura pero deja a todos afuera si Redis se cae.
- Limitación conocida: alguien puede bloquear a propósito a otro usuario durante 15 minutos fallando 5 veces con su nombre. Es el costo de limitar por usuario; limitar además por IP lo mitigaría.

### Frontend (web/)

- **Angular 22 standalone**, sin NgModules. Estado con **signals** (`signal`, `computed`); RxJS sólo para HTTP. `ChangeDetectionStrategy.OnPush` y app sin zone.js.
- **Interceptor JWT** funcional: agrega `Authorization: Bearer …` a `/api/**` y, ante un 401, cierra la sesión y manda al login con el motivo.
- **Guards por rol** (`authGuard`, `rolGuard('OPERADOR')`, `invitadoGuard`). Son una ayuda de navegación, no seguridad: la API controla los permisos de verdad.
- **Errores ProblemDetail:** [`errores.ts`](web/src/app/core/errores.ts) traduce cualquier error de HttpClient a un mensaje en castellano (usa el `detail` de la API si está) y pega los errores de validación de la API en el campo del formulario que corresponde.
- **Idempotency-Key en el cliente:** se genera al pasar a la confirmación y se reusa si el usuario reintenta *esa misma* transferencia (por ejemplo, se cortó la conexión). Si vuelve a editar los datos, se genera una nueva.
- El token se guarda en `sessionStorage` (se borra al cerrar la pestaña). Cualquier script en la página podría leerlo; la defensa es que Angular escapa todo y nginx manda una `Content-Security-Policy` estricta.
- En Docker, **nginx** sirve el build y hace de proxy de `/api` hacia la API: el navegador ve un solo origen y no hace falta CORS. En desarrollo, `ng serve` usa `proxy.conf.json` para lo mismo.

### Diagramas

Capas y modelo ([fuente](docs/diagramas/arquitectura.puml)):

![Arquitectura](docs/diagramas/arquitectura.png)

Secuencia de una transferencia ([fuente](docs/diagramas/secuencia-transferencia.puml)):

![Secuencia](docs/diagramas/secuencia-transferencia.png)

## Estructura

```
src/main/java/ar/cuentas/
├── web/            controllers REST, DTOs (records) y validación de CBU
├── servicio/       casos de uso: TransferenciaService (idempotencia),
│                   EjecutorDeTransferencias (@Transactional, locks), cuentas, movimientos
├── repositorio/    Spring Data JPA (incluye el SELECT ... FOR UPDATE)
├── dominio/        entidades JPA con reglas (Cuenta, Movimiento, Transferencia), Cbu
├── cache/          Spring Cache + Redis: configuración, listado cacheado, invalidación
├── seguridad/      JWT, Spring Security, 401/403 como ProblemDetail, límite de login (Redis)
├── error/          @RestControllerAdvice y excepciones
└── config/         reloj, OpenAPI, propiedades, datos demo
src/main/resources/db/migration/   migraciones de Flyway
src/test/java/ar/cuentas/
├── dominio/        tests unitarios (CBU, reglas de Cuenta)
├── servicio/       tests unitarios con Mockito
└── integracion/    MockMvc y HTTP real contra PostgreSQL y Redis (incluye la concurrencia)
web/                frontend Angular
├── src/app/core/       sesión (signals), interceptor JWT, guards, errores, API, validadores
├── src/app/paginas/    login, cuentas (movimientos, extracto), transferir, operador
├── e2e/                Playwright (recorrido completo + script de capturas)
├── nginx.conf          sirve el build y hace de proxy de /api
└── Dockerfile
.github/workflows/ci.yml   backend + frontend + imágenes Docker + e2e
docs/               diagramas (PlantUML) y capturas
ejemplos.http       pedidos listos para IntelliJ / VS Code REST Client
```

## Cómo correrlo

### Con PostgreSQL local

Requisitos: Java 21, Maven 3.9+, PostgreSQL 16 y (opcional) Redis 7.

```bash
createdb cuentas                      # y cuentas_test para los tests
export DB_URL=jdbc:postgresql://localhost:5432/cuentas DB_USER=postgres DB_PASSWORD=postgres
export JWT_SECRET="$(openssl rand -base64 48)"   # opcional en local; si no, se usa el de desarrollo
mvn spring-boot:run
```

- API: http://localhost:8080
- Swagger UI: http://localhost:8080/swagger-ui.html (botón "Authorize" con el token del login)

Redis es opcional en local: si no hay uno en `localhost:6379` (`REDIS_HOST`/`REDIS_PORT`), la API anda igual, sin caché ni límite de login, y lo avisa en el log.

Con los datos demo cargados, los CBU son: Ana ARS `9990001800000000010001`, Ana USD `9990001800000000010018`, Bruno ARS `9990001800000000010025` y Bruno USD `9990001800000000010032`.

```bash
TOKEN=$(curl -s -X POST localhost:8080/api/auth/login -H 'Content-Type: application/json' \
  -d '{"username":"ana","password":"ana123"}' | jq -r .token)

curl -i -X POST localhost:8080/api/transferencias \
  -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
  -H 'Idempotency-Key: 7f1c0e7a-alquiler-octubre' \
  -d '{"cuentaOrigenId":1,"cbuDestino":"9990001800000000010025","importe":85000,"concepto":"Alquiler octubre"}'
# HTTP/1.1 201 · Location: /api/transferencias/1 · Idempotent-Replayed: false
# Repetir el mismo comando devuelve la misma transferencia con Idempotent-Replayed: true
```

En [`ejemplos.http`](ejemplos.http) hay más pedidos, incluidos los de los casos de error.

### Con Docker Compose (lo más simple; también en Windows 10 con Docker Desktop)

```bash
cp .env.example .env      # y poner un JWT_SECRET propio (mínimo 32 caracteres)
docker compose up -d --build --wait
```

En Windows (PowerShell), con Docker Desktop abierto: `copy .env.example .env`, editar `.env` con el Bloc de notas y después el mismo `docker compose up -d --build --wait`.

| Servicio | Contenedor | URL / puerto en tu máquina |
|---|---|---|
| Web (nginx + Angular) | `cuentas-web` | http://localhost:4201 |
| API (Spring Boot) | `cuentas-api` | http://localhost:8082 · Swagger: http://localhost:8082/swagger-ui.html |
| PostgreSQL 16 | `cuentas-db` | `localhost:54321` (usuario y base `cuentas`) |
| Redis 7 | `cuentas-redis` | `localhost:63791` |

Los puertos son poco comunes a propósito, para no chocar con un PostgreSQL o Redis instalado. Para bajar todo: `docker compose down` (con `-v` también se borra la base).

### Frontend sin Docker

Requisitos: Node.js 24 (o 22.22.3+; Angular 22 no acepta versiones anteriores).

```bash
cd web
npm ci
npm start        # http://localhost:4200, con proxy de /api a http://localhost:8080 (la API local)
```

Si la API corre en Docker (puerto 8082), cambiar el `target` de `web/proxy.conf.json`.

## Tests

### Backend (74 tests)

Los tests de integración usan un **PostgreSQL real** y no H2, porque los locks (`FOR UPDATE`), las constraints y el manejo de fechas tienen que comportarse igual que en producción. Lo mismo con **Redis**: los tests de caché y de límite de login usan un Redis real (base 1, que se vacía en cada test), no un mock. No se usa Testcontainers; el perfil `test` apunta a servicios locales (en CI, a los servicios del job):

```bash
createdb cuentas_test
redis-server &          # o: docker run -d -p 6379:6379 redis:7-alpine
mvn verify
# otra base u otro Redis: TEST_DB_URL=... TEST_DB_USER=... TEST_DB_PASSWORD=... TEST_REDIS_HOST=... TEST_REDIS_PORT=... mvn verify
```

Cada test de integración vacía las tablas (`TRUNCATE … RESTART IDENTITY`) y carga sus datos. Resultado de la última corrida:

```
Tests run: 74, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

| Clase | Tipo | Qué prueba |
|---|---|---|
| `CbuTest` (11) | unitario | Algoritmo de dígitos verificadores, contra un CBU real y con casos inválidos |
| `CuentaTest` (6) | unitario | Débito, saldo insuficiente, descubierto de CC, cuenta bloqueada, CA sin descubierto |
| `EjecutorDeTransferenciasTest` (7) | Mockito | Orden de bloqueo, monedas distintas, límite diario (hora AR), dueño de la cuenta, movimientos |
| `TransferenciaServiceTest` (6) | Mockito | Reintento idempotente, clave con otros datos, carrera resuelta por UNIQUE, hash |
| `AuthIntegrationTest` (4) | MockMvc + PG | Login, credenciales malas, sin token, token adulterado |
| `CuentaIntegrationTest` (8) | MockMvc + PG | Visibilidad por rol, 404 para cuentas ajenas, alta con CBU válido, validación, depósitos, bloqueo |
| `TransferenciaIntegrationTest` (12) | MockMvc + PG | Caso feliz, saldo insuficiente sin efectos, descubierto, idempotencia, límite diario, CBU inválido… |
| `MovimientoIntegrationTest` (4) | MockMvc + PG | Paginación, filtro de fechas en hora AR, extracto, parámetros inválidos |
| `ConcurrenciaIntegrationTest` (3) | HTTP real + PG | **20 transferencias en paralelo** desde una cuenta con saldo para 10: pasan exactamente 10 y el saldo queda en 0. **Transferencias cruzadas A↔B** sin deadlock. **10 reintentos simultáneos** con la misma clave: una sola transferencia |
| `CacheCuentasIntegrationTest` (8) | MockMvc + PG + Redis | El listado queda en Redis como JSON con TTL; la 2.ª consulta no va a la base; transferencia invalida a los dos clientes; una rechazada no invalida; depósito, bloqueo y alta invalidan; un saldo cacheado no afecta las reglas |
| `LimiteLoginIntegrationTest` (4) | MockMvc + PG + Redis | 429 al quinto fallo aunque la contraseña sea buena; el contador vence y un login bueno lo reinicia; mayúsculas/espacios cuentan como el mismo usuario |
| `SinRedisIntegrationTest` (1) | MockMvc + PG, Redis apagado | Login, listado y transferencia funcionan aunque Redis no responda |

Los 61 tests que tenía el proyecto antes de agregar Redis siguen pasando sin cambios.

### Frontend (41 tests unitarios + 1 e2e)

```bash
cd web
npm test           # Vitest (jsdom): 41 tests
npm run lint       # ESLint con angular-eslint
npx playwright test   # e2e; necesita el stack levantado (docker compose up -d --wait) y Chromium
```

| Archivo | Qué prueba |
|---|---|
| `sesion.service.spec.ts` (5) | Guardar/restaurar la sesión, vencimiento del token, dato corrupto en storage |
| `auth.interceptor.spec.ts` (4) | Agrega el Bearer sólo a `/api`, no al login; ante 401 cierra la sesión y va al login (salvo el 401 del propio login) |
| `guards.spec.ts` (4) | Sin sesión → login (recordando a dónde iba); rol equivocado → inicio; con sesión no se muestra el login |
| `errores.spec.ts` (6) | ProblemDetail → mensaje, errores por campo pegados al control, API caída (status 0), respuestas que no son ProblemDetail |
| `validadores.spec.ts` (5) | CBU (22 dígitos y verificadores), importe (positivo, 2 decimales), rango de fechas |
| `idempotencia.spec.ts` (2) | UUID v4, también sin `crypto.randomUUID` (http que no es localhost) |
| `login.spec.ts` (4), `transferir.spec.ts` (5) | Formularios: validación, 429 en castellano, sin redirección abierta con `?volver=`, paso de confirmación, **misma Idempotency-Key en el reintento**, clave nueva si se editan los datos, error 422 sin perder los datos |
| `app.spec.ts` (3), `app.routes.spec.ts` (3) | Menú según el rol; redirecciones de `/` y de rutas desconocidas |
| `e2e/recorrido.spec.ts` (Playwright) | Contra el stack real: login → mis cuentas → transferencia con confirmación → movimientos → **el saldo cambió (caché invalidado)** → salir |

### CI (GitHub Actions)

[`.github/workflows/ci.yml`](.github/workflows/ci.yml) corre en cada push a `main` y en cada pull request:

1. **backend:** `mvn -B verify` con PostgreSQL 16 y Redis 7 como *service containers*.
2. **frontend:** `npm ci`, lint, tests y build de producción (Node 24).
3. **docker:** build de las imágenes de la API y de la web (después de que pasen 1 y 2).
4. **e2e:** `docker compose up --build --wait` y el recorrido de Playwright en Chromium; si falla, sube el reporte y los logs.

## Qué está probado y qué no

| Tema | Estado |
|---|---|
| Tests del backend (74) contra PostgreSQL 16 y Redis 7 locales | ✅ Corridos, todos pasan (`mvn verify`) |
| Tests unitarios del frontend (41), lint y build de producción | ✅ Corridos, todos pasan |
| E2E de Playwright contra el stack de Docker Compose | ✅ Corrido, pasa |
| `docker compose up` con Postgres + Redis + API + web, login real a través de nginx, clave del caché en Redis | ✅ Levantado y probado (puertos 4201/8082/54321/63791). Detalle: en el entorno donde se armó, las imágenes se construyeron con los mismos Dockerfiles más una línea para confiar en el certificado del proxy de red de ese entorno; con internet directo no hace falta |
| Workflow de GitHub Actions | ⚠️ Validado con `actionlint` (sin errores), pero **no ejecutado en GitHub** desde acá: corre recién cuando se sube al repo |
| Docker Desktop en Windows 10 | ⚠️ No probado (sólo Linux); los comandos son los mismos |
| Que el test de concurrencia detecta errores de verdad | ✅ Comprobado a mano: sin `@Lock` falla (20 aprobadas en vez de 10); sin el orden de bloqueo, PostgreSQL reporta `deadlock detected` |
| App corriendo de verdad (`java -jar`) con login, transferencia, reintento idempotente, error 422 y movimientos por curl | ✅ Hecho a mano |
| Swagger UI | ✅ Renderizado y usado con Playwright (son las capturas de `docs/capturas/`) |
| Vencimiento del JWT | ⚠️ Lo valida Spring Security, pero no hay un test específico (sí hay uno de token adulterado) |
| Varias instancias de la API contra la misma base y el mismo Redis | ⚠️ El lock está en la base y los contadores en Redis, así que debería funcionar, pero no lo probé |
| Accesibilidad y diseño responsive de la web | ⚠️ Formularios con `label` y roles ARIA básicos; no se auditó con lector de pantalla ni en celulares |
| Rendimiento y carga | ❌ No medido |
| Transferencias a otros bancos (interbancarias), conversión de moneda, refresh o revocación de tokens, recuperación de contraseña, límite de login por IP | ❌ Fuera de alcance, no implementado |

## Capturas

| Login | Movimientos después de transferir |
|---|---|
| ![Login](docs/capturas/04-web-login.png) | ![Movimientos](docs/capturas/07-web-movimientos.png) |

| Transferencia ejecutada desde Swagger | Error de negocio como ProblemDetail |
|---|---|
| ![Transferencia](docs/capturas/02-swagger-transferencia.png) | ![Error](docs/capturas/03-swagger-error-problemdetail.png) |

Las capturas de la web se regeneran con `CAPTURAS=1 npx playwright test capturas` (desde `web/`, con el stack levantado).

## Cómo contarlo en una entrevista

1. **Qué es:** un mini home banking full stack: API REST en Spring Boot con PostgreSQL y un frontend en Angular, con Redis y CI. Es de portfolio, con datos de ejemplo.
2. **Lo difícil está en el backend:** transferencias atómicas con lock pesimista en orden fijo (sin sobregiros ni deadlocks) e idempotencia con `Idempotency-Key`. Lo demuestro con un test de 20 transferencias en paralelo con saldo para 10.
3. **El front acompaña esa idea:** la `Idempotency-Key` se genera en el navegador al confirmar y se reusa si el usuario reintenta, así un doble clic o un corte de red no transfiere dos veces.
4. **Redis con criterio:** cacheo sólo el listado de cuentas, invalido después del commit desde un listener de la entidad, y las operaciones nunca leen el caché. Sé cuál es el riesgo (un saldo viejo visible hasta 5 minutos si falla la invalidación) y por qué no mueve plata.
5. **Límite de login en Redis:** contador atómico con Lua, 429 con `Retry-After`, y una decisión explícita de *fail-open* si Redis se cae.
6. **Angular moderno:** standalone, signals, Reactive Forms tipados, interceptor y guards funcionales, errores ProblemDetail mapeados a cada campo del formulario.
7. **Tests en todas las capas:** 74 del backend contra Postgres y Redis reales (no H2 ni mocks), 41 unitarios del front y un e2e de Playwright contra el stack de Docker Compose. Todo eso corre en GitHub Actions.
8. **Honestidad:** en el README dejo escrito qué probé y qué no (por ejemplo, el workflow no lo vi correr en GitHub desde el entorno donde lo armé).

**Preguntas probables**

- *¿Por qué no cacheás el saldo para validar la transferencia?* Porque el saldo para operar tiene que ser el de la base, con lock. El caché es sólo para mostrar.
- *¿Qué pasa si Redis se cae?* La API sigue: el caché va directo a la base y el login deja pasar (fail-open). Lo prueba `SinRedisIntegrationTest`.
- *¿Por qué invalidar después del commit?* Si borro antes, otro pedido puede volver a cachear el saldo viejo antes de que se confirme el nuevo.
- *¿Dónde guardás el JWT y qué riesgo tiene?* En `sessionStorage`. Un XSS lo podría leer; lo mitigo con el escapado de Angular y una CSP estricta en nginx. La alternativa es una cookie `HttpOnly`, que obliga a manejar CSRF.
- *¿Los guards de Angular son seguridad?* No, son navegación. Los permisos los controla la API (403/404).
- *¿Por qué signals y no todo con RxJS?* Para estado de pantalla es más simple y no hay que desuscribirse; RxJS lo dejo para HTTP.
- *¿Qué mejorarías?* Testcontainers para no depender de servicios locales, refresh token, límite de login también por IP, y métricas del caché (aciertos/fallos).
