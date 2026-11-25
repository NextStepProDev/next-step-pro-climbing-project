import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { MemoryRouter } from 'react-router-dom'
import { MyConversationSection } from './MyConversationSection'
import type { ConversationMessage, MyConversation } from '../../types'

vi.mock('react-i18next', async (importOriginal) => ({
  ...(await importOriginal<typeof import('react-i18next')>()),
  useTranslation: () => ({ t: (key: string, o?: Record<string, string>) => (o?.title ? `${key}:${o.title}` : key), i18n: { language: 'pl' } }),
}))

const getConversation = vi.fn()
const getOlder = vi.fn()
const getNewer = vi.fn()
const markSeen = vi.fn()
const send = vi.fn()
vi.mock('../../api/client', () => ({
  contactApi: {
    getConversation: () => getConversation(),
    getOlder: (...a: unknown[]) => getOlder(...a),
    getNewer: (...a: unknown[]) => getNewer(...a),
    markSeen: () => markSeen(),
    send: (...a: unknown[]) => send(...a),
  },
}))

function message(over: Partial<ConversationMessage>): ConversationMessage {
  return {
    id: crypto.randomUUID(),
    fromStaff: false,
    body: 'x',
    contextType: 'GENERAL',
    courseId: null,
    courseTitle: null,
    date: null,
    createdAt: '2026-11-22T10:00:00Z',
    ...over,
  }
}

function renderSection(opened: Omit<MyConversation, 'hasOlder'> & { hasOlder?: boolean }) {
  const conversation: MyConversation = { hasOlder: false, ...opened }
  getConversation.mockResolvedValue(conversation)
  // An idle poll by default: nothing new, the same unseen state as the page that opened
  getNewer.mockResolvedValue({ messages: [], unseenReply: conversation.unseenReply })
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  return render(
    <QueryClientProvider client={client}>
      <MemoryRouter>
        <MyConversationSection />
      </MemoryRouter>
    </QueryClientProvider>,
  )
}

beforeEach(() => {
  getConversation.mockReset()
  getOlder.mockReset()
  getNewer.mockReset()
  markSeen.mockReset().mockResolvedValue(undefined)
  send.mockReset().mockResolvedValue({ inConversation: true })
})

describe('MyConversationSection', () => {
  it('invites a first message when there is no conversation yet', async () => {
    renderSection({ messages: [], unseenReply: false })

    expect(await screen.findByText('messages.emptyTitle')).toBeInTheDocument()
    expect(screen.getByText('messages.writeFirst')).toBeInTheDocument()
  })

  it('shows both sides, with the context only on what the person sent', async () => {
    renderSection({
      messages: [
        message({ body: 'Ile osób w grupie?', contextType: 'COURSE', courseTitle: 'Kurs skałkowy' }),
        message({ body: 'Sześć.', fromStaff: true }),
      ],
      unseenReply: false,
    })

    expect(await screen.findByText('Ile osób w grupie?')).toBeInTheDocument()
    expect(screen.getByText('Sześć.')).toBeInTheDocument()
    expect(screen.getByText(/contactForm.about.course:Kurs skałkowy/)).toBeInTheDocument()
    expect(screen.getByText(/messages.fromTeam/)).toBeInTheDocument()
  })

  it('marks an unseen answer as seen once it is on the screen', async () => {
    renderSection({ messages: [message({ body: 'Odpowiedź', fromStaff: true })], unseenReply: true })

    await screen.findByText('Odpowiedź')
    await waitFor(() => expect(markSeen).toHaveBeenCalledTimes(1))
  })

  it('does not report seen when there is nothing new', async () => {
    renderSection({ messages: [message({ body: 'Stare' })], unseenReply: false })

    await screen.findByText('Stare')
    expect(markSeen).not.toHaveBeenCalled()
  })

  it('writes into the same conversation as a general message and clears the box', async () => {
    const user = userEvent.setup()
    renderSection({ messages: [message({ body: 'Hej', fromStaff: true })], unseenReply: false })
    await screen.findByText('Hej')

    const box = screen.getByLabelText('messages.reply')
    await user.type(box, 'Dziękuję!')
    await user.click(screen.getByText('messages.send'))

    expect(send).toHaveBeenCalledWith(expect.objectContaining({ body: 'Dziękuję!', contextType: 'GENERAL' }))
    await waitFor(() => expect(box).toHaveValue(''))
  })
})

