const movimientoReducido = window.matchMedia('(prefers-reduced-motion: reduce)');

// El contenido permanece visible: esta mejora solo anima la entrada y no la habilita.
if (!movimientoReducido.matches && 'IntersectionObserver' in window) {
  const observador = new IntersectionObserver((entradas) => {
    entradas.forEach((entrada) => {
      if (entrada.isIntersecting) {
        entrada.target.classList.add('entrada-activa');
        observador.unobserve(entrada.target);
      }
    });
  }, { threshold: 0.1 });

  document.querySelectorAll('.portada-interior, .encabezado-seccion, .presentacion-proceso, .bloque-preguntas, .contacto-interior')
    .forEach((elemento) => observador.observe(elemento));
  movimientoReducido.addEventListener('change', () => {
    if (movimientoReducido.matches) observador.disconnect();
  });
}

const menu = document.querySelector<HTMLDetailsElement>('.menu-movil');
menu?.querySelectorAll('a[href*="#"]').forEach((enlace) => {
  enlace.addEventListener('click', () => { menu.open = false; });
});
menu?.addEventListener('keydown', (evento) => {
  if (evento.key === 'Escape' && menu.open) {
    menu.open = false;
    menu.querySelector('summary')?.focus();
  }
});
