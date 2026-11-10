-- Foto de perfil (Mi cuenta). La app la manda ya recortada a 256x256 (unos 20 KB).
-- Tabla aparte de users para que leer usuarios no arrastre las fotos.
CREATE TABLE IF NOT EXISTS secop.fotos_perfil (
  usuario_id     INTEGER     NOT NULL PRIMARY KEY REFERENCES secop.users(id) ON DELETE CASCADE,
  contenido      BYTEA       NOT NULL CHECK (octet_length(contenido) <= 153600),
  tipo           VARCHAR(20) NOT NULL CHECK (tipo IN ('image/jpeg', 'image/png')),
  actualizado_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);
ALTER TABLE secop.fotos_perfil ENABLE ROW LEVEL SECURITY;
REVOKE ALL ON secop.fotos_perfil FROM anon, authenticated;
