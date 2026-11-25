import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { MemoryRouter } from 'react-router-dom'
import { format } from 'date-fns'
import { pl } from 'date-fns/locale'
import { AdminMessagesPanel } from './AdminMessagesPanel'
import type { AdminConversationDetail, AdminConversationSummary, AdminUser } from '../../types'

vi.mock('react-i18next', async (importOriginal) => ({
  ...(await importOriginal<typeof import('react-i18next')>()),
  useTranslation: () => ({ t: (key: string) => key, i18n: { language: 'pl' } }),
}))

const list = vi.fn()
const get = vi.fn()
const markRead = vi.fn()
const reply = vi.fn()
const remove = vi.fn()
const start = vi.fn()
const findForUser = vi.fn()
const getAllUsers = vi.fn()
const older = vi.fn()
const newer = vi.fn()
vi.mock('../../api/client', () => ({
  adminContactApi: {
    list: (...a: unknown[]) => list(...a),
    get: (id: string) => get(id),
    older: (...a: unknown[]) => older(...a),
    newer: (...a: unknown[]) => newer(...a),
    markRead: (id: string) => markRead(id),
    reply: (...a: unknown[]) => reply(...a),
    delete: (id: string) => remove(id),
    start: (...a: unknown[]) => start(...a),
    findForUser: (id: string) => findForUser(id),
  },
  adminApi: {
    getAllUsers: () => getAllUsers(),
  },
}))

function person(over: Partial<AdminUser>): AdminUser {
  return {
    id: 'u-new', firstName: 'Ewa', lastName: 'Nowak', email: 'ewa@example.com', phone: '+48100100100',
    role: 'USER', createdAt: '2026-01-01T00:00:00Z', newsletterSubscribed: false, isAthlete: false,
    emailVerified: true, ...over,
  }
}

const EWA = person({})
const UNCONFIRMED = person({ id: 'u-unconfirmed', firstName: 'Ewelina', lastName: 'Niepotwierdzona', email: 'ewelina@example.com', emailVerified: false })
const OTHER_ADMIN = person({ id: 'u-admin', firstName: 'Ewald', lastName: 'Admin', email: 'ewald@example.com', role: 'ADMIN' })
const JAN = person({ id: 'u-1', firstName: 'Jan', lastName: 'Kowalski', email: 'jan@example.com' })

function summary(over: Partial<AdminConversationSummary>): AdminConversationSummary {
  return {
    id: 'c-guest',
    userId: null,
    name: 'Ola Gość',
    email: 'ola@example.com',
    unread: true,
    answered: false,
    lastActivityAt: '2026-11-22T10:00:00Z',
    lastInboundAt: '2026-11-22T10:00:00Z',
    lastReplyAt: null,
    preview: 'Pytanie',
    previewFromStaff: false,
    previewContextType: 'GENERAL',
    previewCourseTitle: null,
    previewDate: null,
    ...over,
  }
}

const GUEST = summary({})
const CLIENT = summary({ id: 'c-user', userId: 'u-1', name: 'Jan Kowalski', email: 'jan@example.com', unread: false })

function detail(s: AdminConversationSummary): AdminConversationDetail {
  return {
    summary: s,
    messages: [{
      id: `${s.id}-m1`, fromStaff: false, staffAuthorName: null, body: `Wiadomość od ${s.name}`,
      contextType: 'GENERAL', courseId: null, courseTitle: null, date: null, language: 'pl',
      createdAt: '2026-11-22T10:00:00Z',
    }],
    hasOlder: false,
  }
}

function staffMessage(id: string, body: string, createdAt = '2026-11-22T11:00:00Z') {
  return {
    id, fromStaff: true, staffAuthorName: 'Trener', body, contextType: 'GENERAL' as const,
    courseId: null, courseTitle: null, date: null, language: 'pl', createdAt,
  }
}

function renderPanel(url = '/admin/messages') {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  return render(
    <QueryClientProvider client={client}>
      <MemoryRouter initialEntries={[url]}>
        <AdminMessagesPanel />
      </MemoryRouter>
    </QueryClientProvider>,
  )
}