describe('MyConversationSection — chat window', () => {
  it('polls only for what comes after the newest message it has', async () => {
    const newest = message({ id: 'm-2', body: 'Druga', createdAt: '2026-11-22T10:05:00.123456Z' })
    renderSection({ messages: [message({ id: 'm-1', body: 'Pierwsza' }), newest], unseenReply: false })

    await screen.findByText('Druga')
    await waitFor(() => expect(getNewer).toHaveBeenCalledWith('2026-11-22T10:05:00.123456Z', 'm-2'))
  })

  it('appends a polled message without reading the whole conversation again', async () => {
    renderSection({ messages: [message({ id: 'm-1', body: 'Pytanie' })], unseenReply: false })
    getNewer.mockResolvedValueOnce({
      messages: [message({ id: 'm-2', body: 'Odpowiedź trenera', fromStaff: true, createdAt: '2026-11-22T11:00:00Z' })],
      unseenReply: true,
    })

    expect(await screen.findByText('Odpowiedź trenera')).toBeInTheDocument()
    expect(getConversation).toHaveBeenCalledTimes(1)
    const bodies = screen.getAllByText(/Pytanie|Odpowiedź trenera/).map((el) => el.textContent)
    expect(bodies).toEqual(['Pytanie', 'Odpowiedź trenera'])
  })

  it('loads the page above the oldest message and shows it on top', async () => {
    const user = userEvent.setup()
    renderSection({
      messages: [message({ id: 'm-31', body: 'Nowsza', createdAt: '2026-11-22T10:31:00Z' })],
      unseenReply: false,
      hasOlder: true,
    })
    getOlder.mockResolvedValueOnce({
      messages: [message({ id: 'm-1', body: 'Najstarsza', createdAt: '2026-11-01T09:00:00Z' })],
      hasOlder: false,
    })

    await user.click(await screen.findByText('messages.loadOlder'))

    expect(getOlder).toHaveBeenCalledWith('2026-11-22T10:31:00Z', 'm-31')
    await screen.findByText('Najstarsza')
    expect(screen.getAllByText(/Najstarsza|Nowsza/).map((el) => el.textContent)).toEqual(['Najstarsza', 'Nowsza'])
    expect(screen.queryByText('messages.loadOlder')).not.toBeInTheDocument()
  })

  // The flag stays "unseen" across two polls (an answer, the mark-seen, another answer), so the
  // effect has to key on the answer itself, or the second one never reports seen.
  it('reports a second answer as seen too, while the chat stays open', async () => {
    renderSection({ messages: [message({ id: 'a-1', body: 'Pierwsza odpowiedź', fromStaff: true })], unseenReply: true })
    await waitFor(() => expect(markSeen).toHaveBeenCalledTimes(1))

    getNewer.mockResolvedValue({
      messages: [message({ id: 'a-2', body: 'Druga odpowiedź', fromStaff: true, createdAt: '2026-11-22T12:00:00Z' })],
      unseenReply: true,
    })

    await screen.findByText('Druga odpowiedź', {}, { timeout: 8000 })
    await waitFor(() => expect(markSeen).toHaveBeenCalledTimes(2))
  }, 15_000)

  it('asks for news right after sending, instead of waiting for the next poll', async () => {
    const user = userEvent.setup()
    renderSection({ messages: [message({ id: 'm-1', body: 'Hej', fromStaff: true })], unseenReply: false })
    await screen.findByText('Hej')
    await waitFor(() => expect(getNewer).toHaveBeenCalled())
    const pollsBefore = getNewer.mock.calls.length

    await user.type(screen.getByLabelText('messages.reply'), 'Dzięki')
    await user.click(screen.getByText('messages.send'))

    await waitFor(() => expect(getNewer.mock.calls.length).toBeGreaterThan(pollsBefore))
  })
})
