import { describe, it, expect, vi } from 'vitest'
import { useState } from 'react'
import { render, screen, fireEvent, act } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { BioBlockEditor, type BioBlock } from './BioBlockEditor'

vi.mock('react-i18next', async (importOriginal) => ({
  ...(await importOriginal<typeof import('react-i18next')>()),
  useTranslation: () => ({ t: (key: string) => key, i18n: { language: 'pl' } }),
}))

let finishUpload: (url: string) => void = () => {}
let failUpload: (e: Error) => void = () => {}
vi.mock('../../api/client', () => ({
  adminAssetsApi: {
    upload: () => new Promise((resolve, reject) => {
      finishUpload = (url) => resolve({ url })
      failUpload = reject
    }),
    list: () => Promise.resolve([]),
  },
  galleryApi: { getAlbums: () => Promise.resolve([]) },
}))

let latest: BioBlock[] = []
function Harness({ initial = [{ type: 'text', content: 'Bio' }] }: { initial?: BioBlock[] }) {
  const [blocks, setBlocks] = useState<BioBlock[]>(initial)
  return <BioBlockEditor blocks={blocks} onChange={(next) => { latest = next; setBlocks(next) }} />
}

function Wrapped({ initial }: { initial?: BioBlock[] }) {
  return <QueryClientProvider client={new QueryClient()}><Harness initial={initial} /></QueryClientProvider>
}

function pickFromDisk(container: HTMLElement) {
  const input = container.querySelector<HTMLInputElement>('input[type="file"]')!
  fireEvent.change(input, { target: { files: [new File(['x'], 'a.png', { type: 'image/png' })] } })
}

describe('BioBlockEditor — a photo uploaded from disk', () => {
  it('keeps what was typed while the photo was uploading', async () => {
    const user = userEvent.setup()
    const { container } = render(<Wrapped />)
    await user.click(screen.getByText('editorUi.bio.disk'))
    pickFromDisk(container)

    await user.type(screen.getByDisplayValue('Bio'), ' dopisane')
    await act(async () => finishUpload('https://example.com/a.png'))

    expect(latest).toEqual([
      { type: 'text', content: 'Bio dopisane' },
      { type: 'image', url: 'https://example.com/a.png', alt: '' },
    ])
  })

  it('says why an upload was refused', async () => {
    const user = userEvent.setup()
    const { container } = render(<Wrapped />)
    await user.click(screen.getByText('editorUi.bio.disk'))
    pickFromDisk(container)

    await act(async () => failUpload(new Error('Plik jest za duży')))

    expect(screen.getByRole('alert')).toBeInTheDocument()
  })
})

describe('BioBlockEditor — replacing a photo from disk', () => {
  // The block to replace was remembered by POSITION. Remove a block above it while the new photo
  // uploads and the photo landed on a different block; remove the photo's own row and it wrote past
  // the end of the list — a hole that crashed the editor.
  it('replaces the photo that was clicked, wherever it has moved to', async () => {
    const user = userEvent.setup()
    const { container } = render(<Wrapped initial={[
      { type: 'text', content: 'Wstęp' },
      { type: 'image', url: '/old.png', alt: '' },
    ]} />)
    await user.click(screen.getByTitle('editorUi.bio.replaceDisk'))
    pickFromDisk(container)

    await user.click(screen.getAllByTitle('editorUi.bio.remove')[0])
    await act(async () => finishUpload('/new.png'))

    expect(latest).toEqual([{ type: 'image', url: '/new.png', alt: '' }])
  })
})

