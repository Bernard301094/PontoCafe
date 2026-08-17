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

# Preserva campos explicitamente serializados pelo Gson caso novos DTOs sejam
# movidos para fora do pacote data no futuro.
-keepclassmembers,allowobfuscation class * {
    @com.google.gson.annotations.SerializedName <fields>;
}
