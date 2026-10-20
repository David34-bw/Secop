// Formulario de perfiles (port simplificado de FormPerfil.js del original).
(function () {
  const raiz = document.getElementById('nuevo-perfil');
  if (!raiz) return;

  const estado = {
    categorias: [],
    palabras_clave: [],
    palabras_excluir: [],
    ciudades: [],
    profesion_id: null,
  };
  let catalogo = null;

  const $ = (id) => document.getElementById(id);
  const esDescendiente = (codigo, ancestro) => codigo.length > ancestro.length && codigo.startsWith(ancestro);

  // ---- Categorías UNSPSC jerárquicas (lib/categorias.js) ----
  // Si se elige un código más específico, su ancestro sale de la lista.
  function quitarAncestros(codigos) {
    const unicos = Array.from(new Set(codigos));
    return unicos.filter((c) => !unicos.some((otro) => esDescendiente(otro, c)));
  }

  function alternarCategoria(codigo) {
    const activa = estado.categorias.some((c) => c === codigo || esDescendiente(c, codigo));
    if (activa) {
      const restantes = estado.categorias.filter((c) => c !== codigo && !esDescendiente(c, codigo));
      // Quitar la última subcategoría de un segmento devuelve el segmento completo.
      const segmento = codigo.slice(0, 2);
      estado.categorias =
        codigo.length > 2 && !restantes.some((c) => c.startsWith(segmento)) ? [...restantes, segmento] : restantes;
    } else {
      estado.categorias = quitarAncestros([...estado.categorias.filter((c) => !esDescendiente(c, codigo)), codigo]);
    }
    pintarCategorias();
    pintarArbol();
    programarVistaPrevia();
  }

  const nombreDe = (codigo) => (catalogo && catalogo.nombres[codigo]) || codigo;

  function pintarCategorias() {
    const cont = $('categorias-elegidas');
    cont.innerHTML = '';
    if (estado.categorias.length === 0) {
      cont.innerHTML = '<span class="texto-chico apagado">Ninguna área elegida (opcional si usas palabras clave).</span>';
      return;
    }
    estado.categorias.forEach((c) => cont.appendChild(chip(`${nombreDe(c)} (${c})`, () => {
      estado.categorias = estado.categorias.filter((x) => x !== c);
      pintarCategorias();
      pintarArbol();
      programarVistaPrevia();
    })));
  }

  function pintarArbol() {
    if (!catalogo) return;
    const filtro = normalizar($('buscar-area').value);
    const arbol = $('arbol-areas');
    arbol.innerHTML = '';
    catalogo.segmentos.forEach((s) => {
      const familias = catalogo.familias[s.code] || [];
      const coincide = (texto) => !filtro || normalizar(texto).includes(filtro);
      const familiasVisibles = familias.filter(([cod, nombre]) => coincide(nombre) || coincide(s.label) || coincide(s.ejemplos));
      if (!coincide(s.label) && !coincide(s.ejemplos) && familiasVisibles.length === 0) return;

      const bloque = document.createElement('div');
      bloque.className = 'arbol-segmento';
      bloque.appendChild(botonArea(s.code, s.label, s.ejemplos));
      const lista = document.createElement('div');
      lista.className = 'arbol-familias';
      const activoSeg = estado.categorias.some((c) => c.startsWith(s.code));
      if (activoSeg || filtro) {
        familiasVisibles.slice(0, filtro ? 30 : 12).forEach(([cod, nombre, n]) => {
          lista.appendChild(botonArea(cod, nombre, `${n} procesos`));
          const clases = (catalogo.clases[cod] || []);
          if (estado.categorias.some((c) => c === cod || esDescendiente(c, cod))) {
            const sub = document.createElement('div');
            sub.className = 'arbol-clases';
            clases.slice(0, 10).forEach(([cc, nc, nn]) => sub.appendChild(botonArea(cc, nc, `${nn} procesos`)));
            lista.appendChild(sub);
          }
        });
      }
      bloque.appendChild(lista);
      arbol.appendChild(bloque);
    });
  }

  function botonArea(codigo, nombre, detalle) {
    const b = document.createElement('button');
    b.type = 'button';
    const activa = estado.categorias.some((c) => c === codigo || esDescendiente(c, codigo));
    b.className = 'area' + (activa ? ' activa' : '');
    b.innerHTML = `<span></span><small></small>`;
    b.firstChild.textContent = nombre;
    b.lastChild.textContent = detalle || '';
    b.addEventListener('click', () => alternarCategoria(codigo));
    return b;
  }

  // ---- Listas con chips (palabras clave, excluir, ciudades) ----
  function chip(texto, alQuitar) {
    const s = document.createElement('span');
    s.className = 'chip activo';
    s.textContent = texto;
    const x = document.createElement('button');
    x.type = 'button';
    x.className = 'chip-quitar';
    x.setAttribute('aria-label', `Quitar ${texto}`);
    x.textContent = '×';
    x.addEventListener('click', alQuitar);
    s.appendChild(x);
    return s;
  }

  function pintarChips(campo) {
    const cont = $(`chips-${campo}`);
    cont.innerHTML = '';
    estado[campo].forEach((v) => cont.appendChild(chip(v, () => {
      estado[campo] = estado[campo].filter((x) => x !== v);
      pintarChips(campo);
      programarVistaPrevia();
    })));
  }

  document.querySelectorAll('[data-chips]').forEach((input) => {
    const campo = input.dataset.chips;
    const agregar = () => {
      input.value.split(',').map((t) => t.trim()).filter(Boolean).forEach((t) => {
        if (!estado[campo].includes(t)) estado[campo].push(t);
      });
      input.value = '';
      pintarChips(campo);
      programarVistaPrevia();
    };
    input.addEventListener('keydown', (e) => {
      if (e.key === 'Enter') {
        e.preventDefault();
        agregar();
      }
    });
    input.addEventListener('blur', agregar);
  });

  // ---- Grupos obligatorios: opciones, "todos" y "otro" ----
  const GRUPOS = Array.from(raiz.querySelectorAll('.grupo[data-grupo]')).map((el) => el.dataset.grupo);

  GRUPOS.forEach((campo) => {
    const todos = raiz.querySelector(`input[data-todos="${campo}"]`);
    const otro = raiz.querySelector(`input[data-otro="${campo}"]`);
    const caja = raiz.querySelector(`[data-otro-caja="${campo}"]`);
    const opciones = () => Array.from(raiz.querySelectorAll(`input[data-campo="${campo}"]`));

    // "Todos" excluye a las demás opciones (y al revés).
    todos.addEventListener('change', () => {
      if (todos.checked) {
        opciones().forEach((o) => { o.checked = false; });
        otro.checked = false;
        caja.classList.add('oculto');
      }
      limpiarError(campo);
    });
    const desmarcarTodos = () => {
      if (todos.checked) todos.checked = false;
      limpiarError(campo);
    };
    opciones().forEach((o) => o.addEventListener('change', () => { if (o.checked) desmarcarTodos(); else limpiarError(campo); }));
    otro.addEventListener('change', () => {
      caja.classList.toggle('oculto', !otro.checked);
      if (otro.checked) {
        desmarcarTodos();
        const input = caja.querySelector('input');
        if (input) input.focus();
      }
      programarVistaPrevia();
    });
    caja.querySelectorAll('input').forEach((i) => i.addEventListener('input', () => {
      limpiarError(campo);
      programarVistaPrevia();
    }));
  });

  // Texto escrito en "Otro": varios separados por coma.
  function textoOtro(campo) {
    const otro = raiz.querySelector(`input[data-otro="${campo}"]`);
    const input = raiz.querySelector(`input[data-otro-texto="${campo}"]`);
    if (!otro || !otro.checked || !input) return [];
    return input.value.split(',').map((t) => t.trim()).filter(Boolean);
  }

  const soloDigitos = (v) => v.replace(/[.\s$]/g, '');
  function rangoOtro() {
    const otro = raiz.querySelector('input[data-otro="rangos_valor"]');
    if (!otro || !otro.checked) return null;
    const min = soloDigitos($('rango-otro-min').value);
    const max = soloDigitos($('rango-otro-max').value);
    return { min, max };
  }

  function mostrarError(campo, mensaje) {
    const p = raiz.querySelector(`[data-error="${campo}"]`);
    const grupo = raiz.querySelector(`[data-grupo="${campo}"]`);
    if (p) {
      p.textContent = mensaje;
      p.classList.remove('oculto');
    }
    if (grupo) grupo.classList.add('grupo-error');
    return grupo;
  }

  function limpiarError(campo) {
    const p = raiz.querySelector(`[data-error="${campo}"]`);
    if (p) p.classList.add('oculto');
    const grupo = raiz.querySelector(`[data-grupo="${campo}"]`);
    if (grupo) grupo.classList.remove('grupo-error');
  }

  const MENSAJES = {
    departamentos: 'Elige al menos un departamento, «Todo el país» u «Otro»',
    modalidades: 'Elige al menos una modalidad, «Todas» u «Otra»',
    tipos_contrato: 'Elige al menos un tipo de contrato, «Todos» u «Otro»',
    tipos_entidad: 'Elige al menos un tipo de entidad, «Todas» u «Otra»',
    rangos_valor: 'Elige al menos un rango de valor, «Cualquier valor» u «Otro rango»',
  };

  // Devuelve el primer grupo con error (o null si todo está bien).
  function validar() {
    const errores = [];
    const select = $('profesion');
    if (!select.value) errores.push(mostrarError('profesion', 'Elige tu profesión, o «Otra» para escribirla'));
    else if (select.value === 'otra' && !$('profesion-otra').value.trim()) {
      errores.push(mostrarError('profesion', 'Escribe tu profesión u oficio'));
    }
    if (!$('nombre').value.trim()) errores.push(mostrarError('nombre', 'Ponle un nombre al perfil'));

    GRUPOS.forEach((campo) => {
      const todos = raiz.querySelector(`input[data-todos="${campo}"]`).checked;
      const marcados = raiz.querySelectorAll(`input[data-campo="${campo}"]:checked`).length;
      const otro = raiz.querySelector(`input[data-otro="${campo}"]`).checked;
      if (campo === 'rangos_valor' && otro) {
        const r = rangoOtro();
        const min = r.min ? Number(r.min) : 0;
        const max = r.max ? Number(r.max) : null;
        const valido = (r.min || r.max) && Number.isFinite(min) && min >= 0 &&
          (max === null || (Number.isFinite(max) && max > min));
        if (!valido) {
          errores.push(mostrarError(campo, 'Escribe montos válidos en pesos: un mínimo, un máximo o ambos (el máximo mayor que el mínimo)'));
        }
        return;
      }
      if (otro && textoOtro(campo).length === 0) {
        errores.push(mostrarError(campo, 'Escribe tu opción en el campo de «Otro»'));
        return;
      }
      if (!todos && !marcados && !otro) errores.push(mostrarError(campo, MENSAJES[campo]));
    });
    return errores.find(Boolean) || null;
  }

  // ---- Profesión: prellena el perfil ----
  function elegirProfesion(id) {
    limpiarError('profesion');
    const otra = $('profesion-otra');
    otra.classList.toggle('oculto', id !== 'otra');
    if (id === 'otra') {
      estado.profesion_id = null;
      $('aviso-directa').classList.add('oculto');
      otra.focus();
      return;
    }
    const p = catalogo.profesiones.find((x) => x.id === id);
    estado.profesion_id = p ? p.id : null;
    const aviso = $('aviso-directa');
    if (!p) {
      aviso.classList.add('oculto');
      return;
    }
    estado.categorias = quitarAncestros([...p.categorias]);
    estado.palabras_clave = [...p.palabrasClave];
    estado.palabras_excluir = [...p.palabrasExcluir];
    if (!$('nombre').value.trim()) $('nombre').value = p.nombre;
    aviso.textContent = catalogo.aviso;
    aviso.classList.toggle('oculto', !p.contratacionDirecta);
    pintarCategorias();
    pintarArbol();
    ['palabras_clave', 'palabras_excluir'].forEach(pintarChips);
    programarVistaPrevia();
  }

  // ---- Datos del formulario ----
  function datos() {
    const marcados = (campo) =>
      Array.from(raiz.querySelectorAll(`input[data-campo="${campo}"]:checked`)).map((i) => i.value)
        .concat(textoOtro(campo));
    const todos = (campo) => raiz.querySelector(`input[data-todos="${campo}"]`).checked;
    const rango = rangoOtro();
    const datos = {
      nombre: $('nombre').value.trim(),
      categorias: estado.categorias,
      palabras_clave: estado.palabras_clave,
      palabras_excluir: estado.palabras_excluir,
      ciudades: estado.ciudades,
      dias_min_restantes: $('dias').value,
      profesion_id: estado.profesion_id,
      profesion_otra: $('profesion').value === 'otra' ? $('profesion-otra').value.trim() : '',
      rango_otro_min: rango ? rango.min : '',
      rango_otro_max: rango ? rango.max : '',
    };
    GRUPOS.forEach((campo) => {
      datos[campo] = marcados(campo);
      datos[`${campo}_todos`] = todos(campo);
    });
    return datos;
  }

  async function enviarJson(url, cuerpo) {
    const res = await fetch(url, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(cuerpo),
    });
    const json = await res.json().catch(() => ({}));
    return { ok: res.ok, json };
  }

  // ---- Vista previa: cuántos procesos vigentes coinciden ----
  let temporizador = null;
  function programarVistaPrevia() {
    clearTimeout(temporizador);
    temporizador = setTimeout(async () => {
      const salida = $('vista-previa');
      salida.textContent = 'Calculando...';
      const { ok, json } = await enviarJson('/api/perfiles/preview', datos());
      if (!ok) {
        salida.textContent = '';
        return;
      }
      salida.textContent =
        json.count === 0
          ? 'Ningún proceso vigente coincide todavía. Prueba con más palabras clave o menos filtros.'
          : `${json.count} proceso${json.count === 1 ? '' : 's'} vigente${json.count === 1 ? '' : 's'} coincide${json.count === 1 ? '' : 'n'} con este perfil.`;
    }, 500);
  }
  raiz.addEventListener('change', (e) => {
    if (e.target.matches('input[type=checkbox], #dias')) programarVistaPrevia();
  });

  $('guardar').addEventListener('click', async () => {
    const error = $('error-perfil');
    error.classList.add('oculto');
    const conError = validar();
    if (conError) {
      error.textContent = 'Revisa los campos marcados en rojo.';
      error.classList.remove('oculto');
      conError.scrollIntoView({ behavior: 'smooth', block: 'center' });
      return;
    }
    const boton = $('guardar');
    boton.disabled = true;
    boton.textContent = 'Guardando...';
    const { ok, json } = await enviarJson('/api/perfiles', datos());
    boton.disabled = false;
    boton.textContent = 'Guardar perfil';
    if (!ok) {
      error.textContent = json.error || 'No se pudo guardar el perfil';
      error.classList.remove('oculto');
      if (json.campo) {
        const grupo = mostrarError(json.campo, json.error);
        if (grupo) grupo.scrollIntoView({ behavior: 'smooth', block: 'center' });
      }
      return;
    }
    window.location.href = `/dashboard?creado=${json.coincidencias}`;
  });

  function normalizar(t) {
    return String(t || '')
      .normalize('NFD')
      .replace(/[̀-ͯ]/g, '')
      .toLowerCase()
      .trim();
  }

  // ---- Carga del catálogo ----
  fetch('/api/catalogo')
    .then((r) => r.json())
    .then((c) => {
      catalogo = c;
      const select = $('profesion');
      c.profesiones.forEach((p) => {
        const o = document.createElement('option');
        o.value = p.id;
        o.textContent = p.nombre;
        select.appendChild(o);
      });
      const otra = document.createElement('option');
      otra.value = 'otra';
      otra.textContent = 'Otra (escribir la mía)';
      select.appendChild(otra);
      select.addEventListener('change', () => elegirProfesion(select.value));
      $('profesion-otra').addEventListener('input', () => limpiarError('profesion'));
      $('nombre').addEventListener('input', () => limpiarError('nombre'));
      $('buscar-area').addEventListener('input', pintarArbol);
      pintarCategorias();
      pintarArbol();
    });
})();
