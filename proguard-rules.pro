# General ProGuard settings
-dontwarn
-dontnote
-repackageclasses 'com.example.addon.obf'
-allowaccessmodification
-overloadaggressively

# Keep the main entry point
-keep public class com.example.addon.Dune {
    public void onInitialize();
    public void onRegisterCategories();
    public java.lang.String getPackage();
}

# Keep all methods annotated with EventHandler (Orbit)
-keepclassmembers class * {
    @meteordevelopment.orbit.EventHandler *;
}

# Keep all Modules so Meteor can register them via reflection
-keep public class * extends meteordevelopment.meteorclient.systems.modules.Module {
    *;
}

# Keep Mixins (Fabric needs these names to match `mixins.json`)
-keep class com.example.addon.mixin.** { *; }

# Keep Meteor Client classes (don't obfuscate dependencies)
-keep class meteordevelopment.meteorclient.** { *; }

# Keep Minecraft/Fabric classes
-keep class net.minecraft.** { *; }
-keep class net.fabricmc.** { *; }

# Keep Java runtime classes (basic, might need more depending on usage)
-keep class java.** { *; }
-keep class javax.** { *; }
-keep class jdk.** { *; }

