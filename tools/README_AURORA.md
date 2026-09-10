# Инструменты «Сердца Авроры»

## Схема ваулта (Option C, с 2026-09-10)

Одна ступень = `salt` + `checks: List<String>` + `data: List<String>`; отдельного поля `iv` НЕТ:

- `key_i = PBKDF2-HMAC-SHA256(normalize(phrase_i), salt, 120000)` → 32 байта;
- `checks[i] = base64(sha256(key_i))`;
- `data[i] = base64(iv12 || AES-256-GCM(key_i, payloadJson))` — auth-tag в конце среза, iv — первые 12 байт;
- `phrase_0` — канонический ответ, `phrase_1..n` — `aliases` из сценария (в порядке сценария);
  payload дублируется в каждом срезе; при отсутствии алиасов раздувание нулевое.

Семантика `AuroraVault.tryOpen` (Kotlin, Task 13): ОДИН PBKDF2 на попытку →
`h = base64(sha256(key))` → `idx = checks.indexOf(h)`; `idx < 0` → null;
иначе дешифровка `data[idx]` под `key` (iv извлекается из среза).

Почему срезы, а не один шифртекст: GCM дешифруется только тем ключом, под которым зашифрован, —
алиас прошёл бы hash-гейт, но не смог бы расшифровать общий шифртекст (AEADBadTag).

Нормализация (JS и Kotlin бит в бит): NFC → lowercase → `ё`→`е` → trim →
схлопывание `[\s\uFEFF]+` в один пробел (JS `\s` уже покрывает NBSP; `\uFEFF` указан явно).

## aurora_forge.mjs (v3)

Генерирует `AuroraVaultData.kt` (можно коммитить) и `vault.json` (для проверки, НЕ коммитить).

```bash
node tools/aurora_forge.mjs --scenario scenario.json            # вывод в пакет aurora (default --out)
node tools/aurora_forge.mjs --scenario scenario.json --out DIR  # dry-run в DIR (пакет не трогается)
```

- `--scenario <path>` — default `./scenario.json`;
- `--out <dir>` — default `app/src/main/java/eu/kanade/domain/easteregg/aurora`;
- шаблон Kotlin несёт `@Suppress("ktlint:standard:max-line-length")` (как закоммиченный файл)
  и константу `FIRST_RIDDLE_EN` рядом с `FIRST_RIDDLE`.

### Схема scenario.json

```jsonc
{
  "firstRiddle": "текст первой загадки",
  "firstRiddleEn": "EN-текст первой загадки",
  "stages": [
    { "answer": "ответ1",
      "aliases": ["alias1", "alias2"],   // опционально; EN-варианты ответа (демо-ступень 0 несёт 13)
      "payload": { "kind": "riddle", "echoTitle": "…", "riddle": "следующая загадка", "riddleEn": "…" } },
    { "answer": "ответN",
      "payload": {
        "kind": "final",
        "achievementTitle": "…", "achievementTitleEn": "…",
        "achievementDescription": "…", "descriptionEn": "…",
        "holderTitle": "…", "holderTitleEn": "…",
        "letter": "…", "letterEn": "…",
        "themeName": "…",
        "themeColors": { "primary": "#B6F04C", "secondary": "…", "background": "…",
                         "surface": "…", "accent": "…" },
        "themeMaterial": { "style": "aurora-metal", "base": "#0A1626", "sheen": "#EAF6FF",
                           "iridescence": "0.45", "gloss": "0.8" },
        "bonusPoints": 0
      } }
  ]
}
```

Правильные имена ключей payload — из `AuroraPayload.kt`: `achievementTitle`, `holderTitle`,
`achievementDescription` (НЕ `title`/`holder`/`description`!); EN-пара для описания — `descriptionEn`
(имя зафиксировано планом Task 13). Неизвестный ключ → `SCHEMA:`-ERROR с подсказкой — silent ignore
запрещён: kotlinx-десериализация молча выбросила бы опечатку, и текст потерялся бы в UI.

### Валидация (v3)

- `firstRiddle` и `firstRiddleEn` — обязательные непустые строки;
- каждый `answer` после нормализации — 3..64 символа, без дублей между ступенями;
- `aliases` (опциональны): после нормализации 3..64 символа, уникальны в пределах ступени
  и не равны canonical-ответу;
- промежуточные ступени — `kind: "riddle"` с непустыми `riddle` И `riddleEn`; последняя — `kind: "final"`;
- финал ОБЯЗАН содержать непустые `achievementTitle` и `holderTitle`;
- финал ОБЯЗАН содержать `themeMaterial` со `style: "aurora-metal"` — ERROR, не warning:
  `AuroraHeartManager.migrateIfNeeded` трактует payload без `themeMaterial` как устаревший
  и сбрасывает прогресс в wipe-loop;
- неизвестные ключи payload — ERROR со списком допустимых имён.

