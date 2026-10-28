package bj.bamm.app.ui

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import androidx.appcompat.app.AppCompatActivity
import bj.bamm.app.core.Lang
import bj.bamm.app.core.Prefs
import bj.bamm.app.databinding.ActivityLanguageBinding
import bj.bamm.app.databinding.ItemLanguageBinding

/** Premier lancement : 5 grands boutons, chaque langue écrite dans sa propre langue. */
class LanguageActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val b = ActivityLanguageBinding.inflate(layoutInflater)
        setContentView(b.root)
        val current = Prefs(this).language
        for (lang in Lang.entries) {
            val item = ItemLanguageBinding.inflate(LayoutInflater.from(this), b.languageList, false)
            item.name.text = lang.nativeName
            item.root.isSelected = lang == current
            item.root.setOnClickListener {
                Prefs(this).language = lang
                startActivity(Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP))
                finish()
            }
            b.languageList.addView(item.root)
        }
    }
}
