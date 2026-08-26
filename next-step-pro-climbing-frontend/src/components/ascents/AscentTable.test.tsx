import { describe, it, expect, vi } from 'vitest'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { AscentTable } from './AscentTable'
import type { Ascent } from '../../types'

vi.mock('react-i18next', () => ({
  useTranslation: () => ({ t: (key: string) => key, i18n: { language: 'pl' } }),
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
  hiddenFromPublicAt: null,
}

function renderTable(entries: Ascent[]) {
  return render(
    <AscentTable
      terrain="ROCK"
      entries={entries}
      sortKey="date"
      sortDirection="desc"
      onSort={() => {}}
    />,
  )
}

describe('AscentTable — the comment can actually be read', () => {
  const withComment = (comment: string): Ascent => ({ ...ENTRY, comment })

  it('should offer no control on an entry that has nothing to expand', () => {
    renderTable([ENTRY])
    expect(screen.queryByRole('button', { expanded: false })).toBeNull()
  })

  it('should show the full comment once expanded, which one clipped line never could', async () => {
    const user = userEvent.setup()
    // Long enough that the clipped teaser cannot be the whole story
    renderTable([withComment('Klucz w połowie, drugi raz poszło.\nNastępnym razem lepsze buty.')])

    const toggle = screen.getByRole('button', { expanded: false })
    await user.click(toggle)

    expect(screen.getByRole('button', { expanded: true })).toBeInTheDocument()
    expect(screen.getByText(/Następnym razem lepsze buty/)).toBeInTheDocument()
  })

  it('should render the markers rather than print them, now that there is room for them', async () => {
    const user = userEvent.setup()
    const { container } = renderTable([withComment('## Jak poszło\n- **klucz** w połowie')])

    await user.click(screen.getByRole('button', { expanded: false }))

    expect(container.querySelector('h4')).toBeInTheDocument()
    expect(container.querySelector('li strong')).toBeInTheDocument()
  })

  // The teaser is one clipped line, so markers there would be noise rather than emphasis
  it('should keep markers out of the collapsed teaser', () => {
    renderTable([withComment('**klucz** w połowie')])
    const toggle = screen.getByRole('button', { expanded: false })
    expect(toggle).toHaveTextContent('klucz w połowie')
    expect(toggle.textContent).not.toContain('**')
  })

  it('should let two entries stay open at once, which is the reason to open one', async () => {
    const user = userEvent.setup()
    renderTable([withComment('pierwszy'), { ...ENTRY, id: 'a2', comment: 'drugi' }])

    const [first, second] = screen.getAllByRole('button', { expanded: false })
    await user.click(first)
    await user.click(second)

    expect(screen.getAllByRole('button', { expanded: true })).toHaveLength(2)
  })

  it('should collapse again on a second click', async () => {
    const user = userEvent.setup()
    renderTable([withComment('klucz w połowie')])

    await user.click(screen.getByRole('button', { expanded: false }))
    await user.click(screen.getByRole('button', { expanded: true }))

    expect(screen.getByRole('button', { expanded: false })).toBeInTheDocument()
  })
})
