// aurora_forge.mjs v3 — генератор секрета пасхалки «Сердце Авроры» (Option C: checks + data-срезы).
// Использование:
//   node tools/aurora_forge.mjs [--scenario <path>] [--out <dir>]
//   --scenario <path>  сценарий с ответами (default: ./scenario.json)
//   --out <dir>        каталог вывода (default: app/src/main/java/eu/kanade/domain/easteregg/aurora)
// Создаёт:
//   <out>/AuroraVaultData.kt — константы для приложения (БЕЗ секрета, можно коммитить)
//   <out>/vault.json         — те же данные в JSON (для aurora_verify.mjs, НЕ коммитить)
//
// Схема ступени (Option C, 2026-09-10): salt + checks[] + data[], отдельного iv-поля НЕТ:
//   key_i     = PBKDF2-HMAC-SHA256(normalize(phrase_i), salt, 120000) -> 32B
//   checks[i] = base64(sha256(key_i))
//   data[i]   = base64(iv12 || AES-256-GCM(key_i, payloadJson)) — tag в конце, iv внутри среза
//   phrase_0 = canonical answer, phrase_1..n = aliases (в порядке сценария).
// VERSION = parseInt(sha256(конкатенация ВСЕХ checks в base64: ступени по порядку,
//           внутри ступени checks по порядку).hex.slice(0, 7), 16).
//
// scenario.json ДЕРЖАТЬ ВНЕ РЕПОЗИТОРИЯ — в нём ответы открытым текстом!

import crypto from 'node:crypto';
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const ITERATIONS = 120000;
const PACKAGE_DIR = 'app/src/main/java/eu/kanade/domain/easteregg/aurora';
const repoRoot = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');

// Должно бит в бит совпадать с AuroraVault.normalize()
// (JS \s уже покрывает NBSP; \uFEFF указан явно — зеркалит явный класс в Kotlin).
const normalize = (s) =>
  s.normalize('NFC').toLowerCase().replaceAll('ё', 'е').trim().replace(/[\s\uFEFF]+/g, ' ');

function fail(msg) {
  console.error('SCHEMA: ' + msg);
  process.exit(1);
}

// Whitelist ключей payload: AuroraPayload.kt + En-поля (Task 13). Неизвестный ключ — ERROR, не silent ignore.
const PAYLOAD_KEYS = new Set([
  'kind', 'riddle', 'riddleEn', 'echoTitle',
  'achievementTitle', 'achievementTitleEn', 'achievementDescription', 'descriptionEn',
  'holderTitle', 'holderTitleEn', 'letter', 'letterEn',
  'themeName', 'themeColors', 'themeMaterial', 'bonusPoints',
]);
const KEY_HINTS = {
  title: 'achievementTitle',
  holder: 'holderTitle',
  description: 'achievementDescription (EN-поле — descriptionEn)',
  achievementDescriptionEn: 'descriptionEn',
};

const nonEmptyString = (v) => typeof v === 'string' && v.trim().length > 0;

function validate(scenario) {
  if (!nonEmptyString(scenario.firstRiddle)) fail('firstRiddle обязателен (непустая строка)');
  if (!nonEmptyString(scenario.firstRiddleEn)) {
    fail('firstRiddleEn обязателен (непустая строка — EN-текст первой загадки)');
  }
  if (!Array.isArray(scenario.stages) || scenario.stages.length < 1) {
    fail('stages обязателен (массив >= 1)');
  }
  scenario.stages.forEach((s, i) => {
    const n = i + 1;
    if (typeof s.answer !== 'string') fail(`stage ${n}: answer обязателен`);
    const norm = normalize(s.answer);
    if (norm.length < 3 || norm.length > 64) {
      fail(`stage ${n}: нормализованный answer должен быть 3..64 символа (сейчас ${norm.length})`);
    }
    if (!s.payload || typeof s.payload.kind !== 'string') fail(`stage ${n}: payload.kind обязателен`);
    const isLast = i === scenario.stages.length - 1;
    if (isLast && s.payload.kind !== 'final') fail(`stage ${n}: последняя ступень должна быть kind=final`);
    if (!isLast && s.payload.kind !== 'riddle') fail(`stage ${n}: промежуточная ступень должна быть kind=riddle`);
    for (const key of Object.keys(s.payload)) {
      if (!PAYLOAD_KEYS.has(key)) {
        const hint = KEY_HINTS[key] ? ` — правильное имя: ${KEY_HINTS[key]}` : '';
        fail(`stage ${n}: неизвестный ключ payload "${key}"${hint}. Допустимы: ${[...PAYLOAD_KEYS].join(', ')}`);
      }
    }
    if (!isLast) {
      if (!nonEmptyString(s.payload.riddle)) {
        fail(`stage ${n}: у промежуточной ступени должна быть следующая загадка payload.riddle`);
      }
      if (!nonEmptyString(s.payload.riddleEn)) {
        fail(`stage ${n}: у промежуточной ступени обязателен payload.riddleEn (EN-текст следующей загадки)`);
      }
    }
    if (isLast) {
      if (!nonEmptyString(s.payload.achievementTitle)) {
        fail(`stage ${n}: финал обязан содержать непустой payload.achievementTitle`);
      }
      if (!nonEmptyString(s.payload.holderTitle)) {
        fail(`stage ${n}: финал обязан содержать непустой payload.holderTitle`);
      }
      if (!s.payload.themeMaterial || s.payload.themeMaterial.style !== 'aurora-metal') {
        fail(`stage ${n}: финал обязан содержать payload.themeMaterial (style="aurora-metal") — без него ` +
          'migrateIfNeeded трактует payload как stale и затирает прогресс (см. README_AURORA.md)');
      }
    }
    const aliases = s.aliases ?? [];
    if (!Array.isArray(aliases)) fail(`stage ${n}: aliases должен быть массивом строк`);
    const seen = new Set([norm]);
    aliases.forEach((a, j) => {
      if (typeof a !== 'string') fail(`stage ${n}: aliases[${j}] должен быть строкой`);
      const na = normalize(a);
      if (na.length < 3 || na.length > 64) {
        fail(`stage ${n}: aliases[${j}] после нормализации должен быть 3..64 символа (сейчас ${na.length})`);
      }
      if (na === norm) fail(`stage ${n}: aliases[${j}] после нормализации равен canonical-ответу`);
      if (seen.has(na)) fail(`stage ${n}: aliases[${j}] после нормализации дублирует другой alias`);
      seen.add(na);
    });
  });
  const answers = scenario.stages.map((s) => normalize(s.answer));
  if (new Set(answers).size !== answers.length) fail('ответы ступеней не должны повторяться');
}

