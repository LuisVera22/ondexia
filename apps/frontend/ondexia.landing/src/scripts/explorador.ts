function iniciarExplorador(explorador: HTMLElement): void {
  const escenas = [...explorador.querySelectorAll<HTMLElement>('[data-escena]')];
  const imagenes = escenas.map(escena => escena.querySelector<HTMLImageElement>('[data-captura]'));
  const enlaces = [...explorador.querySelectorAll<HTMLButtonElement>('[data-paso]')];
  const verbos = [...explorador.querySelectorAll<HTMLAnchorElement>('[data-verbo]')];
  const capas = [...explorador.querySelectorAll<HTMLImageElement>('[data-capa]')];
  const barras = [...explorador.querySelectorAll<HTMLElement>('[data-barra]')];
  const detalles = [...explorador.querySelectorAll<HTMLElement>('[data-detalle]')];
  const adornosGrupos = [...explorador.querySelectorAll<HTMLElement>('[data-adorno-grupo]')];
  const alternativa = explorador.querySelector<HTMLElement>('[data-escenas]');
  const recorrido = explorador.querySelector<HTMLElement>('[data-recorrido]');
  const escenario = explorador.querySelector<HTMLElement>('[data-escenario]');
  const composicion = explorador.querySelector<HTMLElement>('[data-composicion]');
  const timeline = explorador.querySelector<HTMLElement>('[data-timeline]');
  const adornos = explorador.querySelector<HTMLElement>('[data-adornos]');
  const visual = explorador.querySelector<HTMLElement>('[data-visual]');
  const contorno = explorador.querySelector<SVGPathElement>('[data-contorno]');
  const ampliarActual = explorador.querySelector<HTMLButtonElement>('[data-ampliar-actual]');
  const originalActual = explorador.querySelector<HTMLAnchorElement>('[data-original-actual]');
  const detener = explorador.querySelector<HTMLButtonElement>('[data-detener]');
  if (!alternativa || !recorrido || !escenario || !composicion || !timeline || !adornos || !visual
    || !contorno || !ampliarActual || !originalActual || !detener || escenas.length !== 3
    || imagenes.some(imagen => !imagen) || enlaces.length !== 3 || verbos.length !== 3 || capas.length !== 3) return;
  const partes = { alternativa, recorrido, escenario, composicion, timeline, adornos, visual, contorno, ampliarActual, originalActual, detener };
  const movimientoReducido = window.matchMedia('(prefers-reduced-motion: reduce)');
  const dialogo = explorador.querySelector<HTMLDialogElement>('[data-dialogo]');
  const cerrar = explorador.querySelector<HTMLButtonElement>('[data-cerrar]');
  const tamanoReal = explorador.querySelector<HTMLButtonElement>('[data-tamano-real]');
  const lienzoAmpliado = explorador.querySelector<HTMLElement>('[data-lienzo-ampliado]');
  const tituloAmpliado = explorador.querySelector<HTMLElement>('[data-titulo-ampliado]');
  const capturaAmpliada = explorador.querySelector<HTMLImageElement>('[data-captura-ampliada]');
  const originalAmpliado = explorador.querySelector<HTMLAnchorElement>('[data-original-ampliado]');
  let indiceActivo = 0;
  let indicePortada = 0;
  let indiceAmpliado = 0;
  let botonApertura: HTMLButtonElement | null = null;
  let modoScroll = false;
  let detenido = false;
  let inicio = 0;
  let distancia = 1;
  let ancho = 0;
  let anchoCaptura = 0;
  let pendiente = false;
  const limitar = (numero: number) => Math.max(0, Math.min(1, numero));
  // Las formas comparten M/Q/Q/Q/Q/Z; se transforma el fondo, nunca la interfaz.
  const formas = [
    [80, 40, 720, 0, 1360, 40, 1440, 500, 1360, 960, 720, 1000, 80, 960, 0, 500, 80, 40],
    [28, 88, 720, 28, 1412, 88, 1380, 500, 1412, 912, 720, 972, 28, 912, 60, 500, 28, 88],
    [56, 24, 720, 24, 1384, 24, 1384, 500, 1384, 976, 720, 976, 56, 976, 56, 500, 56, 24],
  ];

  function actualizarContorno(avance: number): void {
    const tramo = avance < 0.5 ? 0 : 1;
    const proporcion = Math.min(1, (avance - tramo * 0.5) * 2);
    const puntos = formas[tramo].map((punto, indice) =>
      (punto + (formas[tramo + 1][indice] - punto) * proporcion).toFixed(2));
    partes.contorno.setAttribute('d', `M${puntos.slice(0, 2).join(' ')} Q${puntos.slice(2, 6).join(' ')} Q${puntos.slice(6, 10).join(' ')} Q${puntos.slice(10, 14).join(' ')} Q${puntos.slice(14, 18).join(' ')} Z`);
  }


  function indicarEscena(indice: number): void {
    indiceActivo = indice;
    capas.forEach((capa, posicion) => { capa.style.opacity = posicion === indice ? '1' : '0'; });
    verbos.forEach((verbo, posicion) => {
      if (posicion === indice) verbo.setAttribute('aria-current', 'true');
      else verbo.removeAttribute('aria-current');
    });
    enlaces.forEach((enlace, posicion) => {
      enlace.setAttribute('aria-expanded', String(posicion === indice));
      detalles[posicion].hidden = posicion !== indice;
      adornosGrupos[posicion].hidden = posicion !== indice;
    });
    partes.visual.setAttribute('aria-label', imagenes[indice]?.alt ?? 'Captura de Ondexia');
    partes.originalActual.href = imagenes[indice]?.src ?? '';
  }

  function actualizarRecorrido(): void {
    pendiente = false;
    if (!modoScroll) return;
    const avance = limitar((scrollY - inicio) / distancia);
    // Un control enfocado conserva una composición legible incluso al volver atrás.
    const conservaFoco = partes.timeline.contains(document.activeElement);
    const salida = conservaFoco ? 1 : limitar(avance / .16);
    const traslado = conservaFoco ? 1 : limitar((avance - .16) / .14);
    const capitulos = limitar((avance - .3) / .7) * 3;
    const indice = avance < .3 ? indicePortada : Math.min(2, Math.floor(capitulos));
    indicarEscena(indice);
    partes.adornos.style.opacity = String(1 - salida);
    partes.adornos.style.transform = `translateY(${-160 * salida}px) scale(${1 + .15 * salida})`;
    partes.composicion.style.transform = `translate(calc(-50% + ${ancho * .17 * traslado}px), -50%)`;
    partes.composicion.style.width = `${anchoCaptura * (1 - .12 * traslado)}px`;
    // No ocultar un encabezado que conserve el foco al retroceder con rueda.
    partes.timeline.hidden = traslado === 0 && !conservaFoco;
    partes.timeline.style.opacity = String(conservaFoco ? 1 : traslado);
    partes.timeline.style.transform = `translate(${(1 - traslado) * 70}px, -50%)`;
    barras.forEach((barra, indice) => { barra.style.transform = `scaleX(${limitar(capitulos - indice)})`; });
    actualizarContorno(avance);
  }

  function solicitarCuadro(): void {
    if (!pendiente) { pendiente = true; requestAnimationFrame(actualizarRecorrido); }
  }

  function actualizarMedidas(): void {
    const cabecera = document.querySelector('header')?.getBoundingClientRect().height ?? 90;
    const estabaAnimado = modoScroll;
    const focoAnterior = document.activeElement;
    const focoEnRecorrido = partes.recorrido.contains(focoAnterior);
    const leyendoAlternativa = !partes.alternativa.hidden && partes.alternativa.getBoundingClientRect().top < cabecera;
    const puedeAnimar = !detenido && !movimientoReducido.matches && innerWidth >= 1024 && innerHeight >= 700
      && typeof CSS !== 'undefined' && CSS.supports('height', '100svh') && CSS.supports('aspect-ratio', '1440 / 1000');
    if (!puedeAnimar) {
      modoScroll = false;
      partes.recorrido.hidden = true;
      partes.alternativa.hidden = false;
      explorador.classList.remove('modo-scroll');
      verbos.forEach(verbo => verbo.removeAttribute('aria-current'));
      if (estabaAnimado) {
        const destino = focoEnRecorrido ? escenas[indiceActivo] : null;
        destino?.focus({ preventScroll: true });
        // La portada sigue en su sitio si la persona aún no entró al recorrido.
        if (scrollY >= inicio) escenas[indiceActivo].scrollIntoView({ block: 'start', behavior: 'instant' });
      }
      return;
    }
    if (!modoScroll && (partes.alternativa.contains(focoAnterior) || leyendoAlternativa)) return;
    modoScroll = true;
    partes.recorrido.hidden = false;
    partes.alternativa.hidden = true;
    explorador.classList.add('modo-scroll');
    ancho = partes.escenario.clientWidth;
    anchoCaptura = Math.min(ancho * .76, 900, (innerHeight - 240) * 1.44);
    inicio = partes.recorrido.getBoundingClientRect().top + scrollY - 100;
    distancia = Math.max(1, partes.recorrido.offsetHeight - partes.escenario.offsetHeight);
    actualizarRecorrido();
  }

  enlaces.forEach((enlace, indice) => enlace.addEventListener('click', () => {
    // La navegación solicitada es instantánea: no atraviesa otros capítulos ni pierde foco.
    window.scrollTo({ top: inicio + distancia * (.3 + .7 * (indice + .05) / 3), behavior: 'instant' });
    actualizarRecorrido();
  }));
  verbos.forEach((verbo, indice) => {
    const elegir = () => {
      if (!modoScroll) return;
      indicePortada = indice;
      indicarEscena(indice);
    };
    verbo.addEventListener('pointerenter', elegir);
    verbo.addEventListener('focus', elegir);
    verbo.addEventListener('click', evento => {
      if (!modoScroll || evento.ctrlKey || evento.metaKey || evento.shiftKey || evento.altKey || evento.button !== 0) return;
      evento.preventDefault();
      elegir();
    });
  });
  partes.detener.addEventListener('click', () => { detenido = true; actualizarMedidas(); });
  if (dialogo && typeof dialogo.showModal === 'function' && cerrar && tamanoReal && lienzoAmpliado
    && tituloAmpliado && capturaAmpliada && originalAmpliado) {
    const ampliacion = { dialogo, tamanoReal, lienzoAmpliado, tituloAmpliado, capturaAmpliada, originalAmpliado };
    function ajustarCaptura(): void {
      ampliacion.lienzoAmpliado.classList.remove('tamano-real');
      ampliacion.tamanoReal.setAttribute('aria-pressed', 'false');
      ampliacion.tamanoReal.textContent = 'Ver tamaño real';
      ampliacion.lienzoAmpliado.scrollTo({ top: 0, left: 0, behavior: 'instant' });
    }
    function abrirCaptura(indice: number, boton: HTMLButtonElement): void {
      const imagen = imagenes[indice];
      if (!imagen) return;
      indiceAmpliado = indice;
      botonApertura = boton;
      ampliacion.tituloAmpliado.textContent = escenas[indice].querySelector('h3')?.textContent ?? '';
      ampliacion.capturaAmpliada.src = imagen.src;
      ampliacion.capturaAmpliada.alt = imagen.alt;
      ampliacion.originalAmpliado.href = imagen.src;
      ampliacion.dialogo.showModal();
      ajustarCaptura();
      ampliacion.dialogo.scrollTop = 0;
    }
    escenas.forEach((escena, indice) => {
      const boton = escena.querySelector<HTMLButtonElement>('[data-ampliar]');
      if (!boton) return;
      boton.addEventListener('click', () => abrirCaptura(indice, boton));
      boton.hidden = false;
    });
    partes.ampliarActual.addEventListener('click', () => abrirCaptura(Math.max(0, indiceActivo), partes.ampliarActual));
    tamanoReal.addEventListener('click', () => {
      const real = ampliacion.lienzoAmpliado.classList.toggle('tamano-real');
      ampliacion.tamanoReal.setAttribute('aria-pressed', String(real));
      ampliacion.tamanoReal.textContent = real ? 'Ajustar a ventana' : 'Ver tamaño real';
      ampliacion.lienzoAmpliado.scrollTo({ top: 0, left: 0, behavior: 'instant' });
    });
    cerrar.addEventListener('click', () => ampliacion.dialogo.close());
    dialogo.addEventListener('close', () => {
      const destino = botonApertura && !botonApertura.closest('[hidden]') ? botonApertura
        : escenas[indiceAmpliado].querySelector<HTMLButtonElement>('[data-ampliar]');
      destino?.focus({ preventScroll: true });
      botonApertura = null;
    });
  }


  escenas.forEach(escena => { escena.tabIndex = -1; });
  actualizarMedidas();
  const indiceInicial = escenas.findIndex(escena => `#${escena.id}` === location.hash);
  if (indiceInicial >= 0 && modoScroll) {
    window.scrollTo({top: inicio + distancia * (.3 + .7 * (indiceInicial + .05) / 3), behavior: 'instant'});
    actualizarRecorrido();
  }
  partes.timeline.addEventListener('focusout', solicitarCuadro);
  window.addEventListener('scroll', solicitarCuadro, { passive: true });
  window.addEventListener('resize', actualizarMedidas, { passive: true });
  movimientoReducido.addEventListener('change', actualizarMedidas);
  document.fonts.ready.then(actualizarMedidas);
}

document.querySelectorAll<HTMLElement>('[data-explorador]').forEach(iniciarExplorador);
