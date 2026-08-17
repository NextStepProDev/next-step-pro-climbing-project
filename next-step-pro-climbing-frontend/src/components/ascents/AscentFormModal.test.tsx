import { describe, it, expect, vi } from 'vitest'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { AscentFormModal } from './AscentFormModal'
import type { AscentOptions } from '../../types'

vi.mock('react-i18next', () => ({
  useTranslation: () => ({ t: (key: string) => key, i18n: { language: 'pl' } }),
  initReactI18next: { type: '3rdParty', init: () => {} },
}))

// The catalogue the server serves, in miniature
const OPTIONS: AscentOptions = {
  disciplines: [
    { value: 'SPORT', gradeScale: 'FRENCH_ROUTE', styles: ['OS', 'FLASH', 'RP', 'TR', 'SOLO', 'FREE_SOLO'] },
    // Trad speaks its own dialect and shares nothing with sport — cleanest first, as served
    { value: 'TRAD', gradeScale: 'FRENCH_ROUTE', styles: ['OS_GU', 'FLASH_GU', 'GU', 'HP'] },
    { value: 'BOULDER', gradeScale: 'FONT_BOULDER', styles: ['OS', 'FLASH', 'RP'] },
  ],
  mountainStyles: ['OS', 'FLASH', 'RP', 'A0', 'SOLO', 'FREE_SOLO'],
  gradesByScale: {
    FRENCH_ROUTE: [
      { value: 'FR_6A', label: '6a', rank: 80 },
      { value: 'FR_7A', label: '7a', rank: 140 },
    ],
    FONT_BOULDER: [
      { value: 'FB_6A', label: '6A', rank: 60 },
      { value: 'FB_7A', label: '7A', rank: 120 },
    ],
  },
}

function renderForm(props: Partial<Parameters<typeof AscentFormModal>[0]> = {}) {
  const onSubmit = vi.fn()
  render(
    <AscentFormModal
      isOpen
      onClose={vi.fn()}
      onSubmit={onSubmit}
      saving={false}
      error={null}
      options={OPTIONS}
      places={[{ area: 'Jura Północna', crags: ['Kołoczek'] }]}
      terrain="ROCK"
      {...props}
    />,
  )
  return { onSubmit }
}

