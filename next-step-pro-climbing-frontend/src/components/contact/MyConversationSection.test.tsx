import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { MemoryRouter } from 'react-router-dom'
import { MyConversationSection } from './MyConversationSection'
import type { ContactFile, ConversationMessage, MyConversation } from '../../types'

vi.mock('react-i18next', async (importOriginal) => ({
  ...(await importOriginal<typeof import('react-i18next')>()),
  useTranslation: () => ({ t: (key: string, o?: Record<string, string>) => (o?.title ? `${key}:${o.title}` : key), i18n: { language: 'pl' } }),
}))

const getConversation = vi.fn()
const getOlder = vi.fn()
const getNewer = vi.fn()
const markSeen = vi.fn()
const send = vi.fn()
const sendWithFiles = vi.fn()
const deleteFile = vi.fn()
vi.mock('../../api/client', () => ({
  contactApi: {
    getConversation: () => getConversation(),
    getOlder: (...a: unknown[]) => getOlder(...a),
    getNewer: (...a: unknown[]) => getNewer(...a),
    markSeen: () => markSeen(),
    send: (...a: unknown[]) => send(...a),
    sendWithFiles: (...a: unknown[]) => sendWithFiles(...a),
    deleteFile: (id: string) => deleteFile(id),
  },
}))

// The private previews fetch bytes with the session; here they only need to say what they show.
vi.mock('../training/PrivateFile', () => ({
  PrivateImage: ({ alt }: { alt: string }) => <img alt={alt} />,
  PrivateFileCard: ({ fileName }: { fileName: string | null }) => <span>{`pdf:${fileName}`}</span>,
}))

function file(over: Partial<ContactFile>): ContactFile {
  return {
    id: crypto.randomUUID(), url: '/api/conversation/files/x', mimeType: 'image/jpeg', fileName: 'zdjecie.jpg',
    sizeBytes: 1000, width: 400, height: 300, expiresAt: '2027-11-25T10:00:00Z', canDelete: true, ...over,
  }
}

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
    files: [],
    ...over,
  }
}

