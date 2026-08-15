import { describe, it, expect, vi } from 'vitest'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router-dom'
import { AscentsSection } from './AscentsSection'
import type { AscentAdapter } from './ascentAdapter'
import type { Ascent, AscentLog, AscentOptions, AscentStats } from '../../types'

vi.mock('react-i18next', () => ({
  useTranslation: () => ({ t: (key: string) => key, i18n: { language: 'pl' } }),
  initReactI18next: { type: '3rdParty', init: () => {} },
}))

const OPTIONS: AscentOptions = {
  disciplines: [
    { value: 'SPORT', gradeScale: 'FRENCH_ROUTE', styles: ['OS', 'FLASH', 'RP', 'TR', 'SOLO', 'FREE_SOLO'] },
    { value: 'BOULDER', gradeScale: 'FONT_BOULDER', styles: ['OS', 'FLASH', 'RP'] },
  ],
  mountainStyles: ['OS', 'FLASH', 'RP', 'A0', 'SOLO', 'FREE_SOLO'],
  gradesByScale: {
    FRENCH_ROUTE: [{ value: 'FR_7A', label: '7a', rank: 140 }],
    FONT_BOULDER: [{ value: 'FB_7A', label: '7A', rank: 120 }],
  },
}

vi.mock('../../api/client', () => ({
  ascentApi: { getOptions: vi.fn(async () => OPTIONS) },
}))

// The section reads the current user only to say whether the logbook is public
vi.mock('../../context/AuthContext', () => ({
  useAuth: () => ({ user: { ascentsPublic: true } }),
}))

const ENTRY: Ascent = {
  id: 'a1',
  terrain: 'ROCK',
  climbedOn: '2026-05-01',
  discipline: 'SPORT',
  gradeScale: 'FRENCH_ROUTE',
  grade: 'FR_7A',
  gradeLabel: '7a',
  gradeRank: 140,
  style: 'RP',
  area: 'Jura Północna',
  crag: 'Kołoczek',
  routeName: 'Wielkie Ciśnienie',
  attempts: 3,
  qualityStars: 4,
  comment: null,
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
}

const LOG: AscentLog = {
  entries: [ENTRY],
  availableYears: [2026],
  selectedYear: 2026,
  totalCount: 1,
  places: [{ area: 'Jura Północna', crags: ['Kołoczek'] }],
}

const STATS: AscentStats = {
  selectedYear: 2026,
  totalAscents: 1,
  firstAscentDate: '2026-05-01',
  areaCount: 1,
  cragCount: 1,
  avgAttemptsToRedpoint: 3,
  redpointsWithAttempts: 1,
  avgQualityStars: 4,
  topAreas: [{ area: 'Jura Północna', ascentCount: 1 }],
  disciplines: [],
  mountain: null,
}

function renderSection(api: AscentAdapter, isCoachView = false) {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  return render(
    <MemoryRouter>
      <QueryClientProvider client={client}>
        <AscentsSection api={api} scopeKey="me" isCoachView={isCoachView} />
      </QueryClientProvider>
    </MemoryRouter>,
  )
}

const readOnlyApi: AscentAdapter = {
  getLog: vi.fn(async () => LOG),
  getStats: vi.fn(async () => STATS),
  // No mutations — this is exactly the coach adapter's shape
}

const writableApi: AscentAdapter = {
  ...readOnlyApi,
  mutations: { create: vi.fn(), update: vi.fn(), remove: vi.fn() },
}

describe('AscentsSection', () => {
  it('renders the logbook', async () => {
    renderSection(writableApi)

    expect(await screen.findByText('Wielkie Ciśnienie')).toBeInTheDocument()
    expect(screen.getByText('7a')).toBeInTheDocument()
  })

  // Sport and trad share the French scale, so the grade cannot stand in for this
  it('says which discipline each ascent was', async () => {
    renderSection(writableApi)

    await screen.findByText('Wielkie Ciśnienie')

    expect(screen.getByRole('columnheader', { name: 'table.discipline' })).toBeInTheDocument()
    expect(screen.getByRole('cell', { name: 'discipline.SPORT' })).toBeInTheDocument()
  })

  /**
   * The invariant the coach view exists to keep: read-only is not a flag checked per button,
   * it falls out of the adapter having no `mutations`.
   */
  it('renders no write control at all in the coach view', async () => {
    renderSection(readOnlyApi, true)

    await screen.findByText('Wielkie Ciśnienie')

    expect(screen.queryByRole('button', { name: 'add' })).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: /^edit/ })).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: /^delete/ })).not.toBeInTheDocument()
  })

  it('offers adding and editing when the adapter can write', async () => {
    renderSection(writableApi)

    await screen.findByText('Wielkie Ciśnienie')

    expect(screen.getByRole('button', { name: /add/ })).toBeInTheDocument()
    expect(screen.getByRole('button', { name: /^edit/ })).toBeInTheDocument()
    expect(screen.getByRole('button', { name: /^delete/ })).toBeInTheDocument()
  })

  // The pyramid answers "what am I climbing", which is usually an all-time question — but the
  // table stays on its year, so the two ranges have to be settable apart
  it('lets the statistics range be switched to all-time without moving the table', async () => {
    const getStats = vi.fn(async () => STATS)
    renderSection({ ...writableApi, getStats })

    await screen.findByText('Wielkie Ciśnienie')
    await waitFor(() => expect(getStats).toHaveBeenCalledWith('ROCK', ''))

    await userEvent.click(screen.getByRole('button', { name: 'filters.allYears' }))

    await waitFor(() => expect(getStats).toHaveBeenCalledWith('ROCK', 'all'))
    // the table's own query never asked for another year
    expect(readOnlyApi.getLog).not.toHaveBeenCalledWith('ROCK', 'all')
  })

  it('hides the range toggle when the table already covers every year', async () => {
    const allYears: AscentLog = { ...LOG, selectedYear: null }
    renderSection({ ...writableApi, getLog: vi.fn(async () => allYears) })

    await screen.findByText('Wielkie Ciśnienie')

    expect(screen.queryByRole('group', { name: 'stats.scopeLabel' })).not.toBeInTheDocument()
  })

  it('shows the coach a different empty state than the athlete', async () => {
    const empty: AscentLog = { entries: [], availableYears: [], selectedYear: null, totalCount: 0, places: [] }
    const emptyApi: AscentAdapter = { getLog: vi.fn(async () => empty), getStats: vi.fn(async () => STATS) }

    renderSection(emptyApi, true)

    expect(await screen.findByText('empty.coach')).toBeInTheDocument()
  })

  it('offers the export only when there are rows to export', async () => {
    const empty: AscentLog = { entries: [], availableYears: [], selectedYear: null, totalCount: 0, places: [] }
    const { unmount } = renderSection({ ...writableApi, getLog: vi.fn(async () => empty) })

    await screen.findByText('empty.title')
    expect(screen.queryByRole('button', { name: /export/ })).not.toBeInTheDocument()
    unmount()

    renderSection(writableApi)
    await waitFor(() => expect(screen.getByRole('button', { name: /export.xlsx/ })).toBeInTheDocument())
  })
})
