# ============================================================
# MoonLink ProGuard / R8 混淆规则
# ============================================================
# 本文件包含项目依赖库所需的 keep / dontwarn 规则。
# 使用时配合 proguard-android-optimize.txt（已在 build.gradle 中配置）。
# ============================================================

# ---- 全局：不禁用优化，仅禁用混淆（类/成员重命名） ----
-dontobfuscate

# ============================================================
# 项目自有代码
# ============================================================
-keep class com.limelight.binding.input.evdev.* {*;}
-keep class com.limelight.nvstream.jni.* {*;}

# MoonLink 新增模块 — 保留设备/串流/导航/设置/主题/VPN 等公开类
-keep class com.alexclin.moonlink.android.** { *; }

# ============================================================
# Android / Kotlin 基础
# ============================================================
-keepattributes *Annotation*
-keepattributes Signature
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

-keep class kotlin.Metadata { *; }
-dontwarn kotlin.**
-keep class kotlin.coroutines.Continuation { *; }

-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}
-keepclassmembers class * implements android.os.Parcelable {
    public static final ** CREATOR;
}

# ============================================================
# AndroidX / Jetpack
# ============================================================
# Compose
-dontwarn androidx.compose.**
-keep class androidx.compose.** { *; }

# Navigation Compose
-keep class androidx.navigation.compose.** { *; }

# Lifecycle
-keep class androidx.lifecycle.** { *; }
-dontwarn androidx.lifecycle.**

# AppCompat / Activity
-keep class androidx.appcompat.** { *; }
-dontwarn androidx.appcompat.**

# Splash Screen
-keep class androidx.core.splashscreen.** { *; }

# ConstraintLayout
-keep class androidx.constraintlayout.** { *; }

# RecyclerView / CardView
-keep class androidx.recyclerview.** { *; }
-keep class androidx.cardview.** { *; }

# Preference
-keep class androidx.preference.** { *; }

# ProcessLifecycleOwner
-keep class androidx.lifecycle.ProcessLifecycleOwner { *; }

# ============================================================
# OkHttp / Okio
# ============================================================
-dontwarn okhttp3.**
-dontwarn okio.**
-keep class okhttp3.** { *; }
-keep class okio.** { *; }
-keep class sun.misc.Unsafe {*;}
-dontwarn java.nio.file.*
-dontwarn org.codehaus.mojo.animal_sniffer.IgnoreJRERequirement

# ============================================================
# BouncyCastle（用于 TLS / 加密）
# ============================================================
-keep class org.bouncycastle.jcajce.provider.asymmetric.* {*;}
-keep class org.bouncycastle.jcajce.provider.asymmetric.util.* {*;}
-keep class org.bouncycastle.jcajce.provider.asymmetric.rsa.* {*;}
-keep class org.bouncycastle.jcajce.provider.digest.** {*;}
-keep class org.bouncycastle.jcajce.provider.symmetric.** {*;}
-keep class org.bouncycastle.jcajce.spec.* {*;}
-keep class org.bouncycastle.jce.** {*;}
-keep class org.bouncycastle.** { *; }
-dontwarn javax.naming.**
-dontwarn org.bouncycastle.**

# ============================================================
# Gson
# ============================================================
-keep class com.google.gson.** { *; }
-keepclassmembers,allowobfuscation class * {
    @com.google.gson.annotations.SerializedName <fields>;
}
-keep,allowobfuscation,allowshrinking class com.google.gson.reflect.TypeToken
-keep,allowobfuscation,allowshrinking class * extends com.google.gson.reflect.TypeToken

# ============================================================
# Glide
# ============================================================
-keep public class * implements com.bumptech.glide.module.GlideModule
-keep class * extends com.bumptech.glide.module.AppGlideModule {
    <init>(...);
}
-keep class com.bumptech.glide.load.data.ParcelFileDescriptorRewinder$InternalRewinder { *** rewind(); }
-keep public enum com.bumptech.glide.load.ImageHeaderParser$** {
    **[] $VALUES;
    public *;
}
-keep class com.bumptech.glide.** { *; }
-dontwarn com.bumptech.glide.**

# ============================================================
# Firebase
# ============================================================
-keep class com.google.firebase.** { *; }
-dontwarn com.google.firebase.**
-keep class com.google.android.gms.** { *; }
-dontwarn com.google.android.gms.**

# Crashlytics / NDK
-keepattributes *Annotation*
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# ============================================================
# CameraX + ML Kit
# ============================================================
-keep class androidx.camera.** { *; }
-dontwarn androidx.camera.**
-keep class com.google.mlkit.** { *; }
-dontwarn com.google.mlkit.**

# ============================================================
# jMDNS（mDNS 设备发现）
# ============================================================
-dontwarn javax.jmdns.**
-keep class javax.jmdns.** { *; }

# ============================================================
# jcodec（视频解码）
# ============================================================
-keep class org.jcodec.** { *; }
-dontwarn org.jcodec.**

# ============================================================
# Seismic（地震传感器）
# ============================================================
-keep class com.squareup.seismic.** { *; }

# ============================================================
# FlexboxLayout
# ============================================================
-keep class com.google.android.flexbox.** { *; }

# ============================================================
# TVProvider
# ============================================================
-keep class androidx.tvprovider.** { *; }

# ============================================================
# Coroutines
# ============================================================
-dontwarn kotlinx.coroutines.**
-keep class kotlinx.coroutines.** { *; }

# ============================================================
# SLF4J（日志门面）
# ============================================================
-dontwarn org.slf4j.**
