package eu.kanade.domain.easteregg.aurora

import android.util.Base64
import java.security.MessageDigest
import java.text.Normalizer
import javax.crypto.Cipher
import javax.crypto.Mac
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Одна ступень квеста (Option C — per-phrase срезы). В коде НЕТ ни ответа,
 * ни награды: только соль, контрольные хеши ключей и AES-GCM-срезы
 * шифртекста — по одному на вариант ответа (canonical + aliases):
 * checks[i] = b64(sha256(key_i)), data[i] = b64(iv12 || GCM-шифртекст || tag16).
 * Подобрать ответ анализом кодовой базы невозможно — его можно только знать.
 */
data class AuroraStage(
    val salt: String,
    val checks: List<String>,
    val data: List<String>,
)

object AuroraVault {

    private const val ITERATIONS = 120_000

    /**
     * Unicode-пробелы вне ASCII-класса `\s` — вместе с ним дают ровно JS `\s`
     * из tools/aurora_forge.mjs (NBSP и BOM включены явно). Task 13 (K2c).
     */
    private const val UNICODE_SPACES =
        "\u00A0\u1680\u2000\u2001\u2002\u2003\u2004\u2005\u2006\u2007\u2008\u2009\u200A" +
            "\u2028\u2029\u202F\u205F\u3000\uFEFF"

    /** Класс схлопывания: `[\s\u00A0\u1680\u2000-\u200A\u2028\u2029\u202F\u205F\u3000\uFEFF]+`. */
    private val SPACES_REGEX =
        Regex("[\\s\\u00A0\\u1680\\u2000-\\u200A\\u2028\\u2029\\u202F\\u205F\\u3000\\uFEFF]+")

    /**
     * Нормализация ввода. ДОЛЖНА бит в бит совпадать с normalize()
     * в tools/aurora_forge.mjs: NFC, lowercase, ё→е, trim, схлопывание
     * пробелов. ПОРЯДОК операций заморожен — иначе хеши разъедутся с JS.
     * Alias-таблица удалена (Task 13): алиасы живут в checks ступеней.
     */
    fun normalize(input: String): String =
        Normalizer.normalize(input, Normalizer.Form.NFC)
            .lowercase()
            .replace('ё', 'е')
            .trim { it.isWhitespace() || it in UNICODE_SPACES }
            .replace(SPACES_REGEX, " ")

    /**
     * Пробует открыть ступень фразой (Option C): ОДИН PBKDF2 на попытку →
     * h = b64(sha256(key)) → idx = checks.indexOf(h); idx < 0 → null;
     * иначе AES-GCM-дешифровка среза data[idx] под тем же ключом
     * (iv — первые 12 байт среза, tag — последние 16). Any-match семантика
     * обеспечивается индексом, не перебором дешифровок. Ложное срабатывание
     * исключено: даже при коллизии контрольного хеша GCM-тег не сойдётся.
     */
    fun tryOpen(phrase: String, stage: AuroraStage): ByteArray? {
        val normalized = normalize(phrase)
        if (normalized.isEmpty()) return null
        val salt = Base64.decode(stage.salt, Base64.DEFAULT)
        val key = pbkdf2(normalized.encodeToByteArray(), salt, ITERATIONS, 32)
        val hash = Base64.encodeToString(
            MessageDigest.getInstance("SHA-256").digest(key),
            Base64.NO_WRAP,
        )
        val idx = stage.checks.indexOf(hash)
        if (idx < 0) return null
        val sliceB64 = stage.data.getOrNull(idx) ?: return null
        return runCatching {
            val slice = Base64.decode(sliceB64, Base64.DEFAULT)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(
                Cipher.DECRYPT_MODE,
                SecretKeySpec(key, "AES"),
                GCMParameterSpec(128, slice, 0, 12),
            )
            cipher.doFinal(slice, 12, slice.size - 12)
        }.getOrNull()
    }

    /**
     * PBKDF2-HMAC-SHA256 вручную поверх javax.crypto.Mac — чтобы поведение
     * с UTF-8 (кириллицей) было идентичным на всех версиях Android
     * и совпадало с crypto.pbkdf2Sync в генераторе.
     */
    private fun pbkdf2(password: ByteArray, salt: ByteArray, iterations: Int, keyLen: Int): ByteArray {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(password, "HmacSHA256"))
        val blockCount = (keyLen + 31) / 32
        val out = ByteArray(blockCount * 32)
        for (block in 1..blockCount) {
            mac.reset()
            mac.update(salt)
            mac.update(
                byteArrayOf(
                    (block ushr 24).toByte(),
                    (block ushr 16).toByte(),
                    (block ushr 8).toByte(),
                    block.toByte(),
                ),
            )
            var u = mac.doFinal()
            val t = u.copyOf()
            repeat(iterations - 1) {
                u = mac.doFinal(u)
                for (i in t.indices) t[i] = (t[i].toInt() xor u[i].toInt()).toByte()
            }
            System.arraycopy(t, 0, out, (block - 1) * 32, 32)
        }
        return out.copyOf(keyLen)
    }
}
