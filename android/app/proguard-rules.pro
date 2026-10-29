# LinkFuse keeps no reflection-based APIs; the VPN engine is plain Kotlin/Java IO.
-keepclassmembers class dev.linkfuse.app.** { *; }
-keep class dev.linkfuse.app.vpn.** { *; }
-keep class dev.linkfuse.app.service.** { *; }
-dontwarn kotlin.**
