# Regras conservadoras para manter contratos JSON/Retrofit estáveis enquanto
# habilitamos R8 no release. As classes de dados continuam intactas; o restante
# do app pode ser otimizado e reduzido normalmente.
-keepattributes Signature
-keepattributes RuntimeVisibleAnnotations
-keepattributes RuntimeInvisibleAnnotations
-keepattributes RuntimeVisibleParameterAnnotations
-keepattributes RuntimeInvisibleParameterAnnotations
-keepattributes AnnotationDefault

-keep class com.pontocafe.app.data.** { *; }

# sherpa-onnx expõe classes Kotlin/Java ligadas a JNI. Mantemos esse namespace
# para evitar que R8 renomeie/remova símbolos necessários ao runtime neural.
-keep class com.k2fsa.sherpa.onnx.** { *; }

# Preserva campos explicitamente serializados pelo Gson caso novos DTOs sejam
# movidos para fora do pacote data no futuro.
-keepclassmembers,allowobfuscation class * {
    @com.google.gson.annotations.SerializedName <fields>;
}
