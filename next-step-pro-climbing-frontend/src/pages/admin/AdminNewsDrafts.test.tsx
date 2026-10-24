import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { MemoryRouter } from 'react-router-dom'
import { ToastProvider } from '../../context/ToastContext'
import { AdminNewsPanel } from './AdminNewsPanel'
import { readDraft } from '../../utils/draftStorage'

/** The article editor keeps a draft that a navbar click (an unmount) cannot take away. */

vi.mock('react-i18next', async (importOriginal) => ({
  ...(await importOriginal<typeof import('react-i18next')>()),
  useTranslation: () => ({
    t: (key: string, opts?: Record<string, unknown>) => (opts ? `${key}:${Object.values(opts).join(',')}` : key),
    i18n: { language: 'pl' },
  }),
}))

vi.mock('../../context/AuthContext', () => ({ useCurrentUserId: () => 'admin-1' }))

const article = {
  id: 'n1', title: 'Artykuł', excerpt: '', language: 'pl', translationGroupId: 'g1',
  published: false, publishedAt: null, thumbnailUrl: null, createdAt: '2026-10-01T10:00:00Z',
}
let updatedAt = '2026-10-01T10:00:00Z'

vi.mock('../../api/client', () => ({
  adminNewsApi: {
    getAll: () => Promise.resolve({ content: [article] }),
    getById: () => Promise.resolve({
      ...article, updatedAt, blocks: [], thumbnailFocalPointX: null, thumbnailFocalPointY: null,
    }),
  },
  adminAssetsApi: { list: () => Promise.resolve([]) },
  galleryApi: { getAlbums: () => Promise.resolve([]), getAlbum: () => Promise.resolve({ photos: [] }) },
}))

const renderPanel = () =>
  render(
    <QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}>
      <MemoryRouter><ToastProvider><AdminNewsPanel /></ToastProvider></MemoryRouter>
    </QueryClientProvider>,
  )

async function openEditorAndWrite(user: ReturnType<typeof userEvent.setup>) {
  const view = renderPanel()
  await user.click(await screen.findByTitle('news.edit (PL)'))
  await user.click(await screen.findByText('news.addParagraph'))
  const boxes = screen.getAllByRole('textbox')
  await user.type(boxes[boxes.length - 1], 'Wyjazd w Tatry')
  return view
}

beforeEach(() => {
  localStorage.clear()
  updatedAt = '2026-10-01T10:00:00Z'
})

describe('AdminNewsPanel drafts', () => {
  it('should bring an unsaved paragraph back after leaving through the navbar', async () => {
    const user = userEvent.setup()
    const first = await openEditorAndWrite(user)
    first.unmount()

    renderPanel()
    await user.click(await screen.findByTitle('news.edit (PL)'))
    expect(await screen.findByText(/^draft\.found/)).toBeInTheDocument()
    expect(screen.queryByText('draft.stale')).toBeNull()

    await user.click(screen.getByRole('button', { name: 'draft.restore' }))

    expect(screen.getByDisplayValue('Wyjazd w Tatry')).toBeInTheDocument()
  })

  it('should warn that restoring overwrites a version saved in the meantime', async () => {
    const user = userEvent.setup()
    const first = await openEditorAndWrite(user)
    first.unmount()

    updatedAt = '2026-10-02T08:00:00Z'
    renderPanel()
    await user.click(await screen.findByTitle('news.edit (PL)'))

    expect(await screen.findByText('draft.stale')).toBeInTheDocument()
  })

  it('should forget the draft when leaving is confirmed as a discard', async () => {
    const user = userEvent.setup()
    await openEditorAndWrite(user)
    expect(readDraft('admin-1', 'news:n1')).not.toBeNull()

    await user.click(screen.getByText('news.backToList'))
    await user.click(await screen.findByRole('button', { name: 'news.exitConfirm' }))

    await waitFor(() => expect(readDraft('admin-1', 'news:n1')).toBeNull())
  })
})
