// Head-only prerendering for static/listing routes.
//
// Why: the app is a client-side SPA, so nginx serves the same generic index.html
// for every static route. To bots (incl. Googlebot) every URL looks byte-identical
// with no <link rel="canonical"> -> Google reports "Duplicate without user-selected
// canonical" and skips indexing. Detail pages (/aktualnosci/<id>, ...) are already
// handled for bots by the backend OgController via nginx rewrites; only the static
// and listing routes lack per-page <head>.
//
// This runs after `vite build`. It clones dist/index.html for each static route and
// rewrites <title>, <meta name="description">, og: tags, injects <link rel="canonical">
// and (for multilingual routes) hreflang alternates. Body stays SPA-rendered.
//
// Meta text mirrors each page's <PageHead> props (src/components/ui/PageHead.tsx) and
// is read from the Polish i18n files, so the raw HTML matches what react-helmet sets
// after hydration (no title flicker / mismatch).
//
// Resilient by design: any failure logs a warning and leaves the SPA fallback intact
// (exit code stays 0 so the build never breaks).

import { readFileSync, writeFileSync, mkdirSync } from 'node:fs'
import { dirname, join, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'

const SITE_NAME = 'Next Step Pro Climbing'
const BASE_URL = 'https://nextsteppro.pl'
const LANGS = ['pl', 'en', 'es']

const here = dirname(fileURLToPath(import.meta.url))
const root = resolve(here, '..')
const distIndex = join(root, 'dist', 'index.html')

function escapeAttr(s) {
  return String(s)
    .replace(/&/g, '&amp;')
    .replace(/"/g, '&quot;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
}

function escapeText(s) {
  return String(s)
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
}

function loadLocale(name) {
  return JSON.parse(readFileSync(join(root, 'src', 'locales', 'pl', `${name}.json`), 'utf-8'))
}

try {
  const template = readFileSync(distIndex, 'utf-8')

  const home = loadLocale('home')
  const calendar = loadLocale('calendar')
  const faq = loadLocale('faq')
  const common = loadLocale('common')

  // path -> meta. Keys mirror the <PageHead> props on each page.
  const routes = [
    { path: '/', title: null, description: home.metaDescription, multilang: false },
    { path: '/calendar', title: calendar.title, description: calendar.metaDescription, multilang: false },
    { path: '/kursy', title: common.courses.title, description: common.courses.metaDescription, multilang: true },
    { path: '/aktualnosci', title: common.news.title, description: common.news.metaDescription, multilang: true },
    { path: '/team/instruktorzy', title: common.team.instructors, description: common.team.instructorsMetaDescription, multilang: true },
    { path: '/team/zawodnicy', title: common.team.competitors, description: common.team.competitorsMetaDescription, multilang: true },
    { path: '/galeria', title: common.gallery.title, description: common.gallery.metaDescription, multilang: false },
    { path: '/filmy', title: common.videos.title, description: common.videos.metaDescription, multilang: false },
    { path: '/kontakt', title: common.contact.title, description: common.contact.metaDescription, multilang: false },
    { path: '/faq', title: 'FAQ', description: faq.metaDescription, multilang: false },
  ]

  let written = 0
  for (const route of routes) {
    const fullTitle = route.title ? `${route.title} | ${SITE_NAME}` : SITE_NAME
    const url = `${BASE_URL}${route.path}`
    const desc = route.description ?? ''

    let html = template

    // <title>
    html = html.replace(/<title>[\s\S]*?<\/title>/, `<title>${escapeText(fullTitle)}</title>`)

    // <meta name="description">
    html = html.replace(
      /<meta\s+name="description"\s+content="[^"]*"\s*\/?>/,
      `<meta name="description" content="${escapeAttr(desc)}" />`,
    )

    // og:title / og:description / og:url
    html = html.replace(
      /<meta\s+property="og:title"\s+content="[^"]*"\s*\/?>/,
      `<meta property="og:title" content="${escapeAttr(fullTitle)}" />`,
    )
    html = html.replace(
      /<meta\s+property="og:description"\s+content="[^"]*"\s*\/?>/,
      `<meta property="og:description" content="${escapeAttr(desc)}" />`,
    )
    html = html.replace(
      /<meta\s+property="og:url"\s+content="[^"]*"\s*\/?>/,
      `<meta property="og:url" content="${escapeAttr(url)}" />`,
    )

    // canonical + hreflang injected before </head>
    let inject = `    <link rel="canonical" href="${escapeAttr(url)}" />\n`
    if (route.multilang) {
      for (const lang of LANGS) {
        const alt = `${url}?language=${lang}`
        inject += `    <link rel="alternate" hreflang="${lang}" href="${escapeAttr(alt)}" />\n`
      }
      inject += `    <link rel="alternate" hreflang="x-default" href="${escapeAttr(`${url}?language=pl`)}" />\n`
    }
    html = html.replace('</head>', `${inject}  </head>`)

    // Write: home overwrites dist/index.html, others go to a flat dist/<path>.html
    // so nginx serves /faq from /faq.html with no trailing-slash redirect and the
    // served URL matches the canonical (.../faq) exactly.
    const outFile =
      route.path === '/'
        ? distIndex
        : join(root, 'dist', `${route.path.replace(/^\//, '')}.html`)
    mkdirSync(dirname(outFile), { recursive: true })
    writeFileSync(outFile, html, 'utf-8')
    written++
  }

  console.log(`[prerender] generated ${written} static route page(s)`)
} catch (err) {
  console.warn(`[prerender] skipped (SPA fallback kept): ${err?.message ?? err}`)
}
