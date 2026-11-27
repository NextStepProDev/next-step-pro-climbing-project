import { describe, it, expect } from 'vitest'
import { compareInstants, mergeChatMessages, readReceipt } from './chatMessages'

const m = (id: string, createdAt: string) => ({ id, createdAt })

describe('compareInstants', () => {
  // The server drops a zero fraction ("…05Z"), and as text 'Z' sorts after '.', so a plain string
  // comparison would put a whole second AFTER the same second plus half.
  it('should order an instant without a fraction before the same second with one', () => {
    expect('2026-11-25T10:00:05Z' > '2026-11-25T10:00:05.5Z').toBe(true)
    expect(compareInstants('2026-11-25T10:00:05Z', '2026-11-25T10:00:05.5Z')).toBeLessThan(0)
    expect(compareInstants('2026-11-25T10:00:05Z', '2026-11-25T10:00:05.000001Z')).toBeLessThan(0)
  })

  it('should order fractions of different length by value', () => {
    expect(compareInstants('2026-11-25T10:00:05.1Z', '2026-11-25T10:00:05.123456Z')).toBeLessThan(0)
    expect(compareInstants('2026-11-25T10:00:05.2Z', '2026-11-25T10:00:05.123456Z')).toBeGreaterThan(0)
  })

  it('should tell apart instants that differ below a millisecond', () => {
    expect(compareInstants('2026-11-25T10:00:05.123400Z', '2026-11-25T10:00:05.123700Z')).toBeLessThan(0)
  })

  it('should call the same instant equal however it is written', () => {
    expect(compareInstants('2026-11-25T10:00:05.5Z', '2026-11-25T10:00:05.500000Z')).toBe(0)
  })
})

describe('mergeChatMessages', () => {
  it('should keep each message once and in the server order across pieces', () => {
    const older = [m('a', '2026-11-25T10:00:01Z'), m('b', '2026-11-25T10:00:02Z')]
    const latest = [m('b', '2026-11-25T10:00:02Z'), m('c', '2026-11-25T10:00:03Z')]
    const polled = [m('d', '2026-11-25T10:00:04Z'), m('c', '2026-11-25T10:00:03Z')]

    expect(mergeChatMessages(older, latest, polled).map((x) => x.id)).toEqual(['a', 'b', 'c', 'd'])
  })

  it('should break a tie in time by id, as the server does', () => {
    const same = '2026-11-25T10:00:00Z'
    const merged = mergeChatMessages([m('0b000000-0000-0000-0000-000000000000', same)], [m('0a000000-0000-0000-0000-000000000000', same)])
    expect(merged.map((x) => x.id)).toEqual(['0a000000-0000-0000-0000-000000000000', '0b000000-0000-0000-0000-000000000000'])
  })
})

describe('readReceipt', () => {
  const own = (id: string, createdAt: string) => ({ id, createdAt, own: true })
  const theirs = (id: string, createdAt: string) => ({ id, createdAt, own: false })
  const isOwn = (x: { own: boolean }) => x.own

  it('should mark the own newest message unread until the other side looks', () => {
    expect(readReceipt([own('a', '2026-11-27T10:00:00Z')], isOwn, null)).toEqual({ messageId: 'a', read: false })
    expect(readReceipt([own('a', '2026-11-27T10:00:00Z')], isOwn, '2026-11-27T09:59:59Z'))
      .toEqual({ messageId: 'a', read: false })
  })

  it('should mark it read once the other side had the chat open at or after it', () => {
    expect(readReceipt([own('a', '2026-11-27T10:00:00Z')], isOwn, '2026-11-27T10:05:00Z'))
      .toEqual({ messageId: 'a', read: true })
  })

  it('should compare to the microsecond, not to the millisecond', () => {
    // Seen 300 µs BEFORE the message: Date.parse would call these equal, and so "read"
    expect(readReceipt([own('a', '2026-11-27T10:00:00.000500Z')], isOwn, '2026-11-27T10:00:00.000200Z'))
      .toEqual({ messageId: 'a', read: false })
  })

  it('should put the mark only under the newest message, and none when that one is the other side\'s', () => {
    const older = own('a', '2026-11-27T10:00:00Z')
    expect(readReceipt([older, own('b', '2026-11-27T10:01:00Z')], isOwn, '2026-11-27T10:00:30Z'))
      .toEqual({ messageId: 'b', read: false })
    expect(readReceipt([older, theirs('c', '2026-11-27T10:02:00Z')], isOwn, null)).toBeNull()
    expect(readReceipt([], isOwn, null)).toBeNull()
  })
})
