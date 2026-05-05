package work.slhaf.partner.ctl.i18n

import java.text.MessageFormat
import java.util.*

object I18n {
    private const val BUNDLE = "i18n.messages"

    private val locale: Locale by lazy {
        System.getenv("PARTNER_LOCALE")
            ?.takeIf { it.isNotBlank() }
            ?.let { Locale.forLanguageTag(it.replace('_', '-')) }
            ?: Locale.getDefault()
    }

    fun text(key: String, vararg args: Any?): String {
        val pattern = ResourceBundle.getBundle(BUNDLE, locale).getString(key)
        return MessageFormat.format(pattern, *args)
    }
}