beforeEach(() => {
  list.mockReset().mockResolvedValue({ content: [GUEST, CLIENT], totalElements: 2, totalPages: 1, page: 0 })
  get.mockReset().mockImplementation((id: string) => Promise.resolve(detail(id === CLIENT.id ? CLIENT : GUEST)))
  markRead.mockReset().mockResolvedValue(undefined)
  reply.mockReset()
  remove.mockReset()
  start.mockReset()
  findForUser.mockReset().mockImplementation((id: string) =>
    Promise.resolve({ conversationId: id === JAN.id ? CLIENT.id : null }))
  getAllUsers.mockReset().mockResolvedValue([EWA, UNCONFIRMED, OTHER_ADMIN, JAN])
  older.mockReset()
  // An idle poll: nothing new, the conversation's summary as it was
  newer.mockReset().mockImplementation((id: string) =>
    Promise.resolve({ summary: id === CLIENT.id ? CLIENT : GUEST, messages: [] }))
})

describe('AdminMessagesPanel', () => {
  it('marks an unread conversation read when it is opened, and leaves a read one alone', async () => {
    const user = userEvent.setup()
    renderPanel()

    await user.click(await screen.findByText('Ola Gość'))
    await screen.findByText('Wiadomość od Ola Gość')
    await waitFor(() => expect(markRead).toHaveBeenCalledWith('c-guest'))

    await user.click(screen.getByText('Jan Kowalski'))
    await screen.findByText('Wiadomość od Jan Kowalski')
    expect(markRead).toHaveBeenCalledTimes(1)
  })

  it('says "unread" in words, not only with a red dot', async () => {
    renderPanel()

    // An aria-label on a role-less <span> is ignored by screen readers; the state has to be text
    const row = (await screen.findByText('Ola Gość')).closest('button')!
    expect(within(row).getByText('contactMessages.unread')).toBeInTheDocument()
  })

  it('links an account to its user card and tells that a guest only gets mail', async () => {
    renderPanel('/admin/messages?conversation=c-user')
    expect(await screen.findByRole('link', { name: /contactMessages.userCard/ })).toHaveAttribute('href', '/admin/users/u-1')

    renderPanel('/admin/messages?conversation=c-guest')
    expect(await screen.findByText('contactMessages.guestHint')).toBeInTheDocument()
  })

  it('says whether the reply went out by mail or only into the app', async () => {
    const user = userEvent.setup()
    reply.mockResolvedValueOnce({ message: staffMessage('r-1', 'Odpowiedź'), mailed: false })
    renderPanel('/admin/messages?conversation=c-user')

    await user.type(await screen.findByLabelText('contactMessages.reply'), 'Odpowiedź')
    await user.click(screen.getByText('contactMessages.send'))

    expect(reply).toHaveBeenCalledWith('c-user', 'Odpowiedź')
    expect(await screen.findByText('contactMessages.sentInAppOnly')).toBeInTheDocument()
  })

  it('never carries a half-written reply over to another person', async () => {
    const user = userEvent.setup()
    renderPanel('/admin/messages?conversation=c-guest')

    await user.type(await screen.findByLabelText('contactMessages.reply'), 'Do Oli')
    await user.click(screen.getByText('Jan Kowalski'))
    await screen.findByText('Wiadomość od Jan Kowalski')

    expect(screen.getByLabelText('contactMessages.reply')).toHaveValue('')
  })

  it('does not ask for a conversation again once it is deleted', async () => {
    const user = userEvent.setup()
    remove.mockResolvedValue(undefined)
    renderPanel('/admin/messages?conversation=c-guest')

    await user.click(await screen.findByLabelText('contactMessages.delete'))
    // Opening an unread conversation marks it read and refetches it — that is fine and happens
    // before the delete; only fetches AFTER the delete are the bug.
    await waitFor(() => expect(markRead).toHaveBeenCalled())
    await new Promise((r) => setTimeout(r, 50))
    const fetchesBefore = get.mock.calls.length
    await user.click(within(await screen.findByRole('dialog')).getByText('contactMessages.delete'))
    await screen.findByText('contactMessages.pick')
    await new Promise((r) => setTimeout(r, 50))

    expect(get.mock.calls.slice(fetchesBefore).filter(([id]) => id === 'c-guest')).toHaveLength(0)
  })

  it('cannot start a second delete while the first one is on its way', async () => {
    const user = userEvent.setup()
    remove.mockReturnValue(new Promise(() => {}))
    renderPanel('/admin/messages?conversation=c-guest')

    await user.click(await screen.findByLabelText('contactMessages.delete'))
    await user.click(within(await screen.findByRole('dialog')).getByText('contactMessages.delete'))

    await waitFor(() => expect(screen.getByLabelText('contactMessages.delete')).toBeDisabled())
  })

  it('says on the conversation when deleting fails (the confirmation is gone by then)', async () => {
    const user = userEvent.setup()
    remove.mockRejectedValue(new Error('Serwer nie odpowiada'))
    renderPanel('/admin/messages?conversation=c-guest')

    await user.click(await screen.findByLabelText('contactMessages.delete'))
    const dialog = await screen.findByRole('dialog')
    await user.click(within(dialog).getByText('contactMessages.delete'))

    expect(await screen.findByRole('alert')).toHaveTextContent('Serwer nie odpowiada')
    expect(screen.getByText('Wiadomość od Ola Gość')).toBeInTheDocument()
  })
})

