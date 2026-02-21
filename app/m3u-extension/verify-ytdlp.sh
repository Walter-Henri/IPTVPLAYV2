#!/bin/bash

# Script de verificação do binário yt-dlp
# Este script verifica se o binário está presente e configurado corretamente

set -e

PROJECT_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
BINARY_PATH="$PROJECT_ROOT/yt-dlp"

echo "==================================="
echo "Verificação do Binário yt-dlp"
echo "==================================="
echo ""

# 1. Verificar se o binário existe
echo "1. Verificando existência do binário..."
if [ -f "$BINARY_PATH" ]; then
    echo "   ✓ Binário encontrado: $BINARY_PATH"
else
    echo "   ✗ ERRO: Binário não encontrado em: $BINARY_PATH"
    echo ""
    echo "   Para corrigir, execute:"
    echo "   cd $PROJECT_ROOT"
    echo "   wget https://github.com/yt-dlp/yt-dlp/releases/latest/download/yt-dlp"
    echo "   chmod +x yt-dlp"
    exit 1
fi

# 2. Verificar permissões de execução
echo ""
echo "2. Verificando permissões de execução..."
if [ -x "$BINARY_PATH" ]; then
    echo "   ✓ Binário tem permissão de execução"
else
    echo "   ⚠ Binário não tem permissão de execução"
    echo "   Aplicando chmod +x..."
    chmod +x "$BINARY_PATH"
    if [ -x "$BINARY_PATH" ]; then
        echo "   ✓ Permissão aplicada com sucesso"
    else
        echo "   ✗ ERRO: Falha ao aplicar permissão"
        exit 1
    fi
fi

# 3. Verificar tamanho do arquivo
echo ""
echo "3. Verificando tamanho do arquivo..."
FILE_SIZE=$(stat -f%z "$BINARY_PATH" 2>/dev/null || stat -c%s "$BINARY_PATH" 2>/dev/null)
if [ "$FILE_SIZE" -gt 1000000 ]; then
    echo "   ✓ Tamanho do arquivo: $(numfmt --to=iec-i --suffix=B $FILE_SIZE 2>/dev/null || echo "$FILE_SIZE bytes")"
else
    echo "   ⚠ AVISO: Arquivo muito pequeno ($FILE_SIZE bytes)"
    echo "   O binário pode estar corrompido"
fi

# 4. Testar execução básica
echo ""
echo "4. Testando execução básica..."
if "$BINARY_PATH" --version >/dev/null 2>&1; then
    VERSION=$("$BINARY_PATH" --version | head -n1)
    echo "   ✓ Binário executável: $VERSION"
else
    echo "   ✗ ERRO: Falha ao executar o binário"
    echo "   O arquivo pode estar corrompido"
    exit 1
fi

# 5. Testar extração de URL de exemplo
echo ""
echo "5. Testando extração de URL (exemplo)..."
echo "   Comando: yt-dlp -g -f best 'https://www.youtube.com/watch?v=jNQXAC9IVRw'"
echo "   (Este teste pode falhar se o vídeo não estiver disponível)"
if timeout 30 "$BINARY_PATH" -g -f best "https://www.youtube.com/watch?v=jNQXAC9IVRw" >/dev/null 2>&1; then
    echo "   ✓ Extração de URL funcionando"
else
    echo "   ⚠ AVISO: Teste de extração falhou (pode ser esperado)"
    echo "   Isso não impede o uso da extensão"
fi

echo ""
echo "==================================="
echo "Verificação Concluída"
echo "==================================="
echo ""
echo "Resumo:"
echo "  - Binário: $BINARY_PATH"
echo "  - Tamanho: $(numfmt --to=iec-i --suffix=B $FILE_SIZE 2>/dev/null || echo "$FILE_SIZE bytes")"
echo "  - Versão: $VERSION"
echo ""
echo "✓ O binário yt-dlp está pronto para uso!"
