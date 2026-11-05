<#
.SYNOPSIS
  Configura en Supabase el acceso por HTTPS de SECOP RADAR (modo API) de una sola vez.

.DESCRIPTION
  Se ejecuta UNA vez, en el computador que tiene config.local.properties con bd.password
  (la contraseña de la base nunca sale de ese archivo ni pasa por el chat). Hace:
    1. Revisar Java, Node y config.local.properties.
    2. Iniciar sesión en Supabase CLI (abre el navegador si hace falta).
    3. Enlazar el proyecto, traer el historial de migraciones y aplicar la migración nueva.
    4. Crear el secreto del job (SECOP_JOB_SECRET) en la función.
    5. Desplegar la Edge Function "api".
    6. Guardar el secreto en Vault y programar la actualización diaria (pg_cron).
    7. Probar la función (y que rechace lo que debe rechazar).
    8. Lanzar la primera actualización y mostrar el resultado.
    9. Poner bd.modo=api en config.properties (para todos los computadores).
  Se puede volver a ejecutar: no duplica nada (el secreto se renueva en los dos lados).

.PARAMETER Simular
  Muestra lo que haría sin ejecutar nada contra Supabase.

.PARAMETER SinPrimeraActualizacion
  No lanza la descarga de SECOP al final.

.EXAMPLE
  powershell -ExecutionPolicy Bypass -File scripts\configurar-api.ps1
#>
param(
    [switch]$Simular,
    [switch]$SinPrimeraActualizacion
)

$ErrorActionPreference = 'Stop'
$Ref = 'dfsrvpplxueujiezdogi'
$Raiz = Split-Path -Parent $PSScriptRoot
Set-Location $Raiz
$ConfigPublica = Join-Path $Raiz 'src\main\resources\config.properties'
$ConfigLocal = Join-Path $Raiz 'config.local.properties'
$Resumen = New-Object System.Collections.Generic.List[string]

function Titulo([string]$texto) {
    Write-Host ''
    Write-Host "== $texto" -ForegroundColor Cyan
}

function Bien([string]$texto) {
    Write-Host "   OK  $texto" -ForegroundColor Green
    $Resumen.Add("OK     $texto")
}

function Mal([string]$texto) {
    Write-Host "   ERROR  $texto" -ForegroundColor Red
    $Resumen.Add("ERROR  $texto")
}

function Terminar([int]$codigo) {
    Remove-Item Env:\SUPABASE_DB_PASSWORD -ErrorAction SilentlyContinue
    Remove-Item Env:\SECOP_BD_PASSWORD -ErrorAction SilentlyContinue
    Remove-Item Env:\SECOP_JOB_SECRET -ErrorAction SilentlyContinue
    Write-Host ''
    Write-Host '== Resumen' -ForegroundColor Cyan
    $Resumen | ForEach-Object { Write-Host "   $_" }
    exit $codigo
}

# Ejecuta un programa externo; si falla, corta el script con un mensaje claro.
function Correr([string]$descripcion, [string]$programa, [string[]]$argumentos) {
    Write-Host "   > $descripcion" -ForegroundColor DarkGray
    if ($Simular) {
        Write-Host "     (simulado) $programa $($argumentos -join ' ')" -ForegroundColor DarkYellow
        return
    }
    $codigo = Nativo $programa $argumentos
    if ($codigo -ne 0) {
        Mal "$descripcion (código $codigo)"
        Terminar 1
    }
}

# Corre un programa externo y devuelve su código de salida. En PowerShell 5.1,
# con ErrorActionPreference=Stop, cualquier aviso que un programa escriba en la
# salida de errores (como "npm notice") corta el script: aquí se permite.
function Nativo([string]$programa, [string[]]$argumentos, [switch]$Silencioso, [switch]$Interactivo) {
    $antes = $ErrorActionPreference
    $ErrorActionPreference = 'Continue'
    try {
        if ($Interactivo) { & $programa @argumentos }
        elseif ($Silencioso) { & $programa @argumentos 2>&1 | Out-Null }
        else { & $programa @argumentos 2>&1 | ForEach-Object { Write-Host "     $_" } }
        return $LASTEXITCODE
    } finally {
        $ErrorActionPreference = $antes
    }
}

# npx.cmd y no npx: "npx" en PowerShell es npx.ps1, que falla con los avisos de npm.
$Npx = 'npx.cmd'
# Versión fija de la CLI: la 3.x cambió opciones (--remote en vez de --project-ref).
$Cli = 'supabase@3.0.0'

function Supabase([string]$descripcion, [string[]]$argumentos) {
    Correr $descripcion $Npx (@('--yes', $Cli) + $argumentos)
}

