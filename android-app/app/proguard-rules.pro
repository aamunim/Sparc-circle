# Preserve readable crash stack traces & deobfuscation mapping for Google Play Console
-keepattributes SourceFile,LineNumberTable,*Annotation*,InnerClasses,Signature,EnclosingMethod
-renamesourcefileattribute SourceFile

# Kotlinx Serialization official R8 rules
-dontnote kotlinx.serialization.AnnotationsKt
-keepclassmembers class kotlinx.serialization.json.** { *; }

-if @kotlinx.serialization.Serializable class **
-keepclassmembers class <1> {
    static <1>$Companion Companion;
}

-if @kotlinx.serialization.Serializable class ** {
    static **$* *;
}
-keepclassmembers class <2>$<3> {
    kotlinx.serialization.KSerializer serializer(...);
}

-if @kotlinx.serialization.Serializable class **
-keep,allowobfuscation,allowshrinking class <1>

-keep,includedescriptorclasses class org.sparc.studycircle.**$$serializer { *; }
-keepclassmembers class org.sparc.studycircle.** {
    ** Companion;
    kotlinx.serialization.KSerializer serializer(...);
}

# Keep SPARC domain models, enums & auth session intact for JSON persistence
-keep class org.sparc.studycircle.core.model.** { *; }
-keep class org.sparc.studycircle.core.data.SupabaseAuthSession { *; }
-keep class org.sparc.studycircle.core.data.SupabaseAuthSession$* { *; }
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# OkHttp / JVM platform optional warnings
-dontwarn okhttp3.internal.platform.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**
