import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { AdminAssetsPanel } from './AdminAssetsPanel'

vi.mock('react-i18next', async (importOriginal) => ({
  ...(await importOriginal<typeof import('react-i18next')>()),
  useTranslation: () => ({ t: (key: string) => key, i18n: { language: 'pl' } }),
}))

const first = new File(['a'], 'a.png', { type: 'image/png' })
const second = new File(['b'], 'b.png', { type: 'image/png' })

// The picker is not under test: it hands the panel two files, as a real multi-select would.
vi.mock('../../components/ui/FileUpload', () => ({
  FileUpload: ({ onFileSelect }: { onFileSelect: (files: File[]) => void }) => (
    <button onClick={() => onFileSelect([first, second])}>pick</button>
  ),
}))

const list = vi.fn().mockResolvedValue([])
const upload = vi.fn()

vi.mock('../../api/client', () => ({
  adminAssetsApi: {
    list: () => list(),
    upload: (file: File) => upload(file),
    delete: vi.fn(),
  },
}))

beforeEach(() => {
  list.mockClear()
  upload.mockReset()
  URL.createObjectURL = vi.fn(() => 'blob:x')
  URL.revokeObjectURL = vi.fn()
})

describe('AdminAssetsPanel — a multi-file upload that fails part-way', () => {
  it('shows what did arrive and retries only what did not', async () => {
    upload.mockResolvedValueOnce({}).mockRejectedValueOnce(new Error('boom')).mockResolvedValue({})
    const user = userEvent.setup()
    render(
      <QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}>
        <AdminAssetsPanel />
      </QueryClientProvider>,
    )
    await waitFor(() => expect(list).toHaveBeenCalledTimes(1))

    await user.click(screen.getByText('assets.upload'))
    await user.click(screen.getByText('pick'))
    await user.click(screen.getByText('assets.uploadBtn'))

    await screen.findByText('boom')
    await waitFor(() => expect(list).toHaveBeenCalledTimes(2))

    await user.click(screen.getByText('assets.uploadBtn'))
    await waitFor(() => expect(upload).toHaveBeenCalledTimes(3))
    expect(upload.mock.calls.map(([f]) => f)).toEqual([first, second, second])
  })
})
