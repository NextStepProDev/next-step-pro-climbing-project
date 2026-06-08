import { describe, it, expect } from 'vitest'
import { pickBestTranslation, getDefaultCourseContentLanguage } from './courseLanguages'

// Wspólna logika wyboru języka dla szczegółów kursu ORAZ artykułu — oba moduły
// używają dokładnie tych samych funkcji (CourseDetailPage + NewsDetailPage).

type Tr = { id: string; language: string }
const pl: Tr = { id: 'id-pl', language: 'pl' }
const en: Tr = { id: 'id-en', language: 'en' }
const es: Tr = { id: 'id-es', language: 'es' }

describe('pickBestTranslation', () => {
  it('zwraca dokładne dopasowanie, gdy preferowany język istnieje', () => {
    expect(pickBestTranslation([pl, en, es], 'es')).toBe(es)
    expect(pickBestTranslation([pl, en, es], 'pl')).toBe(pl)
    expect(pickBestTranslation([pl, en, es], 'en')).toBe(en)
  })

  it('PL + EN, wybór ES → otwiera EN (przypadek z wymagań)', () => {
    expect(pickBestTranslation([pl, en], 'es')).toBe(en)
  })

  it('gdy jest tylko jedno tłumaczenie — zawsze je zwraca', () => {
    expect(pickBestTranslation([pl], 'es')).toBe(pl)
    expect(pickBestTranslation([pl], 'en')).toBe(pl)
    expect(pickBestTranslation([es], 'pl')).toBe(es)
  })

  it('kolejność fallbacku: preferowany → en → pl → es', () => {
    expect(pickBestTranslation([pl, es], 'en')?.language).toBe('pl') // en brak → pl (przed es)
    expect(pickBestTranslation([pl, es], 'fr')?.language).toBe('pl') // fr/en brak → pl
    expect(pickBestTranslation([es], 'fr')?.language).toBe('es')     // zostaje tylko es
  })

  it('nieznany język bez żadnego z pl/en/es → pierwszy dostępny', () => {
    const de: Tr = { id: 'id-de', language: 'de' }
    expect(pickBestTranslation([de], 'fr')).toBe(de)
  })

  it('pusta lista lub undefined → undefined', () => {
    expect(pickBestTranslation([], 'pl')).toBeUndefined()
    expect(pickBestTranslation(undefined, 'pl')).toBeUndefined()
  })
})

describe('getDefaultCourseContentLanguage', () => {
  it('pl → pl, es → es', () => {
    expect(getDefaultCourseContentLanguage('pl')).toBe('pl')
    expect(getDefaultCourseContentLanguage('es')).toBe('es')
  })

  it('en oraz każdy inny język UI → en', () => {
    expect(getDefaultCourseContentLanguage('en')).toBe('en')
    expect(getDefaultCourseContentLanguage('de')).toBe('en')
    expect(getDefaultCourseContentLanguage('')).toBe('en')
  })
})