# Lee una clave de un archivo .properties (con los escapes básicos de Java).
function LeerPropiedad([string]$archivo, [string]$clave) {
    if (-not (Test-Path $archivo)) { return $null }
    foreach ($linea in Get-Content $archivo -Encoding UTF8) {
        $l = $linea.TrimStart()
        if ($l.StartsWith('#') -or $l.StartsWith('!')) { continue }
        if ($l -match "^$([regex]::Escape($clave))\s*[=:]\s*(.*)$") {
            $v = $Matches[1]
            $nulo = [string][char]0
            $v = ($v.Replace('\\', $nulo) -replace '\\([=: #!])', '$1').Replace($nulo, '\')
            return $v.TrimEnd()
        }
    }
    return $null
}

# POST a la función; devuelve el código HTTP (0 si no hubo respuesta).
function Probar([string]$operacion, [hashtable]$cabeceras) {
    $url = "https://$Ref.supabase.co/functions/v1/api/$operacion"
    try {
        $r = Invoke-WebRequest -UseBasicParsing -Method Post -Uri $url -Headers $cabeceras -ContentType 'application/json' -Body '{}' -TimeoutSec 60
        return [int]$r.StatusCode
    } catch [System.Net.WebException] {
        if ($_.Exception.Response) { return [int]$_.Exception.Response.StatusCode }
        return 0
    }
}

# ---------------------------------------------------------------- 1
Titulo '1. Revisar lo necesario'
if (-not $env:JAVA_HOME -or -not (Test-Path (Join-Path $env:JAVA_HOME 'bin\java.exe'))) {
    $jdk = Get-ChildItem (Join-Path $HOME '.jdks') -Directory -ErrorAction SilentlyContinue |
        Where-Object { $_.Name -match '21' } | Select-Object -First 1
    if ($jdk) { $env:JAVA_HOME = $jdk.FullName }
}
if (-not $env:JAVA_HOME -or -not (Test-Path (Join-Path $env:JAVA_HOME 'bin\java.exe'))) {
    Mal 'No encontré Java 21. Define JAVA_HOME (el JDK que usa IntelliJ, por ejemplo C:\Users\david\.jdks\temurin-21...).'
    Terminar 1
}
$Java = Join-Path $env:JAVA_HOME 'bin\java.exe'
Bien "Java: $env:JAVA_HOME"

if (-not (Get-Command npx.cmd -ErrorAction SilentlyContinue)) {
    Mal 'No encontré Node.js (npx). Instálalo desde nodejs.org y vuelve a ejecutar.'
    Terminar 1
}
Bien 'Node.js (npx)'

$password = LeerPropiedad $ConfigLocal 'bd.password'
if ([string]::IsNullOrWhiteSpace($password)) {
    Mal 'Falta bd.password en config.local.properties (la contraseña de la base de Supabase).'
    Terminar 1
}
# Solo para los programas que lanza este script; se borra al terminar.
$env:SUPABASE_DB_PASSWORD = $password
$env:SECOP_BD_PASSWORD = $password
$password = $null
Bien 'Contraseña de la base leída de config.local.properties (no se muestra)'

$clave = LeerPropiedad $ConfigPublica 'api.clave'
if ([string]::IsNullOrWhiteSpace($clave)) {
    Mal 'Falta api.clave en config.properties.'
    Terminar 1
}

# ---------------------------------------------------------------- 2
Titulo '2. Sesión en Supabase CLI'
if (-not $Simular) {
    $logueado = (Nativo $Npx @('--yes', $Cli, 'projects', 'list') -Silencioso) -eq 0
} else { $logueado = $true }
if (-not $logueado) {
    Write-Host '   Se abrirá el navegador: inicia sesión en Supabase y autoriza la CLI (si pide Enter, presiónalo).' -ForegroundColor Yellow
    if ((Nativo $Npx @('--yes', $Cli, 'login') -Interactivo) -ne 0) {
        Mal 'No se pudo iniciar sesión en Supabase CLI'
        Terminar 1
    }
}
Bien 'Sesión de Supabase CLI'

# ---------------------------------------------------------------- 3
Titulo '3. Proyecto y migraciones'
Supabase 'Enlazar el proyecto' @('link', '--project-ref', $Ref, '--yes')
Supabase 'Traer el historial de migraciones' @('migration', 'fetch', '--remote', '--yes')
Supabase 'Aplicar la migración nueva (sesiones, Storage...)' @('db', 'push', '--remote', '--yes')
Bien 'Migraciones al día'

# ---------------------------------------------------------------- 4
Titulo '4. Secreto del job'
$bytes = New-Object byte[] 32
$rng = [System.Security.Cryptography.RNGCryptoServiceProvider]::new()
$rng.GetBytes($bytes)
$rng.Dispose()
$env:SECOP_JOB_SECRET = [Convert]::ToBase64String($bytes) -replace '[+/=]', ''
# Va por un archivo temporal (no por la línea de comandos) y se borra enseguida.
$archivoSecreto = Join-Path $env:TEMP ("secop-" + [guid]::NewGuid().ToString('N') + '.env')
try {
    Set-Content -Path $archivoSecreto -Value "SECOP_JOB_SECRET=$($env:SECOP_JOB_SECRET)" -Encoding ASCII
    Supabase 'Guardar SECOP_JOB_SECRET en la función' @('secrets', 'set', '--env-file', $archivoSecreto, '--yes')
} finally {
    Remove-Item $archivoSecreto -Force -ErrorAction SilentlyContinue
}
Bien 'Secreto del job creado (no se muestra)'

# ---------------------------------------------------------------- 5
Titulo '5. Desplegar la función'
Supabase 'Desplegar la Edge Function api' @('functions', 'deploy', 'api', '--use-api', '--no-verify-jwt', '--yes')
Bien 'Función api desplegada'

# ---------------------------------------------------------------- 6
Titulo '6. Actualización diaria (pg_cron)'
Correr 'Compilar la herramienta' (Join-Path $Raiz 'mvnw.cmd') @('-q', '-DskipTests', 'compile')
$archivoCp = Join-Path $env:TEMP ("secop-cp-" + [guid]::NewGuid().ToString('N') + '.txt')
Correr 'Calcular el classpath' (Join-Path $Raiz 'mvnw.cmd') @('-q', 'dependency:build-classpath', "-Dmdep.outputFile=$archivoCp")
if (-not $Simular) {
    $Classpath = (Join-Path $Raiz 'target\classes') + ';' + (Get-Content $archivoCp -Raw).Trim()
    Remove-Item $archivoCp -Force -ErrorAction SilentlyContinue
} else { $Classpath = 'target\classes;...' }
Correr 'Guardar el secreto en Vault y programar el job' $Java @('-cp', $Classpath, 'co.secopradar.herramientas.ConfigurarServidor', 'programar-job')
Bien 'Job diario programado (21:00 de Colombia)'

# ---------------------------------------------------------------- 7
Titulo '7. Probar la función'
if (-not $Simular) {
    $codigo = 0
    for ($i = 1; $i -le 6 -and $codigo -ne 200; $i++) {
        $codigo = Probar 'estado.datos' @{ apikey = $clave }
        if ($codigo -ne 200) { Start-Sleep -Seconds 5 }
    }
    if ($codigo -eq 200) { Bien 'Responde con la clave de la app (200)' } else { Mal "estado.datos respondió $codigo"; Terminar 1 }

    $pruebas = @(
        @{ op = 'estado.datos'; cab = @{}; esperado = 403; que = 'Rechaza peticiones sin la clave de la app' },
        @{ op = 'cuenta.yo'; cab = @{ apikey = $clave }; esperado = 401; que = 'Rechaza peticiones sin sesión' },
        @{ op = 'job.planes'; cab = @{ apikey = $clave }; esperado = 403; que = 'Rechaza el job sin su secreto' }
    )
    foreach ($p in $pruebas) {
        $c = Probar $p.op $p.cab
        if ($c -eq $p.esperado) { Bien "$($p.que) ($c)" } else { Mal "$($p.que): respondió $c, se esperaba $($p.esperado)" }
    }
} else { Write-Host '   (simulado) pruebas HTTP' -ForegroundColor DarkYellow }

# ---------------------------------------------------------------- 8
if (-not $SinPrimeraActualizacion) {
    Titulo '8. Primera actualización en el servidor'
    if (-not $Simular) {
        $c = Probar 'job.ingesta' @{ 'x-secop-job' = $env:SECOP_JOB_SECRET }
        if ($c -eq 200) {
            Bien 'Actualización iniciada (descarga SECOP, cruza perfiles y ajusta planes)'
            Write-Host '   Esperando 90 segundos para ver el resultado...' -ForegroundColor DarkGray
            Start-Sleep -Seconds 90
            Nativo $Java @('-cp', $Classpath, 'co.secopradar.herramientas.ConfigurarServidor', 'ultima-actualizacion') | Out-Null
        } else {
            Mal "job.ingesta respondió $c (revisa Edge Functions > api > Logs)"
        }
    } else { Write-Host '   (simulado) POST job.ingesta' -ForegroundColor DarkYellow }
}

# ---------------------------------------------------------------- 9
Titulo '9. Modo API para todos los computadores'
$texto = Get-Content $ConfigPublica -Raw -Encoding UTF8
if ($texto -match '(?m)^bd\.modo=api\s*$') {
    Bien 'config.properties ya tenía bd.modo=api'
} elseif ($Simular) {
    Write-Host '   (simulado) bd.modo=jdbc -> bd.modo=api en config.properties' -ForegroundColor DarkYellow
} else {
    $texto = $texto -replace '(?m)^bd\.modo=\w+', 'bd.modo=api'
    [System.IO.File]::WriteAllText($ConfigPublica, $texto, (New-Object System.Text.UTF8Encoding($false)))
    Bien 'config.properties: bd.modo=api'
}

Write-Host ''
Write-Host 'Listo. Ahora:' -ForegroundColor Cyan
Write-Host '  1. Ejecuta la app (Lanzador): debe decir "Datos en: Supabase por HTTPS".'
Write-Host '  2. Haz commit y push de config.properties para que la universidad lo reciba con git pull.'
Write-Host '  3. En la universidad no hace falta config.local.properties ni la contraseña de la base.'
Terminar 0
