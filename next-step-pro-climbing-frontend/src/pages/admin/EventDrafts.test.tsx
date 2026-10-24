import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { MemoryRouter } from 'react-router-dom'
import { ToastProvider } from '../../context/ToastContext'
import { CreateEventModal } from './AdminEventsPanel'
import { readDraft } from '../../utils/draftStorage'

/** The event form keeps the writing — and only the writing — as a draft. */

Element.prototype.scrollTo = vi.fn()

vi.mock('react-i18next', async (importOriginal) => ({
  ...(await importOriginal<typeof import('react-i18next')>()),
  useTranslation: () => ({
    t: (key: string, opts?: Record<string, unknown>) => (opts ? `${key}:${Object.values(opts).join(',')}` : key),
    i18n: { language: 'pl' },
  }),
}))

vi.mock('../../context/AuthContext', () => ({ useCurrentUserId: () => 'admin-1' }))

vi.mock('../../api/client', () => ({
  adminApi: {
    createEvent: vi.fn(),
    getAllUsers: () => Promise.resolve([]),
    getAllEvents: () => Promise.resolve([]),
  },
  adminCoursesApi: { getAll: () => Promise.resolve([{ id: 'course-1', title: 'Kurs skałkowy' }]) },
  calendarApi: { getDayView: (date: string) => Promise.resolve({ date, slots: [], events: [] }) },
}))

const renderModal = () =>
  render(
    <QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}>
      <MemoryRouter><ToastProvider><CreateEventModal isOpen onClose={vi.fn()} /></ToastProvider></MemoryRouter>
    </QueryClientProvider>,
  )

const titleField = () => document.querySelector<HTMLInputElement>('input[type="text"][required]')!

beforeEach(() => localStorage.clear())

describe('Event form drafts', () => {
  it('should offer a typed title and description back after the form was torn down', async () => {
    const user = userEvent.setup()
    const first = renderModal()
    await user.type(titleField(), 'Wyjazd na Jurę')
    first.unmount()

    renderModal()
    await user.click(await screen.findByRole('button', { name: 'draft.restore' }))

    expect(titleField()).toHaveValue('Wyjazd na Jurę')
  })

  // Picking a course fills the title from the course: nobody wrote it, and restoring it later
  // without the course link would turn the course's name into a free-text title.
  it('should not treat a title taken from a picked course as writing', async () => {
    const user = userEvent.setup()
    renderModal()
    const courseSelect = screen.getAllByRole('combobox')[0]
    await screen.findByRole('option', { name: 'Kurs skałkowy' })
    await user.selectOptions(courseSelect, 'course-1')

    expect(readDraft('admin-1', 'event:new')).toBeNull()
  })
})
