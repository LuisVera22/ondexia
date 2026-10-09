# Corrección de auditorías de dependencias en CI

El PR #10 se detenía antes de compilar: ejecución [303](https://github.com/LuisVera22/ondexia/actions/runs/37866690107), paso Frontend, salida 1 de `pnpm audit --prod --audit-level=high`.

## Causa y arreglo

- `@angular/router` vulnerable antes de 21.2.24: GHSA-ff3f-86qr-9cv3. Web y panel actualizados a Angular 21.2.24, con paquetes de ejecución y compilador compatibles.
- `source-map-js` vulnerable antes de 1.2.2: GHSA-68fv-2mgg-jv7q. Actualizadas sus copias transitivas en web, panel y landing; sin añadir una dependencia directa ni una excepción a la auditoría.
- La auditoría anticipada de landing también fallaba: nueve alertas altas en total, entre ellas undici, devalue, http-cache-semantics y sharp. Actualizadas dentro de rangos existentes a undici 8.11.2, devalue 5.9.4, http-cache-semantics 4.3.0 y sharp 0.35.5. Astro conserva 7.2.10.

## Evidencia antes y después

Auditorías previas con salida 1: web dos altas y tres moderadas; panel dos altas; landing nueve altas, ocho moderadas y cuatro bajas. Tras corregir: web/panel ninguna alerta; landing ninguna alta/crítica y una moderada. El criterio independiente es el umbral high del CI; no se deriva del resultado observado.

Instalación con `--frozen-lockfile`, compilación de los tres módulos, 119 pruebas web y cinco del panel correctas. Tono/forma y diff comprobados. No cambia código de negocio, API, migraciones ni configuración de seguridad del CI.

Registros y JSON de auditoría junto a este documento; huellas SHA-256 en el JSON adjunto. Primera actualización local detenida por selección incorrecta del almacén pnpm: se usó el almacén existente, sin alterar controles. La landing requirió una segunda actualización para corregir otra copia de source-map-js; auditoría y compilación finales repetidas.

## Límites

La evidencia local no certifica todo el CI remoto ni constituye auditoría independiente. Backend, infraestructura y despliegue AWS no se ejecutaron de nuevo localmente para este cambio de frontend. La landing conserva una alerta moderada. Estado remoto pendiente de ejecución tras subir este commit.