describe('AscentFormModal', () => {
  it('offers only the grades of the selected discipline', async () => {
    renderForm()
    const grade = screen.getByLabelText('form.grade') as HTMLSelectElement

    expect([...grade.options].map(o => o.text)).toContain('7a')
    expect([...grade.options].map(o => o.text)).not.toContain('7A')

    await userEvent.selectOptions(screen.getByLabelText('form.discipline'), 'BOULDER')

    expect([...grade.options].map(o => o.text)).toContain('7A')
    expect([...grade.options].map(o => o.text)).not.toContain('7a')
  })

  // Converting French to Font is guesswork; quietly rewriting the grade would credit an
  // ascent that never happened
  it('clears an incompatible grade on discipline change instead of converting it', async () => {
    renderForm()
    const grade = screen.getByLabelText('form.grade') as HTMLSelectElement

    await userEvent.selectOptions(grade, 'FR_7A')
    expect(grade.value).toBe('FR_7A')

    await userEvent.selectOptions(screen.getByLabelText('form.discipline'), 'BOULDER')

    expect(grade.value).toBe('')
    expect(screen.getByText('form.gradeCleared')).toBeInTheDocument()
  })

  it('keeps the grade when the new discipline shares the scale', async () => {
    renderForm()
    const grade = screen.getByLabelText('form.grade') as HTMLSelectElement

    await userEvent.selectOptions(grade, 'FR_7A')
    await userEvent.selectOptions(screen.getByLabelText('form.discipline'), 'TRAD')

    expect(grade.value).toBe('FR_7A')
  })

  it('does not offer toprope for bouldering', async () => {
    renderForm()
    await userEvent.selectOptions(screen.getByLabelText('form.discipline'), 'BOULDER')

    const styles = [...(screen.getByLabelText('form.style') as HTMLSelectElement).options].map(o => o.value)

    expect(styles).toEqual(expect.arrayContaining(['OS', 'FLASH', 'RP']))
    expect(styles).not.toContain('TR')
    expect(styles).not.toContain('FREE_SOLO')
  })

  // Nobody hangs a rope down an alpine route, so the mountains do not offer toprope either
  it('offers solo styles but no toprope in the mountains', async () => {
    renderForm({ terrain: 'MOUNTAIN' })

    const styles = [...(screen.getByLabelText('form.style') as HTMLSelectElement).options].map(o => o.value)

    expect(styles).toEqual(expect.arrayContaining(['SOLO', 'FREE_SOLO', 'A0']))
    expect(styles).not.toContain('TR')
  })

  // Trad asks where the ascent was worked from, not how much was known about it — so it shares
  // no style with sport, and the default must not hand out an onsight nobody claimed
  it('offers only the ground-up dialect for trad and defaults to the worked send', async () => {
    renderForm()
    await userEvent.selectOptions(screen.getByLabelText('form.discipline'), 'TRAD')

    const style = screen.getByLabelText('form.style') as HTMLSelectElement
    const styles = [...style.options].map(option => option.value)

    expect(styles).toEqual(['OS_GU', 'FLASH_GU', 'GU', 'HP'])
    expect(style.value).toBe('GU')
  })

  it('hides the attempts field for a ground-up onsight, keeps it for a worked one', async () => {
    renderForm()
    await userEvent.selectOptions(screen.getByLabelText('form.discipline'), 'TRAD')
    expect(screen.getByLabelText('form.attempts')).toBeInTheDocument()

    await userEvent.selectOptions(screen.getByLabelText('form.style'), 'OS_GU')

    expect(screen.queryByLabelText('form.attempts')).not.toBeInTheDocument()
  })

  it('offers solo on roped rock', async () => {
    renderForm()

    const styles = [...(screen.getByLabelText('form.style') as HTMLSelectElement).options].map(o => o.value)

    expect(styles).toEqual(expect.arrayContaining(['TR', 'SOLO', 'FREE_SOLO']))
    expect(styles).not.toContain('PP')
    // Aid is a mountain style: the same day on a crag is a redpoint attempt, not an ascent
    expect(styles).not.toContain('A0')
  })

  // OS and FLASH mean "first go" by definition, so there is nothing to count
  it('hides the attempts field for first-try styles', async () => {
    renderForm()
    expect(screen.getByLabelText('form.attempts')).toBeInTheDocument()

    await userEvent.selectOptions(screen.getByLabelText('form.style'), 'OS')

    expect(screen.queryByLabelText('form.attempts')).not.toBeInTheDocument()
  })

  it('submits the trimmed values with nulls for the empty optional fields', async () => {
    const { onSubmit } = renderForm()

    await userEvent.selectOptions(screen.getByLabelText('form.grade'), 'FR_7A')
    await userEvent.type(screen.getByLabelText('form.area'), '  Jura Północna  ')
    await userEvent.type(screen.getByLabelText('form.crag'), 'Kołoczek')
    await userEvent.type(screen.getByLabelText('form.route'), 'Pajęczyna')
    await userEvent.click(screen.getByRole('button', { name: 'form.save' }))

    expect(onSubmit).toHaveBeenCalledWith(expect.objectContaining({
      grade: 'FR_7A',
      area: 'Jura Północna',
      crag: 'Kołoczek',
      routeName: 'Pajęczyna',
      attempts: null,
      qualityStars: null,
      comment: null,
    }))
  })

  // The mountain form is a different form, not the rock one with extra boxes
  it('swaps discipline for season and offers the mountain fields', async () => {
    renderForm({ terrain: 'MOUNTAIN' })

    expect(screen.queryByLabelText('form.discipline')).not.toBeInTheDocument()
    expect(screen.getByLabelText('form.season')).toBeInTheDocument()
    expect(screen.getByLabelText('form.summit')).toBeInTheDocument()
    expect(screen.getByLabelText('form.lengthMeters')).toBeInTheDocument()
    expect(screen.getByLabelText('form.pitches')).toBeInTheDocument()
    expect(screen.getByLabelText('form.durationHours')).toBeInTheDocument()
    expect(screen.getByLabelText('form.ledGrade')).toBeInTheDocument()
    expect(screen.getByLabelText('form.partners')).toBeInTheDocument()
    // rock-only fields are gone
    expect(screen.queryByLabelText('form.attempts')).not.toBeInTheDocument()
  })

  it('sends hours as minutes and marks the entry as mountain', async () => {
    const { onSubmit } = renderForm({ terrain: 'MOUNTAIN' })

    await userEvent.selectOptions(screen.getByLabelText('form.grade'), 'FR_7A')
    await userEvent.type(screen.getByLabelText('form.area'), 'Tatry Wysokie')
    await userEvent.type(screen.getByLabelText('form.summit'), 'Mnich')
    await userEvent.type(screen.getByLabelText('form.route'), 'Filar')
    await userEvent.type(screen.getByLabelText('form.durationHours'), '6,5')
    await userEvent.selectOptions(screen.getByLabelText('form.season'), 'winter')
    await userEvent.click(screen.getByRole('button', { name: 'form.save' }))

    expect(onSubmit).toHaveBeenCalledWith(expect.objectContaining({
      terrain: 'MOUNTAIN',
      discipline: null,
      winter: true,
      // 6,5 h entered with a comma, stored as minutes
      durationMinutes: 390,
      attempts: null,
      qualityStars: null,
    }))
  })

  it('keeps save disabled until every required field is filled', async () => {
    renderForm()

    expect(screen.getByRole('button', { name: 'form.save' })).toBeDisabled()

    await userEvent.selectOptions(screen.getByLabelText('form.grade'), 'FR_7A')
    await userEvent.type(screen.getByLabelText('form.area'), 'Jura')
    await userEvent.type(screen.getByLabelText('form.crag'), 'Kołoczek')

    expect(screen.getByRole('button', { name: 'form.save' })).toBeDisabled()

    await userEvent.type(screen.getByLabelText('form.route'), 'Pajęczyna')

    expect(screen.getByRole('button', { name: 'form.save' })).toBeEnabled()
  })
})
