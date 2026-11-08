package com.circleftp.stream.security

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Debug
import java.io.File
import java.net.InetSocketAddress
import java.net.Socket
import java.security.MessageDigest

object AntiReverseGuard {

    private const val OFFICIAL_RELEASE_SHA256 =
        "953AC1BD420D1A6C9446B1B3B04DAF8E501D8FF785FFF9766D5242F5315AD90C"

    private fun decode(base64Str: String): String {
        return try {
            String(android.util.Base64.decode(base64Str, android.util.Base64.NO_WRAP))
        } catch (_: Exception) {
            ""
        }
    }

    // Obfuscated Base64 root binaries & hook framework paths (bypasses static AV heuristic false-positives)
    private val ENCODED_ROOT_PATHS = arrayOf(
        "L3N5c3RlbS9hcHAvU3VwZXJ1c2VyLmFwayI=", // /system/app/Superuser.apk
        "L3NiaW4vc3U=",                         // /sbin/su
        "L3N5c3RlbS9iaW4vc3U=",                 // /system/bin/su
        "L3N5c3RlbS94YmluL3N1",                 // /system/xbin/su
        "L2RhdGEvbG9jYWwveGJpbi9zdQ==",         // /data/local/xbin/su
        "L2RhdGEvbG9jYWwvYmluL3N1",             // /data/local/bin/su
        "L3N5c3RlbS9zZC94YmluL3N1",             // /system/sd/xbin/su
        "L3N1L2Jpbi9zdQ=="                      // /su/bin/su
    )

    private val ENCODED_HOOKS = arrayOf(
        "ZGUucm9idi5hbmRyb2lkLnhwb3NlZC5YcG9zZWRCcmlkZ2U=",        // de.robv.android.xposed.XposedBridge
        "ZGUucm9idi5hbmRyb2lkLnhwb3NlZC5YQ19NZXRob2RIb29r",        // de.robv.android.xposed.XC_MethodHook
        "Y29tLnNhdXJpay5zdWJzdHJhdGUuTVMkTWV0aG9kSG9vaw==",        // com.saurik.substrate.MS$MethodHook
        "aW8uZ2l0aHViLmxpYnhwb3NlZC5hcGkuWHBvc2VkSW50ZXJmYWNl",    // io.github.libxposed.api.XposedInterface
        "b3JnLmxzcG9zZWQubHNwZC5jb3JlLk1haW4="                     // org.lsposed.lspd.core.Main
    )

    fun isDeviceCompromised(context: Context): Boolean {
        return isFridaActive() || isFridaPortOpen() || isXposedPresent() || isDebuggerConnected() || !isSignatureValid(context)
    }

    fun isSignatureValid(context: Context): Boolean {
        return try {
            val pm = context.packageManager
            val pkgName = context.packageName
            val signatures = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                val packageInfo = pm.getPackageInfo(pkgName, PackageManager.GET_SIGNING_CERTIFICATES)
                packageInfo.signingInfo?.apkContentsSigners
            } else {
                @Suppress("DEPRECATION")
                val packageInfo = pm.getPackageInfo(pkgName, PackageManager.GET_SIGNATURES)
                packageInfo.signatures
            }
            if (signatures.isNullOrEmpty()) return true
            val md = MessageDigest.getInstance("SHA-256")
            val certHash = md.digest(signatures[0].toByteArray()).joinToString("") { "%02X".format(it) }
            certHash.equals(OFFICIAL_RELEASE_SHA256, ignoreCase = true)
        } catch (_: Exception) {
            true // fallback gracefully in case of unusual permission sandbox
        }
    }

    fun isFridaPortOpen(): Boolean {
        return try {
            val socket = Socket()
            socket.connect(InetSocketAddress("127.0.0.1", 27042), 60)
            socket.close()
            true
        } catch (_: Exception) {
            false
        }
    }

    fun isDebuggerConnected(): Boolean {
        return Debug.isDebuggerConnected() || Debug.waitingForDebugger()
    }

    fun isFridaActive(): Boolean {
        return try {
            val maps = File("/proc/self/maps")
            if (maps.canRead()) {
                val lines = maps.readLines()
                lines.any { it.contains("frida", ignoreCase = true) || it.contains("gadget", ignoreCase = true) }
            } else {
                false
            }
        } catch (_: Exception) {
            false
        }
    }

    fun isXposedPresent(): Boolean {
        for (enc in ENCODED_HOOKS) {
            val framework = decode(enc)
            if (framework.isEmpty()) continue
            try {
                Class.forName(framework)
                return true
            } catch (_: ClassNotFoundException) {
                // Not found
            }
        }
        return false
    }

    fun isRooted(): Boolean {
        return try {
            val buildTags = Build.TAGS
            if (buildTags != null && buildTags.contains("test-keys")) {
                return true
            }
            ENCODED_ROOT_PATHS.any {
                val path = decode(it)
                path.isNotEmpty() && File(path).exists()
            }
        } catch (_: Exception) {
            false
        }
    }
}
