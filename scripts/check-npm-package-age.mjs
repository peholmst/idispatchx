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

// Cache: packageName → { version: isoString } from the packument's "time" field
const timeCache = new Map();

async function fetchTimes(name) {
  if (timeCache.has(name)) return;
  try {
    const resp = await fetch(registryUrl(name));
    timeCache.set(name, resp.ok ? (await resp.json()).time ?? {} : {});
  } catch {
    timeCache.set(name, {});
  }
}

// Collect unique name@version pairs across all lockfiles
const packages = new Map(); // "name@version" → { name, version, lockfile }

for (const lockfilePath of lockfilePaths) {
  const { packages: pkgs = {} } = JSON.parse(readFileSync(lockfilePath, 'utf-8'));
  for (const [key, info] of Object.entries(pkgs)) {
    if (!key || !info.version) continue;
    const match = key.match(/(?:^|.*\/)node_modules\/(.+)$/);
    if (!match) continue;
    const name = match[1];
    const id = `${name}@${info.version}`;
    if (!packages.has(id)) packages.set(id, { name, version: info.version, lockfile: lockfilePath });
  }
}

console.log(`Checking ${packages.size} package(s) across ${lockfilePaths.length} lockfile(s) (minimum age: ${MIN_AGE_DAYS} day(s))...`);

// Fetch packuments in parallel, respecting CONCURRENCY limit
const uniqueNames = [...new Set([...packages.values()].map(p => p.name))];
for (let i = 0; i < uniqueNames.length; i += CONCURRENCY) {
  await Promise.all(uniqueNames.slice(i, i + CONCURRENCY).map(fetchTimes));
}

// Evaluate
const failures = [];
for (const { name, version, lockfile } of packages.values()) {
  const timeStr = timeCache.get(name)?.[version];
  if (!timeStr) continue;
  const publishedAt = new Date(timeStr);
  const ageMs = NOW - publishedAt.getTime();
  if (ageMs < MIN_AGE_MS) {
    const ageDays = Math.floor(ageMs / (24 * 60 * 60 * 1000));
    failures.push({ name, version, ageDays, publishedAt, lockfile });
  }
}

if (failures.length > 0) {
  console.error(`\nFAIL: ${failures.length} package(s) do not meet the minimum release age of ${MIN_AGE_DAYS} day(s):\n`);
  for (const { name, version, ageDays, publishedAt, lockfile } of failures) {
    const date = publishedAt.toISOString().slice(0, 10);
    console.error(`  ${name}@${version}  published ${date} (${ageDays} day(s) ago)  [${lockfile}]`);
  }
  process.exit(1);
}

console.log(`OK: All ${packages.size} package(s) meet the minimum release age of ${MIN_AGE_DAYS} day(s).`);