const deriveKey = (phrase, salt) =>
  crypto.pbkdf2Sync(Buffer.from(normalize(phrase), 'utf8'), salt, ITERATIONS, 32, 'sha256');

// Ступень Option C: на каждый вариант ответа (canonical + aliases) — свой check и свой GCM-срез.
function forgeStage(answer, aliases, payload) {
  const salt = crypto.randomBytes(16);
  const plain = JSON.stringify(payload);
  const checks = [];
  const data = [];
  for (const phrase of [answer, ...aliases]) {
    const key = deriveKey(phrase, salt);
    checks.push(crypto.createHash('sha256').update(key).digest('base64'));
    const iv = crypto.randomBytes(12);
    const cipher = crypto.createCipheriv('aes-256-gcm', key, iv);
    // Java AES/GCM ожидает тег в конце шифртекста; iv — префиксом внутри среза.
    const ct = Buffer.concat([cipher.update(plain, 'utf8'), cipher.final(), cipher.getAuthTag()]);
    data.push(Buffer.concat([iv, ct]).toString('base64'));
  }
  return { salt: salt.toString('base64'), checks, data };
}

const ktEscape = (s) =>
  s.replace(/\\/g, '\\\\').replace(/"/g, '\\"').replace(/\$/g, '\\$').replace(/\n/g, '\\n');

const args = process.argv.slice(2);
let scenarioPath = './scenario.json';
let outDir = null;
for (let i = 0; i < args.length; i += 1) {
  if (args[i] === '--scenario' && args[i + 1]) { scenarioPath = args[++i]; }
  else if (args[i] === '--out' && args[i + 1]) { outDir = args[++i]; }
  else {
    console.error('usage: node tools/aurora_forge.mjs [--scenario <path>] [--out <dir>]');
    process.exit(1);
  }
}
if (!outDir) outDir = path.join(repoRoot, PACKAGE_DIR);

const scenario = JSON.parse(fs.readFileSync(scenarioPath, 'utf8'));
validate(scenario);
const stages = scenario.stages.map((s) => forgeStage(s.answer, s.aliases ?? [], s.payload));

// Детерминированная версия ваулта — из контрольных хешей ВСЕХ ступеней (формула — в шапке файла).
// Меняется при ЛЮБОЙ перегенерации (соли случайны) — это намеренно.
const version = parseInt(
  crypto.createHash('sha256').update(stages.flatMap((s) => s.checks).join('')).digest('hex').slice(0, 7),
  16,
);

const ktList = (items) => `listOf(${items.map((x) => `"${x}"`).join(', ')})`;
const stageBlocks = stages
  .map(
    (s) => `        AuroraStage(
            salt = "${s.salt}",
            checks = ${ktList(s.checks)},
            data = ${ktList(s.data)},
        ),`,
  )
  .join('\n');

const kt = `package eu.kanade.domain.easteregg.aurora

// СГЕНЕРИРОВАНО tools/aurora_forge.mjs — не редактировать вручную.
// В этом файле НЕТ секрета: только соли, контрольные хеши ключей (PBKDF2,
// ${ITERATIONS} итераций) и AES-256-GCM-срезы шифртекста (по одному на вариант
// ответа). Ответы и награда в кодовой базе не существуют — их невозможно
// извлечь анализом кода.
@Suppress("ktlint:standard:max-line-length")
object AuroraVaultData {

    // Версия ваулта для мягкой миграции прогресса (AuroraHeartManager).
    const val VERSION = ${version}

    const val FIRST_RIDDLE = "${ktEscape(scenario.firstRiddle)}"

    const val FIRST_RIDDLE_EN = "${ktEscape(scenario.firstRiddleEn)}"

    val STAGES = listOf(
${stageBlocks}
    )
}
`;

fs.mkdirSync(outDir, { recursive: true });
const ktPath = path.join(outDir, 'AuroraVaultData.kt');
fs.writeFileSync(ktPath, kt);
const vault = {
  version,
  firstRiddle: scenario.firstRiddle,
  firstRiddleEn: scenario.firstRiddleEn,
  stages,
};
fs.writeFileSync(path.join(outDir, 'vault.json'), JSON.stringify(vault, null, 2));
const totalChecks = stages.reduce((acc, s) => acc + s.checks.length, 0);
console.log(`OK: ${stages.length} stages, ${totalChecks} checks, vault version ${version} -> ${ktPath}`);
