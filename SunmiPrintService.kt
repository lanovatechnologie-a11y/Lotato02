package com.lotato.pro.print

import android.app.Service
import android.content.Intent
import android.os.IBinder

/**
 * SunmiPrintService
 * -----------------
 * Service vide déclaré dans le manifest pour réserver le contexte
 * d'exécution de l'impression Sunmi si besoin d'étendre vers un
 * mode "impression en arrière-plan" plus tard (ex: file d'attente
 * de tickets à imprimer même si l'app est en pause).
 *
 * Non utilisé activement dans la version actuelle — le pont JS
 * appelle directement PrintManager depuis MainActivity.
 */
class SunmiPrintService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null
}
