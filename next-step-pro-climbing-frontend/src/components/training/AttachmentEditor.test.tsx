import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { AttachmentEditor } from './AttachmentEditor'
import type { AttachmentInput } from '../../types'

vi.mock('react-i18next', () => ({
  useTranslation: () => ({ t: (key: string) => key, i18n: { language: 'pl' } }),
  initReactI18next: { type: '3rdParty', init: () => {} },
}))

const onChange = vi.fn()
const onUpload = vi.fn()

function jpeg(name: string) {
  return new File(['jpeg'], name, { type: 'image/jpeg' })
}

function stored(name: string) {
  return { filename: `${name}-uuid.jpg`, originalName: name, mimeType: 'image/jpeg', sizeBytes: 1024 }
}

function link(url: string): AttachmentInput {
  return { kind: 'LINK', url, label: '' }
}

function renderEditor(value: AttachmentInput[] = []) {
  return render(<AttachmentEditor value={value} onChange={onChange} onUpload={onUpload} />)
}

function fileInput(container: HTMLElement) {
  return container.querySelector('input[type="file"]') as HTMLInputElement
}

describe('AttachmentEditor', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('takes several files from one pick and reports them in a single change', async () => {
    const user = userEvent.setup()
    onUpload
      .mockResolvedValueOnce(stored('a.jpg'))
      .mockResolvedValueOnce(stored('b.jpg'))
      .mockResolvedValueOnce(stored('c.jpg'))
    const { container } = renderEditor()

    await user.upload(fileInput(container), [jpeg('a.jpg'), jpeg('b.jpg'), jpeg('c.jpg')])

    await waitFor(() => expect(onUpload).toHaveBeenCalledTimes(3))
    // One call, three entries: `value` is the prop captured by the render that built the handler,
    // so appending per file would have kept overwriting the list with a one-item version of itself.
    expect(onChange).toHaveBeenCalledTimes(1)
    expect(onChange.mock.calls[0][0]).toHaveLength(3)
    expect(onChange.mock.calls[0][0].map((a: AttachmentInput) => a.originalName))
      .toEqual(['a.jpg', 'b.jpg', 'c.jpg'])
  })

  it('keeps existing materials and appends the new ones after them', async () => {
    const user = userEvent.setup()
    onUpload.mockResolvedValueOnce(stored('a'))
    const { container } = renderEditor([link('https://youtu.be/x')])

    await user.upload(fileInput(container), [jpeg('a.jpg')])

    await waitFor(() => expect(onChange).toHaveBeenCalledTimes(1))
    const next = onChange.mock.calls[0][0] as AttachmentInput[]
    expect(next).toHaveLength(2)
    expect(next[0].kind).toBe('LINK')
    expect(next[1].kind).toBe('FILE')
  })

  it('keeps a change to the list made while the upload was still in flight', async () => {
    const user = userEvent.setup()
    let finishUpload: (v: unknown) => void = () => {}
    onUpload.mockImplementation(() => new Promise(resolve => { finishUpload = resolve }))
    const { container, rerender } = renderEditor()

    await user.upload(fileInput(container), [jpeg('a.jpg')])
    // Six files take seconds, and the section stays live: a link pasted meanwhile must not be
    // wiped by the merge that lands when the upload finally returns.
    rerender(<AttachmentEditor value={[link('https://youtu.be/x')]} onChange={onChange} onUpload={onUpload} />)
    finishUpload(stored('a.jpg'))

    await waitFor(() => expect(onChange).toHaveBeenCalledTimes(1))
    const next = onChange.mock.calls[0][0] as AttachmentInput[]
    expect(next.map(a => a.kind)).toEqual(['LINK', 'FILE'])
  })

  it('will not let a link be added while files are still uploading', async () => {
    const user = userEvent.setup()
    onUpload.mockImplementation(() => new Promise(() => {}))
    const { container } = renderEditor()

    await user.upload(fileInput(container), [jpeg('a.jpg'), jpeg('b.jpg')])

    // The free-slot count is spent but not yet spent visibly: the uploads have not landed, so the
    // cap that hides these buttons still reads zero taken. A link pasted now would merge into a
    // list of seven the server then refuses on save.
    await waitFor(() => expect(screen.getByText('form.attachmentAdd').closest('button')).toBeDisabled())
  })

  it('drops a refusal message once the free-slot count it names has changed', async () => {
    const user = userEvent.setup()
    const value = [link('https://a.com'), link('https://b.com'), link('https://c.com'),
      link('https://d.com'), link('https://e.com')]
    const { container } = renderEditor(value)

    await user.upload(fileInput(container), [jpeg('a.jpg'), jpeg('b.jpg')])
    expect(await screen.findByRole('alert')).toBeInTheDocument()

    // Removing a material frees a slot, so "free slots: 1" stops being true.
    await user.click(screen.getAllByLabelText('form.attachmentRemove')[0])
    expect(screen.queryByRole('alert')).not.toBeInTheDocument()
  })

  it('refuses a pick larger than the free slots without uploading anything', async () => {
    const user = userEvent.setup()
    const { container } = renderEditor([link('https://a.com'), link('https://b.com'),
      link('https://c.com'), link('https://d.com'), link('https://e.com')])

    // Five taken, one slot free, three picked — refuse the lot rather than quietly keeping one.
    await user.upload(fileInput(container), [jpeg('a.jpg'), jpeg('b.jpg'), jpeg('c.jpg')])

    expect(await screen.findByRole('alert')).toHaveTextContent('form.attachmentTooMany')
    expect(onUpload).not.toHaveBeenCalled()
    expect(onChange).not.toHaveBeenCalled()
  })

  it('keeps the files that already reached the server when a later one fails', async () => {
    const user = userEvent.setup()
    onUpload
      .mockResolvedValueOnce(stored('a'))
      .mockRejectedValueOnce(new Error('File size exceeds maximum allowed size (10MB)'))
    const { container } = renderEditor()

    await user.upload(fileInput(container), [jpeg('a.jpg'), jpeg('huge.jpg'), jpeg('c.jpg')])

    expect(await screen.findByRole('alert')).toHaveTextContent('10MB')
    // The third file is never sent, and the first one stays: its bytes are on disk already, so
    // dropping the row would leave an orphan the user can neither see nor remove.
    expect(onUpload).toHaveBeenCalledTimes(2)
    expect(onChange).toHaveBeenCalledTimes(1)
    expect(onChange.mock.calls[0][0]).toHaveLength(1)
  })

  it('hides both add buttons once the cap is reached', () => {
    const full = Array.from({ length: 6 }, (_, i) => link(`https://a${i}.com`))
    const { container } = renderEditor(full)

    expect(screen.queryByText('form.attachmentUpload')).not.toBeInTheDocument()
    expect(screen.queryByText('form.attachmentAdd')).not.toBeInTheDocument()
    expect(fileInput(container)).toBeNull()
  })
})
