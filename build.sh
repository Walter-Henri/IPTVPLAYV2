#!/bin/bash
set -e

# ========================================
# M3U Universal - Build Script
# ========================================
# Compila o APK unificado (Mobile + TV + Extension)
# Otimizado para Docker com PowerShell no Windows
# ========================================

echo "========================================="
echo "  M3U Universal Build Script"
echo "  Version: 2.0 (Unified)"
echo "========================================="
echo ""

# --- CONFIGURAÇÕES ---
KEYSTORE_PATH="/project/meu-app.keystore"
KEYSTORE_PASS="Wa97951211@"
KEY_ALIAS="meu_alias"
BUILD_TOOLS_VERSION="34.0.0"
APKSIGNER="$ANDROID_SDK_ROOT/build-tools/$BUILD_TOOLS_VERSION/apksigner"

# Verificar se o keystore existe
if [ ! -f "$KEYSTORE_PATH" ]; then
    echo "⚠️  AVISO: Keystore não encontrado em $KEYSTORE_PATH"
    echo "   O build continuará, mas o APK pode não estar assinado corretamente."
fi

# Garantir permissão de execução no gradlew (importante para volumes Windows)
echo "🔧 Configurando permissões..."
chmod +x ./gradlew 2>/dev/null || true

# Exibir informações do ambiente
echo ""
echo "📋 Informações do Ambiente:"
echo "   - Java Version: $(java -version 2>&1 | head -n 1)"
echo "   - Android SDK: $ANDROID_SDK_ROOT"
echo "   - Build Tools: $BUILD_TOOLS_VERSION"
echo "   - Working Dir: $(pwd)"
echo ""

# Limpar cache do Gradle
echo "--- 🧹 Limpando Cache do Gradle ---"
./gradlew clean --no-daemon --console=plain

echo ""
echo "--- 🚀 Compilando APK Release (Universal: Mobile + TV + Extension) ---"
echo ""

# Compilação via Gradle com injeção de propriedades de assinatura
# Flags otimizadas para evitar "Daemon disappeared" e melhorar performance
./gradlew :app:universal:assembleRelease \
    -Pandroid.injected.signing.store.file="$KEYSTORE_PATH" \
    -Pandroid.injected.signing.store.password="$KEYSTORE_PASS" \
    -Pandroid.injected.signing.key.alias="$KEY_ALIAS" \
    -Pandroid.injected.signing.key.password="$KEYSTORE_PASS" \
    --no-daemon \
    --console=plain \
    --stacktrace \
    -Dorg.gradle.jvmargs="-Xmx4g -XX:MaxMetaspaceSize=1g -XX:+UseG1GC" \
    -Dorg.gradle.parallel=true \
    -Dorg.gradle.caching=true

echo ""
echo "--- ✍️  Finalizando e Assinando APKs ---"
echo ""

# Função para localizar, assinar e verificar APKs
finalize_apk() {
    local module_path=$1
    local output_name=$2
    
    echo "🔍 Localizando APKs gerados em $module_path..."
    
    # Localizar todos os APKs gerados (incluindo splits ABI)
    local APK_DIR="$module_path/build/outputs/apk/release/"
    
    if [ ! -d "$APK_DIR" ]; then
        echo "⚠️  Diretório de output não encontrado: $APK_DIR"
        return 1
    fi
    
    # Contar APKs gerados
    local APK_COUNT=$(find "$APK_DIR" -name "*.apk" | wc -l)
    echo "📦 Encontrados $APK_COUNT APK(s)"
    
    # Processar cada APK
    local counter=1
    find "$APK_DIR" -name "*.apk" | while read -r APK_FILE; do
        local APK_BASENAME=$(basename "$APK_FILE")
        local APK_SIGNED="${APK_DIR}${APK_BASENAME%.apk}-signed.apk"
        
        echo ""
        echo "[$counter/$APK_COUNT] Processando: $APK_BASENAME"
        
        # Verificar se já está assinado
        if $APKSIGNER verify "$APK_FILE" 2>/dev/null; then
            echo "   ✅ Já assinado - copiando para output final"
            cp "$APK_FILE" "/project/$(basename "$APK_FILE")"
        else
            echo "   ✍️  Assinando APK..."
            $APKSIGNER sign \
                --ks "$KEYSTORE_PATH" \
                --ks-pass pass:"$KEYSTORE_PASS" \
                --ks-key-alias "$KEY_ALIAS" \
                --key-pass pass:"$KEYSTORE_PASS" \
                --out "$APK_SIGNED" \
                "$APK_FILE"
            
            # Verificar assinatura
            if $APKSIGNER verify "$APK_SIGNED" 2>/dev/null; then
                echo "   ✅ Assinatura verificada com sucesso!"
                cp "$APK_SIGNED" "/project/$(basename "$APK_SIGNED")"
            else
                echo "   ⚠️  Falha na verificação da assinatura"
            fi
        fi
        
        counter=$((counter + 1))
    done
    
    # Copiar APK universal (maior) para nome simplificado
    local UNIVERSAL_APK=$(find "$APK_DIR" -name "*universal*.apk" -o -name "*release.apk" | xargs ls -S 2>/dev/null | head -n 1)
    if [ -f "$UNIVERSAL_APK" ]; then
        echo ""
        echo "📱 Copiando APK Universal para: /project/$output_name"
        cp "$UNIVERSAL_APK" "/project/$output_name"
    fi
}

# Processar módulo universal (fusão de extension + universal)
echo "--- 📦 Processando APKs do Módulo Universal ---"
finalize_apk "app/universal" "M3U-Universal-Release.apk"

echo ""
echo "========================================="
echo "  ✨ BUILD CONCLUÍDO COM SUCESSO! ✨"
echo "========================================="
echo ""
echo "📂 APKs gerados em: /project/"
ls -lh /project/*.apk 2>/dev/null || echo "   (Nenhum APK encontrado na raiz)"
echo ""
echo "🎉 Pronto para instalação em:"
echo "   - Smartphones Android (API 26+)"
echo "   - Tablets Android"
echo "   - Android TV"
echo "   - Smart TVs Android"
echo ""
