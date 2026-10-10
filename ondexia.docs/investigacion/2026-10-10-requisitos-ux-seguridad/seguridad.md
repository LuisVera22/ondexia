# Seguridad verificable y proporcional para Ondexia

Estado: investigación y recomendaciones; **no auditoría de penetración**.
Base: `develop`, `6f7ae796af30dc10ccb6307c482097a24e3c53de`.
Fuentes S01–S17 del [registro](README.md). No se cambió código ni infraestructura.

## 1. Referencias y función

| Referencia consultada | Uso propuesto |
|---|---|
| [OWASP Top 10:2025](https://top10.owasp.org/2025/) | Organizar riesgos de aplicación y sensibilización |
| [ASVS 5.0.0](https://owasp.org/projects/asvs) | Seleccionar requisitos verificables, con identificador y versión exactos |
| [API Security Top 10:2023](https://api-security.owasp.org/editions/2023/en/0x11-t10/) | Revisar autorización por objeto/propiedad/función, consumo e integraciones |
| [WSTG 4.2](https://wstg.owasp.org/v4.2/) | Elegir procedimientos de prueba según superficie y amenaza |
| [Cheat Sheets](https://cheatsheetseries.owasp.org/IndexASVS.html) | Traducir requisitos a prácticas de implementación |
| [SAMM 2.0](https://owaspsamm.org/model/) / [NIST SSDF 1.1 final](https://csrc.nist.gov/projects/ssdf) | Organizar prácticas, responsabilidades y mejora según recursos |

La tabla de seguridad del DTE corresponde a categorías de 2021 y mezcla controles
con afirmaciones que deben reconciliarse con decisiones posteriores. No basta
actualizar nombres para afirmar que se cumple 2025.

Propuesta: perfil priorizado basado en ASVS 5.0.0, considerando nivel 2 como objetivo
por datos de clientes y operaciones fiscales. Seleccionar requisitos aplicables,
justificar no aplicables, verificar y registrar pendientes. **No declarar cumplimiento
de nivel 2** por evaluar solo una selección. La matriz de requisitos numerados debe
extraerse de la versión estable; aquí no se inventan IDs a partir del índice.

## 2. Ciberseguridad y seguridad de la información

La primera protege sistemas frente a ataques y abusos. La segunda cubre también
personas, procesos y datos: confidencialidad, integridad, disponibilidad, trazabilidad,
custodia, acceso, recuperación y responsabilidades. Un dato puede perderse por
error operativo sin atacante; un comprobante puede alterarse por una decisión
funcional incorrecta, aunque TLS y autenticación funcionen.

| Activo | Daño relevante | Tratamiento propuesto |
|---|---|---|
| XML/CDR/documentos emitidos | Alteración, pérdida, exposición o emisión indebida | Integridad, conservación, permisos, restauración y evidencia fiscal |
| Certificado privado y credenciales SOL | Firma no autorizada y compromiso del contribuyente | Acceso exclusivo necesario, separación del emisor, custodia y rotación |
| Tokens y cuentas | Suplantación, acceso entre empresas o privilegios indebidos | Autenticación, sesión, revocación efectiva y mínimo privilegio |
| Identidad/domicilio de clientes y usuarios | Divulgación y tratamiento indebido | Minimización, acceso, retención definida y evidencias sin datos reales |
| Pagos, importes, stock y correlativos | Duplicación, fraude o datos económicamente incorrectos | Reglas, precisión decimal, transacciones, constraints y concurrencia |
| Bitácoras y registros | Pérdida de trazabilidad o filtración de secretos | Datos mínimos, acceso restringido, integridad, retención y alarmas |
| Código, dependencias, CI y estado de infraestructura | Introducción de código malicioso o exposición masiva | Integridad de suministro, credenciales acotadas, revisión y trazabilidad |
| Logos públicos e históricos | Contenido no válido, abuso de almacenamiento o historia alterada | Validación de bytes, publicación controlada, límites y referencias inmutables |

La publicidad de un logo no implica que certificados, comprobantes o clientes
puedan usar la misma política. Definir acceso por activo. Revisar normativa peruana
vigente y obligaciones contractuales en una actividad separada: este informe no
confirma plazos legales ni cumplimiento de privacidad.

## 3. Modelo de amenazas del corte de ventas

Fronteras: navegador no confiable → API; identidad Cognito → aplicación; aplicación
→ PostgreSQL con rol restringido; API → objetos S3; evento/orden → emisor;
emisor → SUNAT/proveedor; administración y soporte → cuentas/entornos.
Incluir rutas directas a funciones, perfiles locales, cargas prefirmadas y CI.

Por flujo documentar activo, actor/abuso, precondición, impacto, control, propietario
y prueba. [OWASP Threat Modeling](https://cheatsheetseries.owasp.org/cheatsheets/Threat_Modeling_Cheat_Sheet.html).

| Amenaza concreta | Control a contrastar |
|---|---|
| Usuario A solicita/modifica documento de B | Asignación a empresa, permiso por operación y RLS; rutas alternativas y objetos externos |
| Usuario modifica total, empresa, estado o certificado enviados por cliente | DTO permitidos y servidor autoritativo; rechazar campos/acciones no autorizados |
| Token firmado para otra aplicación o clave rotada | Audiencia obligatoria, uso apropiado y actualización verificable de claves |
| Doble envío/canje o respuesta tardía | Idempotencia, estado, correlativo y efectos transaccionales |
| Subida anuncia PNG pero contiene otros bytes | Validar contenido real y límites; impedir publicación prematura |
| Se reutiliza URL de subida para cambiar un logo ya referenciado | Inmutabilidad de objeto/versión efectiva, no solo UUID en su clave |
| Proveedor/XML/ZIP consume recursos o causa lectura externa | Límites, parsers endurecidos y destinos controlados |
| Robo de token mediante XSS en equipo compartido | Codificación/saneamiento, CSP, sesión, expiración y revocación según política |
| Saturación de consultas/objetos/reintentos con costo por uso | Cuotas, límites por contexto, tiempos y monitoreo; no solo límite global |
| Se omite análisis y el pipeline queda verde | Estado del control visible y puerta de publicación coherente |

## 4. Mapa OWASP Top 10:2025 a la aplicación

Las categorías siguientes se contrastaron con la lista oficial. Las medidas son
selección propia para Ondexia; **no indican controles certificados**.

| Riesgo 2025 | Comprobación propuesta en Ondexia |
|---|---|
| A01 · Control de acceso roto | Empresa, operación, objeto y propiedad; archivos/caché además de RLS |
| A02 · Configuración de seguridad incorrecta | Perfil, audiencia, IAM, CORS, cabeceras, diagnóstico y configuración obligatoria |
| A03 · Fallos de cadena de suministro | Dependencias, acciones, lockfiles, revisiones, procedencia y scanners ejecutados |
| A04 · Fallos criptográficos | TLS, claves/certificados, algoritmo y custodia; no criptografía propia |
| A05 · Inyección | SQL parametrizado, salida HTML, XML, comandos y plantillas |
| A06 · Diseño inseguro | Abusos del flujo, efectos parciales, integridad histórica e idempotencia |
| A07 · Fallos de autenticación | Firma/uso/audiencia/tiempo, sesión, claves rotadas y revocación |
| A08 · Fallos de integridad de software o datos | Órdenes/resultados auténticos, archivos históricos, artefactos y mensajes |
| A09 · Fallos de registro y alertas de seguridad | Eventos útiles, sin secretos; alarmas verificadas y respuesta |
| A10 · Gestión incorrecta de condiciones excepcionales | Falla cerrada, timeout, compensación, recursos y recuperación |

[Lista oficial 2025](https://top10.owasp.org/2025/). API Security complementa este
mapa, especialmente controles por objeto/propiedad/función, automatización de
flujos sensibles, cuotas, inventario de rutas y confianza en APIs externas.

## 5. Revisión de código: reglas concretas

### Autenticación, autorización y contexto

Verificar JWT con biblioteca mantenida: firma, algoritmo permitido, emisor,
caducidad obligatoria, audiencia/cliente y uso previsto. Configuración de seguridad
incompleta debe impedir arranque o denegar; no desactivar una validación por valor
vacío. Modelar diferencias de access/id token por endpoint cuando sean necesarias.

Permisos del servidor por solicitud, mínimo privilegio y denegación por defecto;
comprobar relaciones con empresa y objeto. Ocultar menú o usar UUID impredecible
no autoriza. Consultas privilegiadas y soporte requieren frontera explícita, no
una cuenta de aplicación superusuario. Revisar caché, prefijos S3 y reutilización
de conexiones además de tablas.
[Autorización OWASP](https://cheatsheetseries.owasp.org/cheatsheets/Authorization_Cheat_Sheet.html),
[multiempresa](https://cheatsheetseries.owasp.org/cheatsheets/Multi_Tenant_Security_Cheat_Sheet.html).

### Entradas, salidas y datos

DTO específicos con campos permitidos; validación en servidor de longitud, tipo,
rango y combinaciones; constraints para invariantes persistidas. Dinero decimal
exacto. No concatenar datos en SQL/comandos ni deserializar objetos arbitrarios.
Parámetros de ordenación necesitan una lista permitida, no solo parametrizar valores.

Angular debe conservar su saneamiento; cualquier bypass se limita a contenido
confiable explícito y revisado. El pipe de iconos actual recibe claves de un mapa,
lo que no equivale a admitir HTML arbitrario del servidor. Comprobar su frontera
con pruebas, no prohibir todo uso por búsqueda textual.
[Angular](https://angular.dev/best-practices/security).

### Archivos, XML y recursos

La extensión y el MIME no validan el contenido. Contrastar firma de archivo,
decodificación, dimensiones, límites de procesamiento y publicación; decidir si
conviene normalizar imágenes. No publicar un objeto como aprobado antes del
control. Una URL prefirmada es una capacidad temporal, no garantía de bytes,
uso único o inmutabilidad histórica.
[Carga de archivos](https://cheatsheetseries.owasp.org/cheatsheets/File_Upload_Cheat_Sheet.html).

Endurecer parsers según entradas y uso: DTD/entidades/XInclude y acceso externo
de validadores/transformadores. Limitar XML, ZIP y descompresión, cantidad de
entradas, tiempo y memoria. No basta que un parser esté seguro si otra ruta usa
otra fábrica. Tampoco se afirma XXE explotable en XML interno solo por encontrar
una fábrica sin configuración explícita: hay que seguir su procedencia.
[XXE](https://cheatsheetseries.owasp.org/cheatsheets/XML_External_Entity_Prevention_Cheat_Sheet.html).

### Sesión, CSRF y navegador

`sessionStorage` es accesible a JavaScript y no protege tokens frente a XSS.
HttpOnly cambia esa exposición, pero una arquitectura BFF/cookies introduce
costos y controles CSRF: compararla con el modelo SPA actual antes de adoptarla.
Una sesión stateless no es por sí sola razón para deshabilitar CSRF; comprobar
si el navegador adjunta credenciales automáticamente, incluidos caminos alternos.
CORS no es autorización ni impide llamadas fuera del navegador.
[Sesiones OWASP](https://cheatsheetseries.owasp.org/cheatsheets/Session_Management_Cheat_Sheet.html),
[Spring Security](https://docs.spring.io/spring-security/reference/servlet/exploits/csrf.html).

Separar revocación de permisos en nuestra base, revocación de refresh token y
rechazo de access token ya emitido. Verificar firma/caducidad offline no detecta
por sí solo toda revocación. Definir qué debe pasar al cerrar sesión o deshabilitar
usuario y en cuánto tiempo; no prometer invalidación inmediata sin comprobarla.
[AWS Cognito](https://docs.aws.amazon.com/cognito/latest/developerguide/amazon-cognito-user-pools-using-tokens-verifying-a-jwt.html).

### Errores, auditoría y operación

Errores seguros con código y correlación, sin stacktrace o respuestas del proveedor
en interfaz. No registrar tokens, PIN, claves SOL, certificado privado, URLs
prefirmadas completas ni cuerpos que puedan contener secretos. Guardar contexto
mínimo para investigar, proteger bitácoras y evitar inyección en logs.
[OWASP Logging](https://cheatsheetseries.owasp.org/cheatsheets/Logging_Cheat_Sheet.html).

Definir límites/reintentos y resultados desconocidos; alerta necesita prueba de
activación y responsable. Copia de respaldo requiere ensayo de restauración,
no solo configuración. Especificar roles de soporte, acceso excepcional,
retención, clasificación y procedimiento de incidente con evidencias sanitizadas.

## 6. Evidencia observada y trabajo pendiente

«Prueba localizada» indica lectura de su código, **no ejecución en esta investigación**.
«Configurado» indica repositorio, **no configuración efectiva comprobada en AWS**.

| ID | Observación estática y evidencia | Qué falta demostrar / prioridad propuesta |
|---|---|---|
| SEC-01 | Firma JWT, algoritmo RS256, emisor y exp exigidos en `SeguridadPasarelaConfig`; `FirmaDelTokenIT` tiene casos positivos/negativos | Ejecutar y comprobar sensibilidad; validar configuraciones reales. P0 |
| SEC-02 | `ExigeUsoYAudiencia` retorna éxito si cliente esperado es null/vacío; comentario lo justifica por Terraform | Probar ausencia/vacío y cerrar esa dependencia; no se demostró despliegue vulnerable. P0 |
| SEC-03 | JWKS inmutable cargado en configuración; comentario afirma que Cognito no rota por su cuenta | AWS sí advierte posible rotación. Probar clave nueva y procedimiento automatizado/reconciliado, sin añadir NAT por defecto. P0 |
| SEC-04 | Permisos por meta-anotación y pruebas `PermisosAnotacionIT`; RLS y pruebas `AislamientoEmpresaIT`, grants y bitácora | Cubrir escritura, objetos/rutas de ventas, contexto cambiado, caché y rol efectivo; matriz de acceso negativa. P0 |
| SEC-05 | `Identidad` valida prefijo, MIME/tamaño y existencia; S3 usa HeadObject; `IdentidadVisualIT` prueba SVG/mismatch/empresa | No valida bytes de imagen. Probar contenido anunciado como PNG que no lo sea; dimensión/consumo y publicación. Sin XSS demostrado. P0 |
| SEC-06 | Subida S3 usa PUT prefirmado por cinco minutos a una clave UUID; documento guarda clave histórica | Verificar reutilización/sobrescritura después de confirmar o emitir; decidir escritura única o versión de objeto referenciada. Un UUID nuevo no prueba objeto inmutable. AWS no verificado. P0 |
| SEC-07 | `LectorDeRespuestaSunat` rechaza DOCTYPE y limita entidades; otras fábricas procesan XML generado sin endurecimiento equivalente | Flujo de datos y pruebas de XML/ZIP hostiles, límites y transformaciones. No se probó XXE. P1 |
| SEC-08 | Sesión SPA conserva access/refresh en sessionStorage; logout llama revoke; verificador JWT es offline | Evaluar XSS/equipo compartido, semántica de revocación y rotación; comparación SPA/BFF con costo. P1 |
| SEC-09 | Terraform configura CSP, nosniff, HSTS y otras cabeceras | Verificar respuestas servidas por sitio y contenido permitido; no se contactó despliegue. P1 |
| SEC-10 | CI declara token de lectura, acciones por SHA, auditoría pnpm y Dependency-Check; Dependabot semanal | Falta NVD_API_KEY permite omitir SCA y continuar. Verde no implica que el control corrió; definir bloqueo de publicación/estado verificable. P0 |
| SEC-11 | DTE afirma tfsec/checkov en pipeline; CI inspeccionado ejecuta fmt/init/validate, sin esos scanners ni SAST de código identificados | Reconciliar afirmación; evaluar una herramienta por clase de control, costo y falsos positivos. P1 |
| SEC-12 | DTE contiene referencias a Secrets Manager/SQS frente a decisiones posteriores de S3; comentarios de CI también conservan premisas anteriores | Matriz versión/control/configuración/prueba; retirar afirmaciones sin respaldo, sin alterar migraciones. P1 |

SEC-03 es una contradicción con la referencia externa, no evidencia de que ya haya
ocurrido una caída. SEC-06 puede afectar la garantía de logo histórico: debe
verificarse antes de declarar esa frontera completamente protegida. Esta revisión
no ejecutó exploits ni modificó objetos, certificados o datos reales.

## 7. Cómo verificar un control de seguridad

Registrar requisito, activo/amenaza, configuración o código, prueba, resultado,
commit, alcance y límite. Configuración declarada, prueba de unidad y control real
son evidencias diferentes.

Ejemplos de pruebas propuestas:

- JWT legítimo permitido; firma ajena/algoritmo no permitido/otra audiencia/exp
  ausente rechazados; audiencia configurada vacía no habilita el servicio.
- Usuario A puede usar su recurso; no puede leer/escribir uno de B ni hacerlo
  cambiando contexto, cuerpo o ruta; validar aislamiento con rol restringido real.
- Archivo PNG válido pasa; MIME correcto con bytes ajenos no se publica; imagen
  con dimensiones excedidas no agota recursos; la misma capacidad de subida no
  altera el contenido histórico ya referenciado.
- XML externo con DOCTYPE/entidad y ZIP excedido falla sin lecturas/red no
  autorizadas; la mutación aislada del control debe ser detectada en laboratorio.
- Control de dependencias sin datos/credencial produce estado explícito y no se
  contabiliza como análisis correcto; no silenciar error para hacer verde el CI.

Tests deterministas locales primero; ensayos de integración externos en entorno
aislado con autorización y datos sintéticos. Una premisa IAM/Cognito/S3 no se
demuestra con un mock de «permitido». Si no puede comprobarse, no se usa como
única garantía. No probar ataques contra producción como parte implícita de esta tarea.

## 8. Equilibrio de costo, arquitectura y negocio

El mínimo vigente de aislamiento, autenticación, precisión, integridad fiscal y
custodia no se elimina para ahorrar. Sí puede elegirse una implementación más
simple que cumpla el objetivo y cuyo funcionamiento se pueda comprobar.

| Momento | Inversión prioritaria | Qué evitar |
|---|---|---|
| Antes de ampliar uso real | P0, inventario de controles efectivos, recuperación y permisos; corregir fallos demostrados | Dar por comprado/configurado un control o aceptar exposición crítica con ROI inventado |
| Desarrollo habitual | Revisiones por flujo, regresiones negativas, SCA existente, secretos, controles estáticos seleccionados y procedimientos | Instalar varias plataformas solapadas sin operarlas |
| Tras medir crecimiento/exposición | Ensayo independiente, más automatización y observabilidad, medidas contra abuso según señales | SOC/SIEM caro, WAF genérico o nueva arquitectura sin caso de uso/costo |

Priorizar con impacto fiscal/financiero/privacidad, exposición, explotabilidad,
evidencia, recuperación y costo total. «No probado» no significa ni «seguro» ni
«explotable». El costo incluye implementación, nube, revisión de falsos positivos,
soporte y mantenimiento. Licencia gratuita no equivale a costo total cero.

El caso económico puede comparar pérdida esperada en escenarios y reducción de
exposición, pero probabilidades/costos deben etiquetarse estimados o desconocidos.
No prometer porcentajes de reducción. Registrar aceptación de riesgo residual,
responsable, motivo y fecha de revisión cuando corresponda; no crear un circuito
de aprobaciones adicional para controles reversibles ya autorizados.

Este enfoque es compatible con hexagonal: políticas e invariantes en el lugar
que les corresponde, tecnología en adaptadores y fronteras de confianza verificadas.
Una clase, interface o patrón adicional solo se justifica si mejora el control y
su mantenimiento. SAMM/SSDF orientan mejora gradual; no exigen implantar todo su
catálogo de prácticas a la vez.
