# Keep rules for the optimized release build.

# Encrypted storage (Tink)
-keep class com.google.crypto.tink.** { *; }
-dontwarn com.google.errorprone.annotations.**
-dontwarn javax.annotation.**
-dontwarn org.checkerframework.**

# Networking / image loading
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**
