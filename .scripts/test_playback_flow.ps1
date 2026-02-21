#!/usr/bin/env pwsh
# Script de Teste: Validação do Fluxo de Reprodução

Write-Host "=== TESTE DE FLUXO DE REPRODUÇÃO ===" -ForegroundColor Cyan
Write-Host ""

# 1. Limpar logs anteriores
Write-Host "[1/5] Limpando logs anteriores..." -ForegroundColor Yellow
adb logcat -c

# 2. Iniciar captura de logs
Write-Host "[2/5] Iniciando captura de logs..." -ForegroundColor Yellow
$logFile = "test_playback_$(Get-Date -Format 'yyyyMMdd_HHmmss').log"
Start-Job -ScriptBlock { adb logcat > $using:logFile }
Start-Sleep -Seconds 2

# 3. Instruções para o usuário
Write-Host ""
Write-Host "[3/5] AÇÕES NECESSÁRIAS:" -ForegroundColor Green
Write-Host "  1. Abra o app M3U Extension no dispositivo"
Write-Host "  2. Importe uma lista de canais (ou use uma existente)"
Write-Host "  3. Aguarde a conclusão da extração"
Write-Host "  4. Abra o app M3U Universal"
Write-Host "  5. Tente reproduzir um canal"
Write-Host ""
Write-Host "Pressione ENTER quando terminar os testes..." -ForegroundColor Cyan
Read-Host

# 4. Parar captura de logs
Write-Host "[4/5] Parando captura de logs..." -ForegroundColor Yellow
Get-Job | Stop-Job
Get-Job | Remove-Job

# 5. Analisar logs
Write-Host "[5/5] Analisando logs..." -ForegroundColor Yellow
Write-Host ""

# Verificar registro de headers
Write-Host "=== REGISTRO DE HEADERS ===" -ForegroundColor Cyan
Select-String -Path $logFile -Pattern "Registrando.*canais no JsonHeaderRegistry" | ForEach-Object { Write-Host $_.Line -ForegroundColor Green }
Select-String -Path $logFile -Pattern "✓ Registrado headers para" | Select-Object -First 5 | ForEach-Object { Write-Host $_.Line -ForegroundColor Green }

Write-Host ""
Write-Host "=== RESOLUÇÃO DE HEADERS ===" -ForegroundColor Cyan
Select-String -Path $logFile -Pattern "HEADER RESOLUTION DEBUG" | ForEach-Object { Write-Host $_.Line -ForegroundColor Yellow }
Select-String -Path $logFile -Pattern "Dynamic headers from Registry" | ForEach-Object { Write-Host $_.Line -ForegroundColor Yellow }
Select-String -Path $logFile -Pattern "Using headers from JsonHeaderRegistry" | ForEach-Object { Write-Host $_.Line -ForegroundColor Green }
Select-String -Path $logFile -Pattern "No headers in Registry" | ForEach-Object { Write-Host $_.Line -ForegroundColor Red }

Write-Host ""
Write-Host "=== ERROS DE REPRODUÇÃO ===" -ForegroundColor Cyan
Select-String -Path $logFile -Pattern "ERROR|Exception|Failed" | Select-Object -First 10 | ForEach-Object { Write-Host $_.Line -ForegroundColor Red }

Write-Host ""
Write-Host "=== ROTAÇÃO DE IDENTIDADE ===" -ForegroundColor Cyan
Select-String -Path $logFile -Pattern "Detectado 403/401|Rodando identidade" | ForEach-Object { Write-Host $_.Line -ForegroundColor Magenta }

Write-Host ""
Write-Host "Log completo salvo em: $logFile" -ForegroundColor Cyan
Write-Host ""
Write-Host "=== DIAGNÓSTICO ===" -ForegroundColor Yellow

# Contadores
$registeredChannels = (Select-String -Path $logFile -Pattern "✓ Registrado headers para").Count
$headerResolutions = (Select-String -Path $logFile -Pattern "HEADER RESOLUTION DEBUG").Count
$registryHits = (Select-String -Path $logFile -Pattern "Using headers from JsonHeaderRegistry").Count
$registryMisses = (Select-String -Path $logFile -Pattern "No headers in Registry").Count

Write-Host "Canais registrados: $registeredChannels" -ForegroundColor $(if ($registeredChannels -gt 0) { "Green" } else { "Red" })
Write-Host "Tentativas de reprodução: $headerResolutions" -ForegroundColor $(if ($headerResolutions -gt 0) { "Green" } else { "Yellow" })
Write-Host "Headers encontrados no Registry: $registryHits" -ForegroundColor $(if ($registryHits -gt 0) { "Green" } else { "Red" })
Write-Host "Headers NÃO encontrados no Registry: $registryMisses" -ForegroundColor $(if ($registryMisses -gt 0) { "Red" } else { "Green" })

Write-Host ""
if ($registeredChannels -gt 0 -and $registryHits -gt 0) {
    Write-Host "✅ FLUXO FUNCIONANDO CORRETAMENTE!" -ForegroundColor Green
} elseif ($registeredChannels -gt 0 -and $registryMisses -gt 0) {
    Write-Host "⚠️ Headers registrados mas não encontrados - possível problema de URL mismatch" -ForegroundColor Yellow
} elseif ($registeredChannels -eq 0) {
    Write-Host "❌ Nenhum canal foi registrado - verificar importação" -ForegroundColor Red
} else {
    Write-Host "⚠️ Status indeterminado - revisar logs completos" -ForegroundColor Yellow
}
