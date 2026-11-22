import { describe, it, expect, vi, beforeEach } from 'vitest'
import { fireEvent, render, screen, waitFor } from '@testing-library/react'
import { FileUpload } from './FileUpload'

vi.mock('react-i18next', async (importOriginal) => ({
  ...(await importOriginal<typeof import('react-i18next')>()),
  useTranslation: () => ({ t: (key: string) => key, i18n: { language: 'pl' } }),
}))

// The decode needs codecs jsdom does not have; what is under test is what the picker does with
// the outcome. A file named "unreadable…" stands in for a HEIC this browser cannot decode.
const compressImage = vi.fn(async (file: File) => {
  if (file.name.startsWith('unreadable')) throw new Error('imageUnreadableHeic')
  return new File(['webp'], file.name.replace(/\.[^.]+$/, '.webp'), { type: 'image/webp' })
})

vi.mock('../../utils/imageUtils', async (importOriginal) => ({
  ...(await importOriginal<typeof import('../../utils/imageUtils')>()),
  compressImage: (file: File) => compressImage(file),
}))

function pick(container: HTMLElement, files: File[]) {
  const input = container.querySelector('input[type="file"]') as HTMLInputElement
  // fireEvent rather than userEvent.upload: the latter filters by `accept` itself, which is
  // exactly the browser behaviour that cannot be trusted for HEIC.
  fireEvent.change(input, { target: { files } })
}

describe('FileUpload', () => {
  beforeEach(() => {
    compressImage.mockClear()
  })

  it('should take a HEIC the browser reports with no MIME type', async () => {
    const onFileSelect = vi.fn()
    const { container } = render(<FileUpload onFileSelect={onFileSelect} multiple />)

    pick(container, [new File(['h'], 'IMG_0001.heic', { type: '' })])

    await waitFor(() => expect(onFileSelect).toHaveBeenCalledTimes(1))
    expect(onFileSelect.mock.calls[0][0].map((f: File) => f.name)).toEqual(['IMG_0001.webp'])
  })

  it('should keep the readable photos when one HEIC in the batch cannot be decoded', async () => {
    const onFileSelect = vi.fn()
    const { container } = render(<FileUpload onFileSelect={onFileSelect} multiple />)

    pick(container, [
      new File(['a'], 'a.jpg', { type: 'image/jpeg' }),
      new File(['h'], 'unreadable.heic', { type: 'image/heic' }),
      new File(['b'], 'b.png', { type: 'image/png' }),
    ])

    await waitFor(() => expect(onFileSelect).toHaveBeenCalledTimes(1))
    expect(onFileSelect.mock.calls[0][0].map((f: File) => f.name)).toEqual(['a.webp', 'b.webp'])
    expect(await screen.findByText('unreadable.heic: imageUnreadableHeic')).toBeInTheDocument()
  })

  it('should list HEIC among the formats without tripping over bare extensions in accept', () => {
    render(<FileUpload onFileSelect={vi.fn()} />)
    expect(screen.getByText(/JPEG, PNG, WEBP, HEIC, HEIF/)).toBeInTheDocument()
  })

  it('should still refuse a GIF', async () => {
    const onFileSelect = vi.fn()
    const { container } = render(<FileUpload onFileSelect={onFileSelect} />)

    pick(container, [new File(['g'], 'anim.gif', { type: 'image/gif' })])

    expect(await screen.findByText('editorUi.fileUpload.invalid')).toBeInTheDocument()
    expect(onFileSelect).not.toHaveBeenCalled()
  })
})
