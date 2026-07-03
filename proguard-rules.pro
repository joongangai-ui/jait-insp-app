# WebView JS interface 보존(추후 FCM/네이티브 브리지 대비)
-keepclassmembers class * { @android.webkit.JavascriptInterface <methods>; }
