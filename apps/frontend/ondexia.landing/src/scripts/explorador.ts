function iniciarExplorador(explorador: HTMLElement): void {
  if (typeof CSS === 'undefined' || !CSS.supports('aspect-ratio', '1440 / 1000')) return;
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
  const reproduccion = explorador.querySelector<HTMLButtonElement>('[data-reproduccion]');
  const selectores = [...explorador.querySelectorAll<HTMLButtonElement>('[data-selector]')];
  const tituloActual = explorador.querySelector<HTMLElement>('[data-titulo-actual]');
  const descripcionActual = explorador.querySelector<HTMLElement>('[data-descripcion-actual]');
  const controles = explorador.querySelector<HTMLElement>('[data-controles]');
  if (!alternativa || !recorrido || !escenario || !composicion || !timeline || !adornos || !visual
    || !contorno || !ampliarActual || !reproduccion || !tituloActual || !descripcionActual || !controles || selectores.length !== 3 || escenas.length !== 3
    || imagenes.some(imagen => !imagen) || enlaces.length !== 3 || verbos.length !== 3 || capas.length !== 3) return;
  const partes = { alternativa, recorrido, escenario, composicion, timeline, adornos, visual, contorno, ampliarActual, reproduccion, tituloActual, descripcionActual, controles };
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
  let pausaSolicitada = false;
  let temporizador: ReturnType<typeof setTimeout> | undefined;
  let punteroSobreControl = false;
  let mejoraActiva = false;
  let bloqueoAnterior = '';
  const titulo = (indice: number) => escenas[indice].querySelector('h3')?.textContent ?? '';

  function programarCambio(): void {
    const caja = explorador.getBoundingClientRect();
    const cajaRecorrido = partes.recorrido.getBoundingClientRect();
    const enPortada = modoScroll ? scrollY < inicio + distancia * .16 : cajaRecorrido.bottom > 100;
    const disponible = mejoraActiva && !pausaSolicitada && !movimientoReducido.matches && !document.hidden
      && !dialogo?.open && !punteroSobreControl && !explorador.contains(document.activeElement)
      && caja.top < innerHeight && caja.bottom > 100 && enPortada;
    partes.reproduccion.setAttribute('aria-pressed', String(pausaSolicitada));
    partes.reproduccion.setAttribute('aria-label', pausaSolicitada ? 'Reanudar cambio automático' : 'Pausar cambio automático');
    if (!disponible) {
      clearTimeout(temporizador);
      temporizador = undefined;
      explorador.classList.remove('reproduciendo');
    } else if (temporizador === undefined) {
      explorador.classList.add('reproduciendo');
      temporizador = setTimeout(() => {
        temporizador = undefined;
        indicePortada = (indicePortada + 1) % escenas.length;
        indicarEscena(indicePortada);
        programarCambio();
      }, 6000);
    }
  }

  function elegirEscena(indice: number): void {
    clearTimeout(temporizador);
    temporizador = undefined;
    indicePortada = indice;
    indicarEscena(indice);
    programarCambio();
  }
  let inicio = 0;
  let distancia = 1;
  let ancho = 0;
  let anchoCaptura = 0;
  let pendiente = false;
  let cuadroContorno = 0;
  let progresoContorno = 0;
  const limitar = (numero: number) => Math.max(0, Math.min(1, numero));
  // Las formas comparten M/Q/Q/Q/Q/Z; se transforma el fondo, nunca la interfaz.
  const formas = [
    [80, 40, 720, 0, 1360, 40, 1440, 500, 1360, 960, 720, 1000, 80, 960, 0, 500, 80, 40],
    [28, 88, 720, 28, 1412, 88, 1380, 500, 1412, 912, 720, 972, 28, 912, 60, 500, 28, 88],
    [56, 24, 720, 24, 1384, 24, 1384, 500, 1384, 976, 720, 976, 56, 976, 56, 500, 56, 24],
  ];

  function actualizarContorno(avance: number): void {
    progresoContorno = avance;
    const tramo = avance < 0.5 ? 0 : 1;
    const proporcion = Math.min(1, (avance - tramo * 0.5) * 2);
    const puntos = formas[tramo].map((punto, indice) =>
      (punto + (formas[tramo + 1][indice] - punto) * proporcion).toFixed(2));
    partes.contorno.setAttribute('d', `M${puntos.slice(0, 2).join(' ')} Q${puntos.slice(2, 6).join(' ')} Q${puntos.slice(6, 10).join(' ')} Q${puntos.slice(10, 14).join(' ')} Q${puntos.slice(14, 18).join(' ')} Z`);
  }

  function transformarContorno(indice: number): void {
    cancelAnimationFrame(cuadroContorno);
    const origen = progresoContorno;
    const destino = indice / 2;
    const inicioCambio = performance.now();
    const pintar = (ahora: number) => {
      const fraccion = movimientoReducido.matches ? 1 : limitar((ahora - inicioCambio) / 800);
      actualizarContorno(origen + (destino - origen) * (1 - (1 - fraccion) ** 3));
      if (fraccion < 1) cuadroContorno = requestAnimationFrame(pintar);
    };
    cuadroContorno = requestAnimationFrame(pintar);
  }

  function indicarEscena(indice: number): void {
    if (indice !== indiceActivo && (!modoScroll || scrollY < inicio)) transformarContorno(indice);
    indiceActivo = indice;
    capas.forEach((capa, posicion) => { capa.style.opacity = posicion === indice ? '1' : '0'; });
    verbos.forEach((verbo, posicion) => {
      if (posicion === indice) verbo.setAttribute('aria-current', 'true');
      else verbo.removeAttribute('aria-current');
    });
    enlaces.forEach((enlace, posicion) => {
      enlace.setAttribute('aria-expanded', String(posicion === indice));
      detalles[posicion].hidden = posicion !== indice;
      adornosGrupos[posicion].dataset.activo = String(posicion === indice);
      selectores[posicion].setAttribute('aria-pressed', String(posicion === indice));
    });
    partes.visual.setAttribute('aria-label', `Ampliar: ${titulo(indice)}`);
    partes.tituloActual.textContent = titulo(indice);
    partes.descripcionActual.textContent = escenas[indice].querySelector('[data-descripcion]')?.textContent ?? '';
  }

  function actualizarRecorrido(): void {
    pendiente = false;
    if (!modoScroll) { programarCambio(); return; }
    const avance = limitar((scrollY - inicio) / distancia);
    // Un control enfocado conserva una composición legible incluso al volver atrás.
    const conservaFoco = partes.timeline.contains(document.activeElement);
    const salida = conservaFoco ? 1 : limitar(avance / .16);
    const traslado = conservaFoco ? 1 : limitar((avance - .16) / .14);
    const capitulos = limitar((avance - .3) / .7) * 3;
    const usaAcordeon = avance >= .3 && !partes.controles.contains(document.activeElement);
    explorador.classList.toggle('en-capitulos', usaAcordeon);
    partes.controles.inert = usaAcordeon;
    const indice = avance < .3 ? indicePortada : Math.min(2, Math.floor(capitulos));
    if (indice !== indiceActivo) indicarEscena(indice);
    partes.adornos.style.opacity = String(1 - salida);
    partes.adornos.style.transform = `translateY(${-160 * salida}px) scale(${1 + .15 * salida})`;
    partes.composicion.style.transform = `translate(calc(-50% + ${ancho * .17 * traslado}px), -50%)`;
    partes.composicion.style.width = `${anchoCaptura * (1 - .12 * traslado)}px`;
    // No ocultar un encabezado que conserve el foco al retroceder con rueda.
    partes.timeline.hidden = traslado === 0 && !conservaFoco;
    partes.timeline.style.opacity = String(conservaFoco ? 1 : traslado);
    partes.timeline.style.transform = `translate(${(1 - traslado) * 70}px, -50%)`;
    barras.forEach((barra, indice) => { barra.style.transform = `scaleX(${limitar(capitulos - indice)})`; });
    if (scrollY >= inicio) {
      cancelAnimationFrame(cuadroContorno);
      actualizarContorno(avance);
    }
    programarCambio();
  }

  function solicitarCuadro(): void {
    if (!pendiente) { pendiente = true; requestAnimationFrame(actualizarRecorrido); }
  }

  function actualizarMedidas(): void {
    const estabaAnimado = modoScroll;
    const focoAnterior = document.activeElement;
    const enRecorrido = partes.recorrido.contains(focoAnterior);
    const leyendo = mejoraActiva && partes.recorrido.getBoundingClientRect().top < 100;
    const puedeAnimar = !movimientoReducido.matches && innerWidth >= 1024 && innerHeight >= 700
      && typeof CSS !== 'undefined' && CSS.supports('height', '100svh') && CSS.supports('aspect-ratio', '1440 / 1000');
    // Al ampliar la ventana durante la lectura no introducir seis pantallas de scroll.
    modoScroll = puedeAnimar && (estabaAnimado || !leyendo);
    mejoraActiva = true;
    partes.recorrido.hidden = false;
    partes.alternativa.hidden = true;
    explorador.classList.toggle('modo-scroll', modoScroll);
    explorador.classList.toggle('modo-compacto', !modoScroll);
    if (!modoScroll) {
      explorador.classList.remove('en-capitulos');
      partes.controles.inert = false;
      partes.timeline.hidden = true;
      partes.composicion.style.removeProperty('transform');
      partes.composicion.style.removeProperty('width');
      partes.adornos.style.removeProperty('opacity');
      partes.adornos.style.removeProperty('transform');
      indicePortada = indiceActivo;
      indicarEscena(indiceActivo);
      if (estabaAnimado && leyendo) {
        if (partes.timeline.contains(focoAnterior)) selectores[indiceActivo].focus({ preventScroll: true });
        if (enRecorrido || scrollY >= inicio) partes.recorrido.scrollIntoView({ block: 'start', behavior: 'instant' });
      }
      programarCambio();
      return;
    }
    ancho = partes.escenario.clientWidth;
    anchoCaptura = Math.min(ancho * .76, 900, (innerHeight - 240) * 1.44);
    inicio = partes.recorrido.getBoundingClientRect().top + scrollY - 100;
    distancia = Math.max(1, partes.recorrido.offsetHeight - partes.escenario.offsetHeight);
    indicarEscena(indiceActivo);
    actualizarRecorrido();
  }

  enlaces.forEach((enlace, indice) => enlace.addEventListener('click', () => {
    // La navegación solicitada es instantánea: no atraviesa otros capítulos ni pierde foco.
    window.scrollTo({ top: inicio + distancia * (.3 + .7 * (indice + .05) / 3), behavior: 'instant' });
    actualizarRecorrido();
  }));
  selectores.forEach((selector, indice) => selector.addEventListener('click', () => {
    elegirEscena(indice);
    if (modoScroll && scrollY >= inicio + distancia * .3) {
      window.scrollTo({ top: inicio + distancia * (.3 + .7 * (indice + .05) / 3), behavior: 'instant' });
      actualizarRecorrido();
    }
  }));
  verbos.forEach((verbo, indice) => {
    const elegir = () => { if (mejoraActiva) elegirEscena(indice); };
    verbo.addEventListener('pointerenter', evento => { if (evento.pointerType === 'mouse') elegir(); });
    verbo.addEventListener('focus', elegir);
    verbo.addEventListener('click', evento => {
      if (!mejoraActiva || evento.ctrlKey || evento.metaKey || evento.shiftKey || evento.altKey || evento.button !== 0) return;
      evento.preventDefault();
      elegir();
    });
  });
  [...verbos, ...selectores, partes.ampliarActual, partes.reproduccion].forEach(control => {
    control.addEventListener('pointerenter', evento => {
      if (evento instanceof PointerEvent && evento.pointerType === 'mouse') { punteroSobreControl = true; programarCambio(); }
    });
    control.addEventListener('pointerleave', () => { punteroSobreControl = false; programarCambio(); });
  });
  partes.reproduccion.addEventListener('click', () => { pausaSolicitada = !pausaSolicitada; programarCambio(); });
  explorador.addEventListener('focusin', programarCambio);
  explorador.addEventListener('focusout', () => queueMicrotask(actualizarRecorrido));
  document.addEventListener('visibilitychange', programarCambio);
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
      ampliacion.tituloAmpliado.textContent = titulo(indice);
      ampliacion.capturaAmpliada.src = imagen.src;
      ampliacion.capturaAmpliada.alt = imagen.alt;
      ampliacion.originalAmpliado.href = imagen.src;
      if (!ampliacion.dialogo.open) {
        bloqueoAnterior = document.documentElement.style.overflow;
        document.documentElement.style.overflow = 'hidden';
        ampliacion.dialogo.showModal();
      }
      const contador = explorador.querySelector('[data-contador]');
      if (contador) contador.textContent = `0${indice + 1} / 03`;
      programarCambio();
      ajustarCaptura();
      ampliacion.dialogo.scrollTop = 0;
    }
    explorador.querySelector('[data-captura-anterior]')?.addEventListener('click', () => abrirCaptura((indiceAmpliado + 2) % 3, partes.ampliarActual));
    explorador.querySelector('[data-captura-siguiente]')?.addEventListener('click', () => abrirCaptura((indiceAmpliado + 1) % 3, partes.ampliarActual));
    partes.ampliarActual.addEventListener('click', () => abrirCaptura(Math.max(0, indiceActivo), partes.ampliarActual));
    tamanoReal.addEventListener('click', () => {
      const real = ampliacion.lienzoAmpliado.classList.toggle('tamano-real');
      ampliacion.tamanoReal.setAttribute('aria-pressed', String(real));
      ampliacion.tamanoReal.textContent = real ? 'Ajustar a ventana' : 'Ver tamaño real';
      ampliacion.lienzoAmpliado.scrollTo({ top: 0, left: 0, behavior: 'instant' });
    });
    cerrar.addEventListener('click', () => ampliacion.dialogo.close());
    dialogo.addEventListener('close', () => {
      document.documentElement.style.overflow = bloqueoAnterior;
      const destino = botonApertura;
      destino?.focus({ preventScroll: true });
      botonApertura = null;
      programarCambio();
    });
  }


  escenas.forEach(escena => { escena.tabIndex = -1; });
  actualizarMedidas();
  const indiceInicial = escenas.findIndex(escena => `#${escena.id}` === location.hash);
  if (indiceInicial >= 0 && !modoScroll) elegirEscena(indiceInicial);
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
