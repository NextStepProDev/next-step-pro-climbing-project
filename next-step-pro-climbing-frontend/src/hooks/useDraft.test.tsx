import { describe, it, expect, beforeEach, vi } from 'vitest'
import { act, renderHook } from '@testing-library/react'
import { useDraft, useInitialDraft } from './useDraft'
import { readDraft, writeDraft } from '../utils/draftStorage'

const auth = vi.hoisted(() => ({ userId: 'u1' as string | null }))
vi.mock('../context/AuthContext', () => ({ useCurrentUserId: () => auth.userId }))

interface Props {
  scope: string | null
  value: string
  dirty: boolean
  baseline?: string | null
}

const T = Date.now()

const render = (initial: Props) =>
  renderHook((props: Props) => useDraft(props), { initialProps: initial })

describe('useDraft', () => {
  beforeEach(() => {
    localStorage.clear()
    auth.userId = 'u1'
  })

  it('should keep the writing while it differs from what is saved', () => {
    const { rerender } = render({ scope: 'mail', value: '', dirty: false })
    rerender({ scope: 'mail', value: 'Hej wszystkim', dirty: true, baseline: null })

    expect(readDraft('u1', 'mail')?.data).toBe('Hej wszystkim')
  })

  it('should remove the draft once the editor is back to the saved text', () => {
    const { rerender } = render({ scope: 'mail', value: 'abc', dirty: true })
    rerender({ scope: 'mail', value: '', dirty: false })

    expect(readDraft('u1', 'mail')).toBeNull()
  })

  it('should keep nothing without a signed-in user', () => {
    auth.userId = null
    render({ scope: 'mail', value: 'abc', dirty: true })

    expect(localStorage.length).toBe(0)
  })

  it('should offer a stored draft and not overwrite it before an answer', () => {
    writeDraft('u1', 'news:1', { savedAt: T, baseline: 'v1', data: 'earlier text' })
    const { result, rerender } = render({ scope: 'news:1', value: '', dirty: false, baseline: 'v1' })

    expect(result.current.offer).toMatchObject({ savedAt: T, stale: false, data: 'earlier text' })

    // Typing before answering must not replace the text the banner is offering
    rerender({ scope: 'news:1', value: 'x', dirty: true, baseline: 'v1' })
    expect(readDraft('u1', 'news:1')?.data).toBe('earlier text')
  })

  it('should mark a draft stale when the entity was saved after it began', () => {
    writeDraft('u1', 'news:1', { savedAt: T, baseline: 'v1', data: 'earlier text' })
    const { result } = render({ scope: 'news:1', value: '', dirty: false, baseline: 'v2' })

    expect(result.current.offer?.stale).toBe(true)
  })

  it('should hand the data back on restore and then keep saving the new writing', () => {
    writeDraft('u1', 'news:1', { savedAt: T, baseline: 'v1', data: 'earlier text' })
    const { result, rerender } = render({ scope: 'news:1', value: '', dirty: false, baseline: 'v1' })

    let restored: string | null = null
    act(() => { restored = result.current.restore() })
    expect(restored).toBe('earlier text')
    expect(result.current.offer).toBeNull()

    rerender({ scope: 'news:1', value: 'earlier text, more', dirty: true, baseline: 'v1' })
    expect(readDraft('u1', 'news:1')?.data).toBe('earlier text, more')
  })

  it('should delete the draft on discard', () => {
    writeDraft('u1', 'news:1', { savedAt: T, baseline: null, data: 'earlier text' })
    const { result } = render({ scope: 'news:1', value: '', dirty: false })

    act(() => result.current.discard())

    expect(result.current.offer).toBeNull()
    expect(readDraft('u1', 'news:1')).toBeNull()
  })

  it('should not write the just-saved text back while the editor still reads as dirty', () => {
    const { result, rerender } = render({ scope: 'mail', value: 'sent', dirty: true })

    act(() => result.current.clear())
    rerender({ scope: 'mail', value: 'sent', dirty: true })

    expect(readDraft('u1', 'mail')).toBeNull()
  })

  it('should keep the text again when a cleared save turns out to have failed', () => {
    const { result } = render({ scope: 'training:new:me', value: 'plan', dirty: true })

    act(() => result.current.clear())
    expect(readDraft('u1', 'training:new:me')).toBeNull()

    act(() => result.current.resume())
    expect(readDraft('u1', 'training:new:me')?.data).toBe('plan')
  })

  it('should offer each entity its own draft when the editor switches entity', () => {
    writeDraft('u1', 'news:2', { savedAt: T, baseline: null, data: 'second article' })
    const { result, rerender } = render({ scope: 'news:1', value: '', dirty: false })
    expect(result.current.offer).toBeNull()

    rerender({ scope: 'news:2', value: '', dirty: false })

    expect(result.current.offer?.data).toBe('second article')
  })

  it('should not offer anything when the host restores by itself', () => {
    writeDraft('u1', 'comment:training:t1', { savedAt: T, baseline: null, data: 'half a message' })
    const { result } = renderHook(() => {
      const initial = useInitialDraft<string>('comment:training:t1')
      const draft = useDraft({ scope: 'comment:training:t1', value: initial ?? '', dirty: !!initial, offer: false })
      return { initial, offer: draft.offer }
    })

    expect(result.current.initial).toBe('half a message')
    expect(result.current.offer).toBeNull()
  })
})
