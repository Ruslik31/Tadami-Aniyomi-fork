package eu.kanade.domain.easteregg.aurora

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

/**
 * Значения ключей заморожены байт-в-байт: в SharedPreferences живых
 * пользователей уже лежит прогресс под legacy-именами, а новые ключи
 * (LAST_NIGHT, PENDING) зарезервированы планом aurora-heart-full-fix.
 * Если этот тест падает — чей-то прогресс будет потерян.
 */
class AuroraPrefKeysTest {

    @Test
    fun prefKeyValuesAreFrozenByteForByte() {
        AuroraPrefKeys.STORE shouldBe "render_pipeline_cache"
        AuroraPrefKeys.HINT shouldBe "rp_warm"
        AuroraPrefKeys.STAGE shouldBe "rp_pass"
        AuroraPrefKeys.RIDDLE shouldBe "rp_shader"
        AuroraPrefKeys.DONE shouldBe "rp_baked"
        AuroraPrefKeys.PAYLOAD shouldBe "rp_blob"
        AuroraPrefKeys.ECHOES shouldBe "rp_trace"
        AuroraPrefKeys.VER shouldBe "rp_rev"
        AuroraPrefKeys.NIGHT shouldBe "rp_frames"
        AuroraPrefKeys.WHISPER shouldBe "rp_hdr"
        AuroraPrefKeys.SHOWN shouldBe "rp_shown"
        AuroraPrefKeys.LAST_NIGHT shouldBe "rp_lamp"
        AuroraPrefKeys.PENDING shouldBe "rp_pend"
    }
}
