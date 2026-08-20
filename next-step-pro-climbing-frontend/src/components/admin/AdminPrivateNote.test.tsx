import { describe, it, expect, vi, beforeEach } from 'vitest'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { AdminPrivateNote } from './AdminPrivateNote'

vi.mock('react-i18next', () => ({
  useTranslation: () => ({ t: (key: string) => key, i18n: { language: 'pl' } }),
  // src/i18n.ts is pulled in transitively (utils/errors) and initialises on import
  initReactI18next: { type: '3rdParty', init: () => {} },
}))

const getPrivateNote = vi.fn()
const savePrivateNote = vi.fn()
const deletePrivateNote = vi.fn()

vi.mock('../../api/client', () => ({
  adminApi: {
    getPrivateNote: (...args: unknown[]) => getPrivateNote(...args),
    savePrivateNote: (...args: unknown[]) => savePrivateNote(...args),
    deletePrivateNote: (...args: unknown[]) => deletePrivateNote(...args),
  },
}))

function renderNote() {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  return render(
    <QueryClientProvider client={client}>
      <AdminPrivateNote target="slot" targetId="slot-1" />
    </QueryClientProvider>,
  )
}

describe('AdminPrivateNote', () => {
  beforeEach(() => {
    getPrivateNote.mockReset()
    savePrivateNote.mockReset().mockResolvedValue(undefined)
    deletePrivateNote.mockReset().mockResolvedValue(undefined)
  })

  it('offers to start a note when there is none', async () => {
    getPrivateNote.mockResolvedValue({ body: null, updatedAt: null })

    renderNote()

    expect(await screen.findByText('privateNote.add')).toBeInTheDocument()
    // The whole point of the feature has to be on screen before anything is typed
    expect(screen.getByText('privateNote.onlyYouHint')).toBeInTheDocument()
  })

  it('shows an existing note as text, not as an open editor', async () => {
    getPrivateNote.mockResolvedValue({ body: 'Marek boi się wyklepania', updatedAt: '2026-08-19T10:00:00Z' })

    renderNote()

    expect(await screen.findByText('Marek boi się wyklepania')).toBeInTheDocument()
    expect(screen.queryByRole('textbox')).not.toBeInTheDocument()
  })

  it('keeps Save disabled until the text actually changes', async () => {
    getPrivateNote.mockResolvedValue({ body: 'Pierwsza wersja', updatedAt: '2026-08-19T10:00:00Z' })
    const user = userEvent.setup()

    renderNote()
    await user.click(await screen.findByText('privateNote.edit'))

    // The editor opens seeded with the saved note, so nothing is dirty yet
    const save = screen.getByRole('button', { name: 'privateNote.save' })
    expect(save).toBeDisabled()

    await user.type(screen.getByRole('textbox'), ' i lekkiego przewieszenia')
    expect(save).toBeEnabled()

    await user.click(save)
    await waitFor(() =>
      expect(savePrivateNote).toHaveBeenCalledWith('slot', 'slot-1',
        'Pierwsza wersja i lekkiego przewieszenia'))
  })

  it('will not save a note the author has emptied', async () => {
    getPrivateNote.mockResolvedValue({ body: 'Do skasowania', updatedAt: '2026-08-19T10:00:00Z' })
    const user = userEvent.setup()

    renderNote()
    await user.click(await screen.findByText('privateNote.edit'))
    await user.clear(screen.getByRole('textbox'))

    // Emptying the box is dirty, but blank is a deleted note — that is what the bin is for,
    // and the server rejects it too (@NotBlank + the CHECK in V89).
    expect(screen.getByRole('button', { name: 'privateNote.save' })).toBeDisabled()
    expect(savePrivateNote).not.toHaveBeenCalled()
  })
})
