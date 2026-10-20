import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { AdminVideosPanel } from './AdminVideosPanel'
import type { VideoAdmin } from '../../types'

vi.mock('react-i18next', async (importOriginal) => ({
  ...(await importOriginal<typeof import('react-i18next')>()),
  useTranslation: () => ({ t: (key: string) => key, i18n: { language: 'pl' } }),
}))

const video = {
  id: 'v1',
  title: 'Film',
  excerpt: 'Zajawka',
  content: 'Opis',
  youtubeUrl: 'https://www.youtube.com/watch?v=abc',
  published: false,
} as VideoAdmin

const update = vi.fn((_id: string, data: Partial<VideoAdmin>) => Promise.resolve({ ...video, ...data }))

vi.mock('../../api/client', () => ({
  adminVideoApi: {
    getAll: () => Promise.resolve([video]),
    update: (id: string, data: Partial<VideoAdmin>) => update(id, data),
    create: vi.fn(), delete: vi.fn(), publish: vi.fn(), unpublish: vi.fn(), moveUp: vi.fn(), moveDown: vi.fn(),
  },
}))

function renderPanel() {
  render(
    <QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}>
      <AdminVideosPanel />
    </QueryClientProvider>,
  )
}

beforeEach(() => update.mockClear())

describe('AdminVideosPanel — the editor', () => {
  it('asks before leaving an edited form, instead of doing nothing', async () => {
    const user = userEvent.setup()
    renderPanel()
    await user.click(await screen.findByTitle('videos.edit'))
    await user.type(screen.getAllByRole('textbox')[0], '!')

    await user.click(screen.getByText('videos.backToList'))

    expect(screen.getByText('videos.exitConfirmTitle')).toBeTruthy()
    await user.click(screen.getByText('videos.exitConfirm'))
    expect(await screen.findByText('videos.title')).toBeTruthy()
  })

  it('sends an emptied excerpt as empty, so the server clears it', async () => {
    const user = userEvent.setup()
    renderPanel()
    await user.click(await screen.findByTitle('videos.edit'))
    await user.clear(screen.getByDisplayValue('Zajawka'))

    await user.click(screen.getByText('videos.saveDraft'))

    expect(update).toHaveBeenCalledWith('v1', expect.objectContaining({ excerpt: '' }))
  })
})
