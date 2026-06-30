package com.lotato.pro.print

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import android.util.Log
import com.lotato.pro.bridge.TicketData

/**
 * GenericAidlPrintHelper
 * -----------------------
 * Driver pour les POS qui exposent un service système AIDL
 * pour piloter leur imprimante thermique intégrée
 * (PAX, Urovo, Newland, et autres POS "tout-en-un").
 *
 * Chaque fabricant a son propre package/service, mais le principe
 * AIDL (bindService + appel de méthodes) est similaire à Sunmi.
 *
 * NOTE IMPORTANTE : Pour une intégration 100% fiable avec PAX/Urovo/Newland,
 * il est recommandé de télécharger le SDK officiel du fabricant et de
 * l'intégrer en .aar (voir README_INTEGRATION.md). Cette classe fournit
 * une implémentation de base qui couvre les cas les plus courants, et
 * bascule sur ESC/POS Bluetooth en cas d'échec (géré par PrintManager).
 */
class GenericAidlPrintHelper(
    private val context: Context,
    private val posType: PosType
) : PrintDriver {

    enum class PosType { PAX, UROVO, NEWLAND, GENERIC }

    private val TAG = "GenericAidlPrintHelper"
    private var service: IBinder? = null
    private var connected = false

    // Packages des services d'impression connus par fabricant
    private val servicePackages = mapOf(
        PosType.PAX to "com.pax.dal.IDAL",                         // PAX DAL service
        PosType.UROVO to "com.urovo.sdk.print.PrinterService",     // Urovo print service
        PosType.NEWLAND to "com.newland.printservice.PrintService", // Newland print service
        PosType.GENERIC to "android.print.PrintService"            // Fallback générique
    )

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            service = binder
            connected = true
            Log.d(TAG, "✅ Service AIDL ($posType) connecté")
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            service = null
            connected = false
            Log.w(TAG, "⚠️ Service AIDL ($posType) déconnecté")
        }
    }

    override fun connect() {
        try {
            val packageName = servicePackages[posType] ?: return
            val intent = Intent().apply {
                action = packageName
            }
            // Recherche du service installé sur le device
            val resolved = context.packageManager.queryIntentServices(intent, 0)
            if (resolved.isEmpty()) {
                Log.w(TAG, "Aucun service AIDL trouvé pour $posType")
                connected = false
                return
            }
            val bound = context.bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
            connected = bound
        } catch (e: Exception) {
            Log.e(TAG, "Erreur connexion AIDL ($posType): ${e.message}")
            connected = false
        }
    }

    override fun disconnect() {
        try {
            if (connected) context.unbindService(serviceConnection)
        } catch (e: Exception) {
            Log.e(TAG, "Erreur déconnexion AIDL: ${e.message}")
        }
        connected = false
        service = null
    }

    override fun isConnected(): Boolean = connected && service != null

    override fun printTicket(ticket: TicketData) {
        if (!isConnected()) {
            connect()
            if (!isConnected()) {
                throw Exception("Service d'impression $posType non disponible — fallback requis")
            }
        }

        // Construction du texte brut formaté en colonnes (ESC/POS-like)
        // pour les fabricants qui exposent une méthode "printText" générique
        val text = buildPlainTextTicket(ticket)

        try {
            invokeGenericPrint(text)
            Log.d(TAG, "✅ Impression AIDL ($posType) envoyée")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Échec impression AIDL ($posType): ${e.message}")
            throw e
        }
    }

    /**
     * Tente d'invoquer une méthode d'impression générique via réflexion.
     * Les noms de méthodes varient selon le fabricant ; on essaie
     * les plus courants : printText, printString, addText + start.
     */
    private fun invokeGenericPrint(text: String) {
        val binder = service ?: throw Exception("Service nul")
        val candidates = listOf("printText", "printString", "print")

        var success = false
        for (methodName in candidates) {
            try {
                val stubClass = binder.javaClass
                val method = stubClass.getMethod(methodName, String::class.java)
                method.invoke(binder, text)
                success = true
                break
            } catch (e: Exception) {
                // essai suivant
            }
        }

        if (!success) {
            throw Exception("Aucune méthode d'impression compatible trouvée pour $posType")
        }
    }

    /**
     * Construit un ticket texte brut (48 colonnes, format 80mm)
     */
    private fun buildPlainTextTicket(ticket: TicketData): String {
        val sb = StringBuilder()
        val width = 32 // largeur typique en caractères pour POS 58-80mm

        fun centered(s: String) {
            val pad = ((width - s.length) / 2).coerceAtLeast(0)
            sb.append(" ".repeat(pad)).append(s).append("\n")
        }

        centered(ticket.lotteryName)
        if (ticket.slogan.isNotBlank()) centered(ticket.slogan)
        sb.append("-".repeat(width)).append("\n")

        ticket.infoLines.forEach { if (it.isNotBlank()) sb.append(it).append("\n") }
        sb.append("-".repeat(width)).append("\n")

        ticket.betLines.forEach { bet ->
            val label = bet.label.padEnd(width - bet.amount.length)
            sb.append(label).append(bet.amount).append("\n")
        }
        sb.append("=".repeat(width)).append("\n")

        val totalLabel = "TOTAL:"
        sb.append(totalLabel.padEnd(width - ticket.totalAmount.length)).append(ticket.totalAmount).append("\n")
        sb.append("-".repeat(width)).append("\n")

        ticket.footerLines.forEach { if (it.isNotBlank()) centered(it) }
        sb.append("\n\n\n")

        return sb.toString()
    }
}
