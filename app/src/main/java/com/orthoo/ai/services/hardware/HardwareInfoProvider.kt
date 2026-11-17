package com.orthoo.ai.services.hardware

import android.app.ActivityManager
import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.opengl.GLES20
import android.os.Build
import android.os.Debug
import android.os.Environment
import android.os.StatFs
import com.orthoo.ai.data.model.CpuInfo
import com.orthoo.ai.data.model.DeviceInfo
import com.orthoo.ai.data.model.GpuInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.Inet4Address
import javax.microedition.khronos.egl.EGL10
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.egl.EGLContext

class HardwareInfoProvider(private val context: Context) {

    suspend fun collectDeviceInfo(): DeviceInfo = withContext(Dispatchers.IO) {
        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val memInfo = ActivityManager.MemoryInfo()
        activityManager.getMemoryInfo(memInfo)

        val stat = StatFs(context.filesDir.absolutePath)
        val freeStorage = stat.availableBytes
        val totalStorage = stat.totalBytes

        val chipset = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            Build.SOC_MODEL.takeUnless { it.isNullOrEmpty() }
                ?: Build.HARDWARE.takeUnless { it.isNullOrEmpty() }
                ?: Build.BOARD
        } else {
            Build.HARDWARE.takeUnless { it.isNullOrEmpty() } ?: Build.BOARD
        }