function renderSection(
  opened: Omit<MyConversation, 'hasOlder' | 'teamReadAt'> & { hasOlder?: boolean; teamReadAt?: string | null },
) {
  const conversation: MyConversation = { hasOlder: false, teamReadAt: null, ...opened }
  getConversation.mockResolvedValue(conversation)
  // An idle poll by default: nothing new, the same unseen and read state as the page that opened
  getNewer.mockResolvedValue({ messages: [], unseenReply: conversation.unseenReply, teamReadAt: conversation.teamReadAt })
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
  sendWithFiles.mockReset()
  deleteFile.mockReset().mockResolvedValue(undefined)
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

  // The editor writes markers; the bubble must show what they mean, and a list cannot live in a p.
  it('renders formatting in the bubble and keeps typed markup as text', async () => {
    renderSection({
      messages: [message({ body: '**Ważne:** zabierz\n• kask\n• uprząż\n<b>x</b>', fromStaff: true })],
      unseenReply: false,
    })

    const bold = await screen.findByText('Ważne:')
    expect(bold.tagName).toBe('STRONG')
    expect(screen.getByText('kask').tagName).toBe('LI')
    expect(bold.closest('p')).toBeNull()
    expect(screen.queryByText('**Ważne:**', { exact: false })).toBeNull()
    expect(screen.getByText(/<b>x<\/b>/)).toBeInTheDocument()
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

  it('offers "clear" only once something is written, and empties the box after a confirmation', async () => {
    const user = userEvent.setup()
    renderSection({ messages: [message({ body: 'Hej', fromStaff: true })], unseenReply: false })
    await screen.findByText('Hej')
    expect(screen.queryByRole('button', { name: 'unsentMessage.clear' })).not.toBeInTheDocument()

    const box = screen.getByLabelText('messages.reply')
    await user.type(box, 'Długa wiadomość')
    await user.click(screen.getByRole('button', { name: 'unsentMessage.clear' }))
    // Asked first: nothing is gone yet
    expect(box).toHaveValue('Długa wiadomość')
    expect(screen.getByText('unsentMessage.clearTitle')).toBeInTheDocument()

    const dialog = screen.getByRole('dialog')
    await user.click(within(dialog).getByRole('button', { name: 'unsentMessage.clear' }))
    expect(box).toHaveValue('')
    expect(send).not.toHaveBeenCalled()
    expect(screen.queryByRole('button', { name: 'unsentMessage.clear' })).not.toBeInTheDocument()
  })

  it('reports an unsent message to the page, and a clean box once it is sent', async () => {
    const user = userEvent.setup()
    const reports: boolean[] = []
    getConversation.mockResolvedValue({ hasOlder: false, messages: [message({ body: 'Hej', fromStaff: true })], unseenReply: false })
    getNewer.mockResolvedValue({ messages: [], unseenReply: false })
    const client = new QueryClient({ defaultOptions: { queries: { retry: false } } })
    render(
      <QueryClientProvider client={client}>
        <MemoryRouter>
          <MyConversationSection onDirtyChange={(d) => reports.push(d)} />
        </MemoryRouter>
      </QueryClientProvider>,
    )
    await screen.findByText('Hej')
    expect(reports.at(-1)).toBe(false)

    await user.type(screen.getByLabelText('messages.reply'), 'x')
    expect(reports.at(-1)).toBe(true)

    await user.click(screen.getByText('messages.send'))
    await waitFor(() => expect(reports.at(-1)).toBe(false))
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

describe('MyConversationSection — files', () => {
  it('shows a photo and a PDF of a message, and a message that is only a file', async () => {
    renderSection({
      messages: [
        message({ id: 'm-1', body: 'Plan na sobotę', fromStaff: true, files: [file({ mimeType: 'application/pdf', fileName: 'plan.pdf', canDelete: false })] }),
        message({ id: 'm-2', body: null, files: [file({ fileName: 'droga.jpg' })] }),
      ],
      unseenReply: false,
    })

    expect(await screen.findByText('pdf:plan.pdf')).toBeInTheDocument()
    expect(screen.getByAltText('droga.jpg')).toBeInTheDocument()
    // Only what the person sent can be withdrawn: one remove control, on their own photo
    expect(screen.getAllByText('comments.fileDelete')).toHaveLength(1)
  })

  it('sends a picked file through the upload endpoint and shows the message at once', async () => {
    const user = userEvent.setup()
    sendWithFiles.mockResolvedValue(message({ id: 'm-new', body: null, files: [file({ mimeType: 'application/pdf', fileName: 'wynik.pdf' })] }))
    const { container } = renderSection({ messages: [message({ id: 'm-1', body: 'Hej', fromStaff: true })], unseenReply: false })
    await screen.findByText('Hej')

    const pdf = new File(['%PDF-1.4'], 'wynik.pdf', { type: 'application/pdf' })
    await user.upload(container.querySelector('input[type="file"]') as HTMLInputElement, pdf)
    await user.click(await screen.findByText('messages.send'))

    expect(sendWithFiles).toHaveBeenCalledWith(null, 'pl', [pdf])
    expect(send).not.toHaveBeenCalled()
    expect(await screen.findByText('pdf:wynik.pdf')).toBeInTheDocument()
  })

  // Messages are otherwise immutable in the window: a removed file has to go from it at once, and a
  // message that was only that file with it - as on the server.
  it('takes a withdrawn file off the screen, and the message that was only that file', async () => {
    const user = userEvent.setup()
    renderSection({
      messages: [
        message({ id: 'm-1', body: 'Zostaje', fromStaff: true }),
        message({ id: 'm-2', body: null, files: [file({ id: 'f-1', fileName: 'pomylka.jpg' })] }),
      ],
      unseenReply: false,
    })

    await user.click(await screen.findByText('comments.fileDelete'))

    expect(deleteFile).toHaveBeenCalledWith('f-1')
    await waitFor(() => expect(screen.queryByAltText('pomylka.jpg')).not.toBeInTheDocument())
    expect(screen.getByText('Zostaje')).toBeInTheDocument()
  })
})

describe('MyConversationSection — read mark', () => {
  const mine = message({ id: 'mine-1', body: 'Moje pytanie', createdAt: '2026-11-22T10:00:00Z' })

  it('shows "not read yet" under the person\'s last message until the team looks', async () => {
    renderSection({ messages: [mine], unseenReply: false, teamReadAt: '2026-11-22T09:00:00Z' })
    expect(await screen.findByText('messages.notReadYet')).toBeInTheDocument()
  })

  it('turns to "read by the team" when a poll brings the team\'s look, without reopening', async () => {
    renderSection({ messages: [mine], unseenReply: false })
    expect(await screen.findByText('messages.notReadYet')).toBeInTheDocument()

    getNewer.mockResolvedValue({ messages: [], unseenReply: false, teamReadAt: '2026-11-22T10:05:00Z' })
    expect(await screen.findByText('messages.readByTeam', {}, { timeout: 12_000 })).toBeInTheDocument()
    expect(screen.queryByText('messages.notReadYet')).not.toBeInTheDocument()
  }, 15_000)

  it('shows no mark when the team wrote last', async () => {
    renderSection({ messages: [mine, message({ fromStaff: true, body: 'Odpowiedź', createdAt: '2026-11-22T11:00:00Z' })], unseenReply: false })
    await screen.findByText('Odpowiedź')

    expect(screen.queryByText('messages.notReadYet')).not.toBeInTheDocument()
    expect(screen.queryByText('messages.readByTeam')).not.toBeInTheDocument()
  })
})
