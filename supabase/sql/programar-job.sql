-- Programa la actualización diaria en el servidor (pg_cron + pg_net).
-- Se ejecuta UNA vez, a mano, DESPUÉS de desplegar la función "api" y de
-- crear el secreto SECOP_JOB_SECRET (ver la guía de despliegue).
-- Reemplaza los dos valores marcados antes de ejecutarlo. No guardes este
-- archivo con los valores reales en git.

CREATE EXTENSION IF NOT EXISTS pg_cron;
CREATE EXTENSION IF NOT EXISTS pg_net;

-- Los valores quedan cifrados en Supabase Vault, no en el texto del cron.
SELECT vault.create_secret('https://dfsrvpplxueujiezdogi.supabase.co', 'secop_project_url');
SELECT vault.create_secret('PEGA_AQUI_EL_MISMO_VALOR_DE_SECOP_JOB_SECRET', 'secop_job_secret');

-- Todos los días a las 21:00 de Colombia (02:00 UTC).
SELECT cron.schedule(
  'secop-actualizacion-diaria',
  '0 2 * * *',
  $$
  SELECT net.http_post(
    url := (SELECT decrypted_secret FROM vault.decrypted_secrets WHERE name = 'secop_project_url') || '/functions/v1/api/job.ingesta',
    headers := jsonb_build_object(
      'Content-Type', 'application/json',
      'x-secop-job', (SELECT decrypted_secret FROM vault.decrypted_secrets WHERE name = 'secop_job_secret')
    ),
    body := '{"offset": 0, "dias": 5}'::jsonb,
    timeout_milliseconds := 60000
  );
  $$
);
