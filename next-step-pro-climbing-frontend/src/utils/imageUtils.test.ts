import { afterEach, beforeEach, describe, it, expect, vi } from 'vitest'

vi.mock('../i18n', () => ({
  default: {
    t: (key: string, opts?: Record<string, unknown>) => {
      if (key === 'fileTooLarge') return `File too large (${opts?.size} MB). Max 50 MB.`
      if (key === 'fileInvalidType') return 'Invalid format. Allowed: JPG, PNG, WebP.'
      return key
    },
  },
}))

import { CMS_IMAGE_ACCEPT, compressImage, isHeic, validateImageFile } from './imageUtils'

function createFile(name: string, size: number, type: string): File {
  const buffer = new ArrayBuffer(size)
  return new File([buffer], name, { type })
}

describe('validateImageFile', () => {
  it('should accept valid JPEG under the cap', () => {
    const file = createFile('photo.jpg', 5 * 1024 * 1024, 'image/jpeg')
    expect(validateImageFile(file)).toBeNull()
  })

  it('should accept valid PNG under the cap', () => {
    const file = createFile('image.png', 1 * 1024 * 1024, 'image/png')
    expect(validateImageFile(file)).toBeNull()
  })

  it('should accept valid WebP under the cap', () => {
    const file = createFile('image.webp', 2 * 1024 * 1024, 'image/webp')
    expect(validateImageFile(file)).toBeNull()
  })

  it('should accept a large camera photo (compressed client-side before upload)', () => {
    const file = createFile('dslr.jpg', 20 * 1024 * 1024, 'image/jpeg')
    expect(validateImageFile(file)).toBeNull()
  })

  it('should reject file exceeding the 50MB input cap', () => {
    const file = createFile('huge.jpg', 51 * 1024 * 1024, 'image/jpeg')
    const error = validateImageFile(file)
    expect(error).not.toBeNull()
    expect(error).toContain('50 MB')
  })

  it('should reject file just over boundary', () => {
    const file = createFile('edge.jpg', 50 * 1024 * 1024 + 1, 'image/jpeg')
    expect(validateImageFile(file)).not.toBeNull()
  })

  it('should accept file exactly at the cap', () => {
    const file = createFile('exact.jpg', 50 * 1024 * 1024, 'image/jpeg')
    expect(validateImageFile(file)).toBeNull()
  })

  it('should reject GIF format', () => {
    const file = createFile('anim.gif', 500 * 1024, 'image/gif')
    const error = validateImageFile(file)
    expect(error).not.toBeNull()
    expect(error).toContain('JPG')
  })

  it('should reject PDF format', () => {
    const file = createFile('doc.pdf', 500 * 1024, 'application/pdf')
    expect(validateImageFile(file)).not.toBeNull()
  })

  it('should reject SVG format', () => {
    const file = createFile('icon.svg', 10 * 1024, 'image/svg+xml')
    expect(validateImageFile(file)).not.toBeNull()
  })

  it('should include file size in error message', () => {
    const file = createFile('big.jpg', 60 * 1024 * 1024, 'image/jpeg')
    const error = validateImageFile(file)
    expect(error).toContain('60.0')
  })
})

describe('HEIC in CMS uploads', () => {
  it('should accept a HEIC by its MIME type', () => {
    expect(validateImageFile(createFile('IMG_0001.HEIC', 3 * 1024 * 1024, 'image/heic'))).toBeNull()
  })

  it('should accept a HEIC the browser reports with no type, by its extension', () => {
    // Desktop Chrome on a .heic from Finder, and iOS now and then.
    const file = createFile('IMG_0001.heic', 3 * 1024 * 1024, '')
    expect(isHeic(file)).toBe(true)
    expect(validateImageFile(file)).toBeNull()
  })

  it('should offer HEIC in the picker by extension too', () => {
    expect(CMS_IMAGE_ACCEPT.split(',')).toEqual(expect.arrayContaining(['image/heic', '.heic', '.heif']))
  })
})

