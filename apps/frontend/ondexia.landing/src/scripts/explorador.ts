function iniciarExplorador(explorador: HTMLElement): void {
  const pestanas = [...explorador.querySelectorAll<HTMLButtonElement>('[data-pestana]')];
  const escenas = [...explorador.querySelectorAll<HTMLElement>('[data-escena]')];
  const listaPestanas = explorador.querySelector<HTMLElement>('[data-pestanas]');
  const dialogo = explorador.querySelector<HTMLDialogElement>('[data-dialogo]');
  const cerrar = explorador.querySelector<HTMLButtonElement>('[data-cerrar]');
  const tituloAmpliado = explorador.querySelector<HTMLElement>('[data-titulo-ampliado]');
  const capturaAmpliada = explorador.querySelector<HTMLImageElement>('[data-captura-ampliada]');
  const fuenteAmpliada = explorador.querySelector<HTMLSourceElement>('[data-fuente-ampliada]');
  const originalAmpliado = explorador.querySelector<HTMLAnchorElement>('[data-original-ampliado]');

  if (!listaPestanas || pestanas.length !== escenas.length || pestanas.length === 0) return;

  const vistaMovil = window.matchMedia('(max-width: 600px)');
  let botonApertura: HTMLButtonElement | null = null;

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

  function seleccionarEscena(indice: number, moverFoco = false): void {
    pestanas.forEach((pestana, posicion) => {
      const seleccionada = posicion === indice;
      pestana.setAttribute('aria-selected', String(seleccionada));
      pestana.tabIndex = seleccionada ? 0 : -1;
      escenas[posicion].hidden = !seleccionada;
    });
    if (moverFoco) pestanas[indice].focus();
  }

  pestanas.forEach((pestana, indice) => {
    pestana.setAttribute('role', 'tab');
    escenas[indice].setAttribute('role', 'tabpanel');
    escenas[indice].setAttribute('aria-labelledby', pestana.id);
    escenas[indice].tabIndex = 0;
    pestana.addEventListener('click', () => seleccionarEscena(indice));
    pestana.addEventListener('keydown', (evento) => {
      let siguiente: number;
      switch (evento.key) {
        case 'ArrowRight':
          siguiente = (indice + 1) % pestanas.length;
          break;
        case 'ArrowLeft':
          siguiente = (indice - 1 + pestanas.length) % pestanas.length;
          break;
        case 'Home':
          siguiente = 0;
          break;
        case 'End':
          siguiente = pestanas.length - 1;
          break;
        default:
          return;
      }
      evento.preventDefault();
      seleccionarEscena(siguiente, true);
    });
  });

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
        // Cada captura se abre desde el inicio, aunque se haya recorrido otra antes.
        dialogo.scrollTop = 0;
      });
      boton.hidden = false;
    });

    cerrar.addEventListener('click', () => dialogo.close());
    // El evento close incluye Escape y conserva un único punto de retorno del foco.
    dialogo.addEventListener('close', () => {
      botonApertura?.focus();
      botonApertura = null;
    });
  }

  actualizarOriginales();
  vistaMovil.addEventListener('change', actualizarOriginales);
  listaPestanas.setAttribute('role', 'tablist');
  seleccionarEscena(0);
  listaPestanas.hidden = false;
}

document.querySelectorAll<HTMLElement>('[data-explorador]').forEach(iniciarExplorador);
