package io.github.ccch1mneyyy.dsh_app.runtime

import android.content.Context
import java.io.File

/**
 * Launches the engine inside the user's own Linux rootfs through `su` + chroot,
 * instead of the bundled PRoot Ubuntu runtime. Enabled by writing a line with the
 * rootfs path into `files/rootchroot.conf`.
 */
object RootChroot {
    private const val CONFIG = "rootchroot.conf"

    fun root(context: Context): String? {
        val cfg = File(context.filesDir, CONFIG)
        if (!cfg.isFile) return null
        val path = cfg.readText().lineSequence()
            .map { it.trim() }
            .firstOrNull { it.isNotEmpty() && !it.startsWith("#") }
            ?: return null
        // The app uid cannot stat Termux-owned rootfs paths (0700); su does the probing.
        return path
    }

    /** Full `su -c` command that mounts the kernel filesystems and execs `dsh web`. */
    fun command(context: Context, port: Int): List<String>? {
        val root = root(context) ?: return null
        val q = root.replace("'", "'\\''")
        val guest = "/usr/local/bin/dsh web --host 127.0.0.1 --port $port --trusted-host 127.0.0.1:$port --no-open"
        val mounts = "mountpoint -q '$q/proc' || mount -t proc proc '$q/proc' 2>/dev/null; " +
            "mountpoint -q '$q/dev' || mount --bind /dev '$q/dev' 2>/dev/null; " +
            "mountpoint -q '$q/dev/pts' || mount --bind /dev/pts '$q/dev/pts' 2>/dev/null; " +
            "mountpoint -q '$q/sdcard' || mount --bind /sdcard '$q/sdcard' 2>/dev/null; "
        val script = mounts +
            "echo \$\$ > '$q/tmp/dsh-web.pid'; " +
            "exec chroot '$q' /bin/bash -lc '" + guest + "'"
        return listOf("su", "-c", script)
    }
}
