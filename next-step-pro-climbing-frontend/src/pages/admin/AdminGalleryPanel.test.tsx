import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { AdminGalleryPanel } from './AdminGalleryPanel'

vi.mock('react-i18next', async (importOriginal) => ({
  ...(await importOriginal<typeof import('react-i18next')>()),
  useTranslation: () => ({ t: (key: string) => key, i18n: { language: 'pl' } }),
}))

const first = new File(['a'], 'a.jpg', { type: 'image/jpeg' })
const second = new File(['b'], 'b.jpg', { type: 'image/jpeg' })

vi.mock('../../components/ui/FileUpload', () => ({
  FileUpload: ({ onFileSelect }: { onFileSelect: (files: File[]) => void }) => (
    <button type="button" onClick={() => onFileSelect([first, second])}>pick</button>
  ),
}))

const album = { id: 'al1', name: 'Jura', description: null, displayOrder: 0, photoCount: 0, published: true, thumbnailUrl: null }
const uploadPhoto = vi.fn()

vi.mock('../../api/client', () => ({
  adminGalleryApi: {
    getAllAlbums: () => Promise.resolve([album]),
    getAlbum: () => Promise.resolve({ ...album, photos: [], thumbnailPhotoId: null }),
    uploadPhoto: (_albumId: string, file: File) => uploadPhoto(file),
  },
}))

beforeEach(() => {
  uploadPhoto.mockReset()
  URL.createObjectURL = vi.fn(() => 'blob:x')
  URL.revokeObjectURL = vi.fn()
})

describe('AdminGalleryPanel — a multi-photo upload that fails part-way', () => {
  it('keeps the dialog open with the photos that did not make it, and says why', async () => {
    uploadPhoto.mockResolvedValueOnce({}).mockRejectedValueOnce(new Error('za duże')).mockResolvedValue({})
    const user = userEvent.setup()
    render(
      <QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}>
        <AdminGalleryPanel />
      </QueryClientProvider>,
    )

    await user.click(await screen.findByText('galleryAdmin.expand'))
    await user.click(await screen.findByText('galleryAdmin.addPhoto'))
    await user.click(screen.getByText('pick'))
    await user.click(screen.getByRole('button', { name: /galleryAdmin\.upload/ }))

    expect(await screen.findByText('za duże')).toBeTruthy()
    await user.click(screen.getByRole('button', { name: /galleryAdmin\.upload/ }))
    await waitFor(() => expect(uploadPhoto).toHaveBeenCalledTimes(3))
    expect(uploadPhoto.mock.calls.map(([f]) => f)).toEqual([first, second, second])
  })
})
