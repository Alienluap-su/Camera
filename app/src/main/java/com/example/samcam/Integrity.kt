package com.example.samcam

import android.content.Context
import android.content.pm.PackageManager
import android.content.pm.Signature
import android.os.Build
import java.security.MessageDigest

object Integrity {
    private fun hex(b: ByteArray): String {
        val sb = StringBuilder()
        for (x in b) sb.append("%02x".format(x))
        return sb.toString()
    }

    fun ok(ctx: Context): Boolean {
        val expected = BuildConfig.CERT_SHA.trim().lowercase()
        if (BuildConfig.DEBUG || expected.isEmpty()) return true
        return try {
            val pm = ctx.packageManager
            val list = ArrayList<Signature>()
            if (Build.VERSION.SDK_INT >= 28) {
                val si = pm.getPackageInfo(ctx.packageName, PackageManager.GET_SIGNING_CERTIFICATES).signingInfo ?: return false
                val arr = if (si.hasMultipleSigners()) si.apkContentsSigners else si.signingCertificateHistory
                if (arr != null) list.addAll(arr)
            } else {
                @Suppress("DEPRECATION")
                val arr = pm.getPackageInfo(ctx.packageName, PackageManager.GET_SIGNATURES).signatures
                if (arr != null) list.addAll(arr)
            }
            val md = MessageDigest.getInstance("SHA-256")
            list.any { hex(md.digest(it.toByteArray())) == expected }
        } catch (e: Exception) {
            false
        }
    }
}
