package bj.bamm.app.service

import android.accessibilityservice.AccessibilityService
import android.os.Bundle
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import bj.bamm.app.core.UssdAnalyzer
import bj.bamm.app.ui.UssdGuard

/**
 * Couche 2 (voie 2) — lit le champ de saisie du clavier téléphonique. Dès qu'un code de
 * transfert complet (terminé par #) apparaît dans un contexte suspect, le champ est vidé
 * avant que l'utilisateur n'appuie sur « Appeler », et l'écran STOP s'affiche.
 */
class UssdAccessibilityService : AccessibilityService() {

    override fun onAccessibilityEvent(e: AccessibilityEvent) {
        if (e.eventType != AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED) return
        val pkg = e.packageName?.toString().orEmpty()
        if (DIALERS.none { pkg.contains(it, ignoreCase = true) }) return
        val text = (e.source?.text ?: e.text.firstOrNull())?.toString()?.replace(" ", "") ?: return
        if (!UssdAnalyzer.isCompleteUssd(text)) return
        val d = UssdGuard.check(this, text)
        if (d.block) e.source?.let { clear(it) }
    }

    private fun clear(node: AccessibilityNodeInfo) {
        val args = Bundle().apply {
            putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, "")
        }
        node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
    }

    override fun onInterrupt() = Unit

    companion object {
        /** Claviers courants au Bénin : Google, AOSP, Samsung, Tecno/Infinix/itel (Transsion), Xiaomi. */
        val DIALERS = listOf("dialer", "contacts", "incallui", "telecom", "transsion", "phone")
    }
}
