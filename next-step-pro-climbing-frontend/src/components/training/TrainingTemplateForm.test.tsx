import { describe, it, expect, vi, beforeEach } from 'vitest'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { TrainingTemplateForm, type TemplateDraft } from './TrainingTemplateForm'
import type { TrainingTemplate } from '../../types'

vi.mock('react-i18next', () => ({
  useTranslation: () => ({ t: (key: string) => key, i18n: { language: 'pl' } }),
  initReactI18next: { type: '3rdParty', init: () => {} },
}))

const createTemplate = vi.fn().mockResolvedValue({})
const updateTemplate = vi.fn().mockResolvedValue({})

vi.mock('../../api/client', () => ({
  adminTrainingCalendarApi: {
    createTemplate: (...args: unknown[]) => createTemplate(...args),
    updateTemplate: (...args: unknown[]) => updateTemplate(...args),
    uploadAttachment: vi.fn(),
  },
}))

function renderForm(props: Parameters<typeof TrainingTemplateForm>[0]) {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  return render(
    <QueryClientProvider client={client}>
      <TrainingTemplateForm {...props} />
    </QueryClientProvider>,
  )
}

beforeEach(() => {
  createTemplate.mockClear()
  updateTemplate.mockClear()
})

describe('TrainingTemplateForm — kind decides the shape', () => {
  it('should send a duration and no calorie target for a training', async () => {
    renderForm({ onDone: vi.fn(), onCancel: vi.fn() })

    const [titleInput] = screen.getAllByRole('textbox')
    await userEvent.type(titleInput, 'Siła A')
    await userEvent.click(screen.getByRole('button', { name: 'form.save' }))

    await waitFor(() => expect(createTemplate).toHaveBeenCalled())
    expect(createTemplate).toHaveBeenCalledWith(expect.objectContaining({
      kind: 'TRAINING',
      defaultDurationMinutes: 90,
      targetCalories: null,
    }))
  })

  it('should send a calorie target and no duration for a task', async () => {
    renderForm({ onDone: vi.fn(), onCancel: vi.fn() })

    await userEvent.click(screen.getByRole('button', { name: /form.kind.TASK/ }))
    const [titleInput] = screen.getAllByRole('textbox')
    await userEvent.type(titleInput, 'Limit 2200 kcal')
    await userEvent.type(screen.getByRole('spinbutton'), '2200')
    await userEvent.click(screen.getByRole('button', { name: 'form.save' }))

    await waitFor(() => expect(createTemplate).toHaveBeenCalled())
    expect(createTemplate).toHaveBeenCalledWith(expect.objectContaining({
      kind: 'TASK',
      defaultDurationMinutes: null,
      targetCalories: 2200,
    }))
  })

  it('should offer no duration field at all while the kind is a task', async () => {
    // Not a disabled input: a whole-day commitment has no span, and a greyed-out "90 min"
    // still claims there is one
    renderForm({ onDone: vi.fn(), onCancel: vi.fn() })
    expect(screen.getByText('templates.form.duration')).toBeInTheDocument()

    await userEvent.click(screen.getByRole('button', { name: /form.kind.TASK/ }))

    expect(screen.queryByText('templates.form.duration')).not.toBeInTheDocument()
    expect(screen.getByText('form.calories')).toBeInTheDocument()
  })

  it('should keep the typed duration when the kind is switched away and back', async () => {
    renderForm({ onDone: vi.fn(), onCancel: vi.fn() })
    const duration = screen.getByRole('spinbutton')
    await userEvent.clear(duration)
    await userEvent.type(duration, '120')

    await userEvent.click(screen.getByRole('button', { name: /form.kind.TASK/ }))
    await userEvent.click(screen.getByRole('button', { name: /form.kind.TRAINING/ }))

    expect(screen.getByRole('spinbutton')).toHaveValue(120)
  })

  it('should allow a task template with no calorie target at all', async () => {
    // "Drink 3 litres of water" carries its number in the title and needs no column
    renderForm({ onDone: vi.fn(), onCancel: vi.fn() })

    await userEvent.click(screen.getByRole('button', { name: /form.kind.TASK/ }))
    const [titleInput] = screen.getAllByRole('textbox')
    await userEvent.type(titleInput, '3 litry wody')
    await userEvent.click(screen.getByRole('button', { name: 'form.save' }))

    await waitFor(() => expect(createTemplate).toHaveBeenCalled())
    expect(createTemplate).toHaveBeenCalledWith(expect.objectContaining({
      kind: 'TASK',
      targetCalories: null,
      defaultDurationMinutes: null,
    }))
  })
})

describe('TrainingTemplateForm — editing an existing template', () => {
  const template: TrainingTemplate = {
    id: 'tpl-1',
    kind: 'TRAINING',
    title: 'Siła A',
    description: null,
    defaultDurationMinutes: 60,
    targetCalories: null,
    attachments: [],
    updatedAt: '2026-08-01T00:00:00Z',
  }

  it('should let an existing template change its kind', async () => {
    // Unlike an entry's kind, this one is editable — a template has no completion or RPE to lose
    renderForm({ template, onDone: vi.fn(), onCancel: vi.fn() })

    await userEvent.click(screen.getByRole('button', { name: /form.kind.TASK/ }))
    await userEvent.click(screen.getByRole('button', { name: 'form.save' }))

    await waitFor(() => expect(updateTemplate).toHaveBeenCalled())
    expect(updateTemplate).toHaveBeenCalledWith('tpl-1', expect.objectContaining({
      kind: 'TASK',
      defaultDurationMinutes: null,
    }))
  })
})

describe('TrainingTemplateForm — seeded from an entry (save as template)', () => {
  it('should carry a task draft over with its ceiling and no duration', async () => {
    const draft: TemplateDraft = {
      kind: 'TASK',
      title: 'Limit 2200 kcal',
      defaultDurationMinutes: null,
      targetCalories: 2200,
      attachments: [],
    }
    renderForm({ draft, onDone: vi.fn(), onCancel: vi.fn() })

    expect(screen.getByRole('spinbutton')).toHaveValue(2200)
    await userEvent.click(screen.getByRole('button', { name: 'form.save' }))

    await waitFor(() => expect(createTemplate).toHaveBeenCalled())
    expect(createTemplate).toHaveBeenCalledWith(expect.objectContaining({
      kind: 'TASK',
      title: 'Limit 2200 kcal',
      targetCalories: 2200,
      defaultDurationMinutes: null,
    }))
  })

  it('should fall back to a default duration for an untimed training draft', async () => {
    // An all-day training brings no span, but the library entry needs one — the backend
    // rejects a TRAINING template without a duration
    const draft: TemplateDraft = {
      kind: 'TRAINING',
      title: 'Mobilność',
      defaultDurationMinutes: null,
      targetCalories: null,
      attachments: [],
    }
    renderForm({ draft, onDone: vi.fn(), onCancel: vi.fn() })

    await userEvent.click(screen.getByRole('button', { name: 'form.save' }))

    await waitFor(() => expect(createTemplate).toHaveBeenCalled())
    expect(createTemplate).toHaveBeenCalledWith(expect.objectContaining({
      kind: 'TRAINING',
      defaultDurationMinutes: 90,
    }))
  })
})
