// Bundles every Iconify icon the UI uses into the build, so icons show without internet
// (by default @iconify/svelte downloads icons from api.iconify.design when a page needs them).
//
// Scans src/ for "prefix:name" strings, looks them up in the installed @iconify-json/* packages,
// and writes src/libs/icons.generated.json, which src/main.ts registers on startup.
import { readFileSync, readdirSync, statSync, writeFileSync, existsSync } from "fs"
import { join } from "path"
import { getIcons } from "@iconify/utils"

const files = []
const walk = dir => readdirSync(dir).forEach(f => {
  const p = join(dir, f)
  if (statSync(p).isDirectory()) walk(p)
  else if (/\.(svelte|ts)$/.test(f) && !f.endsWith(".generated.json")) files.push(p)
})
walk("src")

const names = new Set()
for (const f of files)
  for (const m of readFileSync(f, "utf8").matchAll(/["'`]([a-z0-9-]+):([a-z0-9-]+)["'`]/g))
    names.add(`${m[1]}:${m[2]}`)

const byPrefix = {}
for (const n of names) {
  const [prefix, name] = n.split(":")
  ;(byPrefix[prefix] ??= []).push(name)
}

const collections = []
for (const [prefix, icons] of Object.entries(byPrefix)) {
  const file = `node_modules/@iconify-json/${prefix}/icons.json`
  if (!existsSync(file)) continue // not an icon set (e.g. "sass:color")
  const subset = getIcons(JSON.parse(readFileSync(file, "utf8")), icons)
  if (subset) collections.push(subset)
}

writeFileSync("src/libs/icons.generated.json", JSON.stringify(collections))
console.log(`Bundled ${collections.reduce((n, c) => n + Object.keys(c.icons).length, 0)} icons from ${collections.length} sets`)
