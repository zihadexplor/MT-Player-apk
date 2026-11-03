# MT Player — R8 rules
# AndroidX (Compose, Media3, Lifecycle, Navigation) ship their own consumer rules.

# Keep readable stack traces for crash reports (mapping.txt is attached to CI artifacts).
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# Manifest-declared components are kept automatically; keep updater receivers explicit for clarity.
-keep class com.example.update.DownloadCompleteReceiver { <init>(); }
-keep class com.example.update.InstallResultReceiver { <init>(); }
