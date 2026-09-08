import { describe, it, expect, vi } from 'vitest'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router-dom'
import { AdminNav } from './AdminNav'
import type { AdminNotifications } from '../../types'

vi.mock('react-i18next', () => ({
  useTranslation: () => ({ t: (key: string) => key, i18n: { language: 'pl' } }),
  initReactI18next: { type: '3rdParty', init: () => {} },
}))

const NOTHING_PENDING: AdminNotifications = {
  pendingRequests: 0,
  newReservations: 0,
  newWaitlistEntries: 0,
  athleteActivity: 0,
  newUsers: 0,
}

function renderNav(pathname: string, notifications: AdminNotifications = NOTHING_PENDING) {
  const onOpenPalette = vi.fn()
  render(
    <MemoryRouter initialEntries={[pathname]}>
      <AdminNav notifications={notifications} onOpenPalette={onOpenPalette} />
    </MemoryRouter>,
  )
  return { onOpenPalette }
}

describe('AdminNav', () => {
  it('collapses eighteen tabs into four group buttons', () => {
    renderNav('/admin')

    expect(screen.getByRole('button', { name: /tabGroups.calendar/ })).toBeInTheDocument()
    expect(screen.getByRole('button', { name: /tabGroups.content/ })).toBeInTheDocument()
    expect(screen.getByRole('button', { name: /tabGroups.team/ })).toBeInTheDocument()
    expect(screen.getByRole('button', { name: /tabGroups.system/ })).toBeInTheDocument()
    // Nothing is open, so no tab link is on screen yet.
    expect(screen.queryByRole('link', { name: /tabs.settlements/ })).not.toBeInTheDocument()
  })

  it('names the active tab inside its group button', async () => {
    // `AdminSlotsPanel` renders no heading of its own, so without this the Slots screen would
    // have nothing at all saying where you are.
    renderNav('/admin/slots')

    const calendar = screen.getByRole('button', { name: /tabGroups.calendar/ })
    expect(calendar).toHaveTextContent('tabGroups.calendar: tabs.slots')
    expect(screen.getByRole('button', { name: /tabGroups.system/ })).toHaveTextContent(
      /^tabGroups\.system$/,
    )
  })

  it('treats a person route as inside its tab', () => {
    renderNav('/admin/users/abc-123')

    expect(screen.getByRole('button', { name: /tabGroups.system/ })).toHaveTextContent(
      'tabGroups.system: tabs.users',
    )
  })

  it('does not let /admin/slots light up a second group', () => {
    // The boundary slash in `isTabActive` is what keeps one path from matching another's prefix.
    renderNav('/admin/slots')

    const highlighted = screen
      .getAllByRole('button')
      .filter((button) => button.textContent?.includes(': '))
    expect(highlighted).toHaveLength(1)
  })

  it('opens a group and lists its tabs', async () => {
    const user = userEvent.setup()
    renderNav('/admin')

    await user.click(screen.getByRole('button', { name: /tabGroups.calendar/ }))

    expect(screen.getByRole('link', { name: /tabs.slots/ })).toHaveAttribute('href', '/admin/slots')
    expect(screen.getByRole('link', { name: /tabs.settlements/ })).toHaveAttribute(
      'href',
      '/admin/settlements',
    )
  })

  it('carries its tabs badges on the collapsed group', async () => {
    const user = userEvent.setup()
    // Calendar: 1 proposal + (2 reservations + 1 waitlist) + 0 athlete activity = 4.
    // System: 3 new accounts.
    renderNav('/admin', {
      pendingRequests: 1,
      newReservations: 2,
      newWaitlistEntries: 1,
      athleteActivity: 0,
      newUsers: 3,
    })

    expect(screen.getByRole('button', { name: /tabGroups.calendar/ })).toHaveTextContent('4')
    expect(screen.getByRole('button', { name: /tabGroups.system/ })).toHaveTextContent('3')

    // …and the individual tabs still carry their own once the group is open.
    await user.click(screen.getByRole('button', { name: /tabGroups.calendar/ }))
    expect(screen.getByRole('link', { name: /tabs.reservations/ })).toHaveTextContent('3')
    expect(screen.getByRole('link', { name: /tabs.requests/ })).toHaveTextContent('1')
  })

  it('keeps Escape from reaching a modal listening on the document', async () => {
    // `Modal` and `ConfirmModal` both bind Escape to `document` without stopping it. Closing the
    // menu must not close whatever is underneath as well.
    const user = userEvent.setup()
    const onDocumentEscape = vi.fn()
    document.addEventListener('keydown', onDocumentEscape)

    try {
      renderNav('/admin')
      const calendar = screen.getByRole('button', { name: /tabGroups.calendar/ })
      await user.click(calendar)
      expect(screen.getByRole('link', { name: /tabs.slots/ })).toBeInTheDocument()

      onDocumentEscape.mockClear()
      await user.keyboard('{Escape}')

      expect(screen.queryByRole('link', { name: /tabs.slots/ })).not.toBeInTheDocument()
      expect(onDocumentEscape).not.toHaveBeenCalled()
    } finally {
      document.removeEventListener('keydown', onDocumentEscape)
    }
  })

  it('asks the page to open the palette', async () => {
    const user = userEvent.setup()
    const { onOpenPalette } = renderNav('/admin')

    await user.click(screen.getByRole('button', { name: 'palette.open' }))

    expect(onOpenPalette).toHaveBeenCalledOnce()
  })
})
