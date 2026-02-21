$ErrorActionPreference = "Continue"
./gradlew :data:kspDebugKotlin 2>&1 | Tee-Object -FilePath error_output.txt
