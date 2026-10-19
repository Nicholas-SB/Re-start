# Rules for R8, which shrinks and optimizes the release build.

# youtubedl-android (the downloader) ships no rules of its own. It reads yt-dlp's answers
# into its classes with Jackson, by reflection, so keep its classes and Jackson whole.
-keep class com.yausername.** { *; }
-keep class com.fasterxml.jackson.** { *; }
-dontwarn com.fasterxml.jackson.**
-dontwarn org.apache.commons.compress.**

# Unpacking its Python on first use goes through Apache Commons Compress, which creates the
# classes for zip "extra fields" by reflection. Optimized, they stopped being creatable and
# the app crashed the first time the downloader started (For You, Start Mix, Download).
-keep class org.apache.commons.compress.** { *; }
