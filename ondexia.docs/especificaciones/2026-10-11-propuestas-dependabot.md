# Alinear la configuración de propuestas de Dependabot

ACT-23/F23. Base develop `04076afda5012eb6fb61d083014fe733dd17549d`.
Reglas aprobadas: actualizaciones se evalúan en develop; main se reserva para
publicar; Conventional Commits y español en los cambios que integramos.

Defecto de configuración: cinco entradas sin target-branch y con prefijo
Actualiza. Plan acotado: declarar develop y build(dependencias) en cada área,
manteniendo ecosistemas/directorios, frecuencia semanal/lunes y límite cinco.
Pruebas deben fallar antes por destino/prefijo y pasar después; mutar la
frecuencia existente para comprobar sensibilidad y restaurar bytes exactos.
La lectura estructural de la prueba no sustituye validación del servicio GitHub.

No se actualizan dependencias, no se retargetean ni cierran automáticamente los
18 PR #13–#30 ni se reescriben commits existentes. Cada paquete requiere release
notes, aplicabilidad y pruebas del head sobre develop. CI verde de un PR antiguo
sobre main no acredita compatibilidad con el develop actual.

El prefijo no traduce el texto nativo del bot. Una propuesta automática no es un
cambio aprobado para integrar: adoptar su diff en feature desde develop y
confirmarlo con descripción y cuerpo españoles, conservando referencia al PR
de origen. No incorporar commits ingleses del bot por merge directo ni asumir
que cambiar el título traduce el historial. No se implementa automatización de
traducción ni auto-merge.

La configuración queda en develop. No se afirma operación nueva del bot hasta
contrastar configuración activa y una propuesta real; publicación en main
requiere el ciclo de release del propietario, fuera de este bloque. No se
cambia main aquí. Tampoco se da por cambiado el destino de actualizaciones de
seguridad del servicio por configurar las propuestas de versión.

Sin API, importes, migraciones o interfaz. Publicar PR separado, contrastar CI
del mismo head y commit integrado y cerrar solo su rama al terminar.
