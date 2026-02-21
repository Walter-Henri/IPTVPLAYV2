import os
import sys

def check_file_exists(path):
    if os.path.exists(path):
        print(f"✅ Encontrado: {path}")
        return True
    else:
        print(f"❌ NÃO ENCONTRADO: {path}")
        return False

def check_content(path, pattern):
    with open(path, 'r', encoding='utf-8') as f:
        content = f.read()
        if pattern in content:
            print(f"✅ Padrão encontrado em {os.path.basename(path)}: '{pattern}'")
            return True
        else:
            print(f"❌ PADRÃO NÃO ENCONTRADO em {os.path.basename(path)}: '{pattern}'")
            return False

print("=== VERIFICAÇÃO DO REPARO YOUTUBE EXTRACTION ===")

paths = [
    "app/m3u-extension/src/main/python/extractor_v2.py",
    "app/m3u-extension/src/main/python/streamlink_extractor.py",
    "app/m3u-extension/src/main/java/com/m3u/extension/logging/ExtractionLogger.kt",
    "app/m3u-extension/src/main/java/com/m3u/extension/ExtensionService.kt",
    "data/src/main/java/com/m3u/data/service/internal/PlayerManagerImpl.kt"
]

all_ok = True
for p in paths:
    abs_p = os.path.join(os.getcwd(), p)
    if not check_file_exists(abs_p):
        all_ok = False

if all_ok:
    print("\n--- Verificação de Conteúdo Crítico ---")
    check_content("app/m3u-extension/src/main/python/extractor_v2.py", "extract_with_streamlink")
    check_content("app/m3u-extension/src/main/java/com/m3u/extension/ExtensionService.kt", "kodiUrl")
    check_content("data/src/main/java/com/m3u/data/service/internal/PlayerManagerImpl.kt", "Origin")
    check_content("app/m3u-extension/src/main/java/com/m3u/extension/ui/MainActivity.kt", "VER RELATÓRIO DE EXTRAÇÃO")

print("\n=== FIM DA VERIFICAÇÃO ===")
