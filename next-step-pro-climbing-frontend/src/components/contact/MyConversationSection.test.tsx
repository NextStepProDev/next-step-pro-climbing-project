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
const markSeen = vi.fn()
const send = vi.fn()
vi.mock('../../api/client', () => ({
  contactApi: {
    getConversation: () => getConversation(),
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

function renderSection(conversation: MyConversation) {
  getConversation.mockResolvedValue(conversation)
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
