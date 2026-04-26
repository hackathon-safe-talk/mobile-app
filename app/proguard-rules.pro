# ═══════════════════════════════════════════════════════════════════
# SafeTalk ProGuard / R8 Rules
# ═══════════════════════════════════════════════════════════════════

# ── Debugging ────────────────────────────────────────────────────
# Keep line numbers for crash reports
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# ── ONNX Runtime ─────────────────────────────────────────────────
# ONNX Runtime uses JNI and reflection internally
-keep class ai.onnxruntime.** { *; }
-keepclassmembers class ai.onnxruntime.** { *; }
-dontwarn ai.onnxruntime.**

# ── Room Database ────────────────────────────────────────────────
-keep class * extends androidx.room.RoomDatabase { *; }
-keep @androidx.room.Entity class * { *; }
-keep @androidx.room.Dao interface * { *; }

# ── Kotlin Serialization ────────────────────────────────────────
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt

-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# Keep serializable model classes
-keep,includedescriptorclasses class com.snow.safetalk.**$$serializer { *; }
-keepclassmembers class com.snow.safetalk.** {
    *** Companion;
}
-keepclasseswithmembers class com.snow.safetalk.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# ── NotificationListenerService ──────────────────────────────────
# System binds to this by class name — must not be renamed
-keep class com.snow.safetalk.telegram.TelegramNotificationListenerService { *; }

# ── BroadcastReceivers ───────────────────────────────────────────
# System delivers intents by class name
-keep class com.snow.safetalk.sms.SmsReceiver { *; }
-keep class com.snow.safetalk.protection.BootReceiver { *; }
-keep class com.snow.safetalk.protection.RestartReceiver { *; }

# ── Services ─────────────────────────────────────────────────────
-keep class com.snow.safetalk.protection.ProtectionForegroundService { *; }
-keep class com.snow.safetalk.protection.ProtectionJobService { *; }

# ── Application class ───────────────────────────────────────────
-keep class com.snow.safetalk.SafeTalkApp { *; }

# ── Compose ──────────────────────────────────────────────────────
# Compose stability configuration — keep data classes for Compose stability
-dontwarn androidx.compose.**

# ── DataStore ────────────────────────────────────────────────────
-keepclassmembers class * extends androidx.datastore.preferences.protobuf.GeneratedMessageLite {
    <fields>;
}

# ── General Android ──────────────────────────────────────────────
-keep class * extends android.app.Activity { *; }
-keep class * extends android.app.Service { *; }
-keep class * extends android.content.BroadcastReceiver { *; }