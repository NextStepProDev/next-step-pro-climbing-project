import { describe, it, expect, vi } from 'vitest'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter, Routes, Route, useLocation } from 'react-router-dom'
import { AdminCommandPalette } from './AdminCommandPalette'

vi.mock('react-i18next', () => ({
  useTranslation: () => ({ t: (key: string) => key, i18n: { language: 'pl' } }),
  initReactI18next: { type: '3rdParty', init: () => {} },
}))

function Where() {
  const location = useLocation()
  return <div data-testid="where">{location.pathname}</div>
}

function renderPalette() {
  const onClose = vi.fn()
  render(
    <MemoryRouter initialEntries={['/admin']}>
      <AdminCommandPalette onClose={onClose} />
      <Routes>
        <Route path="*" element={<Where />} />
      </Routes>
    </MemoryRouter>,
  )
  return { onClose, user: userEvent.setup() }
}

function optionLabels(): string[] {
  return screen.getAllByRole('option').map((option) => option.textContent ?? '')
}

describe('AdminCommandPalette', () => {
  it('opens showing the whole panel, grouped', () => {
    // Eighteen destinations listed is the point: the palette doubles as a map for someone who
    // cannot remember what a tab is called.
    renderPalette()

    expect(screen.getAllByRole('option')).toHaveLength(18)
    expect(screen.getByText('tabGroups.calendar')).toBeInTheDocument()
    expect(screen.getByText('tabGroups.system')).toBeInTheDocument()
  })

  it('narrows to what was typed', async () => {
    const { user } = renderPalette()

    await user.type(screen.getByRole('combobox'), 'settle')

    expect(optionLabels()).toHaveLength(1)
    expect(optionLabels()[0]).toContain('tabs.settlements')
  })

  it('says so when nothing matches', async () => {
    const { user } = renderPalette()

    await user.type(screen.getByRole('combobox'), 'zzzz')

    expect(screen.getByText('palette.empty')).toBeInTheDocument()
    expect(screen.queryAllByRole('option')).toHaveLength(0)
  })

  it('walks the list with the arrows and opens with Enter', async () => {
    const { user, onClose } = renderPalette()
    const input = screen.getByRole('combobox')

    // First option is Slots; one step down lands on Reservations.
    await user.type(input, '{ArrowDown}')
    expect(screen.getAllByRole('option')[1]).toHaveAttribute('aria-selected', 'true')

    await user.type(input, '{Enter}')

    expect(screen.getByTestId('where')).toHaveTextContent('/admin/reservations')
    expect(onClose).toHaveBeenCalledOnce()
  })

  it('wraps around the ends', async () => {
    const { user } = renderPalette()

    // Up from the top is the last entry, not a dead key.
    await user.type(screen.getByRole('combobox'), '{ArrowUp}')

    const options = screen.getAllByRole('option')
    expect(options[options.length - 1]).toHaveAttribute('aria-selected', 'true')
  })

  it('re-aims at the top after every keystroke', async () => {
    const { user } = renderPalette()
    const input = screen.getByRole('combobox')

    await user.type(input, '{ArrowDown}{ArrowDown}')
    await user.type(input, 'a')

    expect(screen.getAllByRole('option')[0]).toHaveAttribute('aria-selected', 'true')
  })

  it('navigates on click', async () => {
    const { user, onClose } = renderPalette()

    await user.click(screen.getByRole('option', { name: /tabs.storage/ }))

    expect(screen.getByTestId('where')).toHaveTextContent('/admin/storage')
    expect(onClose).toHaveBeenCalledOnce()
  })

  it('closes on Escape without letting it reach the document', async () => {
    const onDocumentEscape = vi.fn()
    document.addEventListener('keydown', onDocumentEscape)

    try {
      const { user, onClose } = renderPalette()
      onDocumentEscape.mockClear()

      await user.type(screen.getByRole('combobox'), '{Escape}')

      expect(onClose).toHaveBeenCalledOnce()
      expect(onDocumentEscape).not.toHaveBeenCalled()
    } finally {
      document.removeEventListener('keydown', onDocumentEscape)
    }
  })

  it('closes when the backdrop is clicked', async () => {
    const { user, onClose } = renderPalette()

    await user.click(document.querySelector('[aria-hidden="true"]') as HTMLElement)

    expect(onClose).toHaveBeenCalledOnce()
  })
})
