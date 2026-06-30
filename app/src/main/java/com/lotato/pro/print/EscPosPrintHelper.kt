package com.lotato.pro.print

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import android.content.Context
import android.util.Log
import com.dantsu.escposprinter.EscPosPrinter
import com.dantsu.escposprinter.connection.bluetooth.BluetoothConnection
import com.dantsu.escposprinter.connection.bluetooth.BluetoothPrintersConnections
import com.lotato.pro.bridge.TicketData

/**
 * EscPosPrintHelper
 * -----------------
 * Driver ESC/POS générique via Bluetooth.
 * Fonctionne avec PRESQUE TOUTES les imprimantes thermiques Bluetooth
 * (Rongta, Telpo, Goojprt, Xprinter, MUNBYN, etc.) et certains POS
 * qui exposent leur imprimante comme un module Bluetooth interne.
 *
 * Utilise la librairie open-source DantSu/ESCPOS-ThermalPrinter-Android.
 */
class EscPosPrintHelper(private val context: Context) : PrintDriver {

    private val TAG = "EscPosPrintHelper"
    private var printerConnection: BluetoothConnection? = null
    private var escPosPrinter: EscPosPrinter? = null
    private var connected = false

    // Largeur du papier en mm (58mm ou 80mm selon le POS) - 80mm par défaut
    private val PAPER_WIDTH_MM = 80f
    private val PRINTER_DPI = 203 // DPI standard imprimantes thermiques
    private val CHARS_PER_LINE = 48 // pour papier 80mm

    override fun connect() {
        try {
            // Récupère la première imprimante Bluetooth appairée trouvée
            val connection = BluetoothPrintersConnections.selectFirstPaired()
            if (connection == null) {
                Log.w(TAG, "Aucune imprimante Bluetooth appairée trouvée")
                connected = false
                return
            }

            printerConnection = connection
            escPosPrinter = EscPosPrinter(connection, PRINTER_DPI, PAPER_WIDTH_MM, CHARS_PER_LINE)
            connected = true
            Log.d(TAG, "✅ Imprimante Bluetooth connectée: ${connection.device?.name}")
        } catch (e: Exception) {
            Log.e(TAG, "Erreur connexion Bluetooth: ${e.message}")
            connected = false
        }
    }

    /**
     * Connexion à une imprimante Bluetooth spécifique par son adresse MAC
     * Utile quand plusieurs imprimantes sont appairées
     */
    fun connectToDevice(macAddress: String) {
        try {
            val bluetoothAdapter = BluetoothAdapter.getDefaultAdapter()
            val device = bluetoothAdapter?.getRemoteDevice(macAddress)
            if (device != null) {
                val connection = BluetoothConnection(device)
                printerConnection = connection
                escPosPrinter = EscPosPrinter(connection, PRINTER_DPI, PAPER_WIDTH_MM, CHARS_PER_LINE)
                connected = true
                Log.d(TAG, "✅ Connecté à l'imprimante: $macAddress")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Erreur connexion à $macAddress: ${e.message}")
            connected = false
        }
    }

    override fun disconnect() {
        try {
            printerConnection?.disconnect()
        } catch (e: Exception) {
            Log.e(TAG, "Erreur déconnexion: ${e.message}")
        }
        connected = false
        escPosPrinter = null
    }

    override fun isConnected(): Boolean = connected

    override fun printTicket(ticket: TicketData) {
        val printer = escPosPrinter
        if (printer == null || !isConnected()) {
            connect()
            if (escPosPrinter == null) {
                throw Exception("Imprimante Bluetooth non disponible")
            }
        }

        try {
            // Construction du contenu avec syntaxe DantSu
            // [C] = Centré, [L] = Gauche, [R] = Droite
            // <b> = Gras, <font size='big'> = grande taille
            val sb = StringBuilder()

            // ----- En-tête -----
            sb.append("[C]<font size='big'><b>${escapeText(ticket.lotteryName)}</b></font>\n")
            if (ticket.slogan.isNotBlank()) {
                sb.append("[C]${escapeText(ticket.slogan)}\n")
            }
            sb.append("[C]--------------------------------\n")

            // ----- Infos ticket -----
            for (line in ticket.infoLines) {
                if (line.isNotBlank()) {
                    sb.append("[L]${escapeText(line)}\n")
                }
            }
            sb.append("[C]--------------------------------\n")

            // ----- Lignes de paris -----
            for (bet in ticket.betLines) {
                sb.append("[L]<b>${escapeText(bet.label)}</b>[R]<b>${escapeText(bet.amount)}</b>\n")
            }
            sb.append("[C]================================\n")

            // ----- TOTAL -----
            sb.append("[L]<font size='big'><b>TOTAL:</b></font>[R]<font size='big'><b>${escapeText(ticket.totalAmount)}</b></font>\n")
            sb.append("[C]--------------------------------\n")

            // ----- Footer -----
            for (line in ticket.footerLines) {
                if (line.isNotBlank()) {
                    sb.append("[C]${escapeText(line)}\n")
                }
            }

            sb.append("\n\n")

            printer!!.printFormattedTextAndCut(sb.toString())
            Log.d(TAG, "✅ Impression ESC/POS Bluetooth envoyée")

        } catch (e: Exception) {
            Log.e(TAG, "❌ Erreur impression ESC/POS: ${e.message}")
            connected = false
            throw e
        }
    }

    /**
     * Échappe les caractères spéciaux qui pourraient casser
     * la syntaxe de formatage DantSu ([ ] < >)
     */
    private fun escapeText(text: String): String {
        return text
            .replace("[", "(")
            .replace("]", ")")
            .replace("<", "")
            .replace(">", "")
    }

    /**
     * Liste les imprimantes Bluetooth appairées disponibles
     * (utile pour un écran de configuration dans l'app)
     */
    fun listPairedPrinters(): List<Pair<String, String>> {
        val result = mutableListOf<Pair<String, String>>()
        try {
            val adapter = BluetoothAdapter.getDefaultAdapter()
            adapter?.bondedDevices?.forEach { device ->
                result.add(Pair(device.name ?: "Inconnu", device.address))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Erreur listing imprimantes: ${e.message}")
        }
        return result
    }
}
