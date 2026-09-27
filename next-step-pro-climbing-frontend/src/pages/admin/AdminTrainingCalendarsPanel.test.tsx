import { describe, it, expect, vi, beforeEach } from 'vitest'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen, fireEvent } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { AdminTrainingCalendarsPanel } from './AdminTrainingCalendarsPanel'
import type { AthleteSummary } from '../../types'

vi.mock('react-i18next', () => ({
  useTranslation: () => ({
    t: (key: string, opts?: { query?: string }) => (opts?.query !== undefined ? `${key}:${opts.query}` : key),
    i18n: { language: 'pl' },
  }),
  initReactI18next: { type: '3rdParty', init: () => {} },
}))

vi.mock('../../components/training/TrainingTemplatesModal', () => ({ TrainingTemplatesModal: () => null }))
vi.mock('../../components/training/TrainingMaterialsModal', () => ({ TrainingMaterialsModal: () => null }))

const getAthletes = vi.fn()

vi.mock('../../api/client', () => ({
  adminTrainingCalendarApi: {
    getAthletes: () => getAthletes(),
  },
}))

function athlete(id: string, firstName: string, lastName: string, newCount = 0): AthleteSummary {
  return { id, firstName, lastName, nickname: '', avatarUrl: null, newCount, lastActivityAt: null }
}

// Server order: unread first — Zofia on top despite the alphabet
const ROSTER = [
  athlete('z', 'Zofia', 'Kozieł', 2),
  athlete('a', 'Anna', 'Kowalska'),
  athlete('l', 'Łukasz', 'Nowak'),
]

async function renderPanel(initialUrl = '/admin/training-calendars') {
  getAthletes.mockResolvedValue(ROSTER)
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  render(
    <QueryClientProvider client={client}>
      <MemoryRouter initialEntries={[initialUrl]}>
        <AdminTrainingCalendarsPanel />
      </MemoryRouter>
    </QueryClientProvider>,
  )
  await screen.findByText('Anna Kowalska')
}

function visibleNames(): string[] {
  return screen.getAllByRole('link').map((l) => l.textContent ?? '')
}

describe('AdminTrainingCalendarsPanel search', () => {
  beforeEach(() => getAthletes.mockReset())

  it('narrows the roster to athletes matching two letters, keeping the server order', async () => {
    await renderPanel()

    fireEvent.change(screen.getByRole('searchbox'), { target: { value: 'ko' } })

    const names = visibleNames()
    expect(names).toHaveLength(2)
    expect(names[0]).toContain('Zofia Kozieł')
    expect(names[1]).toContain('Anna Kowalska')
  })

  it('finds a Polish name typed without Polish letters', async () => {
    await renderPanel()

    fireEvent.change(screen.getByRole('searchbox'), { target: { value: 'lukasz' } })

    expect(visibleNames()).toHaveLength(1)
    expect(screen.getByText('Łukasz Nowak')).toBeInTheDocument()
  })

  it('says nobody matched instead of showing the "flag an athlete" empty state', async () => {
    await renderPanel()

    fireEvent.change(screen.getByRole('searchbox'), { target: { value: 'xyz' } })

    expect(screen.getByText('trainingCalendars.noMatches:xyz')).toBeInTheDocument()
    expect(screen.queryByText('trainingCalendars.emptyTitle')).not.toBeInTheDocument()
  })

  it('restores the filter from the URL, so Back from an athlete keeps it', async () => {
    await renderPanel('/admin/training-calendars?q=ko')

    expect(screen.getByRole('searchbox')).toHaveValue('ko')
    expect(visibleNames()).toHaveLength(2)
    expect(screen.queryByText('Łukasz Nowak')).not.toBeInTheDocument()
  })
})
