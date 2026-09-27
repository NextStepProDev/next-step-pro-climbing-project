import { describe, it, expect } from 'vitest'
import { foldForSearch, matchesPersonQuery } from './personSearch'

describe('foldForSearch', () => {
  it('strips Polish diacritics, including the one NFD cannot decompose', () => {
    // `ł` is a letter in its own right with no combining form, so NFD leaves it untouched — the
    // one accent that would silently escape a generic diacritic strip.
    expect(foldForSearch('Zgłoszenia')).toBe('zgloszenia')
    expect(foldForSearch('Użytkownicy')).toBe('uzytkownicy')
    expect(foldForSearch('Pieniądze')).toBe('pieniadze')
  })
})

describe('matchesPersonQuery', () => {
  const anna = { firstName: 'Anna', lastName: 'Kowalska', email: 'anna@example.com', nickname: 'Wiewiórka' }
  const lukasz = { firstName: 'Łukasz', lastName: 'Żółć' }

  it('matches everyone when nothing has been typed', () => {
    expect(matchesPersonQuery(anna, '')).toBe(true)
    expect(matchesPersonQuery(anna, '   ')).toBe(true)
  })

  it('matches two letters from either the first or the last name', () => {
    expect(matchesPersonQuery(anna, 'an')).toBe(true)
    expect(matchesPersonQuery(anna, 'ko')).toBe(true)
    expect(matchesPersonQuery(anna, 'KO')).toBe(true)
  })

  it('matches across the space between first and last name', () => {
    expect(matchesPersonQuery(anna, 'anna kow')).toBe(true)
  })

  it('finds Polish names typed without Polish letters', () => {
    expect(matchesPersonQuery(lukasz, 'lukasz')).toBe(true)
    expect(matchesPersonQuery(lukasz, 'zolc')).toBe(true)
    // and the other way round — typing the accent still works
    expect(matchesPersonQuery(lukasz, 'łuk')).toBe(true)
  })

  it('matches e-mail and nickname when the row carries them', () => {
    expect(matchesPersonQuery(anna, 'example')).toBe(true)
    expect(matchesPersonQuery(anna, 'wiewiorka')).toBe(true)
  })

  it('does not match what is in none of the fields', () => {
    expect(matchesPersonQuery(anna, 'zz')).toBe(false)
    expect(matchesPersonQuery(lukasz, 'example')).toBe(false)
  })

  it('tolerates null optional fields', () => {
    expect(matchesPersonQuery({ firstName: 'Ola', lastName: 'Nowak', email: null, nickname: null }, 'no')).toBe(true)
  })
})
