#!/bin/bash

# Script para compilar APKs específicos do projeto M3U IPTV Player
# APKs a serem gerados:
# 1. universal-universal-debug.apk (do módulo app:universal)
# 2. m3u-extension-debug.apk (do módulo app:m3u-extension)

set -e  # Parar em caso de erro

echo "=========================================="
echo "Build Script - M3U IPTV Player"
echo "=========================================="
echo ""

# Cores para output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# Verificar se estamos no diretório correto
if [ ! -f "settings.gradle.kts" ]; then
    echo -e "${RED}Erro: settings.gradle.kts não encontrado!${NC}"
    echo "Execute este script a partir do diretório raiz do projeto."
    exit 1
fi

echo -e "${YELLOW}Limpando builds anteriores...${NC}"
./gradlew clean || true

echo ""
echo -e "${YELLOW}Compilando app:universal (universal-universal-debug.apk)...${NC}"
./gradlew :app:universal:assembleDebug

echo ""
echo -e "${YELLOW}Compilando app:m3u-extension (m3u-extension-debug.apk)...${NC}"
./gradlew :app:m3u-extension:assembleDebug

echo ""
echo -e "${GREEN}=========================================="
echo "Build concluído com sucesso!"
echo "==========================================${NC}"
echo ""

# Localizar e listar os APKs gerados
echo -e "${YELLOW}APKs gerados:${NC}"
echo ""

# Universal APK
UNIVERSAL_APK=$(find app/universal -name "*universal-debug.apk" 2>/dev/null | head -1)
if [ -n "$UNIVERSAL_APK" ]; then
    echo -e "${GREEN}✓${NC} $UNIVERSAL_APK"
    ls -lh "$UNIVERSAL_APK"
else
    echo -e "${RED}✗ universal-universal-debug.apk NÃO ENCONTRADO${NC}"
fi

echo ""

# M3U Extension APK
M3U_EXT_APK=$(find app/m3u-extension -name "*debug.apk" 2>/dev/null | head -1)
if [ -n "$M3U_EXT_APK" ]; then
    echo -e "${GREEN}✓${NC} $M3U_EXT_APK"
    ls -lh "$M3U_EXT_APK"
else
    echo -e "${RED}✗ m3u-extension-debug.apk NÃO ENCONTRADO${NC}"
fi

echo ""
echo -e "${GREEN}Processo de build finalizado!${NC}"