describe('compressImage', () => {
  // jsdom has no codecs and no 2d context: the decode and the encode are stubbed, which is the
  // part under test anyway — WHEN compressImage re-encodes and what it hands back.
  let decodes: 'ok' | 'fail'
  let encodedSize: number

  beforeEach(() => {
    decodes = 'ok'
    encodedSize = 100
    vi.stubGlobal('Image', class {
      width = 800
      height = 600
      onload: (() => void) | null = null
      onerror: (() => void) | null = null
      set src(_: string) {
        queueMicrotask(() => (decodes === 'ok' ? this.onload?.() : this.onerror?.()))
      }
      get src() { return 'blob:stub' }
    })
    URL.createObjectURL = vi.fn(() => 'blob:stub')
    URL.revokeObjectURL = vi.fn()
    vi.spyOn(HTMLCanvasElement.prototype, 'getContext').mockReturnValue({ drawImage: vi.fn() } as never)
    vi.spyOn(HTMLCanvasElement.prototype, 'toDataURL').mockReturnValue('data:image/webp;base64,')
    vi.spyOn(HTMLCanvasElement.prototype, 'toBlob').mockImplementation(function (cb, type) {
      cb(new Blob([new Uint8Array(encodedSize)], { type: type ?? 'image/png' }))
    })
  })

  afterEach(() => {
    vi.unstubAllGlobals()
    vi.restoreAllMocks()
  })

  it('should leave a small JPEG within the dimension cap untouched', async () => {
    const file = createFile('small.jpg', 500 * 1024, 'image/jpeg')
    expect(await compressImage(file)).toBe(file)
  })

  it('should re-encode a small HEIC, which the server could not read as it is', async () => {
    const result = await compressImage(createFile('IMG_0001.HEIC', 300 * 1024, 'image/heic'))
    expect(result.type).toBe('image/webp')
    expect(result.name).toBe('IMG_0001.webp')
  })

  it('should keep the re-encoded HEIC even when it comes out bigger than the original', async () => {
    encodedSize = 400 * 1024
    const result = await compressImage(createFile('IMG_0002.heic', 300 * 1024, ''))
    expect(result.type).toBe('image/webp')
  })

  it('should say what to do when this browser cannot decode a HEIC', async () => {
    decodes = 'fail'
    await expect(compressImage(createFile('IMG_0003.heic', 300 * 1024, 'image/heic')))
      .rejects.toThrow('imageUnreadableHeic')
  })

  it('should keep the generic message for an unreadable JPEG', async () => {
    decodes = 'fail'
    await expect(compressImage(createFile('broken.jpg', 3 * 1024 * 1024, 'image/jpeg')))
      .rejects.toThrow('imageLoadFailed')
  })
})

describe('compressImage when the encoder gives nothing back', () => {
  beforeEach(() => {
    vi.stubGlobal('Image', class {
      width = 800
      height = 600
      onload: (() => void) | null = null
      set src(_: string) { queueMicrotask(() => this.onload?.()) }
      get src() { return 'blob:stub' }
    })
    URL.createObjectURL = vi.fn(() => 'blob:stub')
    URL.revokeObjectURL = vi.fn()
    vi.spyOn(HTMLCanvasElement.prototype, 'getContext').mockReturnValue({ drawImage: vi.fn() } as never)
    vi.spyOn(HTMLCanvasElement.prototype, 'toDataURL').mockReturnValue('data:image/webp;base64,')
    vi.spyOn(HTMLCanvasElement.prototype, 'toBlob').mockImplementation(function (cb) { cb(null) })
  })

  afterEach(() => {
    vi.unstubAllGlobals()
    vi.restoreAllMocks()
  })

  it('should refuse a HEIC rather than hand the server the original it cannot read', async () => {
    await expect(compressImage(createFile('IMG_0004.heic', 300 * 1024, 'image/heic')))
      .rejects.toThrow('imageLoadFailed')
  })

  it('should still fall back to the original JPEG, which the server reads fine', async () => {
    const file = createFile('big.jpg', 3 * 1024 * 1024, 'image/jpeg')
    expect(await compressImage(file)).toBe(file)
  })
})
