package bj.bamm.app.ui

import android.Manifest
import android.app.role.RoleManager
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.text.format.DateUtils
import android.view.LayoutInflater
import android.widget.ArrayAdapter
import android.widget.AdapterView
import android.widget.Toast
import android.view.View
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import bj.bamm.app.BammApp
import bj.bamm.app.BuildConfig
import bj.bamm.app.R
import bj.bamm.app.core.Level
import bj.bamm.app.core.Prefs
import bj.bamm.app.core.RiskEngine
import bj.bamm.app.data.CommunityRepository
import bj.bamm.app.data.IncidentLog
import bj.bamm.app.databinding.ActivityMainBinding
import bj.bamm.app.databinding.ItemIncidentBinding
import bj.bamm.app.databinding.ItemStatusBinding
import bj.bamm.app.ml.RiskModel
import bj.bamm.app.service.UssdAccessibilityService

class MainActivity : AppCompatActivity() {

    private lateinit var b: ActivityMainBinding
    private lateinit var prefs: Prefs

    private val permLauncher = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { refresh() }
    private val roleLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { refresh() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = Prefs(this)
        if (prefs.language == null) {
            startActivity(Intent(this, LanguageActivity::class.java))
            finish()
            return
        }
        b = ActivityMainBinding.inflate(layoutInflater)
        setContentView(b.root)

        b.languageChip.setOnClickListener { startActivity(Intent(this, LanguageActivity::class.java)) }
        setupRegion()
        setupDemo()
        setupCommunity()
        showModelInfo()
    }

    override fun onResume() {
        super.onResume()
        if (::b.isInitialized) refresh()
    }

    // ---------------------------------------------------------------- protections
    private data class Protection(val title: Int, val ok: Boolean, val action: () -> Unit)

    private fun protections(): List<Protection> = listOf(
        Protection(R.string.prot_screening, screeningOk()) { requestScreening() },
        Protection(R.string.prot_phone, granted(*phonePerms())) { permLauncher.launch(phonePerms()) },
        Protection(R.string.prot_overlay, Settings.canDrawOverlays(this)) {
            startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
        },
    ) + (if (BuildConfig.USSD_ACCESSIBILITY) listOf(
        Protection(R.string.prot_accessibility, accessibilityOk()) {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }) else emptyList()) + if (Build.VERSION.SDK_INT >= 33) listOf(
        Protection(R.string.prot_notifications, granted(Manifest.permission.POST_NOTIFICATIONS)) {
            permLauncher.launch(arrayOf(Manifest.permission.POST_NOTIFICATIONS))
        }) else emptyList()

    private fun refresh() {
        b.languageChip.text = prefs.language?.nativeName
        val list = protections()
        b.statusList.removeAllViews()
        for (p in list) {
            val row = ItemStatusBinding.inflate(LayoutInflater.from(this), b.statusList, false)
            row.statusIcon.setImageResource(if (p.ok) R.drawable.ic_check else R.drawable.ic_question)
            row.statusIcon.setColorFilter(getColor(if (p.ok) R.color.bamm_green else R.color.bamm_orange))
            row.statusTitle.setText(p.title)
            row.statusAction.visibility = if (p.ok) View.GONE else View.VISIBLE
            row.root.setOnClickListener { if (!p.ok) p.action() }
            b.statusList.addView(row.root)
        }
        val allOk = list.all { it.ok }
        b.shieldIcon.setColorFilter(getColor(if (allOk) R.color.bamm_green else R.color.bamm_orange))
        b.shieldText.setText(if (allOk) R.string.shield_on else R.string.shield_partial)
        refreshCommunity()
        refreshIncidents()
    }

    private fun phonePerms() = arrayOf(
        Manifest.permission.READ_PHONE_STATE, Manifest.permission.READ_CALL_LOG,
        Manifest.permission.CALL_PHONE, Manifest.permission.PROCESS_OUTGOING_CALLS)

    private fun granted(vararg perms: String) = perms.all { checkSelfPermission(it) == PackageManager.PERMISSION_GRANTED }

    private fun screeningOk() = Build.VERSION.SDK_INT < 29 ||
        getSystemService(RoleManager::class.java).isRoleHeld(RoleManager.ROLE_CALL_SCREENING)

    private fun requestScreening() {
        if (Build.VERSION.SDK_INT >= 29) {
            roleLauncher.launch(getSystemService(RoleManager::class.java).createRequestRoleIntent(RoleManager.ROLE_CALL_SCREENING))
        }
    }

    private fun accessibilityOk(): Boolean {
        val me = ComponentName(this, UssdAccessibilityService::class.java).flattenToString()
        val enabled = Settings.Secure.getString(contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES).orEmpty()
        return enabled.split(':').any { it.equals(me, ignoreCase = true) }
    }

