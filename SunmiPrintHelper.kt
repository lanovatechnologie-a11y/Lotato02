package com.lotato.pro.print

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.os.IBinder
import android.os.RemoteException
import android.util.Log
import com.lotato.pro.bridge.TicketData

/**
 * SunmiPrintHelper
 * ----------------
 * Driver pour tous les POS Sunmi (V2s, V2 Pro, P2, T2 mini, L2, etc.)
 *
 * Sunmi expose son imprimante via un service AIDL interne.
 * Package: woyou.aidlservice.jiuiv5
 *
 * NOTE : Pour une intégration officielle, téléchargez le SDK Sunmi :
 * https://developer.sunmi.com/docs/en-US/xeghjk491/zdvkhelp318
 * et ajoutez le fichier IWoyouService.aidl dans votre projet.
 *
 * Cette implémentation utilise la réflexion pour fonctionner
 * même sans le SDK officiel sur les appareils Sunmi.
 */
class SunmiPrintHelper(private val context: Context) : PrintDriver {

    private val TAG = "SunmiPrintHelper"

    // Service AIDL Sunmi
    private var printService: android.os.IBinder? = null
    private var connected = false

    // Package du service d'impression Sunmi
    private val SUNMI_SERVICE_PACKAGE = "woyou.aidlservice.jiuiv5"
    private val SUNMI_SERVICE_CLASS = "woyou.aidlservice.jiuiv5.JiuIV5"

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            printService = service
            connected = true
            Log.d(TAG, "✅ Service Sunmi connecté")
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            printService = null
            connected = false
            Log.w(TAG, "⚠️ Service Sunmi déconnecté")
        }
    }

    override fun connect() {
        try {
            val intent = Intent().apply {
                setPackage(SUNMI_SERVICE_PACKAGE)
                action = "woyou.aidlservice.jiuiv5.IWoyouService"
            }
            val bound = context.bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
            if (!bound) {
                Log.w(TAG, "Impossible de se connecter au service Sunmi")
                connected = false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Erreur connexion Sunmi: ${e.message}")
            connected = false
        }
    }

    override fun disconnect() {
        try {
            if (connected) {
                context.unbindService(serviceConnection)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Erreur déconnexion: ${e.message}")
        }
        connected = false
        printService = null
    }

    override fun isConnected(): Boolean = connected && printService != null

    /**
     * Impression principale du ticket LOTATO
     * Utilise l'AIDL Sunmi via réflexion (sans SDK)
     */
    override fun printTicket(ticket: TicketData) {
        if (!isConnected()) {
            Log.w(TAG, "Non connecté, reconnexion...")
            connect()
            Thread.sleep(500)
            if (!isConnected()) {
                throw Exception("Imprimante Sunmi non disponible")
            }
        }

        try {
            // Utiliser la réflexion pour appeler les méthodes AIDL Sunmi
            val service = printService ?: throw Exception("Service null")
            val clazz = Class.forName("woyou.aidlservice.jiuiv5.IWoyouService\$Stub")
            val asInterface = clazz.getMethod("asInterface", IBinder::class.java)
            val woyouService = asInterface.invoke(null, service)

            val serviceClass = woyouService.javaClass

            // Helper pour appeler les méthodes
            fun call(methodName: String, vararg args: Any?) {
                try {
                    val types = args.map { it?.javaClass }.toTypedArray()
                    val method = serviceClass.getMethod(methodName, *types.map {
                        when (it) {
                            java.lang.Integer::class.java -> Int::class.java
                            java.lang.Boolean::class.java -> Boolean::class.java
                            java.lang.Float::class.java -> Float::class.java
                            else -> it
                        }
                    }.toTypedArray())
                    method.invoke(woyouService, *args)
                } catch (e: NoSuchMethodException) {
                    Log.w(TAG, "Méthode $methodName non trouvée, skip")
                }
            }

            // ===== DÉBUT IMPRESSION =====
            call("printerInit", null)

            // En-tête : Nom de la loterie (grand, centré, gras)
            call("setAlignment", 1, null)        // Centre
            call("setFontSize", 32f, null)
            call("setFontAttribute", 0.toByte(), null) // Gras
            call("printText", "${ticket.lotteryName}\n", null)

            if (ticket.slogan.isNotBlank()) {
                call("setFontSize", 24f, null)
                call("printText", "${ticket.slogan}\n", null)
            }

            // Ligne séparatrice
            call("setFontSize", 20f, null)
            call("setAlignment", 0, null)
            call("printText", "--------------------------------\n", null)

            // Infos ticket (centré, taille normale)
            call("setAlignment", 0, null)
            call("setFontSize", 22f, null)
            for (line in ticket.infoLines) {
                if (line.isNotBlank()) {
                    call("printText", "$line\n", null)
                }
            }

            // Séparateur tirets
            call("printText", "--------------------------------\n", null)

            // Lignes de paris
            call("setFontSize", 24f, null)
            for (bet in ticket.betLines) {
                // Format : "bor 23          50 G"
                val label = bet.label.padEnd(20)
                val amount = bet.amount.padStart(10)
                call("printText", "$label$amount\n", null)
            }

            // Séparateur
            call("printText", "================================\n", null)

            // TOTAL (grand, gras)
            call("setAlignment", 0, null)
            call("setFontSize", 28f, null)
            call("printText", "TOTAL:          ${ticket.totalAmount}\n", null)

            // Séparateur
            call("printText", "--------------------------------\n", null)

            // Footer
            call("setAlignment", 1, null)
            call("setFontSize", 20f, null)
            for (line in ticket.footerLines) {
                if (line.isNotBlank()) {
                    call("printText", "$line\n", null)
                }
            }

            // Avancer le papier et couper
            call("printText", "\n\n\n", null)
            call("cutPaper", 1, null)   // 1 = coupe partielle

            Log.d(TAG, "✅ Impression Sunmi terminée")

        } catch (e: Exception) {
            Log.e(TAG, "❌ Erreur impression Sunmi: ${e.message}")
            throw e
        }
    }

    /**
     * Convertit un Bitmap en tableau de bytes ESC/POS pour Sunmi
     */
    private fun bitmapToBytes(bmp: Bitmap): ByteArray {
        val width = bmp.width
        val height = bmp.height
        val pixels = IntArray(width * height)
        bmp.getPixels(pixels, 0, width, 0, 0, width, height)

        val bytes = ByteArray((width / 8 + if (width % 8 != 0) 1 else 0) * height)
        var byteIndex = 0

        for (y in 0 until height) {
            for (x in 0 until width step 8) {
                var byte = 0
                for (bit in 0 until 8) {
                    if (x + bit < width) {
                        val pixel = pixels[y * width + x + bit]
                        val r = (pixel shr 16) and 0xff
                        val g = (pixel shr 8) and 0xff
                        val b = pixel and 0xff
                        val gray = (0.299 * r + 0.587 * g + 0.114 * b).toInt()
                        if (gray < 128) {
                            byte = byte or (0x80 shr bit)
                        }
                    }
                }
                bytes[byteIndex++] = byte.toByte()
            }
        }
        return bytes
    }
}
