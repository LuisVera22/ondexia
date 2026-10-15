function iniciarExplorador(explorador: HTMLElement): void {
  const enlaces = [...explorador.querySelectorAll<HTMLAnchorElement>('[data-paso]')];
  const escenas = [...explorador.querySelectorAll<HTMLElement>('[data-escena]')];
  const imagenes = escenas.map((escena) => escena.querySelector<HTMLImageElement>('[data-captura]'));
  const navegacion = explorador.querySelector<HTMLElement>('[data-pasos]');
  const alternativa = explorador.querySelector<HTMLElement>('[data-escenas]');
  const recorrido = explorador.querySelector<HTMLElement>('[data-recorrido]');
  const escenario = explorador.querySelector<HTMLElement>('[data-escenario]');
  const marco = explorador.querySelector<HTMLElement>('[data-marco]');
  const capas = [...explorador.querySelectorAll<HTMLImageElement>('[data-capa]')];
  const contorno = explorador.querySelector<SVGPathElement>('[data-contorno]');
  const visual = explorador.querySelector<HTMLElement>('[data-visual]');
  const tituloActual = explorador.querySelector<HTMLElement>('[data-titulo-actual]');
  const descripcionActual = explorador.querySelector<HTMLElement>('[data-descripcion-actual]');
  const ampliarActual = explorador.querySelector<HTMLButtonElement>('[data-ampliar-actual]');
  const originalActual = explorador.querySelector<HTMLAnchorElement>('[data-original-actual]');
  const detener = explorador.querySelector<HTMLButtonElement>('[data-detener]');
  const progreso = explorador.querySelector<HTMLElement>('[data-progreso]');
  if (!navegacion || !alternativa || !recorrido || !escenario || !marco || !contorno || !visual
    || !tituloActual || !descripcionActual || !ampliarActual || !originalActual || !detener || !progreso
    || escenas.length !== 3 || capas.length !== 3 || enlaces.length !== 3 || imagenes.some((imagen) => !imagen)) return;
  const partes = { navegacion, alternativa, recorrido, escenario, marco, contorno, visual,
    tituloActual, descripcionActual, ampliarActual, originalActual, detener, progreso };
  const movimientoReducido = window.matchMedia('(prefers-reduced-motion: reduce)');
  const dialogo = explorador.querySelector<HTMLDialogElement>('[data-dialogo]');
  const cerrar = explorador.querySelector<HTMLButtonElement>('[data-cerrar]');
  const tamanoReal = explorador.querySelector<HTMLButtonElement>('[data-tamano-real]');
  const lienzoAmpliado = explorador.querySelector<HTMLElement>('[data-lienzo-ampliado]');
  const tituloAmpliado = explorador.querySelector<HTMLElement>('[data-titulo-ampliado]');
  const capturaAmpliada = explorador.querySelector<HTMLImageElement>('[data-captura-ampliada]');
  const originalAmpliado = explorador.querySelector<HTMLAnchorElement>('[data-original-ampliado]');
  let animaciones: Animation[] = [];
  let modoScroll = false;
  let detenido = false;
  let indiceActivo = -1;
  let indiceAmpliado = 0;
  let botonApertura: HTMLButtonElement | null = null;
  let tope = 0;
  let inicio = 0;
  let distancia = 1;
  let iniciosEscenas: number[] = [];
  let cuadroPendiente: number | null = null;

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
    if (indice === indiceActivo) return;
    indiceActivo = indice;
    enlaces.forEach((enlace, posicion) => {
      if (posicion === indice) enlace.setAttribute('aria-current', 'step');
      else enlace.removeAttribute('aria-current');
    });
    const imagen = imagenes[indice];
    partes.tituloActual.textContent = escenas[indice].querySelector('h3')?.textContent ?? '';
    partes.descripcionActual.textContent = escenas[indice].querySelector('[data-descripcion]')?.textContent ?? '';
    partes.visual.setAttribute('aria-label', imagen?.alt ?? 'Captura de Ondexia');
    partes.originalActual.href = imagen?.src ?? '';
  }

  function actualizarRecorrido(): void {
    cuadroPendiente = null;
    if (modoScroll) {
      const avance = Math.max(0, Math.min(1, (window.scrollY - inicio) / distancia));
      animaciones.forEach((animacion) => { animacion.currentTime = avance * 1000; });
      actualizarContorno(avance);
      indicarEscena(Math.round((avance + Number.EPSILON) * 2));
      partes.progreso.textContent = `${String(indiceActivo + 1).padStart(2, '0')} / 03`;
    } else {
      let indice = 0;
      iniciosEscenas.forEach((posicion, numero) => {
        if (window.scrollY + tope + 32 >= posicion) indice = numero;
      });
      indicarEscena(indice);
      partes.progreso.textContent = '3 escenas';
    }
  }

  function solicitarCuadro(): void {
    if (cuadroPendiente === null) cuadroPendiente = window.requestAnimationFrame(actualizarRecorrido);
  }

  function prepararAnimaciones(): void {
    const opacidades = [[1, 1, 0, 0, 0, 0], [0, 0, 1, 1, 0, 0], [0, 0, 0, 0, 1, 1]];
    const posiciones = [0, 0.2, 0.3, 0.7, 0.8, 1];
    capas.forEach((capa, indice) => animaciones.push(capa.animate(posiciones.map((offset, posicion) =>
      ({ offset, opacity: opacidades[indice][posicion] })), { duration: 1000, fill: 'both' })));
    animaciones.push(partes.marco.animate([
      { offset: 0, transform: 'translateY(12px) scale(0.96)' },
      { offset: 0.5, transform: 'translateY(0) scale(1)' },
      { offset: 1, transform: 'translateY(-8px) scale(0.98)' },
    ], { duration: 1000, fill: 'both' }));
    animaciones.forEach((animacion) => { animacion.pause(); animacion.currentTime = 0; });
  }

  function mostrarAlternativa(): void {
    const estabaAnimado = modoScroll;
    const focoAnterior = document.activeElement;
    const focoEnVentana = partes.recorrido.contains(focoAnterior) || focoAnterior === partes.detener;
    const indice = Math.max(0, indiceActivo);
    modoScroll = false;
    animaciones.forEach((animacion) => animacion.cancel());
    animaciones = [];
    explorador.classList.remove('modo-scroll');
    partes.recorrido.hidden = true;
    partes.alternativa.hidden = false;
    partes.detener.hidden = true;
    if (estabaAnimado) {
      window.scrollTo({ top: escenas[indice].getBoundingClientRect().top + window.scrollY - tope, behavior: 'instant' });
    }
    if (focoEnVentana) {
      const selector = focoAnterior === partes.originalActual ? '[data-original]'
        : focoAnterior === partes.ampliarActual ? '[data-ampliar]:not([hidden])' : null;
      const destino = (selector && escenas[indice].querySelector<HTMLElement>(selector)) || escenas[indice];
      destino.focus({ preventScroll: true });
      if (selector) destino.scrollIntoView({ block: 'nearest', inline: 'nearest', behavior: 'instant' });
    }
  }

  function actualizarMedidas(): void {
    const cabecera = document.querySelector('header')?.getBoundingClientRect().height ?? 0;
    explorador.style.setProperty('--alto-cabecera', `${cabecera}px`);
    const altoNavegacion = getComputedStyle(partes.navegacion).position === 'sticky'
      ? partes.navegacion.getBoundingClientRect().height : 0;
    tope = cabecera + altoNavegacion + 16;
    explorador.style.setProperty('--tope-recorrido', `${tope}px`);
    const disponible = document.documentElement.clientHeight - tope;
    const puedeAnimar = !detenido && !movimientoReducido.matches
      && document.documentElement.clientHeight >= 700 && typeof Element.prototype.animate === 'function'
      && typeof CSS !== 'undefined' && CSS.supports('height', '100svh') && CSS.supports('aspect-ratio', '1440 / 1000');
    // No ocultar un artículo mientras una persona está usando sus controles.
    const focoEnAlternativa = partes.alternativa.contains(document.activeElement);
    const alternativaVisible = !partes.alternativa.hidden;
    const estaLeyendo = alternativaVisible && partes.alternativa.getBoundingClientRect().top < cabecera + 32
      && partes.alternativa.getBoundingClientRect().bottom > cabecera;
    if (puedeAnimar && !modoScroll && !focoEnAlternativa) {
      partes.recorrido.hidden = false;
      explorador.classList.add('modo-scroll');
      if (partes.escenario.getBoundingClientRect().height <= disponible - 12) {
        try {
          prepararAnimaciones();
          modoScroll = true;
          partes.alternativa.hidden = true;
          partes.detener.hidden = false;
        } catch {
          mostrarAlternativa();
        }
      } else mostrarAlternativa();
    } else if (!puedeAnimar || (modoScroll && partes.escenario.getBoundingClientRect().height > disponible - 12)) {
      mostrarAlternativa();
    }
    if (modoScroll) {
      inicio = partes.recorrido.getBoundingClientRect().top + window.scrollY - tope;
      distancia = Math.max(1, partes.recorrido.getBoundingClientRect().height - partes.escenario.getBoundingClientRect().height);
      if (estaLeyendo) window.scrollTo({ top: inicio + distancia * Math.max(0, indiceActivo) / 2, behavior: 'instant' });
    } else {
      iniciosEscenas = escenas.map((escena) => escena.getBoundingClientRect().top + window.scrollY);
    }
    solicitarCuadro();
  }

  function irA(indice: number): void {
    const destino = modoScroll ? partes.tituloActual : escenas[indice];
    destino.focus({ preventScroll: true });
    const posicion = modoScroll ? inicio + distancia * indice / 2
      : escenas[indice].getBoundingClientRect().top + window.scrollY - tope;
    window.history.replaceState(null, '', `#${escenas[indice].id}`);
    window.scrollTo({ top: posicion, behavior: movimientoReducido.matches ? 'instant' : 'smooth' });
  }

  enlaces.forEach((enlace, indice) => enlace.addEventListener('click', (evento) => {
    if (evento.ctrlKey || evento.metaKey || evento.shiftKey || evento.altKey || evento.button !== 0) return;
    evento.preventDefault();
    irA(indice);
  }));
  partes.detener.addEventListener('click', () => {
    detenido = true;
    const indice = Math.max(0, indiceActivo);
    mostrarAlternativa();
    actualizarMedidas();
    escenas[indice].focus({ preventScroll: true });
    window.scrollTo({ top: iniciosEscenas[indice] - tope, behavior: 'instant' });
  });

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

  escenas.forEach((escena) => { escena.tabIndex = -1; });
  actualizarMedidas();
  const indiceInicial = escenas.findIndex((escena) => `#${escena.id}` === window.location.hash);
  if (indiceInicial >= 0 && modoScroll) {
    window.scrollTo({ top: inicio + distancia * indiceInicial / 2, behavior: 'instant' });
  }
  window.addEventListener('scroll', solicitarCuadro, { passive: true });
  window.addEventListener('resize', actualizarMedidas, { passive: true });
  movimientoReducido.addEventListener('change', actualizarMedidas);
  partes.alternativa.addEventListener('focusout', () => window.requestAnimationFrame(actualizarMedidas));
  imagenes.forEach((imagen) => imagen?.addEventListener('load', actualizarMedidas, { once: true }));
  document.fonts.ready.then(actualizarMedidas);
  if ('ResizeObserver' in window) {
    const observador = new ResizeObserver(actualizarMedidas);
    observador.observe(partes.navegacion);
    const cabecera = document.querySelector('header');
    if (cabecera) observador.observe(cabecera);
  }
}

document.querySelectorAll<HTMLElement>('[data-explorador]').forEach(iniciarExplorador);
