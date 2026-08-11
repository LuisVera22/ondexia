# Backend de Ondexia

Java 21 · Spring Boot 4.0 · PostgreSQL 17 · Maven multi-módulo.

| Módulo | Artefacto | Qué es |
|---|---|---|
| `ondexia.domain` | `ondexia-domain` | Entidades, enumerados, catálogos SUNAT y puertos de persistencia. **No depende de ningún otro módulo de Ondexia** |
| `ondexia.api` | `ondexia-api` | API Core: reglas de negocio, correlativos, stock. **Nunca abre una conexión hacia SUNAT** |
| `ondexia.facturacion` | *(pendiente)* | Emisor + Poller. El único que toca certificados y SUNAT |

## Arrancar en local

Necesitas **Java 21** y **Docker**. Maven no hace falta: lo trae el wrapper.

```bash
cd apps/backend/ondexia.api && docker compose up -d
```

```bash
cd apps/backend && ./mvnw spring-boot:run -pl ondexia.api -am
```

La primera vez, el wrapper descarga Maven 3.9.16 y Flyway crea el esquema con datos de ejemplo.

### Pedir un token y llamar a la API

No hay Cognito desplegado todavía, así que el perfil `local` emite sus propios tokens. Son JWT RS256 de verdad, firmados con una clave que se genera nueva en cada arranque y solo vive en memoria — el código que se ejecuta después es idéntico al de producción.

```bash
curl -s -X POST "http://localhost:8080/desarrollo/token?sub=usuario-demo"
```

```bash
curl -s http://localhost:8080/api/v1/contexto -H "Authorization: Bearer $TOKEN"
```

El usuario de ejemplo tiene **dos empresas**: en una es Administrador y en la otra Vendedor acotado a una sucursal. Sin elegir empresa, el conjunto de permisos sale vacío. Para elegirla:

```bash
curl -s http://localhost:8080/api/v1/contexto -H "Authorization: Bearer $TOKEN" -H "X-Empresa-Id: 00000000-0000-4000-8000-000000000010"
```

Cambia el último dígito a `11` y verás cómo el mismo token devuelve otro rol y otros permisos. Ese contraste es el sistema de autorización entero en dos llamadas.

## Comandos

```bash
cd apps/backend && ./mvnw test
```

```bash
cd apps/backend && ./mvnw verify
```

`test` levanta PostgreSQL con Testcontainers y corre las 15 pruebas de integración. `verify` añade OWASP Dependency-Check, que es **bloqueante**: en un sistema que firma comprobantes con valor tributario, un CVE conocido es un defecto, no un aviso.