describe('AdminMessagesPanel — message centre', () => {
  // A conversation the team started has no message from the person: its inbound clock is null, and
  // `new Date(null)` is 1 January 1970 — no crash, just a silently wrong date on the row.
  it('dates a conversation the team started by its newest message', async () => {
    const started = summary({
      id: 'c-started', userId: 'u-new', name: 'Ewa Nowak', unread: false, answered: true,
      lastInboundAt: null, lastReplyAt: '2026-11-23T09:00:00Z', lastActivityAt: '2026-11-23T09:00:00Z',
      preview: 'Cześć!', previewFromStaff: true, previewContextType: 'GENERAL',
    })
    list.mockResolvedValue({ content: [started], totalElements: 1, totalPages: 1, page: 0 })
    renderPanel()

    const row = (await screen.findByText('Ewa Nowak')).closest('button')!
    expect(row).toHaveTextContent(format(new Date('2026-11-23T09:00:00Z'), 'd MMM, HH:mm', { locale: pl }))
    expect(row).not.toHaveTextContent(format(new Date(0), 'd MMM, HH:mm', { locale: pl }))
    expect(screen.queryByText('contactMessages.awaiting')).not.toBeInTheDocument()
  })

  it('searches on the server once typing pauses, from the first page', async () => {
    const user = userEvent.setup()
    renderPanel()
    await screen.findByText('Ola Gość')

    await user.type(screen.getByLabelText('contactMessages.searchLabel'), 'kowal')

    await waitFor(() => expect(list).toHaveBeenLastCalledWith(false, 0, 20, 'kowal'))
    // Not one request per key: the intermediate prefixes never reach the server
    expect(list.mock.calls.some((c) => c[3] === 'k' || c[3] === 'ko')).toBe(false)
  })

  it('starts a conversation with a registered person and opens it', async () => {
    const user = userEvent.setup()
    start.mockResolvedValue({ conversationId: 'c-new', message: {} })
    get.mockImplementation((id: string) => Promise.resolve(detail(id === 'c-new'
      ? summary({ id: 'c-new', userId: EWA.id, name: 'Ewa Nowak', unread: false })
      : GUEST)))
    renderPanel()

    await user.click(await screen.findByText('contactMessages.newConversation'))
    await user.type(screen.getByLabelText('contactMessages.pickLabel'), 'ewa')
    await user.click(await screen.findByText('Ewa Nowak'))
    await waitFor(() => expect(findForUser).toHaveBeenCalledWith(EWA.id))
    await user.type(screen.getByLabelText('contactMessages.firstMessage'), 'Mamy wolne miejsce')
    await user.click(screen.getByText('contactMessages.start'))

    expect(start).toHaveBeenCalledWith(EWA.id, 'Mamy wolne miejsce')
    expect(await screen.findByText('Wiadomość od Ewa Nowak')).toBeInTheDocument()
  })

  it('opens the existing conversation instead of starting a second one', async () => {
    const user = userEvent.setup()
    renderPanel()

    await user.click(await screen.findByText('contactMessages.newConversation'))
    await user.type(screen.getByLabelText('contactMessages.pickLabel'), 'jan')
    const results = await screen.findByRole('list', { name: 'contactMessages.pickResults' })
    await user.click(within(results).getByText('Jan Kowalski'))

    expect(await screen.findByText('Wiadomość od Jan Kowalski')).toBeInTheDocument()
    expect(start).not.toHaveBeenCalled()
  })

  it('offers no admin and no unconfirmed account as somebody to write to', async () => {
    const user = userEvent.setup()
    renderPanel('/admin/messages?new=1')

    await user.type(await screen.findByLabelText('contactMessages.pickLabel'), 'ew')

    const results = await screen.findByRole('list', { name: 'contactMessages.pickResults' })
    expect(within(results).queryByText(/Ewald/)).not.toBeInTheDocument()
    expect(within(results).getByText('Ewelina Niepotwierdzona').closest('button')).toBeDisabled()
    expect(within(results).getByText('Ewa Nowak').closest('button')).toBeEnabled()
  })

  it('arrives with the person already chosen from their user card', async () => {
    renderPanel(`/admin/messages?new=${EWA.id}`)

    expect(await screen.findByText('ewa@example.com')).toBeInTheDocument()
    expect(screen.queryByLabelText('contactMessages.pickLabel')).toBeNull()
    await waitFor(() => expect(findForUser).toHaveBeenCalledWith(EWA.id))
  })

  it('asks before throwing away a message that was already typed', async () => {
    const user = userEvent.setup()
    renderPanel(`/admin/messages?new=${EWA.id}`)

    await user.type(await screen.findByLabelText('contactMessages.firstMessage'), 'Szkic')
    await user.click(screen.getByLabelText('contactMessages.cancel'))

    expect(await screen.findByRole('dialog')).toBeInTheDocument()
    expect(screen.getByLabelText('contactMessages.firstMessage')).toHaveValue('Szkic')
  })
})

