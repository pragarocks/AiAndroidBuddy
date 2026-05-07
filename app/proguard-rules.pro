-keep class com.pocketpet.** { *; }
-keep class ai.onnxruntime.** { *; }
-keepclassmembers class * {
    @com.pocketpet.* <methods>;
}
-dontwarn com.microsoft.onnxruntime.**
