package com.lotato.pro.print

import android.content.Context
import android.os.Build
import android.util.Log
import android.widget.Toast
import com.lotato.pro.bridge.TicketData

/**
 * PrintManager
 * ------------
 * Point d'entrée unique pour l'impression.
 * Détecte automatiquement le type de POS et délègue
 * au bon driver d'impression.
 *
 * Ordre de priorité :
 * 1. Sunmi (si SDK Sunmi disponible)
 * 2. Imprimante interne POS via AIDL générique
 * 3. ESC/POS Bluetooth
 * 4. ESC/POS USB
 */
class PrintManager(private val context: Context) {

    private val TAG = "PrintManager"

    // Drivers disponibles
    private var sunmiPrinter: SunmiPrintHelper? = null
    private var escPosBluetooth: EscPosPrintHelper? = null
    private var genericAidl: GenericAidlPrintHelper? = null

    // Driver actif
    private var activeDriver: PrintDriver? = null

    init {
        initPrinter()
    }

    private fun initPrinter() {
        val deviceModel = Build.MODEL.uppercase()
        val manufacturer = Build.MANUFACTURER.uppercase()

        Log.d(TAG, "Device: $manufacturer $deviceModel")

        when {
            // ===== Sunmi POS (V2s, P2, T2 mini, etc.) =====
            manufacturer.contains("SUNMI") || deviceModel.startsWith("V2") ||
            deviceModel.startsWith("P2") || deviceModel.startsWith("T2") -> {
                Log.d(TAG, "Détecté: Sunmi POS")
                sunmiPrinter = SunmiPrintHelper(context)
                activeDriver = sunmiPrinter
            }

            // ===== PAX POS =====
            manufacturer.contains("PAX") -> {
                Log.d(TAG, "Détecté: PAX POS")
                genericAidl = GenericAidlPrintHelper(context, GenericAidlPrintHelper.PosType.PAX)
                activeDriver = genericAidl
            }

            // ===== Urovo POS =====
            manufacturer.contains("UROVO") || deviceModel.contains("UROVO") -> {
                Log.d(TAG, "Détecté: Urovo POS")
                genericAidl = GenericAidlPrintHelper(context, GenericAidlPrintHelper.PosType.UROVO)
                activeDriver = genericAidl
            }

            // ===== Newland POS =====
            manufacturer.contains("NEWLAND") -> {
                Log.d(TAG, "Détecté: Newland POS")
                genericAidl = GenericAidlPrintHelper(context, GenericAidlPrintHelper.PosType.NEWLAND)
                activeDriver = genericAidl
            }

            // ===== Rongta / Telpo / Autres POS =====
            // Essayer AIDL générique d'abord, puis fallback Bluetooth
            else -> {
                Log.d(TAG, "Device générique - tentative AIDL puis Bluetooth")
                // Tentative AIDL générique
                genericAidl = GenericAidlPrintHelper(context, GenericAidlPrintHelper.PosType.GENERIC)
                // Fallback Bluetooth si AIDL échoue
                escPosBluetooth = EscPosPrintHelper(context)
                activeDriver = genericAidl ?: escPosBluetooth
            }
        }

        activeDriver?.connect()
        Log.d(TAG, "Driver actif: ${activeDriver?.javaClass?.simpleName}")
    }

    /**
     * Imprime un ticket - point d'entrée principal
     */
    fun print(ticket: TicketData) {
        val driver = activeDriver

        if (driver == null || !driver.isConnected()) {
            Log.w(TAG, "Imprimante non disponible, tentative de reconnexion...")
            reconnect()

            if (activeDriver?.isConnected() == false) {
                // Dernier recours : fallback Bluetooth si non déjà utilisé
                if (activeDriver !is EscPosPrintHelper) {
                    Log.d(TAG, "Fallback vers Bluetooth")
                    escPosBluetooth = EscPosPrintHelper(context)
                    escPosBluetooth?.connect()
                    activeDriver = escPosBluetooth
                }
            }
        }

        try {
            activeDriver?.printTicket(ticket)
            Log.d(TAG, "✅ Impression envoyée avec succès")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Erreur impression: ${e.message}")
            showError("Erreur impression: ${e.message}")
        }
    }

    fun isPrinterReady(): Boolean = activeDriver?.isConnected() == true

    fun getPrinterStatus(): String {
        return when {
            activeDriver == null -> "Aucun driver"
            !activeDriver!!.isConnected() -> "Déconnectée"
            else -> "Prête (${activeDriver!!.javaClass.simpleName})"
        }
    }

    fun reconnect() {
        activeDriver?.connect()
        if (activeDriver?.isConnected() == false) {
            // Réinitialiser complètement
            disconnect()
            initPrinter()
        }
    }

    fun disconnect() {
        sunmiPrinter?.disconnect()
        escPosBluetooth?.disconnect()
        genericAidl?.disconnect()
        activeDriver = null
    }

    private fun showError(msg: String) {
        android.os.Handler(android.os.Looper.getMainLooper()).post {
            Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
        }
    }
}
