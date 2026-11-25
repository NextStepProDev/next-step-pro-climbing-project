import { afterAll, beforeAll, describe, it, expect, vi } from 'vitest'
import { fireEvent, render, screen } from '@testing-library/react'
import { ChatScroller } from './ChatScroller'
import type { ChatMessage } from '../../utils/chatMessages'

/**
 * The reader's place must survive the chat changing under them. jsdom has no layout, so each test
 * plays the browser: it sets the box's heights by hand, and reads back where the component put
 * the scroll position.
 */

const msg = (id: string, own = false): ChatMessage & { own: boolean } => ({ id, createdAt: `2026-11-25T10:00:${id.padStart(2, '0')}Z`, own })
const isOwn = (m: ChatMessage) => (m as ChatMessage & { own: boolean }).own

const layout = { scrollHeight: 1000, clientHeight: 400 }

// On the prototype, before anything renders: the layout effect reads the heights during render()
// itself, before a test could reach the element to stub it.
beforeAll(() => {
  Object.defineProperty(HTMLDivElement.prototype, 'scrollHeight', { configurable: true, get: () => layout.scrollHeight })
  Object.defineProperty(HTMLDivElement.prototype, 'clientHeight', { configurable: true, get: () => layout.clientHeight })
})
afterAll(() => {
  delete (HTMLDivElement.prototype as unknown as Record<string, unknown>).scrollHeight
  delete (HTMLDivElement.prototype as unknown as Record<string, unknown>).clientHeight
})

function renderScroller(messages: ChatMessage[], extra: Partial<Parameters<typeof ChatScroller>[0]> = {}) {
  const props = {
    messages, hasOlder: false, loadingOlder: false, onLoadOlder: vi.fn(), isOwn,
    loadOlderLabel: 'older', newBelowLabel: 'new below', className: 'box', ...extra,
  }
  const view = render(<ChatScroller {...props}><ol>{messages.map((m) => <li key={m.id}>{m.id}</li>)}</ol></ChatScroller>)
  const box = view.container.querySelector('.box') as HTMLDivElement
  const rerender = (next: ChatMessage[], nextExtra: Partial<typeof props> = {}) =>
    view.rerender(<ChatScroller {...props} {...nextExtra} messages={next}><ol>{next.map((m) => <li key={m.id}>{m.id}</li>)}</ol></ChatScroller>)
  return { box, rerender, props }
}

/** The reader scrolls the box to `top` (the browser then fires a scroll event). */
function scrollTo(box: HTMLDivElement, top: number) {
  box.scrollTop = top
  fireEvent.scroll(box)
}

describe('ChatScroller', () => {
  it('follows a new message when the reader is at the bottom', () => {
    layout.scrollHeight = 1000
    const { box, rerender } = renderScroller([msg('1'), msg('2')])
    scrollTo(box, 600) // 1000 - 600 - 400 = 0: at the bottom

    layout.scrollHeight = 1100
    rerender([msg('1'), msg('2'), msg('3')])

    expect(box.scrollTop).toBe(1100)
    expect(screen.queryByText('new below')).toBeNull()
  })

  // The user's question: reading older messages, a poll brings a new one — the view must not move.
  it('leaves a reader who scrolled up where they are, and offers the way down instead', () => {
    layout.scrollHeight = 1000
    const { box, rerender } = renderScroller([msg('1'), msg('2')])
    scrollTo(box, 200) // reading something above

    layout.scrollHeight = 1100
    rerender([msg('1'), msg('2'), msg('3')])

    expect(box.scrollTop).toBe(200)
    fireEvent.click(screen.getByText('new below'))
    expect(box.scrollTop).toBe(1100)
    expect(screen.queryByText('new below')).toBeNull()
  })

  it('keeps the message being read in place when an older page appears above it', () => {
    layout.scrollHeight = 1000
    const onLoadOlder = vi.fn()
    const { box, rerender } = renderScroller([msg('31'), msg('32')], { hasOlder: true, onLoadOlder })
    scrollTo(box, 50) // near the top: asks for the older page
    expect(onLoadOlder).toHaveBeenCalledTimes(1)

    layout.scrollHeight = 1600 // the older page adds 600 px above
    rerender([msg('1'), msg('2'), msg('31'), msg('32')], { hasOlder: false })

    expect(box.scrollTop).toBe(50 + 600)
  })

  it('always brings the reader down to their own new message', () => {
    layout.scrollHeight = 1000
    const { box, rerender } = renderScroller([msg('1'), msg('2')])
    scrollTo(box, 100)

    layout.scrollHeight = 1100
    rerender([msg('1'), msg('2'), msg('3', true)])

    expect(box.scrollTop).toBe(1100)
  })

  it('opens at the newest message', () => {
    layout.scrollHeight = 1000
    const { box } = renderScroller([msg('1'), msg('2')])

    expect(box.scrollTop).toBe(1000)
  })
})
