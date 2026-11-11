# LiteRT-LM ka JNI surface R8 se hata na diya jaye (release build ke liye)
-keep class com.google.ai.edge.litertlm.** { *; }
-keepclassmembers class com.google.ai.edge.litertlm.** { *; }
-dontwarn com.google.ai.edge.litertlm.**
