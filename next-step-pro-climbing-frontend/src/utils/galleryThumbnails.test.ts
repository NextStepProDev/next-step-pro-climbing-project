import { describe, expect, it } from 'vitest'
import { visibleThumbnailCount } from './galleryThumbnails'

describe('visibleThumbnailCount', () => {
  it('should show every photo of a small album', () => {
    expect([0, 1, 2, 3].map(visibleThumbnailCount)).toEqual([0, 1, 2, 3])
  })

  it('should show one full row instead of a ragged second one', () => {
    expect([4, 5, 6, 7].map(visibleThumbnailCount)).toEqual([4, 4, 4, 4])
  })

  it('should stop at two rows however big the album is', () => {
    expect([8, 9, 32, 500].map(visibleThumbnailCount)).toEqual([8, 8, 8, 8])
  })
})
