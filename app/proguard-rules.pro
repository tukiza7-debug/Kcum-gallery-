# Peraturan ProGuard / R8 untuk Kcum Gallery
# Glide, Room dan Media3 sudah membawa peraturan consumer mereka sendiri.

# Kekalkan kelas model Room
-keep class com.kcum.gallery.data.** { *; }

# Elak amaran duplikat META-INF
-dontwarn org.jetbrains.annotations.**
