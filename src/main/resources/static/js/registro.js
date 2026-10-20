// Registro: valida el correo mientras se escribe (formato, dominio mal
// escrito, correo temporal y que el dominio reciba correos). El servidor
// vuelve a validar todo al enviar.
(function () {
  const form = document.getElementById('form-registro');
  const email = document.getElementById('email');
  const error = document.getElementById('email-error');
  let ultimo = { valor: null, ok: false };
  let temporizador = null;

  function mostrar(mensaje) {
    error.textContent = mensaje || '';
    error.classList.toggle('oculto', !mensaje);
    email.classList.toggle('campo-error', Boolean(mensaje));
    email.setAttribute('aria-invalid', mensaje ? 'true' : 'false');
  }

  async function validar() {
    const valor = email.value.trim().toLowerCase();
    if (valor === ultimo.valor) return ultimo.ok;
    try {
      const res = await fetch(`/api/validar-correo?email=${encodeURIComponent(valor)}`);
      const json = await res.json();
      ultimo = { valor, ok: json.ok };
      mostrar(json.ok ? '' : json.error);
      return json.ok;
    } catch {
      return true; // Sin red: decide el servidor.
    }
  }

  email.addEventListener('input', () => {
    clearTimeout(temporizador);
    // Mientras escribe no se le grita; se revisa al hacer una pausa.
    temporizador = setTimeout(() => { if (email.value.includes('@')) validar(); }, 700);
  });
  email.addEventListener('blur', () => { if (email.value.trim()) validar(); });

  form.addEventListener('submit', async (e) => {
    e.preventDefault();
    const okCorreo = await validar();
    const password = document.getElementById('password');
    const acepto = form.querySelector('input[name="acepto"]');
    if (!okCorreo) return email.focus();
    if (password.value.length < 8) {
      password.focus();
      return alert('La contraseña debe tener al menos 8 caracteres');
    }
    if (!acepto.checked) {
      acepto.focus();
      return alert('Debes aceptar los Términos y la Política de datos personales');
    }
    form.submit();
  });
})();
