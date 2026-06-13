# Only keep Android framework entry points (referenced in manifest)
-keep class com.jhopanstore.vpnhospot.ProxyService { *; }
-keep class com.jhopanstore.vpnhospot.MainActivity { *; }

# Keep enum names for debugging
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}
