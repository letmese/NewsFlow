# Proguard rules for NewsFlow
-keepattributes Signature
-keepattributes *Annotation*
-keep class com.newsaggregator.** { *; }
-keep class com.squareup.picasso.** { *; }
-dontwarn com.squareup.picasso.**
