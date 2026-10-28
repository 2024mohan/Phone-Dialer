# Tink references optional dependencies that are not used on Android.
-dontwarn com.google.errorprone.annotations.**
-dontwarn javax.annotation.**
-dontwarn com.google.api.client.**
-dontwarn org.joda.time.**
-dontwarn com.google.crypto.tink.**
# Tink loads key managers and protos reflectively.
-keep class com.google.crypto.tink.** { *; }
-keep class * extends com.google.protobuf.GeneratedMessageLite { *; }
