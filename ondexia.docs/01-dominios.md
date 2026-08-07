# Ondexia — Esquema de dominios y DNS

Estado: **decidido (v1)** · Fecha: 2026-08-06

**Decisiones cerradas:** marca `ondexia` (dominio verificado libre) · región principal `us-east-1`.

## 1. Dominios a registrar

| Dominio | Uso | Prioridad |
|---|---|---|
| `ondexia.com` | Marca principal: landing + toda la plataforma | **Obligatorio — libre, confirmado** |
| `ondexia.pe` | Defensivo + confianza local (cliente peruano, contexto SUNAT). Redirige 301 a `.com` | Alta |
| `ondexia.com.pe` | Defensivo (evita suplantación en el TLD más usado en Perú) | Media |
| `ondexia.app` / `ondexia.net` | Defensivo de marca | Baja |

> Verificar disponibilidad antes de fijar la marca. Si `ondexia.com` no está libre, **no** usar `getondexia.com` ni `ondexia-app.com` como plan A: el costo de marca a largo plazo es mayor que buscar otro nombre ahora.

**Dónde registrar cada uno:**

- `ondexia.com` → **Route 53 Domains**. Registrar y hosted zone en la misma cuenta: la validación DNS de ACM se automatiza y no dependes de un panel externo para emitir certificados.
- `ondexia.pe` y `ondexia.com.pe` → **Route 53 no es registrar de TLD peruanos**. Hay que registrarlos con un agente acreditado ante NIC.pe (Punto.pe u otro) y luego **apuntar sus nameservers a la hosted zone de Route 53**. Así el DNS sigue centralizado aunque el registrar sea distinto.
- El registro `.pe` exige datos del titular; usar la razón social, no una persona natural, y una cuenta de correo de rol (`dominios@`) que sobreviva a cambios de personal.

## 2. Subdominios de producción

Todo cuelga de `ondexia.com`. Un solo apex, una sola marca.

| Host | Qué sirve | Infra AWS |
|---|---|---|
| `ondexia.com` | Landing / marketing (estática, Astro) | S3 + CloudFront (OAC) |
| `www.ondexia.com` | 301 → apex | CloudFront function |
| `app.ondexia.com` | Aplicación Angular (SPA): ventas, compras, usuarios | CloudFront + S3 |
| `api.ondexia.com` | API backend (REST/GraphQL) | API Gateway o ALB |
| `auth.ondexia.com` | Login / OIDC | Cognito custom domain (o Keycloak en ECS) |
| `cdn.ondexia.com` | Assets estáticos, PDFs y XML de comprobantes servidos al cliente | CloudFront + S3 |
| `ver.ondexia.com` | **Portal público de comprobantes** (sin login): consulta de boleta/factura por el cliente final | CloudFront + Lambda@Edge o API Gateway |
| `docs.ondexia.com` | Documentación de API para integradores | S3 + CloudFront |
| `status.ondexia.com` | Página de estado | Externo (fuera de la infra que monitorea) |

### Decisiones incorporadas

- **Landing y app separadas por subdominio, no por path.** La landing es estática, cacheada agresivamente y desplegada por marketing; la app es dinámica y con auth. Mezclarlas en `ondexia.com/app` obliga a compartir distribución de CloudFront, políticas de caché y pipeline de deploy.
- **`api` separado de `app`.** Permite mover el backend (Lambda → ECS → EKS) sin tocar el frontend, y aplicar WAF/rate-limit distintos.
- **Cookies:** emitir con `Domain=.ondexia.com` solo si `app` y `api` deben compartir sesión. Preferible: tokens JWT en `api` con CORS explícito hacia `https://app.ondexia.com`, sin cookies cross-subdomain.

## 3. Entornos no productivos

**Dos entornos: `dev` y producción.** Sin QA por ahora.

Zona delegada, no subdominios sueltos:

```
dev.ondexia.com      → hosted zone propia
  app.dev.ondexia.com
  api.dev.ondexia.com
  auth.dev.ondexia.com
  ver.dev.ondexia.com
```

La zona delegada conviene aunque uses una sola cuenta AWS: `dev` administra sus propios registros sin tocar la zona raíz, y si mañana separas cuentas, el corte ya está hecho. Si en algún momento entra QA, se replica el mismo patrón en `qa.ondexia.com` sin tocar nada de lo existente.

Con solo dos entornos, la regla que hay que respetar sin excepción es que **`dev` apunte al entorno beta de SUNAT y producción al de producción**, parametrizado en SSM (§5). Sin QA de por medio, `dev` es el único lugar donde se prueban comprobantes antes de que sean legalmente válidos.

