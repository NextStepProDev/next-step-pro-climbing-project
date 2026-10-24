import { describe, it, expect } from 'vitest'
import { keepablePendingBlocks } from './pendingBlockDraft'

describe('keepablePendingBlocks', () => {
  it('should keep text, video and library images, and count device images it cannot store', () => {
    const file = new File(['x'], 'a.jpg', { type: 'image/jpeg' })
    const { kept, dropped } = keepablePendingBlocks([
      { type: 'TEXT', tempId: '1', content: 'akapit' },
      { type: 'IMAGE', tempId: '2', caption: '', source: 'file', file, preview: 'blob:x' },
      { type: 'IMAGE', tempId: '3', caption: 'Tatry', source: 'library', imageUrl: '/api/files/assets/a.jpg', preview: 'blob:y' },
      { type: 'VIDEO_EMBED', tempId: '4', url: 'https://youtu.be/abcdefghijk' },
    ])

    expect(dropped).toBe(1)
    expect(kept.map((b) => b.tempId)).toEqual(['1', '3', '4'])
    // A blob: URL dies with the page; keeping it would restore a broken preview
    expect(kept[1]).not.toHaveProperty('preview')
  })
})