    // ---------------------------------------------------------------- réglages
    private fun setupRegion() {
        val regions = RiskModel.get(this).meta.regions
        b.regionSpinner.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, regions)
        b.regionSpinner.setSelection(regions.indexOf(prefs.region).coerceAtLeast(0))
        b.regionSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(p: AdapterView<*>?, v: View?, pos: Int, id: Long) { prefs.region = regions[pos] }
            override fun onNothingSelected(p: AdapterView<*>?) = Unit
        }
        b.apiUrl.setText(prefs.apiUrl)
    }

    // ---------------------------------------------------------------- démo & composeur sûr
    private fun setupDemo() {
        b.demoCallButton.setOnClickListener {
            val number = b.demoNumber.text.toString().ifBlank { "0166778899" }
            BammApp.io.execute {
                // Simule un appel reçu à 11 h (heure de marché) qui vient de se terminer
                val a = RiskEngine(this).assessIncoming(number, hour = 11)
                prefs.lastCallNumber = a.number
                prefs.lastCallLevel = a.level.name
                prefs.callRingingAt = System.currentTimeMillis() - 180_000
                prefs.callAnsweredAt = System.currentTimeMillis() - 170_000
                prefs.callEndedAt = System.currentTimeMillis()
                prefs.callInProgress = false
                if (a.level == Level.RED) IncidentLog(this).add(IncidentLog.Type.CALL_RED, a.number, a.reasons.joinToString(" · "))
                runOnUiThread {
                    startActivity(AlertActivity.intent(this, AlertActivity.Mode.CALL, a.number, a.level, a.reasons))
                }
            }
        }
        b.demoUssdButton.setOnClickListener { dialSafely(DEMO_USSD) }
        b.safeDialButton.setOnClickListener { dialSafely(b.safeDialInput.text.toString()) }
    }

    private fun dialSafely(code: String) {
        val d = UssdGuard.check(this, code)
        when {
            d.block -> Unit // l'écran STOP est affiché par UssdGuard
            d.ussd.kind == bj.bamm.app.core.UssdAnalyzer.Kind.NOT_USSD ->
                Toast.makeText(this, R.string.not_ussd, Toast.LENGTH_SHORT).show()
            else -> {
                b.ussdResponse.text = getString(R.string.ussd_allowed, d.ussd.kind.name)
                if (granted(Manifest.permission.CALL_PHONE)) UssdGuard.execute(this, d.ussd.code) { b.ussdResponse.text = it }
            }
        }
    }

    // ---------------------------------------------------------------- base communautaire
    private fun setupCommunity() {
        b.reportButton.setOnClickListener {
            val n = b.reportNumber.text.toString()
            BammApp.io.execute {
                val sent = CommunityRepository(this).report(n)
                IncidentLog(this).add(IncidentLog.Type.REPORT, n, if (sent) "envoyé" else "en attente (hors ligne)")
                runOnUiThread {
                    Toast.makeText(this, if (sent) R.string.report_done else R.string.report_queued, Toast.LENGTH_SHORT).show()
                    refresh()
                }
            }
        }
        b.syncButton.setOnClickListener {
            prefs.apiUrl = b.apiUrl.text.toString()
            BammApp.io.execute {
                val ok = CommunityRepository(this).sync()
                runOnUiThread {
                    Toast.makeText(this, if (ok) R.string.sync_ok else R.string.sync_failed, Toast.LENGTH_SHORT).show()
                    refresh()
                }
            }
        }
    }

    private fun refreshCommunity() {
        val repo = CommunityRepository(this)
        val last = repo.lastSync.takeIf { it > 0 }?.let { DateUtils.getRelativeTimeSpanString(it).toString() } ?: "—"
        b.communityStats.text = getString(R.string.community_stats, repo.redCount(), repo.myReports().size, repo.pending().size, last)
    }

    private fun refreshIncidents() {
        b.incidentList.removeAllViews()
        val items = IncidentLog(this).all()
        b.noIncidents.visibility = if (items.isEmpty()) View.VISIBLE else View.GONE
        for (i in items.take(15)) {
            val row = ItemIncidentBinding.inflate(LayoutInflater.from(this), b.incidentList, false)
            val (icon, color) = when (i.type) {
                IncidentLog.Type.CALL_RED -> R.drawable.ic_stop_hand to R.color.bamm_red
                IncidentLog.Type.CALL_ORANGE -> R.drawable.ic_question to R.color.bamm_orange
                IncidentLog.Type.USSD_BLOCKED -> R.drawable.ic_lock to R.color.bamm_red
                IncidentLog.Type.USSD_UNLOCKED -> R.drawable.ic_lock to R.color.bamm_orange
                IncidentLog.Type.REPORT -> R.drawable.ic_thumb_down to R.color.bamm_green
            }
            row.incidentIcon.setImageResource(icon)
            row.incidentIcon.setColorFilter(getColor(color))
            row.incidentTitle.text = "${i.number} · ${DateUtils.getRelativeTimeSpanString(i.at)}"
            row.incidentDetail.text = i.detail
            b.incidentList.addView(row.root)
        }
    }

    private fun showModelInfo() {
        val m = RiskModel.get(this).meta
        b.modelInfo.text = getString(R.string.model_info, m.precall.features.size, m.precall.threshold, m.full.features.size, m.full.threshold)
    }

    companion object { const val DEMO_USSD = "*880*7721*45000#" }
}
