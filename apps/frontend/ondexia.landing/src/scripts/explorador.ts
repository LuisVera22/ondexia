function iniciarExplorador(explorador: HTMLElement): void {
  const enlaces = [...explorador.querySelectorAll<HTMLAnchorElement>('[data-paso]')];
  const escenas = [...explorador.querySelectorAll<HTMLElement>('[data-escena]')];
  const navegacion = explorador.querySelector<HTMLElement>('[data-pasos]');
  const anterior = explorador.querySelector<HTMLButtonElement>('[data-anterior]');
  const siguiente = explorador.querySelector<HTMLButtonElement>('[data-siguiente]');
  const progreso = explorador.querySelector<HTMLElement>('[data-progreso]');
  const dialogo = explorador.querySelector<HTMLDialogElement>('[data-dialogo]');
  const cerrar = explorador.querySelector<HTMLButtonElement>('[data-cerrar]');
  const tituloAmpliado = explorador.querySelector<HTMLElement>('[data-titulo-ampliado]');
  const capturaAmpliada = explorador.querySelector<HTMLImageElement>('[data-captura-ampliada]');
  const fuenteAmpliada = explorador.querySelector<HTMLSourceElement>('[data-fuente-ampliada]');
  const originalAmpliado = explorador.querySelector<HTMLAnchorElement>('[data-original-ampliado]');
  if (!navegacion || !anterior || !siguiente || !progreso || enlaces.length !== escenas.length || !escenas.length) return;
  const controles = { navegacion, anterior, siguiente, progreso };

  const vistaMovil = window.matchMedia('(max-width: 600px)');
  const movimientoReducido = window.matchMedia('(prefers-reduced-motion: reduce)');
  const figuras = escenas.map((escena) => escena.querySelector<HTMLElement>('[data-figura]'));
  const planos = escenas.map((escena) => escena.querySelector<HTMLElement>('[data-plano]'));
  let botonApertura: HTMLButtonElement | null = null;
  let indiceActivo = -1;
  let topeRecorrido = 0;
  let cuadroPendiente: number | null = null;

  function actualizarOriginales(): void {
    explorador.querySelectorAll<HTMLAnchorElement>('[data-original]').forEach((enlace) => {
      const destino = vistaMovil.matches ? enlace.dataset.originalMovil : enlace.dataset.originalEscritorio;
      if (destino) enlace.href = destino;
    });
    if (dialogo?.open && originalAmpliado && botonApertura) {
      const enlace = botonApertura.closest('[data-escena]')?.querySelector<HTMLAnchorElement>('[data-original]');
      if (enlace) originalAmpliado.href = enlace.href;
    }
  }

  function indicarEscena(indice: number): void {
    if (indice === indiceActivo) return;
    indiceActivo = indice;
    enlaces.forEach((enlace, posicion) => {
      if (posicion === indice) enlace.setAttribute('aria-current', 'step');
      else enlace.removeAttribute('aria-current');
    });
    escenas.forEach((escena, posicion) => escena.classList.toggle('escena-actual', posicion === indice));
    controles.progreso.textContent = `Escena ${indice + 1} de ${escenas.length}`;
    controles.anterior.disabled = indice === 0;
    controles.siguiente.disabled = indice === escenas.length - 1;
  }

  function actualizarRecorrido(): void {
    cuadroPendiente = null;
    const alto = document.documentElement.clientHeight;
    const medidas = escenas.map((escena) => escena.getBoundingClientRect());
    let indice = 0;
    medidas.forEach((medida, posicion) => {
      if (medida.top <= topeRecorrido + (alto - topeRecorrido) * 0.25) indice = posicion;
    });
    indicarEscena(indice);
    medidas.forEach((medida, posicion) => {
      let desplazamiento = 0;
      if (!movimientoReducido.matches && !dialogo?.open && medida.bottom > 0 && medida.top < alto) {
        const avance = Math.max(0, Math.min(1, (alto - medida.top) / (alto + medida.height)));
        desplazamiento = (0.5 - avance) * 24;
      }
      planos[posicion]?.style.setProperty('--desplazamiento-captura', `${desplazamiento.toFixed(2)}px`);
    });
  }

  function solicitarCuadro(): void {
    if (cuadroPendiente === null) cuadroPendiente = window.requestAnimationFrame(actualizarRecorrido);
  }

  function actualizarMedidas(): void {
    const cabecera = document.querySelector('header')?.getBoundingClientRect().height ?? 0;
    const alto = document.documentElement.clientHeight;
    explorador.style.setProperty('--alto-cabecera', `${cabecera}px`);
    const separacion = alto < 600 ? 8 : 24;
    topeRecorrido = cabecera + controles.navegacion.getBoundingClientRect().height + separacion;
    explorador.style.setProperty('--tope-recorrido', `${topeRecorrido}px`);
    const ancho = document.documentElement.clientWidth;
    figuras.forEach((figura) => {
      if (!figura) return;
      const cabe = figura.getBoundingClientRect().height <= alto - topeRecorrido - 24;
      figura.classList.toggle('figura-fijada', !movimientoReducido.matches && ancho >= 1024 && alto >= 800 && cabe);
    });
    solicitarCuadro();
  }

  function irA(indice: number): void {
    const destino = Math.max(0, Math.min(escenas.length - 1, indice));
    const escena = escenas[destino];
    escena.focus({ preventScroll: true });
    window.history.replaceState(null, '', `#${escena.id}`);
    const posicion = escena.getBoundingClientRect().top + window.scrollY - topeRecorrido;
    window.scrollTo({ top: posicion, behavior: movimientoReducido.matches ? 'instant' : 'smooth' });
    indicarEscena(destino);
  }

  enlaces.forEach((enlace, indice) => {
    enlace.addEventListener('click', (evento) => {
      // Conservar abrir en otra pestaña y los enlaces nativos como alternativa.
      if (evento.ctrlKey || evento.metaKey || evento.shiftKey || evento.altKey || evento.button !== 0) return;
      evento.preventDefault();
      irA(indice);
    });
  });
  anterior.addEventListener('click', () => irA(indiceActivo - 1));
  siguiente.addEventListener('click', () => irA(indiceActivo + 1));

  if (dialogo && typeof dialogo.showModal === 'function' && cerrar && tituloAmpliado && capturaAmpliada && fuenteAmpliada && originalAmpliado) {
    escenas.forEach((escena) => {
      const boton = escena.querySelector<HTMLButtonElement>('[data-ampliar]');
      const imagen = escena.querySelector<HTMLImageElement>('[data-captura]');
      const fuente = escena.querySelector<HTMLSourceElement>('source');
      const titulo = escena.querySelector<HTMLElement>('h3');
      const enlace = escena.querySelector<HTMLAnchorElement>('[data-original]');
      if (!boton || !imagen || !fuente || !titulo || !enlace) return;
      boton.addEventListener('click', () => {
        tituloAmpliado.textContent = titulo.textContent;
        fuenteAmpliada.srcset = fuente.srcset;
        fuenteAmpliada.width = fuente.width;
        fuenteAmpliada.height = fuente.height;
        capturaAmpliada.src = imagen.src;
        capturaAmpliada.alt = imagen.alt;
        capturaAmpliada.width = Number(imagen.getAttribute('width'));
        capturaAmpliada.height = Number(imagen.getAttribute('height'));
        originalAmpliado.href = enlace.href;
        botonApertura = boton;
        dialogo.showModal();
        dialogo.scrollTop = 0;
        solicitarCuadro();
      });
      boton.hidden = false;
    });
    cerrar.addEventListener('click', () => dialogo.close());
    dialogo.addEventListener('close', () => {
      botonApertura?.focus();
      botonApertura = null;
      solicitarCuadro();
    });
  }

  escenas.forEach((escena) => { escena.tabIndex = -1; });
  anterior.hidden = false;
  siguiente.hidden = false;
  indicarEscena(0);
  actualizarOriginales();
  actualizarMedidas();
  window.addEventListener('scroll', solicitarCuadro, { passive: true });
  window.addEventListener('resize', actualizarMedidas, { passive: true });
  movimientoReducido.addEventListener('change', actualizarMedidas);
  vistaMovil.addEventListener('change', actualizarOriginales);
  if ('ResizeObserver' in window) {
    const observador = new ResizeObserver(actualizarMedidas);
    observador.observe(navegacion);
    const cabecera = document.querySelector('header');
    if (cabecera) observador.observe(cabecera);
    figuras.forEach((figura) => { if (figura) observador.observe(figura); });
  }
}

document.querySelectorAll<HTMLElement>('[data-explorador]').forEach(iniciarExplorador);
