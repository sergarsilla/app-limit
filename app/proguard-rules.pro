# Debug logging is compiled out of release builds.
-assumenosideeffects class android.util.Log {
    public static int d(...);
}
