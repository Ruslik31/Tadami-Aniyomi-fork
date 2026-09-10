package eu.kanade.domain.easteregg.aurora

/**
 * Единый реестр маскировочных prefs-ключей пасхалки «Сердце Авроры».
 * Имена нарочно неприметные (под render pipeline) и ЗАМОРОЖЕНЫ байт-в-байт:
 * в SharedPreferences пользователей уже лежит прогресс под legacy-ключами
 * (страховка от случайного переименования — AuroraPrefKeysTest).
 */
object AuroraPrefKeys {
    const val STORE = "render_pipeline_cache"
    const val HINT = "rp_warm"
    const val STAGE = "rp_pass"
    const val RIDDLE = "rp_shader"
    const val DONE = "rp_baked"
    const val PAYLOAD = "rp_blob"
    const val ECHOES = "rp_trace"
    const val VER = "rp_rev"
    const val NIGHT = "rp_frames"
    const val WHISPER = "rp_hdr"
    const val SHOWN = "rp_shown" // comma separated stages that have auto-opened their riddle once
    const val LAST_NIGHT = "rp_lamp" // ключ последних засчитанных суток (анти-коллизия ночей, Task 3)
    const val PENDING = "rp_pend" // персистентная очередь отложенных эхо шины (Task 11)
}
