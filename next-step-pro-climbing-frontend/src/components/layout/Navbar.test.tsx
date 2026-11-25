import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen, within, fireEvent } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { MemoryRouter } from 'react-router-dom'
import { Navbar } from './Navbar'

vi.mock('react-i18next', async (importOriginal) => ({
  ...(await importOriginal<typeof import('react-i18next')>()),
  useTranslation: () => ({ t: (key: string) => key, i18n: { language: 'pl', changeLanguage: vi.fn() } }),
}))

vi.mock('../ui/LanguageSwitcher', () => ({ LanguageSwitcher: () => null }))

vi.mock('../../context/ThemeContext', () => ({
  useTheme: () => ({ theme: 'dark', toggleTheme: vi.fn() }),
}))

let authState = {
  isAuthenticated: true,
  isAdmin: false,
  user: { id: 'u1', firstName: 'Ola', lastName: 'K', isAthlete: false } as unknown,
  logout: vi.fn(),
}
vi.mock('../../context/AuthContext', () => ({
  useAuth: () => authState,
}))

let unseenReply = false
vi.mock('../../api/client', () => ({
  adminApi: { getNotifications: vi.fn(async () => ({})) },
  reservationApi: { getMyInvitations: vi.fn(async () => []) },
  trainingCalendarApi: { getNotifications: vi.fn(async () => ({ newCount: 0 })) },
  contactApi: { getUnseen: vi.fn(async () => ({ unseenReply })) },
}))

function renderNavbar(path = '/kursy') {
  return render(
    <QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}>
      <MemoryRouter initialEntries={[path]}>
        <Navbar />
      </MemoryRouter>
    </QueryClientProvider>,
  )
}

// jsdom has no ResizeObserver; the navbar uses it only to re-measure the tab underline.
vi.stubGlobal('ResizeObserver', class { observe() {} unobserve() {} disconnect() {} })

beforeEach(() => {
  unseenReply = false
  authState = {
    isAuthenticated: true,
    isAdmin: false,
    user: { id: 'u1', firstName: 'Ola', lastName: 'K', isAthlete: false },
    logout: vi.fn(),
  }
})

describe('Navbar chat', () => {
  // On desktop the chat is an icon, so its accessible name has to carry the unread state.
  it('should announce an unseen reply on the chat icon, and not on My Reservations too', async () => {
    unseenReply = true
    renderNavbar()

    const chat = await screen.findByRole('link', { name: 'nav.chatUnread' })
    expect(chat.querySelector('.bg-rose-500')).not.toBeNull()
    const myReservations = screen.getByRole('link', { name: /nav\.myReservations/ })
    expect(within(myReservations).queryByText('1')).not.toBeInTheDocument()
  })

  it('should show a plain chat icon when nothing is waiting', async () => {
    renderNavbar()

    const chat = await screen.findByRole('link', { name: 'nav.chat' })
    expect(chat).toHaveAttribute('href', '/my-reservations?tab=messages')
    expect(chat.querySelector('.bg-rose-500')).toBeNull()
  })

  // The chat sends no mail to an account holder, so on a phone the dot on the closed menu is
  // the only sign that an answer is waiting.
  it('should light the closed mobile menu when only a chat reply is waiting', async () => {
    unseenReply = true
    renderNavbar()

    await screen.findByRole('link', { name: 'nav.chatUnread' })
    const menuButton = screen.getByRole('button', { name: 'nav.openMenu' })
    expect(menuButton.querySelector('.bg-rose-500')).not.toBeNull()
  })

  it('should leave the closed mobile menu dark when nothing is waiting', async () => {
    renderNavbar()

    await screen.findByRole('link', { name: 'nav.chat' })
    expect(screen.getByRole('button', { name: 'nav.openMenu' }).querySelector('.bg-rose-500')).toBeNull()
  })

  it('should list the chat with its badge in the mobile drawer', async () => {
    unseenReply = true
    renderNavbar()
    await screen.findByRole('link', { name: 'nav.chatUnread' })

    fireEvent.click(screen.getByRole('button', { name: 'nav.openMenu' }))

    const row = screen.getAllByRole('link', { name: /nav\.chat/ })
      .find((a) => a.textContent?.includes('nav.chat') && a.textContent.includes('1'))
    expect(row).toBeDefined()
  })

  it('should mark only the chat as current on the messages tab', () => {
    renderNavbar('/my-reservations?tab=messages')

    expect(screen.getByRole('link', { name: 'nav.chat' })).toHaveAttribute('aria-current', 'page')
    expect(screen.getByRole('link', { name: /nav\.myReservations/ })).not.toHaveAttribute('data-nav-active')
  })

  it('should mark only My Reservations on its other tabs', () => {
    renderNavbar('/my-reservations?tab=calendar')

    expect(screen.getByRole('link', { name: /nav\.myReservations/ })).toHaveAttribute('data-nav-active', 'true')
    expect(screen.getByRole('link', { name: 'nav.chat' })).not.toHaveAttribute('aria-current')
  })

  it('should not offer the chat to an admin, who answers in the panel', () => {
    authState = { ...authState, isAdmin: true }
    renderNavbar()

    expect(screen.queryByRole('link', { name: /nav\.chat/ })).not.toBeInTheDocument()
  })

  it('should not offer the chat to a guest', () => {
    authState = { ...authState, isAuthenticated: false, user: null }
    renderNavbar()

    expect(screen.queryByRole('link', { name: /nav\.chat/ })).not.toBeInTheDocument()
  })
})
