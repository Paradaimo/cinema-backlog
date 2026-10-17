# Kotlinx serialization
-keepclassmembers class kotlinx.serialization.json.** { *** Companion; }
-keepclasseswithmembers class kotlinx.serialization.json.** { kotlinx.serialization.KSerializer serializer(...); }
-keep,includedescriptorclasses class com.cb.backlog.**$$serializer { *; }
-keepclassmembers class com.cb.backlog.** { *** Companion; }
-keepclasseswithmembers class com.cb.backlog.** { kotlinx.serialization.KSerializer serializer(...); }

# Retrofit
-keepattributes Signature, InnerClasses, EnclosingMethod, RuntimeVisibleAnnotations, RuntimeVisibleParameterAnnotations, AnnotationDefault
-keepclassmembers,allowshrinking,allowobfuscation interface * { @retrofit2.http.* <methods>; }
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn javax.annotation.**