### Живая тема (`themeMaterial`)

Все значения — строки:

- `style` — обязательно `"aurora-metal"` (иначе материал игнорируется клиентом);
- `base` — hex-цвет «металла» (по умолчанию берётся `themeColors.surface`);
- `sheen` — hex-цвет блика (по умолчанию `#EAF6FF`);
- `iridescence`, `gloss` — числа `"0".."1"` (по умолчанию `0.4` / `0.8`).

При наличии этих данных клиент (v3.1) оживляет наградную тему: акцентные
цвета бликуют от наклона устройства и «дышат» (`AuroraPrimeColors.kt`),
а hero-поверхности рендерятся шейдерным «живым металлом»
(`Modifier.auroraMetal`, `AuroraLivingMaterial.kt`).

Канон AURORA_PRIME (Q3 + решение контроллера, Task 12 Round 3): `themeColors` сценария должны
совпадать со статической палитрой (`AuroraPrimeColorScheme.darkScheme` / `AuroraPayload.fallback()`):
primary `#B6F04C`, secondary `#C25CFF`, accent `#2FC9A0` (tertiary схемы), background `#030810`,
surface `#081020` — тема выглядит идентично и с payload, и без него (fallback-путь Task 9).

### Версия ваулта (VERSION) — точная формула

```text
VERSION = parseInt( sha256( C ).hex.slice(0, 7), 16 )
C = конкатенация БЕЗ разделителей (UTF-8) base64-строк ВСЕХ checks ВСЕХ ступеней:
    ступени в порядке 0..N-1, внутри ступени checks[0] (canonical) первым,
    затем checks алиасов в порядке сценария.
```

Диапазон: 0..268435455 (7 hex-цифр). `AuroraHeartManager` сравнивает VERSION с сохранённым и при
несовпадении мягко сбрасывает прогресс — чистить данные приложения после перегенерации не нужно.
Соли случайны, поэтому VERSION меняется при КАЖДОЙ перегенерации — это намеренно.

## aurora_verify.mjs (v3)

```bash
node tools/aurora_verify.mjs --scenario scenario.json vault.json        # обычная верификация
node tools/aurora_verify.mjs --release [vault.json|AuroraVaultData.kt]  # релиз-гард
```

Обычная верификация: для каждой ступени canonical И каждый alias обязаны открыть ступень
(PBKDF2 → sha256 → индекс в `checks` → дешифровка среза `data[idx]`), payload должен байт в байт
совпасть со сценарием; неверный ответ не должен матчить ни один check. Exit 0 — PASS, 1 — FAIL.

### Релиз-гард (`--release`)

Без пути читает `app/src/main/java/eu/kanade/domain/easteregg/aurora/AuroraVaultData.kt`
(распознаёт и legacy-схему с одним `check`). Гард пересчитывает PBKDF2-хеши ТРЁХ публично
скомпрометированных демо-ответов («час волка», «sigil:2-5-8», «категория:guovssahas») на соли
каждой ступени и сравнивает с `checks[0]`:

- совпадение → exit 1: «ДЕМО-ВАУЛТ: перед релизом создай свой scenario.json и перегенерируй»;
- нет совпадений → exit 0 (в комбо-режиме — после обычной верификации).

## Каналы ответов (после Task 6)

Каналы `ritual:` и `search:` УДАЛЕНЫ. Действующий контракт:

- поисковый текст идёт напрямую в `manager.offer(query)` — любой поисковый запрос является
  кандидатом (без префиксов);
- `sigil:` — 3..9 ячеек, значения 1..9, БЕЗ повторяющихся ячеек (пример: `sigil:2-5-8`);
- имя категории — `категория:<имя>` (`AuroraChannels.named`).

## Релиз-чек-лист

1. Создать свой приватный `scenario.json` (свои ответы, НЕ демо; `aliases` опциональны).
2. `node tools/aurora_forge.mjs --scenario scenario.json`
3. `node tools/aurora_verify.mjs --scenario scenario.json` + путь к `vault.json` в пакете → PASS.
4. `node tools/aurora_verify.mjs --release` → exit 0 (демо-ответы не найдены).
5. Удалить `vault.json` из пакета.
6. НИКОГДА не коммитить `scenario*.json` / `vault.json`.

## Безопасность

- `scenario*.json` и `vault.json` — НИКОГДА не коммитить (см. .gitignore);
- ответы и награда существуют только в виде PBKDF2-хешей и AES-GCM-срезов; в коде нет ни ответов,
  ни алиасов (alias-таблица Kotlin удалена в Task 13);
- не трогать `normalize()` и `ITERATIONS`: они должны бит в бит совпадать с AuroraVault.kt
  (включая класс `[\s\uFEFF]+`);
- юнит-тесты контракта: `AuroraNormalizeTest`, `AuroraVaultChecksTest` (Task 13).
