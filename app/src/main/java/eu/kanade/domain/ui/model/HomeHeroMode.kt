package eu.kanade.domain.ui.model

import dev.icerock.moko.resources.StringResource
import tachiyomi.i18n.aniyomi.AYMR

enum class HomeHeroMode(val key: String, val titleRes: StringResource) {
    Continue("continue", AYMR.strings.pref_home_hero_mode_continue),
    Collage("collage", AYMR.strings.pref_home_hero_mode_collage),
    Hybrid("hybrid", AYMR.strings.pref_home_hero_mode_hybrid),
    ;

    companion object {
        fun fromKey(key: String?): HomeHeroMode =
            entries.firstOrNull { it.key == key } ?: Continue
    }
}
