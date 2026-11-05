// Conexión a Postgres del lado del servidor. Usa el secreto SUPABASE_DB_URL
// que Supabase pone en todas las Edge Functions: la contraseña de la base
// nunca sale del servidor. Todas las consultas usan parámetros
// (plantillas de postgres.js) y nombres de tabla fijos del esquema secop.
import postgres from "postgres";

const url = Deno.env.get("SUPABASE_DB_URL");
if (!url) throw new Error("Falta SUPABASE_DB_URL");

export const sql = postgres(url, {
  // Sin sentencias preparadas: funciona igual con el pooler en modo transacción.
  prepare: false,
  max: 3,
  idle_timeout: 20,
  connect_timeout: 15,
  types: {
    // bigint (valor, count) como número; los valores de SECOP caben en 2^53.
    numero: { to: 20, from: [20], serialize: (x: number) => String(x), parse: (x: string) => Number(x) },
    // date como texto YYYY-MM-DD (sin convertirlo a una hora).
    fecha: { to: 1082, from: [1082], serialize: (x: string) => x, parse: (x: string) => x },
  },
});

export type Tx = postgres.TransactionSql;

/** Date de la base a ISO (o null). */
export function iso(d: unknown): string | null {
  if (d == null) return null;
  return d instanceof Date ? d.toISOString() : String(d);
}
