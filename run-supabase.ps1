param()

$envFile = Join-Path $PSScriptRoot '.env'
if (-not (Test-Path -LiteralPath $envFile)) {
    throw 'Copy .env.example to .env, then set DATABASE_PASSWORD and JWT_SECRET before running this script.'
}

Get-Content -LiteralPath $envFile | ForEach-Object {
    $line = $_.Trim()
    if ($line -and -not $line.StartsWith('#')) {
        $separator = $line.IndexOf('=')
        if ($separator -gt 0) {
            $name = $line.Substring(0, $separator).Trim()
            $value = $line.Substring($separator + 1).Trim().Trim('"').Trim("'")
            [Environment]::SetEnvironmentVariable($name, $value, 'Process')
        }
    }
}

& (Join-Path $PSScriptRoot 'mvnw.cmd') spring-boot:run
exit $LASTEXITCODE
