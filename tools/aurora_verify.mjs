// aurora_verify.mjs v3 — проверка, что цепочка решается ответами из сценария (Option C: checks + data-срезы).
// Повторяет логику AuroraVault.tryOpen (Kotlin, Task 13) один в один:
//   ОДИН PBKDF2 на попытку -> h = b64(sha256(key)) -> idx = checks.indexOf(h) -> idx < 0: null,
//   иначе AES-256-GCM-дешифровка data[idx] (iv = первые 12 байт среза, tag = последние 16).
// Использование:
//   node tools/aurora_verify.mjs --scenario scenario.json vault.json        — обычная верификация
//   node tools/aurora_verify.mjs --release [vault.json|AuroraVaultData.kt]  — релиз-гард (демо-ответы)
//     без пути гард читает app/src/main/java/eu/kanade/domain/easteregg/aurora/AuroraVaultData.kt
// Exit-коды: 0 — OK; 1 — FAIL верификации ИЛИ найден демо-ваулт (--release).

import crypto from 'node:crypto';
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const ITERATIONS = 120000;
const PACKAGE_KT = 'app/src/main/java/eu/kanade/domain/easteregg/aurora/AuroraVaultData.kt';
const repoRoot = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');

// Демо-ответы публично скомпрометированы (git-история, старая alias-таблица) — захардкожены как гард.
const DEMO_ANSWERS = ['час волка', 'sigil:2-5-8', 'категория:guovssahas'];

// Должно бит в бит совпадать с AuroraVault.normalize() (JS \s покрывает NBSP; \uFEFF указан явно).
const normalize = (s) =>
  s.normalize('NFC').toLowerCase().replaceAll('ё', 'е').trim().replace(/[\s\uFEFF]+/g, ' ');

const deriveKey = (phrase, salt) =>
  crypto.pbkdf2Sync(Buffer.from(normalize(phrase), 'utf8'), salt, ITERATIONS, 32, 'sha256');

const checkOf = (key) => crypto.createHash('sha256').update(key).digest('base64');

function tryOpen(phrase, stage) {
  const key = deriveKey(phrase, Buffer.from(stage.salt, 'base64'));
  const idx = stage.checks.indexOf(checkOf(key));
  if (idx < 0) return null;
  const raw = Buffer.from(stage.data[idx], 'base64');
  const iv = raw.subarray(0, 12);
  const body = raw.subarray(12);
  const d = crypto.createDecipheriv('aes-256-gcm', key, iv);
  d.setAuthTag(body.subarray(body.length - 16));
  try {
    return Buffer.concat([d.update(body.subarray(0, body.length - 16)), d.final()]).toString('utf8');
  } catch {
    return null; // хеш совпал, но GCM-тег не сошёлся — на практике недостижимо
  }
}

function usage() {
  console.error('usage: node tools/aurora_verify.mjs [--scenario <path>] [--release] [vault.json|AuroraVaultData.kt]');
  process.exit(1);
}

const args = process.argv.slice(2);
let scenarioPath = null;
let release = false;
const positional = [];
for (let i = 0; i < args.length; i += 1) {
  if (args[i] === '--scenario' && args[i + 1]) scenarioPath = args[++i];
  else if (args[i] === '--release') release = true;
  else if (args[i].startsWith('--')) usage();
  else positional.push(args[i]);
}
if (!scenarioPath && !release) usage();
if (scenarioPath && positional.length !== 1) {
  console.error('SCHEMA: для обычной верификации нужен ровно один путь к vault.json');
  process.exit(1);
}
if (scenarioPath && !positional[0].endsWith('.json')) {
  console.error('SCHEMA: обычная верификация поддерживает только vault.json (не .kt)');
  process.exit(1);
}