Ventaja: delegas `dev.ondexia.com` con un NS record desde la zona raíz y la cuenta de desarrollo administra sus registros sin acceso a producción. Los certificados ACM de dev se validan solos dentro de su zona.

Añadir `noindex` + Basic Auth (CloudFront function) en todo lo que no sea producción.

## 4. Multi-tenant

Arrancar con **path-based**: `app.ondexia.com/o/{slug}`. Es lo que soporta un certificado simple y no exige DNS por cliente.

Reservar desde ya la ruta de crecimiento:
- Certificado ACM en `us-east-1` con SAN `*.ondexia.com` (además del apex) → habilita `{cliente}.ondexia.com` sin re-arquitectura.
- Dominio propio del cliente (`facturacion.sucliente.com` → CNAME a la app) es fase 3; requiere emisión de certificados on-demand.

**Reservar slugs** desde el día uno para que ningún tenant pueda tomarlos: `www, app, api, auth, cdn, docs, status, ver, admin, mail, smtp, blog, static, assets, help, soporte, sunat, ose, dev, qa`.

## 5. Facturación electrónica (SUNAT) — implicancias de red

No requiere subdominio público: la comunicación con SUNAT/OSE es **saliente** (SOAP hacia sus endpoints). Pero sí condiciona el diseño:

- El servicio de facturación debe salir por una **IP fija (Elastic IP)**. Algunos OSE requieren allowlist de IP de origen, y una IP estable evita rehacer trámites cada despliegue. Por costo se implementa con una **instancia NAT `t4g.nano`**, no con NAT Gateway administrado — ver DT-14 del DTE.
- Certificado digital de firma (.pfx del contribuyente) va en **AWS Secrets Manager**, nunca en el repo ni en la imagen. Es distinto e independiente de los certificados TLS de los dominios.
- Endpoints SUNAT a parametrizar por entorno (beta vs producción) en SSM Parameter Store; nunca hardcodeados.
## 5.1 Portal público de comprobantes — `ver.ondexia.com`

**Decidido: sí habrá portal público.**

### El QR de SUNAT no es una URL

El código QR de la representación impresa tiene contenido **fijado por SUNAT**: una cadena delimitada por `|` con RUC emisor, tipo de comprobante, serie, número, total IGV, total venta, fecha de emisión, tipo y número de documento del adquiriente, y el valor resumen de la firma digital. No admite una URL propia.

Consecuencia de diseño: el acceso al portal va **aparte** del QR normativo. Dos opciones en la representación impresa:

1. Un texto corto con el enlace (`ver.ondexia.com/c/XXXXXXXX`) al pie del documento. Es lo mínimo y no compite con el QR obligatorio.
2. Un **segundo QR** rotulado ("Ver comprobante en línea"), claramente separado del QR de SUNAT para que nadie los confunda al fiscalizar.

Recomendado: opción 1 en la primera versión, y el segundo QR solo si los clientes finales lo piden. Dos QR en un ticket de boleta generan más confusión de la que resuelven.

### La URL debe ser opaca, no enumerable

**No usar** `ver.ondexia.com/{ruc}/{serie}/{numero}`. Esa ruta es adivinable: cualquiera puede iterar números de serie y leer los comprobantes de todos los clientes de un emisor — nombre del adquiriente, su documento de identidad y montos. Es una fuga de datos personales, no un detalle estético.

Formato correcto:

```
ver.ondexia.com/c/{token}
```

donde `{token}` es un identificador aleatorio de ≥128 bits (22 caracteres base62), generado con CSPRNG, sin relación derivable con el RUC ni el correlativo. Se almacena junto al comprobante al emitirlo.

### Qué expone

| Contenido | ¿Público? |
|---|---|
| PDF de la representación impresa | Sí |
| XML firmado | Sí |
| CDR de SUNAT (constancia de recepción) | Sí |
| Estado del comprobante (aceptado / rechazado / anulado) | Sí |
| Datos de otros comprobantes del mismo emisor | **No** |

### Controles

- `X-Robots-Tag: noindex, nofollow` — los comprobantes no deben terminar indexados en buscadores.
- **Rate limit por IP** en WAF: el token es largo, pero el límite corta el escaneo automatizado y el scraping si un token se filtra.
- **Sin sesión, sin cookies, sin analytics de terceros.** La página se sirve a un tercero que no es usuario de la plataforma; no hay motivo para rastrearlo.
- Caché de CloudFront alta para PDF y XML (son inmutables una vez emitidos); el **estado** se consulta aparte con TTL corto, porque una anulación debe reflejarse rápido.
- Considerar caducidad del enlace (ej. 12 meses) según lo que decidas sobre retención.

