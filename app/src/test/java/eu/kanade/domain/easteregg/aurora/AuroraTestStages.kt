package eu.kanade.domain.easteregg.aurora

import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * Fixture-строитель ступеней ваулта (Task 15): валидные [AuroraStage] из fixture-фраз,
 * собранные ТОЙ ЖЕ криптой, что tools/aurora_forge.mjs (схема Option C):
 *  - key_i     = PBKDF2WithHmacSHA256(AuroraVault.normalize(phrase_i), random salt 16B, 120000, 256-bit);
 *  - checks[i] = b64(sha256(key_i));
 *  - data[i]   = b64(iv12 || AES-256-GCM(key_i, payloadJson UTF-8) || tag16) — тег в конце.
 *
 * Солти/iv случайные, как у forge: повторный вызов даёт ДРУГУЮ ступень — тесты сверяют
 * вскрываемость фразой, а не бинарные константы.
 *
 * Base64: fixture пишет через java.util.Base64.getEncoder() (без переносов == android NO_WRAP,
 * алфавиты идентичны). В тестах production-[AuroraVault.tryOpen] декодирует/сравнивает через
 * мост android.util.Base64 → java.util (mockkStatic-паттерн из AuroraQuestGateTest).
 *
 * Ответов реального ваулта здесь НЕТ — только нейтральные fixture-фразы ([TEST_PAYLOAD_JSON]).
 */
object AuroraTestStages {

    private const val ITERATIONS = 120_000
    private const val KEY_BITS = 256
    private const val SALT_SIZE = 16
    private const val IV_SIZE = 12
    private const val TAG_BITS = 128

    /** Нейтральный fixture-payload (kind=riddle + riddle/riddleEn/echoTitle), без реальных ответов. */
    const val TEST_PAYLOAD_JSON: String =
        "{\"kind\":\"riddle\",\"riddle\":\"fixture riddle one\",\"riddleEn\":\"fixture riddle EN\"," +
            "\"echoTitle\":\"fixture echo title\"}"

    /** Спецификация одной fixture-ступени для [buildStages]. */
    data class StageSpec(
        val phrase: String,
        val aliases: List<String> = emptyList(),
        val payloadJson: String = TEST_PAYLOAD_JSON,
    )

    /** Список ступеней для internal-seam конструктора [AuroraQuest]. */
    fun buildStages(vararg specs: StageSpec): List<AuroraStage> =
        specs.map { buildStage(it.phrase, it.aliases, it.payloadJson) }

    /** Ступень Option C: на каждую фразу (canonical + aliases) — свой check и свой GCM-срез. */
    fun buildStage(phrase: String, aliases: List<String>, payloadJson: String): AuroraStage {
        val random = SecureRandom()
        val salt = ByteArray(SALT_SIZE)
        random.nextBytes(salt)
        val checks = ArrayList<String>(1 + aliases.size)
        val data = ArrayList<String>(1 + aliases.size)
        for (fixturePhrase in listOf(phrase) + aliases) {
            val key = deriveKey(AuroraVault.normalize(fixturePhrase), salt)
            val hash = MessageDigest.getInstance("SHA-256").digest(key)
            checks += Base64.getEncoder().encodeToString(hash)
            val iv = ByteArray(IV_SIZE)
            random.nextBytes(iv)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(TAG_BITS, iv))
            val body = cipher.doFinal(payloadJson.toByteArray(Charsets.UTF_8))
            data += Base64.getEncoder().encodeToString(iv + body)
        }
        return AuroraStage(
            salt = Base64.getEncoder().encodeToString(salt),
            checks = checks,
            data = data,
        )
    }

    /**
     * PBKDF2 с явным salt и UTF-8 входом: OpenJDK (JDK 17) кодирует char[]-пароль в
     * PBEKeySpec как UTF-8, поэтому ключ байт-в-байт совпадает с crypto.pbkdf2Sync
     * из forge и с ручным pbkdf2 в [AuroraVault] (тоже UTF-8 байты).
     */
    private fun deriveKey(normalized: String, salt: ByteArray): ByteArray {
        val spec = PBEKeySpec(normalized.toCharArray(), salt, ITERATIONS, KEY_BITS)
        return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
    }
}
