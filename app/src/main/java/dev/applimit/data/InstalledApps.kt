package dev.applimit.data

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas

data class InstalledApp(val pkg: String, val label: String)

/** Apps with a launcher icon, found through the manifest's <queries> block rather than QUERY_ALL_PACKAGES. */
fun Context.launcherApps(): List<InstalledApp> {
    val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
    return packageManager.queryIntentActivities(intent, PackageManager.ResolveInfoFlags.of(0))
        .map { it.activityInfo.packageName }
        .distinct()
        .filter { it != packageName }
        .map { InstalledApp(it, appLabel(it)) }
}

fun Context.appLabel(pkg: String): String = try {
    packageManager.getApplicationLabel(packageManager.getApplicationInfo(pkg, 0)).toString()
} catch (_: PackageManager.NameNotFoundException) {
    pkg
}

fun Context.appIcon(pkg: String, sizePx: Int): Bitmap? = try {
    val drawable = packageManager.getApplicationIcon(pkg)
    Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888).also {
        drawable.setBounds(0, 0, sizePx, sizePx)
        drawable.draw(Canvas(it))
    }
} catch (_: PackageManager.NameNotFoundException) {
    null
}
