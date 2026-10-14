# Landing interactiva · producto y escritura

## Necesidad

Tras desplegar y revisar la versión candidata anterior, el propietario solicita
una presentación más interactiva inspirada en la página de ChatGPT, con capturas
reales del sistema y una escritura más adecuada. No se alteran precios, reglas
de negocio, autenticación ni emisión fiscal.

Base: develop `69290ea19f6a27279460c57d271c73e3c5d6a5b7`.
Rama: `feature/landing-interactiva`. La autorización se refiere al diseño y las
interacciones; no supone que SUNAT, correo o condiciones comerciales pendientes
se hayan verificado ni autoriza desplegar AWS en esta ejecución.

## Referencia y límites de observación

Se consultó la [página oficial de ChatGPT](https://chatgpt.com/es-ES/overview/?openaicom_referred=true).
Su estructura vincula acciones, imágenes de tareas dentro del producto,
explicaciones de utilidad y siguientes pasos. El contenido y los enlaces se
contrastaron en la página; el lector web no permite observar las animaciones
exactas. Selectores, transiciones y ampliación son propuestas para Ondexia,
no efectos copiados ni comportamientos externos dados por comprobados.

## Plan presentado

1. Mostrar el producto Angular real con capturas nuevas y datos de demostración.
2. Sustituir la portada en dos columnas por un titular breve y una demostración
   central. Relacionar cada pantalla con su tarea y utilidad.
3. Incorporar selección manual de pantallas, ampliación y transiciones de entrada.
4. Mantener teclado, foco, móvil, movimiento reducido y contenido sin JavaScript.
5. Verificar build, restricciones de seguridad y navegación con evidencia.
6. Entregar vista local y capturas para evaluación; no desplegar en AWS.

## Convenciones de escritura

Se complementan doc 10 §2 y las notas SEO existentes:

- Abrir con una tarea reconocible, no con una lista de módulos o un eslogan vacío.
- Escribir una idea por titular y una explicación breve por pantalla.
- Usar verbos concretos: preparar, cobrar, revisar, consultar, previsualizar.
- Explicar lo que permite hacer una función y mostrarlo en el producto.
- Evitar encadenar infinitivos en párrafos que solo repiten el titular.
- Mantener acciones en infinitivo, español formal y términos fiscales precisos.
- No asumir demostración asistida, atención 24/7, ahorro, certificaciones,
  demanda de clientes o rentabilidad sin evidencia.
- Distinguir capturas reales de interfaz con datos sintéticos de una captura de
  producción o de una maqueta. Mostrar la identificación cerca de la galería.

Las escenas de venta, registro y documento representan el mismo recorrido. La
emisión real ante SUNAT continúa en ensayo según la documentación vigente.

## Interacciones y casos de uso

**Explorar el producto.** Al seleccionar una escena, cambiar captura, explicación
y estado seleccionado. Flechas, Inicio y Fin recorren las pestañas; solo la
seleccionada queda en el recorrido Tab. No hay rotación automática ni pérdida de
foco. Si el script no carga, las tres escenas y sus enlaces siguen disponibles.

**Ampliar la pantalla.** Abrir un diálogo de la escena actual con nombre accesible.
Escape y Cerrar lo cierran; el foco vuelve al control que abrió el diálogo. El
enlace al original permanece como alternativa. El contenido no se fabrica ni
se modifica por encima de la captura. Cada apertura comienza desde arriba,
aunque se haya desplazado otra escena antes.

**Recorrer la página.** Las entradas y cambios usan los tokens de movimiento
existentes. Con movimiento reducido no hay animación de desplazamiento. El
contenido no se oculta estáticamente a la espera de JavaScript. En móvil, cargar
una captura tomada a ancho móvil; evitar encoger una pantalla de escritorio hasta
hacer ilegible el texto.

## Seguridad y arquitectura

La landing sigue siendo estática y aislada de API, Cognito y datos. El propietario
solicita interacciones: se sustituye la decisión anterior de cero JavaScript por
mejora progresiva local mínima, sin frameworks, analítica, proveedores o red remota.

La CSP vigente deniega scripts por `default-src 'none'`. Permitir únicamente
`script-src 'self'` para archivos generados por Astro. No permitir scripts inline,
eval, conexiones ni iframes; conservar el resto de restricciones. Verificar el
comportamiento con cabeceras equivalentes a la política Terraform, no solo leyendo
la cadena. Aplicar esa política en AWS continúa pendiente del despliegue revisado.

## Aceptación y oráculos

- Tres escenas reales, identificadas, con rutas de asset existentes y procedencia.
- Cambiar pestaña actualiza la escena sin recargar la página.
- Flechas/Inicio/Fin, Escape y retorno del foco funcionan según el caso de uso.
- Sin JavaScript, las escenas siguen visibles y los enlaces originales funcionan.
- Script local operativo bajo CSP; script inline y conexión externa rechazados.
- 320/390/768/1024/1440 px sin desbordamiento horizontal de página; ampliación móvil
  puede desplazarse dentro del diálogo y conserva control de cierre accesible.
- Texto normal con contraste 4,5:1; pesos, radio y marca del doc 10 conservados.
- Capturas provenientes de componentes Angular, sin HTML del producto inventado.

Los esperados se definen aquí antes de observar el resultado. La evidencia debe
separar bloqueos por CSP, defectos funcionales y problemas de preparación. CI,
AWS, dispositivos físicos, conversión y WCAG completa solo se declaran verificados
si realmente se ejecutan sus comprobaciones.
