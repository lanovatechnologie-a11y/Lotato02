package com.lotato.pro.print

import com.lotato.pro.bridge.TicketData

/**
 * Interface commune pour tous les drivers d'impression
 */
interface PrintDriver {
    fun connect()
    fun disconnect()
    fun isConnected(): Boolean
    fun printTicket(ticket: TicketData)
}
