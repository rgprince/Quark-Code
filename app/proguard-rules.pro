# Quark Code — release keep rules (R8 full mode).

# kotlinx.serialization: keep generated serializers and annotations.
-keepattributes *Annotation*, InnerClasses, Signature, EnclosingMethod
-keep @kotlinx.serialization.Serializable class * { *; }
-keepclassmembers class **$$serializer { *; }
-keepclasseswithmembernames class * {
    @kotlinx.serialization.Serializable <fields>;
}
-keepclasseswithmembers class * {
    @kotlinx.serialization.Serializable <methods>;
}

# DataStore preferences (protobuf-backed, keep generated code).
-keep class androidx.datastore.** { *; }

# OkHttp/Okio/Coil/WorkManager ship their own consumer rules; silence
# optional-platform warnings only.
-dontwarn org.bouncycastle.**
-dontwarn org.conscrypt.**
-dontwarn org.openjsse.**
-dontwarn java.lang.invoke.**