// Разбор ступеней (salt + checks) из сгенерированного Kotlin-файла — для --release без vault.json.
// Поддерживает и legacy-схему (одно поле check) — до Task 13 ваулт в пакете ещё старый.
function parseKtStages(kt) {
  const stages = [];
  for (const chunk of kt.split('AuroraStage(').slice(1)) {
    const salt = chunk.match(/salt\s*=\s*"([^"]+)"/)?.[1];
    const listBody = chunk.match(/checks\s*=\s*listOf\(([^)]*)\)/)?.[1];
    const legacy = chunk.match(/\bcheck\s*=\s*"([^"]+)"/)?.[1];
    let checks = [];
    if (listBody !== undefined) {
      checks = listBody.split(',').map((x) => x.trim().replace(/^"|"$/g, '')).filter(Boolean);
    } else if (legacy !== undefined) {
      checks = [legacy];
    }
    if (salt && checks.length) stages.push({ salt, checks });
  }
  return stages;
}

// Гард: пересчёт checks-хешей трёх демо-ответов на соли каждой ступени; совпадение с checks[0] = демо-ваулт.
function releaseGuard(stages) {
  const hits = [];
  stages.forEach((stage, i) => {
    for (const answer of DEMO_ANSWERS) {
      const h = checkOf(deriveKey(answer, Buffer.from(stage.salt, 'base64')));
      if (stage.checks[0] === h) hits.push(`stage ${i + 1}: checks[0] == PBKDF2-хеш демо-ответа «${answer}»`);
    }
  });
  return hits;
}

let failed = false;

if (scenarioPath) {
  const scenario = JSON.parse(fs.readFileSync(scenarioPath, 'utf8'));
  const vault = JSON.parse(fs.readFileSync(positional[0], 'utf8'));
  vault.stages.forEach((stage, i) => {
    const n = i + 1;
    const sc = scenario.stages[i];
    if (!sc) { console.error(`FAIL stage ${n}: в сценарии нет соответствующей ступени`); failed = true; return; }
    if (tryOpen('неправильный ответ', stage) !== null) {
      console.error(`FAIL stage ${n}: открылась неверным ответом!`);
      failed = true;
      return;
    }
    const phrases = [sc.answer, ...(sc.aliases ?? [])];
    if (stage.checks.length !== phrases.length || stage.data.length !== phrases.length) {
      console.error(`FAIL stage ${n}: checks/data должны быть размером 1 + число aliases (${phrases.length})`);
      failed = true;
      return;
    }
    let stageFailed = false;
    let sample = null;
    for (const phrase of phrases) {
      const plain = tryOpen(phrase, stage);
      if (plain === null) {
        console.error(`FAIL stage ${n}: не открылась ответом «${phrase}»`);
        stageFailed = true;
        continue;
      }
      if (plain !== JSON.stringify(sc.payload)) {
        console.error(`FAIL stage ${n}: payload для «${phrase}» не совпадает со сценарием`);
        stageFailed = true;
        continue;
      }
      if (sample === null) sample = JSON.parse(plain);
    }
    if (stageFailed) { failed = true; return; }
    const aliasCount = phrases.length - 1;
    console.log(
      `stage ${n} OK (kind=${sample.kind}${sample.echoTitle ? ', ' + sample.echoTitle : ''}): ` +
        `canonical + ${aliasCount}/${aliasCount} aliases открывают, wrong answer отклонён`,
    );
  });
}

let guardFailed = false;
if (release) {
  const target = positional[0] ?? path.join(repoRoot, PACKAGE_KT);
  const text = fs.readFileSync(target, 'utf8');
  const stages = target.endsWith('.json') ? JSON.parse(text).stages : parseKtStages(text);
  if (!stages.length) {
    console.error(`FAIL --release: не удалось разобрать ступени из ${target}`);
    guardFailed = true;
  } else {
    const hits = releaseGuard(stages);
    if (hits.length) {
      console.error('ДЕМО-ВАУЛТ: перед релизом создай свой scenario.json и перегенерируй ' +
        '(см. README_AURORA.md «Релиз-чек-лист»)');
      hits.forEach((h) => console.error('  ' + h));
      guardFailed = true;
    } else {
      console.log(`RELEASE OK: демо-ответы не найдены в checks ${stages.length} ступеней (${target})`);
    }
  }
}

process.exit(failed || guardFailed ? 1 : 0);
