package com.m3u.processor.likable

import com.google.devtools.ksp.KspExperimental
import com.google.devtools.ksp.getDeclaredProperties
import com.google.devtools.ksp.isAnnotationPresent
import com.google.devtools.ksp.processing.CodeGenerator
import com.google.devtools.ksp.processing.KSPLogger
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.processing.SymbolProcessor
import com.google.devtools.ksp.symbol.KSAnnotated
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSVisitorVoid
import com.google.devtools.ksp.symbol.Modifier
import com.google.devtools.ksp.validate
import com.m3u.annotation.Exclude
import com.m3u.annotation.Likable
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.KModifier
import com.squareup.kotlinpoet.ParameterSpec
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.TypeName
import com.squareup.kotlinpoet.asTypeName
import com.squareup.kotlinpoet.ksp.toTypeName
import com.squareup.kotlinpoet.ksp.writeTo

/**
 * Processador KSP que gera funções de comparação infix para classes anotadas com @Likable.
 * 
 * Gera as seguintes funções de extensão infix:
 * - like / unlike
 * - belong / notbelong (para instância em coleção)
 * - hold / nothold (para coleção contendo elemento)
 * 
 * Apenas classes data são processadas. Propriedades marcadas com @Exclude são ignoradas.
 */
class LikableSymbolProcessor(
    private val logger: KSPLogger,
    private val codeGenerator: CodeGenerator
) : SymbolProcessor {

    override fun process(resolver: Resolver): List<KSAnnotated> {
        val annotationName = requireNotNull(Likable::class.qualifiedName) {
            "Não foi possível obter o nome qualificado da anotação @Likable"
        }

        val symbols = resolver.getSymbolsWithAnnotation(annotationName)

        // Filtra símbolos inválidos que precisam de mais rodadas de processamento
        val unableToProcess = symbols.filterNot { it.validate() }.toList()

        symbols
            .filterIsInstance<KSClassDeclaration>()
            .forEach { it.accept(Visitor(), Unit) }

        return unableToProcess
    }

    private inner class Visitor : KSVisitorVoid() {

        @OptIn(KspExperimental::class)
        override fun visitClassDeclaration(classDeclaration: KSClassDeclaration, data: Unit) {
            // Verifica se é uma data class
            if (Modifier.DATA !in classDeclaration.modifiers) {
                logger.error(
                    message = "@Likable só pode ser aplicada em data classes",
                    symbol = classDeclaration
                )
                return
            }

            val packageName = classDeclaration.packageName.asString()
            val classSimpleName = classDeclaration.simpleName.asString()
            val fileName = "${classSimpleName}LikeablePlugins"

            val typeName: TypeName = classDeclaration.asType(emptyList()).toTypeName()

            // Coleta propriedades públicas não excluídas
            val relevantProperties = classDeclaration
                .getDeclaredProperties()
                .filter { property ->
                    !property.isAnnotationPresent(Exclude::class)
                }
                .toList()

            if (relevantProperties.isEmpty()) {
                logger.warn(
                    message = "Nenhuma propriedade relevante encontrada para comparação em @Likable ($classSimpleName)",
                    symbol = classDeclaration
                )
            }

            val fileSpec = FileSpec.builder(packageName, fileName)
                .addFunction(createLikeFunction(typeName, relevantProperties))
                .addFunction(createUnlikeFunction(typeName))
                .addFunction(createBelongFunction(typeName))
                .addFunction(createNotBelongFunction(typeName))
                .addFunction(createHoldFunction(typeName))
                .addFunction(createNotHoldFunction(typeName))
                .build()

            fileSpec.writeTo(codeGenerator, aggregating = false)
        }

        private fun createLikeFunction(
            receiver: TypeName,
            properties: List<com.google.devtools.ksp.symbol.KSPropertyDeclaration>
        ) = FunSpec.builder("like")
            .receiver(receiver)
            .addModifiers(KModifier.INFIX)
            .addParameter("other", receiver)
            .returns(Boolean::class)
            .addKdoc("Compara esta instância com outra usando todas as propriedades não excluídas.")
            .addStatement("return " + buildComparisonExpression(properties, "other"))
            .build()

        private fun createUnlikeFunction(receiver: TypeName) = FunSpec.builder("unlike")
            .receiver(receiver)
            .addModifiers(KModifier.INFIX)
            .addParameter("other", receiver)
            .returns(Boolean::class)
            .addKdoc("Retorna true se esta instância for diferente da outra (negação de like).")
            .addStatement("return !this.like(other)")
            .build()

        private fun createBelongFunction(receiver: TypeName) = FunSpec.builder("belong")
            .receiver(receiver)
            .addModifiers(KModifier.INFIX)
            .addParameter(
                "collection",
                ClassName("kotlin.collections", "Collection").parameterizedBy(receiver)
            )
            .returns(Boolean::class)
            .addKdoc("Verifica se esta instância está presente na coleção (comparação via like).")
            .addStatement("return collection.any { it like this }")
            .build()

        private fun createNotBelongFunction(receiver: TypeName) = FunSpec.builder("notbelong")
            .receiver(receiver)
            .addModifiers(KModifier.INFIX)
            .addParameter(
                "collection",
                ClassName("kotlin.collections", "Collection").parameterizedBy(receiver)
            )
            .returns(Boolean::class)
            .addKdoc("Verifica se esta instância NÃO está presente na coleção.")
            .addStatement("return collection.none { it like this }")
            .build()

        private fun createHoldFunction(receiver: TypeName) = FunSpec.builder("hold")
            .receiver(
                ClassName("kotlin.collections", "Collection").parameterizedBy(receiver)
            )
            .addModifiers(KModifier.INFIX)
            .addParameter("element", receiver)
            .returns(Boolean::class)
            .addKdoc("Verifica se a coleção contém o elemento (comparação via like).")
            .addStatement("return this.any { it like element }")
            .build()

        private fun createNotHoldFunction(receiver: TypeName) = FunSpec.builder("nothold")
            .receiver(
                ClassName("kotlin.collections", "Collection").parameterizedBy(receiver)
            )
            .addModifiers(KModifier.INFIX)
            .addParameter("element", receiver)
            .returns(Boolean::class)
            .addKdoc("Verifica se a coleção NÃO contém o elemento.")
            .addStatement("return this.none { it like element }")
            .build()

        private fun buildComparisonExpression(
            properties: List<com.google.devtools.ksp.symbol.KSPropertyDeclaration>,
            otherParam: String
        ): String = buildString {
            if (properties.isEmpty()) {
                append("true")
                return@buildString
            }

            properties.forEachIndexed { index, prop ->
                val name = prop.simpleName.asString()
                append("this.$name == $otherParam.$name")
                if (index < properties.lastIndex) {
                    append(" && ")
                }
            }
        }
    }
}