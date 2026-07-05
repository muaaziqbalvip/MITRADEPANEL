# MITV ProGuard rules
-keep class com.mitv.master.data.model.** { *; }
-keepattributes Signature
-keepattributes *Annotation*
-dontwarn com.google.firebase.**
-keep class androidx.media3.** { *; }