describe('AdminMessagesPanel — chat window', () => {
  it('shows a sent reply at once, from the answer, without re-reading the conversation', async () => {
    const user = userEvent.setup()
    reply.mockResolvedValueOnce({ message: staffMessage('r-1', 'Tak, w sobotę'), mailed: true })
    renderPanel('/admin/messages?conversation=c-guest')

    await user.type(await screen.findByLabelText('contactMessages.reply'), 'Tak, w sobotę')
    await user.click(screen.getByText('contactMessages.send'))

    expect(await screen.findByText('Tak, w sobotę', { selector: 'p' })).toBeInTheDocument()
    expect(get.mock.calls.filter(([id]) => id === 'c-guest')).toHaveLength(1)
  })

  // A new message from the person arriving on a poll while the conversation is open must be
  // marked read again — the unread flag was already "true" before, so only the message changed.
  it('marks a conversation read again when a new message arrives while it is open', async () => {
    renderPanel('/admin/messages?conversation=c-guest')
    await waitFor(() => expect(markRead).toHaveBeenCalledTimes(1))

    const later = '2026-11-22T12:00:00Z'
    newer.mockResolvedValue({
      summary: { ...GUEST, unread: true, lastInboundAt: later, lastActivityAt: later },
      messages: [{ ...staffMessage('m-2', 'A w niedzielę?', later), fromStaff: false, staffAuthorName: null }],
    })

    await screen.findByText('A w niedzielę?', {}, { timeout: 8000 })
    await waitFor(() => expect(markRead).toHaveBeenCalledTimes(2))
  }, 15_000)

  it('pages up from the oldest message it shows', async () => {
    const user = userEvent.setup()
    get.mockImplementation(() => Promise.resolve({ ...detail(GUEST), hasOlder: true }))
    older.mockResolvedValueOnce({
      messages: [{ ...staffMessage('m-0', 'Dawno temu', '2026-10-01T08:00:00Z'), fromStaff: false, staffAuthorName: null }],
      hasOlder: false,
    })
    renderPanel('/admin/messages?conversation=c-guest')

    await user.click(await screen.findByText('contactMessages.loadOlder'))

    expect(older).toHaveBeenCalledWith('c-guest', '2026-11-22T10:00:00Z', 'c-guest-m1')
    expect(await screen.findByText('Dawno temu')).toBeInTheDocument()
  })
})
