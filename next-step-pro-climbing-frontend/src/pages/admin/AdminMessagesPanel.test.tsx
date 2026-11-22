import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { MemoryRouter } from 'react-router-dom'
import { AdminMessagesPanel } from './AdminMessagesPanel'
import type { AdminConversationDetail, AdminConversationSummary } from '../../types'

vi.mock('react-i18next', async (importOriginal) => ({
  ...(await importOriginal<typeof import('react-i18next')>()),
  useTranslation: () => ({ t: (key: string) => key, i18n: { language: 'pl' } }),
}))

const list = vi.fn()
const get = vi.fn()
const markRead = vi.fn()
const reply = vi.fn()
const remove = vi.fn()
vi.mock('../../api/client', () => ({
  adminContactApi: {
    list: (...a: unknown[]) => list(...a),
    get: (id: string) => get(id),
    markRead: (id: string) => markRead(id),
    reply: (...a: unknown[]) => reply(...a),
    delete: (id: string) => remove(id),
  },
}))

function summary(over: Partial<AdminConversationSummary>): AdminConversationSummary {
  return {
    id: 'c-guest',
    userId: null,
    name: 'Ola Gość',
    email: 'ola@example.com',
    unread: true,
    answered: false,
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
    reply.mockResolvedValueOnce({ message: {}, mailed: false })
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
