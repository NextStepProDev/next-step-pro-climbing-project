import { describe, it, expect, vi, beforeEach } from 'vitest'

/*
 * Browsers report the language with a region — Chrome and Safari in Poland send "pl-PL" — and the
 * detector handed that through. The UI still came out Polish (i18next falls back from "pl-PL" to
 * "pl" for the strings), but every place that compares the code read "pl-PL" as "not Polish":
 * a Polish visitor got the ENGLISH news, courses and team pages (seen live, 2026-10-20), date-fns
 * fell back to the Polish locale for an English one, and the language pills highlighted nothing.
 * The code is cut to the two letters once, at detection, so nothing downstream has to.
 */
async function freshI18n() {
  vi.resetModules()
  return (await import('./i18n')).default
}

describe('detected language', () => {
  beforeEach(() => {
    localStorage.clear()
  })

  it('is the bare two-letter code when the browser reports a region', async () => {
    // jsdom's navigator says "en-US"
    const i18n = await freshI18n()
    expect(i18n.language).toBe('en')
  })

  it('cleans up a regional code an earlier visit left in storage', async () => {
    localStorage.setItem('i18nextLng', 'pl-PL')
    const i18n = await freshI18n()
    expect(i18n.language).toBe('pl')
  })

  it('falls back to English for a language the site does not have', async () => {
    localStorage.setItem('i18nextLng', 'de-DE')
    const i18n = await freshI18n()
    expect(i18n.language).toBe('en')
  })
})
