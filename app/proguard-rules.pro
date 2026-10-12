-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers @kotlinx.serialization.Serializable class com.weatherclone.app.** {
    *** Companion;
    kotlinx.serialization.KSerializer serializer(...);
}
-keep class com.weatherclone.app.**$$serializer { *; }
-dontwarn okhttp3.internal.platform.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**
