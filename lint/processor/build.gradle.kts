plugins {
    kotlin("jvm")
    alias(libs.plugins.com.google.devtools.ksp)
}

kotlin {
    jvmToolchain(21)
}

ksp {
    arg("autoserviceKsp.verify", "true")
    arg("autoserviceKsp.verbose", "true")
    arg("ksp.debug", "true")  // Adicionado para depuração de erros
}

dependencies {
    implementation(project(":lint:annotation"))

    implementation(libs.symbol.processing.api)
    implementation(libs.kotlinpoet)
    implementation(libs.kotlinpoet.ksp)
    implementation(libs.auto.service.annotations)

    ksp(libs.auto.service.ksp)
}
