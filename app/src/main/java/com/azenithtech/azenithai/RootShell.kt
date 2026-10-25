package com.azenithtech.azenithai

import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.util.concurrent.TimeUnit

data class ShellResult(val code: Int, val out: String, val err: String)

/**
 * Menjalankan perintah lewat `su`. Skrip dikirim lewat STDIN (bukan `su -c "<cmd>"`) karena
 * WebUI mengirim chunk base64 ~240 KB dalam satu perintah (upload kernel), sedangkan argumen
 * tunggal di Linux dibatasi 128 KB. Ini juga cara yang sama dipakai manager root.
 */
object RootShell {

    fun exec(script: String, timeoutSec: Long = 180): ShellResult {
        val proc = try {
            ProcessBuilder("su").start()
        } catch (e: Exception) {
            return ShellResult(127, "", "su tidak ditemukan: ${e.message}")
        }

        val outBuf = ByteArrayOutputStream()
        val errBuf = ByteArrayOutputStream()
        val tOut = Thread { pump(proc.inputStream, outBuf) }.apply { isDaemon = true; start() }
        val tErr = Thread { pump(proc.errorStream, errBuf) }.apply { isDaemon = true; start() }

        try {
            proc.outputStream.use { os ->
                os.write(script.toByteArray(Charsets.UTF_8))
                os.write("\nexit\n".toByteArray(Charsets.UTF_8)) // `exit` tanpa argumen = status perintah terakhir
                os.flush()
            }
        } catch (_: Exception) {
            // su sudah mati lebih dulu (mis. izin root ditolak); hasilnya dibaca dari exit code di bawah
        }

        val finished = try {
            proc.waitFor(timeoutSec, TimeUnit.SECONDS)
        } catch (_: InterruptedException) {
            false
        }
        if (!finished) {
            proc.destroyForcibly()
            tOut.join(500)
            tErr.join(500)
            return ShellResult(124, outBuf.toString("UTF-8"), "timeout setelah ${timeoutSec}s")
        }

        // Proses latar yang mewarisi pipe bisa menahan EOF; jangan menunggu selamanya.
        tOut.join(2000)
        tErr.join(2000)
        return ShellResult(proc.exitValue(), outBuf.toString("UTF-8"), errBuf.toString("UTF-8"))
    }

    private fun pump(input: InputStream, sink: ByteArrayOutputStream) {
        try {
            val buf = ByteArray(16 * 1024)
            while (true) {
                val n = input.read(buf)
                if (n < 0) break
                sink.write(buf, 0, n)
            }
        } catch (_: Exception) {
        }
    }
}
