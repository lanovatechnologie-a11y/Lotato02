# Conserver les méthodes appelées depuis JavaScript via @JavascriptInterface
-keepclassmembers class com.lotato.pro.bridge.AndroidPrintBridge {
    public *;
}
-keep class com.lotato.pro.bridge.** { *; }