        DeviceInfo(
            manufacturer = Build.MANUFACTURER.replaceFirstChar { it.uppercase() },
            model = Build.MODEL,
            androidVersion = Build.VERSION.RELEASE,
            sdkInt = Build.VERSION.SDK_INT,
            abis = Build.SUPPORTED_ABIS.toList(),
            totalMemoryBytes = memInfo.totalMem,
            availableMemoryBytes = memInfo.availMem,
            freeStorageBytes = freeStorage,
            totalStorageBytes = totalStorage,
            chipset = chipset,
            wifiIpv4 = readWifiIpv4(),
            cpuInfo = readCpuInfo(),
            gpuInfo = readGpuInfo()
        )
    }

    fun getMemorySnapshotMb(): Pair<Double, Double> {
        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val memInfo = ActivityManager.MemoryInfo()
        activityManager.getMemoryInfo(memInfo)
        val totalMb = memInfo.totalMem / (1024.0 * 1024.0)
        val pssMb = Debug.getPss() / 1024.0
        return pssMb to totalMb
    }

    fun getFreeStorageBytes(): Long {
        val stat = StatFs(context.filesDir.absolutePath)
        return stat.availableBytes
    }

    private fun readCpuInfo(): CpuInfo {
        val cores = Runtime.getRuntime().availableProcessors()
        val features = linkedSetOf<String>()
        val cpuInfoFile = File("/proc/cpuinfo")
        if (cpuInfoFile.exists()) {
            runCatching {
                cpuInfoFile.readLines().forEach { line ->
                    val idx = line.indexOf(':')
                    if (idx > 0) {
                        val key = line.substring(0, idx).trim()
                        val value = line.substring(idx + 1).trim()
                        if (key == "flags" || key == "Features") {
                            value.split(" ").filter { it.isNotBlank() }.forEach { features.add(it) }
                        }
                    }
                }
            }
        }
        val socModel = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) Build.SOC_MODEL else Build.HARDWARE
        return CpuInfo(
            cores = cores,
            socModel = socModel.orEmpty(),
            hardware = Build.HARDWARE.orEmpty(),
            maxFreqMhz = readMaxCpuFreqMhz(),
            features = features.toList(),
            hasFp16 = features.any { it == "fphp" || it == "fp16" },
            hasDotProd = features.any { it == "dotprod" || it == "asimddp" },
            hasSve = features.any { it == "sve" },
            hasI8mm = features.any { it == "i8mm" }
        )
    }

    private fun readMaxCpuFreqMhz(): Int? {
        return runCatching {
            var maxKhz = 0L
            val coreCount = Runtime.getRuntime().availableProcessors().coerceIn(1, 16)
            for (core in 0 until coreCount) {
                val node = File("/sys/devices/system/cpu/cpu$core/cpufreq/cpuinfo_max_freq")
                if (!node.exists()) continue
                node.readText().trim().toLongOrNull()?.let { khz ->
                    if (khz > maxKhz) maxKhz = khz
                }
            }
            if (maxKhz > 0L) (maxKhz / 1000).toInt() else null
        }.getOrNull()
    }

    private fun readGpuInfo(): GpuInfo {
        var renderer = ""
        var vendor = ""
        var version = ""
        runCatching {
            val egl = EGLContext.getEGL() as EGL10
            val display = egl.eglGetDisplay(EGL10.EGL_DEFAULT_DISPLAY)
            if (display != EGL10.EGL_NO_DISPLAY) {
                egl.eglInitialize(display, IntArray(2))
                val configsCount = IntArray(1)
                val configs = arrayOfNulls<EGLConfig>(1)
                val configSpec = intArrayOf(EGL10.EGL_RENDERABLE_TYPE, 4, EGL10.EGL_NONE)
                egl.eglChooseConfig(display, configSpec, configs, 1, configsCount)
                if (configsCount[0] > 0) {
                    val ctx = egl.eglCreateContext(
                        display,
                        configs[0],
                        EGL10.EGL_NO_CONTEXT,
                        intArrayOf(0x3098, 2, EGL10.EGL_NONE)
                    )
                    if (ctx != null && ctx != EGL10.EGL_NO_CONTEXT) {
                        val surface = egl.eglCreatePbufferSurface(
                            display,
                            configs[0],
                            intArrayOf(EGL10.EGL_WIDTH, 1, EGL10.EGL_HEIGHT, 1, EGL10.EGL_NONE)
                        )
                        if (surface != null && surface != EGL10.EGL_NO_SURFACE) {
                            egl.eglMakeCurrent(display, surface, surface, ctx)
                            renderer = GLES20.glGetString(GLES20.GL_RENDERER) ?: ""
                            vendor = GLES20.glGetString(GLES20.GL_VENDOR) ?: ""
                            version = GLES20.glGetString(GLES20.GL_VERSION) ?: ""
                            egl.eglMakeCurrent(display, EGL10.EGL_NO_SURFACE, EGL10.EGL_NO_SURFACE, EGL10.EGL_NO_CONTEXT)
                            egl.eglDestroySurface(display, surface)
                        }
                        egl.eglDestroyContext(display, ctx)
                    }
                }
                egl.eglTerminate(display)
            }
        }
        val lower = renderer.lowercase()
        val hasAdreno = lower.contains("adreno") || lower.contains("qcom") || lower.contains("qualcomm")
        val hasMali = lower.contains("mali")
        val hasPowerVr = lower.contains("powervr")
        val gpuType = when {
            hasAdreno -> "Adreno (Qualcomm)"
            hasMali -> "Mali (ARM)"
            hasPowerVr -> "PowerVR (Imagination)"
            renderer.isNotBlank() -> renderer
            else -> "Software / Unknown"
        }
        return GpuInfo(
            renderer = renderer,
            vendor = vendor,
            version = version,
            gpuType = gpuType,
            hasAdreno = hasAdreno,
            hasMali = hasMali,
            hasPowerVr = hasPowerVr,
            supportsOpenCl = hasAdreno
        )
    }

    private fun readWifiIpv4(): String? {
        return runCatching {
            val connectivity = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            @Suppress("DEPRECATION")
            val networks = connectivity.allNetworks
            networks.filter { net ->
                val caps = connectivity.getNetworkCapabilities(net)
                caps != null && caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) &&
                    !caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN)
            }.flatMap { connectivity.getLinkProperties(it)?.linkAddresses.orEmpty() }
                .map { it.address }
                .firstOrNull { it is Inet4Address && !it.isLoopbackAddress && !it.isLinkLocalAddress }
                ?.hostAddress
        }.getOrNull()
    }
}