> **`verify` necesita una clave de API del NIST.** Desde 2023 la NVD limita con dureza a quien consulta sin clave: la primera sincronización pasa de minutos a horas, o falla por límite de peticiones. Es gratuita y se pide en [nvd.nist.gov/developers/request-an-api-key](https://nvd.nist.gov/developers/request-an-api-key).
>
> ```bash
> cd apps/backend && ./mvnw verify -Ddependency-check.nvdApiKey=TU_CLAVE
> ```
>
> Para el trabajo del día a día, `./mvnw test` no lo ejecuta. Y para saltarlo explícitamente, `-Ddependency-check.skip=true`.

Acotar a un módulo exige `-am`, o Maven no construye sus dependencias:

```bash
cd apps/backend && ./mvnw test -pl ondexia.api -am
```

## El contrato OpenAPI

Se genera **desde el código**, no al revés. `ExportarContratoIT` lo escribe en `ondexia.contracts/openapi.yaml` en cada ejecución de la suite; de ahí se genera el cliente Angular.

Si ese archivo aparece modificado en `git status`, es porque la API cambió. Revisa el diff antes de confirmar.

## Tres cosas que conviene saber antes de tocar nada

### 1. El aislamiento multiempresa no depende de que te acuerdes del `WHERE`

Cada tabla transaccional lleva `empresa_id` y una política de Row Level Security. La variable de inquilino la fija `GestorTransaccionesConAislamiento` al abrir **cada** transacción, con `set_config(..., true)` — válida solo dentro de la transacción y revertida por el motor al terminar. Ese `true` es lo que impide la fuga por reutilización de conexiones en Lambda.

Sin contexto, las políticas no dejan pasar ninguna fila. **Falla cerrado**: un despiste deja al sistema sin datos, que es ruidoso y se arregla; nunca con datos de más, que no se nota.

Al añadir una tabla transaccional, su migración debe llamar a `activar_aislamiento_empresa('nombre_tabla')`. La aplicación avisa en el log de arranque si encuentra tablas con `empresa_id` sin política.

### 2. Por qué hay dos roles de base de datos en local

**Un superusuario de PostgreSQL se salta todas las políticas de RLS**, y `FORCE ROW LEVEL SECURITY` tampoco le alcanza — `FORCE` solo afecta al propietario de la tabla. Con la imagen oficial, `POSTGRES_USER` es el superusuario bootstrap del clúster, y no se le puede quitar ese atributo:

```
ALTER ROLE postgres NOSUPERUSER;
ERROR:  permission denied to alter role
DETAIL: The bootstrap superuser must have the SUPERUSER attribute.
```

Por eso `docker/initdb/10-rol-aplicacion.sql` crea un rol `ondexia` aparte, dueño de su base pero sin privilegios de clúster. Es la misma forma que tiene en RDS, donde el usuario maestro tampoco es el superusuario bootstrap.

Esto se descubrió porque las pruebas de aislamiento fallaron. Sin ellas habría llegado a producción **pareciendo protegido**: las políticas existían, `pg_policies` las listaba, y no filtraban nada. `ComprobacionAislamiento` aborta el arranque si vuelve a ocurrir.

> Si levantaste la base antes de que existiera ese script, el volumen ya está inicializado y los scripts de `initdb` no se vuelven a ejecutar: `docker compose down -v`.

### 3. Los permisos no viajan en el token

El JWT porta identidad y nada más. La empresa activa, el rol y los ~200 permisos se resuelven en la base **en cada petición**, porque un JWT es válido hasta que caduca y revocar «anular comprobante» no puede esperar a la renovación.

La autorización se declara así:

```java
@PreAuthorize("@permisos.puede('ventas.comprobante', 'anular')")
```

Deniega por defecto: sin contexto, sin empresa activa o sin rol, devuelve `false`.

## Convenciones

Sustantivo del dominio en español, sufijo técnico en inglés: `ProductoRepository`, `EmitirBoletaUseCase`. Métodos de negocio en español; los derivados de Spring Data en inglés, porque el framework *analiza* el nombre para construir la consulta — `findByEmpresaIdAndCodigo` no se puede llamar de otra forma sin escribir la consulta a mano.

Tablas y columnas en `snake_case` español (DTE §5).

El corte de paquetes es **por dominio, no por capa técnica**: existe `almacen` y existe `ventas`, y dentro de cada uno están sus capas. Ver `com.ondexia.domain.package-info` para el razonamiento completo.

## Notas sobre Spring Boot 4

La versión 4 reorganizó los módulos, y los ejemplos que circulan por internet siguen usando los nombres de la 3.x. Lo que cambió y afecta aquí:

| Antes (3.x) | Ahora (4.0) |
|---|---|
| `spring-boot-starter-aop` | `spring-boot-starter-aspectj` — aquí no hace falta ninguno |
| `flyway-core` a secas | `spring-boot-starter-flyway`; con la librería suelta **no hay autoconfiguración y no migra nada** |
| `org.springframework.boot.autoconfigure.domain.EntityScan` | `org.springframework.boot.persistence.autoconfigure.EntityScan` |
| `@AutoConfigureMockMvc` en `spring-boot-starter-test` | módulo aparte `spring-boot-starter-webmvc-test` |

Y de Testcontainers 2.0: los artefactos llevan prefijo (`testcontainers-postgresql`, no `postgresql`) y `PostgreSQLContainer` ya no es genérica.
