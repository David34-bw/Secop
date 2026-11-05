CREATE TABLE IF NOT EXISTS secop.procesos_ocultos (
  usuario_id INTEGER      NOT NULL REFERENCES secop.users(id) ON DELETE CASCADE,
  proceso_id VARCHAR(100) NOT NULL REFERENCES secop.procesos(id_del_proceso) ON DELETE CASCADE,
  fecha      TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT procesos_ocultos_pk PRIMARY KEY (usuario_id, proceso_id)
);
ALTER TABLE secop.procesos_ocultos ENABLE ROW LEVEL SECURITY;
REVOKE ALL ON secop.procesos_ocultos FROM anon, authenticated;;
