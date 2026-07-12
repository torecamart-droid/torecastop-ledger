# App-specific R8 rules. The libraries in use (Compose, Room, CameraX, ML Kit,
# Coil, Accompanist) all ship their own consumer rules, so nothing extra is
# needed for them. Add rules here only if a release build breaks at runtime.

# Keep release stack traces readable.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