### Por qué subdominio propio y no una ruta de `app`

Es la única superficie de la plataforma **sin autenticación y expuesta a tráfico anónimo masivo**. Separarla permite darle su propio WAF, su propio rate limit y su propia distribución, sin que un pico de consultas públicas afecte a la app de los clientes de pago. Además mantiene la URL corta, que importa cuando va impresa.

## 6. Correo

- Registros MX + SPF + DKIM + DMARC en `ondexia.com` desde el inicio, aunque no envíes correo todavía. Un dominio sin SPF/DMARC es suplantable.
- Correo transaccional (envío de boletas al cliente final) por **SES**, con subdominio dedicado `mail.ondexia.com` como MAIL FROM. Aísla la reputación del transaccional respecto al correo corporativo.
- DMARC inicial: `p=none` con reporte, y endurecer a `p=reject` cuando el flujo esté estable.

## 7. Certificados TLS

Región principal fijada: **`us-east-1`**. Como CloudFront y ACM ya exigen esa región para certificados de distribución, y la app corre ahí también, **basta un solo certificado por entorno** — no hay que duplicarlo en una región regional distinta.

| Certificado | Región | Cubre |
|---|---|---|
| Prod | `us-east-1` | `ondexia.com`, `*.ondexia.com` |
| Dev | `us-east-1` | `dev.ondexia.com`, `*.dev.ondexia.com` |

Validación por DNS (no email) y renovación automática.

Nota sobre el wildcard: `*.ondexia.com` cubre `app.ondexia.com` pero **no** `app.dev.ondexia.com` — el comodín solo cubre un nivel. Por eso cada zona delegada lleva su propio certificado.

La latencia a Perú desde `us-east-1` es de ~60–80 ms, y CloudFront la absorbe casi por completo en el frontend. El único punto donde se nota es la API; si más adelante pesa, la respuesta es CloudFront delante de `api.ondexia.com` (no mudar la región).

## 8. Orden de ejecución

1. **Registrar `ondexia.com`** en Route 53 Domains (con privacidad WHOIS y auto-renovación activadas).
2. **Crear la hosted zone** de producción (Route 53 la crea sola al registrar).
3. **Solicitar ACM** en `us-east-1`: `ondexia.com` + `*.ondexia.com`, validación DNS.
4. **Delegar `dev.ondexia.com`**: crear la hosted zone de dev y copiar sus 4 NS como registro `NS` en la zona raíz.
5. **Publicar SPF y DMARC** aunque no haya buzón todavía (ver §6) — un dominio recién registrado sin estos registros es suplantable de inmediato.
6. **Registrar `.pe` / `.com.pe`** vía agente NIC.pe y apuntar sus NS a Route 53.
7. **Levantar la landing** en `ondexia.com` — es lo único desplegable sin depender del backend, y valida la cadena completa (dominio → ACM → CloudFront → S3).

### Registros DNS iniciales de la zona raíz

| Nombre | Tipo | Valor |
|---|---|---|
| `ondexia.com` | A (alias) | distribución CloudFront de la landing |
| `www` | CNAME | `ondexia.com` |
| `ondexia.com` | TXT | `v=spf1 include:amazonses.com -all` |
| `_dmarc` | TXT | `v=DMARC1; p=none; rua=mailto:dmarc@ondexia.com; fo=1` |
| `dev` | NS | 4 nameservers de la zona `dev.ondexia.com` |
| `_acm-challenge…` | CNAME | los que genere ACM (automático si la zona es de Route 53) |

`app`, `api`, `auth`, `cdn` y `ver` se agregan cuando exista el destino; no crear registros apuntando a nada.

## Pendientes de decisión

- **Retención de comprobantes en el portal público**: ¿el enlace `ver.ondexia.com/c/{token}` vive indefinidamente o caduca? SUNAT obliga al emisor a conservar el XML y el CDR, pero eso es distinto de mantener el enlace público abierto para siempre.
- **Estructura de cuentas AWS**: una sola cuenta, o dos (prod / dev) bajo Organizations. La delegación de `dev.ondexia.com` funciona en ambos casos, así que no bloquea nada; conviene resolverlo antes del primer despliegue de producción.

## Registro de cambios

- **v1 (2026-08-06)** — Esquema inicial. Decidido: marca `ondexia`, región `us-east-1`, entornos `dev` + producción, portal público de comprobantes en `ver.ondexia.com` con URL opaca.
