package com.lotato.pro.bridge

import android.content.Context
import android.webkit.JavascriptInterface
import android.util.Log
import com.lotato.pro.print.PrintManager

/**
 * AndroidPrintBridge
 * ------------------
 * Ce pont est exposé à JavaScript sous le nom "AndroidPrint".
 * Votre cartManager.js appelle déjà :
 *    window.AndroidPrint.printHTML(fullHTML)
 *
 * Ici on intercepte cet appel et on envoie vers le bon module d'impression.
 */
class AndroidPrintBridge(
    private val context: Context,
    private val printManager: PrintManager
) {

    private val TAG = "AndroidPrintBridge"

    /**
     * Appelé depuis JavaScript : window.AndroidPrint.printHTML(html)
     * @param html Le HTML complet du ticket (généré par buildTicketPrintHTML())
     */
    @JavascriptInterface
    fun printHTML(html: String) {
        Log.d(TAG, "printHTML appelé, taille HTML: ${html.length} chars")

        // Extraction du contenu ticket depuis le HTML
        val ticketData = parseTicketFromHTML(html)

        // Impression via le bon driver selon le device
        printManager.print(ticketData)
    }

    /**
     * Appelé depuis JS pour vérifier si l'imprimante est disponible
     */
    @JavascriptInterface
    fun isPrinterAvailable(): Boolean {
        return printManager.isPrinterReady()
    }

    /**
     * Appelé depuis JS pour obtenir le statut de l'imprimante
     */
    @JavascriptInterface
    fun getPrinterStatus(): String {
        return printManager.getPrinterStatus()
    }

    /**
     * Parse le HTML du ticket pour extraire les données structurées
     * afin de les envoyer aux commandes ESC/POS
     */
    private fun parseTicketFromHTML(html: String): TicketData {
        // On utilise Jsoup pour parser le HTML
        val doc = org.jsoup.Jsoup.parse(html)

        // En-tête
        val lotteryName = doc.selectFirst(".lottery-name")?.text() ?: "LOTATO"
        val slogan = doc.selectFirst(".slogan")?.text() ?: ""
        val logoUrl = doc.selectFirst(".header img")?.attr("src") ?: ""

        // Infos ticket
        val infoDiv = doc.selectFirst(".info")
        val infoLines = infoDiv?.select("p")?.map { it.text() } ?: emptyList()

        // Lignes de paris
        val betRows = doc.select(".bet-row").map { row ->
            val spans = row.select("span")
            BetLine(
                label = spans.getOrNull(0)?.text() ?: "",
                amount = spans.getOrNull(1)?.text() ?: ""
            )
        }

        // Total
        val totalRow = doc.selectFirst(".total-row")
        val totalSpans = totalRow?.select("span")
        val totalAmount = totalSpans?.getOrNull(1)?.text() ?: ""

        // Footer
        val footerLines = doc.select(".footer p").map { it.text() }

        return TicketData(
            lotteryName = lotteryName,
            slogan = slogan,
            logoUrl = logoUrl,
            infoLines = infoLines,
            betLines = betRows,
            totalAmount = totalAmount,
            footerLines = footerLines,
            rawHtml = html
        )
    }
}

/**
 * Données structurées d'un ticket
 */
data class TicketData(
    val lotteryName: String,
    val slogan: String,
    val logoUrl: String,
    val infoLines: List<String>,
    val betLines: List<BetLine>,
    val totalAmount: String,
    val footerLines: List<String>,
    val rawHtml: String
)

data class BetLine(
    val label: String,
    val amount: String
)
