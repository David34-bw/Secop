// Panel: los selectores aplican el filtro al cambiar; los textos con Enter o "Aplicar".
document.querySelectorAll('[data-autoenvio]').forEach((el) => {
  el.addEventListener('change', () => {
    // Cambiar un filtro reinicia la paginación.
    el.form.submit();
  });
});
