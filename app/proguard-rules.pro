# MT Player 2 - R8 rules
# AndroidX (Compose, Media3, Room, DataStore, Lifecycle, Navigation) ship their own consumer rules.

# Keep readable stack traces for local crash reports (mapping.txt is uploaded as a CI artifact).
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# Manifest components are kept by AAPT rules; nothing here needs reflection.
