import { describe, it, expect } from 'vitest'
import { pickBestTranslation, getDefaultCourseContentLanguage } from './courseLanguages'

// Shared language-selection logic for course details AND articles — both modules
// use exactly the same functions (CourseDetailPage + NewsDetailPage).

type Tr = { id: string; language: string }
const pl: Tr = { id: 'id-pl', language: 'pl' }
const en: Tr = { id: 'id-en', language: 'en' }
const es: Tr = { id: 'id-es', language: 'es' }

describe('pickBestTranslation', () => {
  it('returns the exact match when the preferred language exists', () => {
    expect(pickBestTranslation([pl, en, es], 'es')).toBe(es)
    expect(pickBestTranslation([pl, en, es], 'pl')).toBe(pl)
    expect(pickBestTranslation([pl, en, es], 'en')).toBe(en)
  })

  it('PL + EN, ES selected → opens EN (case from the requirements)', () => {
    expect(pickBestTranslation([pl, en], 'es')).toBe(en)
  })

  it('with only one translation — always returns it', () => {
    expect(pickBestTranslation([pl], 'es')).toBe(pl)
    expect(pickBestTranslation([pl], 'en')).toBe(pl)
    expect(pickBestTranslation([es], 'pl')).toBe(es)
  })

  it('fallback order: preferred → en → pl → es', () => {
    expect(pickBestTranslation([pl, es], 'en')?.language).toBe('pl') // no en → pl (before es)
    expect(pickBestTranslation([pl, es], 'fr')?.language).toBe('pl') // no fr/en → pl
    expect(pickBestTranslation([es], 'fr')?.language).toBe('es')     // only es left
  })

  it('unknown language with none of pl/en/es → first available', () => {
    const de: Tr = { id: 'id-de', language: 'de' }
    expect(pickBestTranslation([de], 'fr')).toBe(de)
  })

  it('empty list or undefined → undefined', () => {
    expect(pickBestTranslation([], 'pl')).toBeUndefined()
    expect(pickBestTranslation(undefined, 'pl')).toBeUndefined()
  })
})

describe('getDefaultCourseContentLanguage', () => {
  it('pl → pl, es → es', () => {
    expect(getDefaultCourseContentLanguage('pl')).toBe('pl')
    expect(getDefaultCourseContentLanguage('es')).toBe('es')
  })

  it('en and any other UI language → en', () => {
    expect(getDefaultCourseContentLanguage('en')).toBe('en')
    expect(getDefaultCourseContentLanguage('de')).toBe('en')
    expect(getDefaultCourseContentLanguage('')).toBe('en')
  })
})
