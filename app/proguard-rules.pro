# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface
# class:
#-keepclassmembers class fqcn.of.javascript.interface.for.webview {
#   public *;
#}

# Uncomment this to preserve the line number information for
# debugging stack traces.
#-keepattributes SourceFile,LineNumberTable

# If you keep the line number information, uncomment this to
# hide the original source file name.
#-renamesourcefileattribute SourceFile

# Preserve Kotlin Coroutines
-keepclassmembers class kotlinx.coroutines.** { *; }

# Keep Google Wearable and ChannelClient-related classes
-keep class com.google.android.gms.wearable.** { *; }

# Keep file transfer and communication-related classes
-keepclassmembers class * {
    @com.google.android.gms.wearable.MessageApi$MessageListener <methods>;
}
-keep class com.mobilewizards.logging_app.** { *; }

# Prevent stripping out Serializable classes
-keepclassmembers class * implements java.io.Serializable { *; }

# Prevent obfuscation of Parcelable implementation
-keepclassmembers class * implements android.os.Parcelable {
    public static final android.os.Parcelable$Creator *;
    public void writeToParcel(android.os.Parcel, int);
}

# Prevent obfuscation of annotated methods and fields
-keepattributes *Annotation*

# Keep Android components (Activity, Service, BroadcastReceiver)
-keep public class * extends android.app.Activity
-keep public class * extends android.app.Service
-keep public class * extends android.content.BroadcastReceiver

# Keep UI Components
-keep class android.widget.** { *; }
-keep class android.view.** { *; }
-keep class com.google.android.material.snackbar.Snackbar { *; }

# Prevent stripping of reflection and runtime intent calls
-keepclassmembers class * {
    @androidx.annotation.Nullable <methods>;
    @androidx.annotation.NonNull <methods>;
    void *(android.content.Intent);
    void *(android.os.Bundle);
}

# Preserve GlobalNotification class
-keep class com.mobilewizards.logging_app.GlobalNotification { *; }

# Preserve LoggingService and MessageListenerService
-keep class com.mobilewizards.logging_app.MessageListenerService { *; }

# Preserve RecyclerView Adapter
-keep class com.mobilewizards.logging_app.SatelliteAdapter { *; }
-keepclassmembers class com.mobilewizards.logging_app.SatelliteAdapter$SatelliteViewHolder { *; }

# Preserve file-related classes and methods
-keepclassmembers class * {
    public java.io.File *(...);
    public java.io.InputStream *(...);
    public java.io.OutputStream *(...);
}

# Preserve File Operations (java.io and java.nio)
-keep class java.io.File { *; }
-keep class java.nio.file.Files { *; }
-keep class java.nio.file.attribute.BasicFileAttributes { *; }

# Preserve Context and Intent Reflection
-keepclassmembers class android.content.Context {
    public * *(android.content.Intent);
}
-keepclassmembers class android.app.Activity {
    public * *(android.content.Intent);
}

# Preserve MediaStore-related classes
-keep class android.provider.MediaStore { *; }
-keep class android.provider.MediaStore$Downloads { *; }

# Preserve RecyclerView and LayoutManager
-keep class androidx.recyclerview.widget.RecyclerView { *; }
-keep class androidx.recyclerview.widget.LinearLayoutManager { *; }

# ---------------------------
# Preserve Dialogs and LayoutInflater
# ---------------------------
-keep class android.app.AlertDialog { *; }
-keep class android.view.LayoutInflater { *; }
-keep class android.widget.TextView { *; }
-keep class android.widget.Button { *; }
-keep class android.widget.ImageButton { *; }

# Prevent obfuscation of reflection in Wearable methods
-keepclassmembers class * {
    void *(com.google.android.gms.wearable.ChannelClient$Channel, ...);
    void *(com.google.android.gms.wearable.MessageClient, ...);
}

# Preserve NodeClient methods
-keep class com.google.android.gms.wearable.NodeClient { *; }

# Preserve Toast messages (UI consistency)
-keep class android.widget.Toast { *; }

# Preserve CSV and File Streaming Operations
-keepclassmembers class java.io.FileOutputStream { *; }
-keepclassmembers class java.io.InputStream { *; }
-keepclassmembers class java.security.MessageDigest { *; }

# Preserve BuildConfig
-keep class com.mobilewizards.logging_app.BuildConfig { *; }