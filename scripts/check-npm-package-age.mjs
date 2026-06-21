#!/usr/bin/env node
/**
 * Verifies that all NPM packages listed in package-lock.json files were published
 * at least MIN_AGE_DAYS days ago, guarding against supply chain attacks that exploit
 * freshly-published packages before they attract scrutiny.
 *
 * Usage:  node scripts/check-npm-package-age.mjs <package-lock.json> [...]
 * Env:    MIN_AGE_DAYS  – minimum days since publication (default: 3)
 */

import { readFileSync } from 'fs';

const MIN_AGE_DAYS = parseInt(process.env.MIN_AGE_DAYS ?? '3', 10);
const MIN_AGE_MS = MIN_AGE_DAYS * 24 * 60 * 60 * 1000;
const NOW = Date.now();
const CONCURRENCY = 10;

const lockfilePaths = process.argv.slice(2);
if (lockfilePaths.length === 0) {
  console.error('Usage: node check-npm-package-age.mjs <package-lock.json> [...]');
  process.exit(2);
}

function registryUrl(name) {
  // Scoped packages: @scope/pkg → @scope%2Fpkg
  return `https://registry.npmjs.org/${name.startsWith('@') ? name.replace('/', '%2F') : name}`;
}

// Cache: registryName → { version: isoString } | null (null means fetch failed)
const timeCache = new Map();

async function fetchTimes(name) {
  if (timeCache.has(name)) return;
  try {
    const resp = await fetch(registryUrl(name));
    if (!resp.ok) {
      console.error(`  registry error for ${name}: HTTP ${resp.status}`);
      timeCache.set(name, null);
      return;
    }
    timeCache.set(name, (await resp.json()).time ?? null);
  } catch (err) {
    console.error(`  fetch failed for ${name}: ${err.message}`);
    timeCache.set(name, null);
  }
}

// Collect unique registryName@version pairs across all lockfiles.
// Only packages resolved from the public npm registry are checked; workspace,
// git, and private-registry packages are skipped.
const packages = new Map(); // "registryName@version" → { registryName, version, lockfile }

for (const lockfilePath of lockfilePaths) {
  const { packages: pkgs = {} } = JSON.parse(readFileSync(lockfilePath, 'utf-8'));
  for (const [key, info] of Object.entries(pkgs)) {
    if (!key || !info.version) continue;
    if (!info.resolved?.startsWith('https://registry.npmjs.org/')) continue;
    // Match the package name after the last node_modules/ in the path.
    // Handles nesting (a/node_modules/b) and scoped packages (@scope/pkg).
    const match = key.match(/node_modules\/(@[^/]+\/[^/]+|[^/]+)$/);
    if (!match) continue;
    // info.name is the canonical registry name; it differs from match[1] for aliased
    // packages (e.g. foo: npm:real-pkg@1.0.0 → key is node_modules/foo, name is real-pkg).
    const registryName = info.name ?? match[1];
    const id = `${registryName}@${info.version}`;
    if (!packages.has(id)) packages.set(id, { registryName, version: info.version, lockfile: lockfilePath });
  }
}

console.log(`Checking ${packages.size} package(s) across ${lockfilePaths.length} lockfile(s) (minimum age: ${MIN_AGE_DAYS} day(s))...`);

// Fetch packuments in parallel, respecting CONCURRENCY limit
const uniqueNames = [...new Set([...packages.values()].map(p => p.registryName))];
for (let i = 0; i < uniqueNames.length; i += CONCURRENCY) {
  await Promise.all(uniqueNames.slice(i, i + CONCURRENCY).map(fetchTimes));
}

// Evaluate — fail closed: any package whose publish time cannot be verified also fails
const failures = [];
for (const { registryName, version, lockfile } of packages.values()) {
  const times = timeCache.get(registryName);
  if (times === null) {
    failures.push({ id: `${registryName}@${version}`, reason: 'registry unavailable', lockfile });
    continue;
  }
  const timeStr = times?.[version];
  if (!timeStr) {
    failures.push({ id: `${registryName}@${version}`, reason: 'publish time not found in registry', lockfile });
    continue;
  }
  const publishedAt = new Date(timeStr);
  const ageMs = NOW - publishedAt.getTime();
  if (ageMs < MIN_AGE_MS) {
    const ageDays = Math.floor(ageMs / (24 * 60 * 60 * 1000));
    const date = publishedAt.toISOString().slice(0, 10);
    failures.push({ id: `${registryName}@${version}`, reason: `published ${date} (${ageDays} day(s) ago)`, lockfile });
  }
}

if (failures.length > 0) {
  console.error(`\nFAIL: ${failures.length} package(s) failed the minimum release age check (${MIN_AGE_DAYS} day(s)):\n`);
  for (const { id, reason, lockfile } of failures) {
    console.error(`  ${id}  —  ${reason}  [${lockfile}]`);
  }
  process.exit(1);
}

console.log(`OK: All ${packages.size} package(s) meet the minimum release age of ${MIN_AGE_DAYS} day(s).`);
