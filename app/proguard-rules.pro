# ── ProGuard / R8 rules for DormMate ─────────────────────────────────────────
# Referenced by the `release` buildType in build.gradle. The build fails at
# configuration time if this file is missing.

# Keep line numbers for readable crash reports, hide the source file name.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# ── Firestore model classes ──────────────────────────────────────────────────
# Firestore deserializes via reflection, so field names and no-arg constructors
# must survive shrinking. Without this, every document read returns nulls.
-keepclassmembers class com.dormmate.model.** {
    <init>();
    <fields>;
    *** get*();
    void set*(***);
}
-keep class com.dormmate.model.** { *; }

# ── Firebase ─────────────────────────────────────────────────────────────────
-keepattributes Signature,*Annotation*,EnclosingMethod,InnerClasses
-keep class com.google.firebase.** { *; }
-dontwarn com.google.firebase.**

# ── Glide ────────────────────────────────────────────────────────────────────
-keep public class * implements com.bumptech.glide.module.GlideModule
-keep class * extends com.bumptech.glide.module.AppGlideModule { <init>(...); }
-keep public enum com.bumptech.glide.load.ImageHeaderParser$** {
    **[] $VALUES;
    public *;
}

# ── Gson ─────────────────────────────────────────────────────────────────────
-keep class com.google.gson.reflect.TypeToken { *; }
-keep class * extends com.google.gson.reflect.TypeToken

# ── AndroidX Navigation / Fragments instantiated by name ─────────────────────
-keep public class * extends androidx.fragment.app.Fragment
