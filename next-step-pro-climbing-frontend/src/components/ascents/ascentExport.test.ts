import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { exportFileName, slugify, toExportRows } from './ascentExport'
import type { Ascent, AscentDiscipline } from '../../types'

// The font is a binary import; only the pure helpers are under test here, and jsPDF is not
vi.mock('../../assets/fonts/NotoSans-Regular.ttf?inline', () => ({ default: 'data:font/ttf;base64,AAAA' }))

const DISCIPLINES: Record<AscentDiscipline, string> = {
  SPORT: 'Sport',
  BOULDER: 'Boulder',
  TRAD: 'Trad',
}

function ascent(overrides: Partial<Ascent> = {}): Ascent {
  return {
    id: 'a1',
    terrain: 'ROCK',
    climbedOn: '2026-05-01',
    discipline: 'SPORT',
    gradeScale: 'FRENCH_ROUTE',
    grade: 'FR_7A_PLUS',
    gradeLabel: '7a+',
    gradeRank: 150,
    style: 'RP',
    area: 'Jura Północna',
    crag: 'Kołoczek',
    routeName: 'Wielkie Ciśnienie',
    attempts: 4,
    qualityStars: 5,
    comment: 'klucz w połowie',
    winter: null,
    originalGrade: null,
    lengthMeters: null,
    pitches: null,
    durationMinutes: null,
    ledGrade: null,
    ledGradeLabel: null,
    ledGradeRank: null,
    ledPitches: null,
    partners: null,
    createdAt: '2026-05-01T10:00:00Z',
    hiddenFromPublicAt: null,
    ...overrides,
  }
}

describe('slugify', () => {
  it('strips Polish diacritics, including the stroked l', () => {
    expect(slugify('Jan Kowalski')).toBe('jan-kowalski')
    expect(slugify('Michał Wójcik')).toBe('michal-wojcik')
    expect(slugify('Łukasz Ćwik')).toBe('lukasz-cwik')
  })

  it('collapses punctuation and trims separators', () => {
    expect(slugify('  Anna   Wspinaczka!  ')).toBe('anna-wspinaczka')
  })
})

describe('exportFileName', () => {
  beforeEach(() => {
    vi.useFakeTimers()
    // Warsaw is a day ahead of UTC here, which is the whole point of using todayInWarsaw
    vi.setSystemTime(new Date('2026-08-14T23:30:00Z'))
  })
  afterEach(() => vi.useRealTimers())

  it('names the file after the year and Warsaw today', () => {
    expect(exportFileName('xlsx', 2026)).toBe('przejscia_2026_2026-08-15.xlsx')
    expect(exportFileName('xlsx', 2026, undefined, { terrain: 'MOUNTAIN' }))
      .toBe('przejscia-gorskie_2026_2026-08-15.xlsx')
  })

  it('says "wszystkie" when no year is selected', () => {
    expect(exportFileName('pdf', null)).toBe('przejscia_wszystkie_2026-08-15.pdf')
  })

  it('carries the athlete name when the coach exports somebody else', () => {
    expect(exportFileName('xlsx', 2026, 'Anna Wspinaczka'))
      .toBe('przejscia_anna-wspinaczka_2026_2026-08-15.xlsx')
  })
})

describe('toExportRows', () => {
  it('maps a row to the ten columns in order', () => {
    expect(toExportRows([ascent()], DISCIPLINES)).toEqual([[
      '2026-05-01', 'Sport', 'Jura Północna', 'Kołoczek', 'Wielkie Ciśnienie',
      '7a+', 'RP', '4', '5', 'klucz w połowie',
    ]])
  })

  // SOLO and FREE_SOLO are words, not shorthand, so they are the two styles that translate
  it('writes the translated style label when one is given', () => {
    const styles = {
      OS: 'OS', FLASH: 'FLASH', RP: 'RP', TR: 'TR', SOLO: 'Solo', FREE_SOLO: 'Na żywca', A0: 'A0',
      OS_GU: 'OS GU', FLASH_GU: 'Flash GU', GU: 'GU', HP: 'HP',
    }
    const row = toExportRows([ascent({ style: 'FREE_SOLO' })], DISCIPLINES, undefined, styles)[0]

    expect(row[6]).toBe('Na żywca')
  })

  it('writes empty cells rather than "null" for the optional fields', () => {
    const row = toExportRows([ascent({ attempts: null, qualityStars: null, comment: null })], DISCIPLINES)[0]

    expect(row[7]).toBe('')
    expect(row[8]).toBe('')
    expect(row[9]).toBe('')
  })

  it('exports the grade label, not the enum constant', () => {
    const row = toExportRows([ascent()], DISCIPLINES)[0]

    expect(row[5]).toBe('7a+')
    expect(row[5]).not.toBe('FR_7A_PLUS')
  })
})
