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

**Explorar el producto.** Las tres escenas permanecen visibles como capítulos.
El desplazamiento actualiza el indicador; enlaces y Anterior/Siguiente permiten
navegar directamente. El salto solicitado coloca el foco en la escena y el
desplazamiento por sí solo no lo mueve. Si el script no carga, los artículos y
sus enlaces ancla siguen disponibles. La evolución detallada se especifica abajo.

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
- Enlaces y Anterior/Siguiente recorren las escenas sin recargar la página.
- Navegación nativa por teclado, Escape y retorno del foco funcionan según el caso de uso.
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

## Evolución: recorrido por escenas

El propietario aclara que busca contenido interactivo «casi como diapositivas».
Se prepara una revisión local sobre la candidata 4350294, sin publicarla. Se
presentó una elección opcional entre avanzar al bajar y avanzar con botones;
sin respuesta después del plazo de consulta, se toma como propuesta el recorrido
al bajar, manteniendo controles manuales. Una respuesta posterior puede ajustar
esta decisión de presentación; no afecta reglas del negocio.

Plan presentado: tres escenas que cuentan preparación, registro y consulta;
capturas con movimiento suave, navegación manual alternativa y movimiento
reducido sin parallax. Se reutilizan los PNG acreditados, sin inventar interfaz.

### Flujo y extensiones

1. El visitante encuentra las tres escenas en el orden de la venta. Todas siguen
   en el documento: avanzar no oculta ni sustituye artículos completos.
2. Al bajar, el indicador señala la escena alcanzada; no mueve el foco ni anuncia
   cada cambio al lector de pantalla. No hay temporizador ni avance autónomo.
3. Los enlaces de escenas y Anterior/Siguiente permiten avanzar directamente.
   El avance solicitado coloca el foco en la escena y deja su título visible
   bajo la cabecera y la navegación. Los extremos no permiten rebasar 1 y 3.
4. Ampliar sigue abriendo la captura correspondiente desde arriba; Cerrar/Escape
   retornan el foco a su botón. Los originales mantienen enlaces móviles reales.
5. En una ventana amplia, la figura puede fijarse dentro de su artículo solo si
   cabe bajo los controles. En móvil, ventana baja, zoom que reduzca espacio o
   movimiento reducido, permanece en el flujo normal.
6. El parallax mueve solamente el marco de la captura entre −12 y +12 píxeles,
   ligado al desplazamiento nativo, sin bloquear rueda, teclas ni gestos. No
   modifica los PNG ni agrega una ilustración presentada como interfaz real.
7. Movimiento reducido elimina el parallax y la fijación de figuras. Si cambia mientras la
   página está abierta, la mejora se desactiva sin recargar ni perder contenido.
8. Sin JavaScript quedan las tres escenas y enlaces ancla operativos. Los botones
   que requieren código se habilitan únicamente después de inicializarlo.

La regla del doc 10 §6 sobre entradas/salidas de avisos y diálogos se conserva.
Este movimiento continuo se acota a la demostración pública por la solicitud de
una landing interactiva y se documenta en §16; no se generaliza a formularios,
comprobantes ni otras pantallas de trabajo.

### Oráculos del recorrido

- Tres artículos visibles tanto con JavaScript como sin él. Navegación por
  capítulos con `aria-current="step"`, sin roles de pestaña sobre varios paneles
  simultáneamente visibles.
- Primera, segunda y tercera escenas corresponden a los índices 1, 2 y 3 fijados
  en la especificación; enlace, avance manual y desplazamiento concuerdan.
- La transformación de la captura cambia al desplazar la escena y siempre queda
  dentro de ±12 px; una animación breve de entrada no satisface ese oráculo.
- Bajo movimiento reducido la transformación es cero y la figura no está fija.
- Cada navegación manual termina con título y foco visibles; no hay cambio
  automático de foco por desplazamiento.
- 320/390/768/1024/1440 px, altura reducida y zoom: sin desbordamiento horizontal
  de página ni controles o contenido inaccesibles por una fijación excesiva.
- Persisten ampliación, seguridad CSP, contenido identificado y originales.

El build anterior se conserva fuera del repo para contraste. La revisión nueva
debe demostrar esos comportamientos en navegador y registrar los pendientes.
