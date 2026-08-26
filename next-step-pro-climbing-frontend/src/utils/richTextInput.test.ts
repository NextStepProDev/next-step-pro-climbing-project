import { describe, it, expect } from 'vitest'
import { continueList, normalizeBulletMarker } from './richTextInput'

/** Places the caret at the marker `|`, mirroring how the field is read on a keypress. */
function at(text: string): { value: string; caret: number } {
  const caret = text.indexOf('|')
  return { value: text.replace('|', ''), caret }
}

describe('continueList', () => {
  it('should open the next bullet when a newline lands on a filled bullet', () => {
    const { value, caret } = at('• rozgrzewka|')
    expect(continueList(value, caret)).toEqual({ value: '• rozgrzewka\n• ', caret: 15 })
  })

  it('should number the next item rather than repeating the current one', () => {
    const { value, caret } = at('2. dipy|')
    expect(continueList(value, caret)?.value).toBe('2. dipy\n3. ')
  })

  it('should advance a lettered list to the next letter', () => {
    const { value, caret } = at('b) wariant|')
    expect(continueList(value, caret)?.value).toBe('b) wariant\nc) ')
  })

  it('should repeat z rather than wrapping past the alphabet', () => {
    const { value, caret } = at('z) ostatni|')
    expect(continueList(value, caret)?.value).toBe('z) ostatni\nz) ')
  })

  it('should drop the marker when the item is still empty, which is how you leave a list', () => {
    const { value, caret } = at('• rozgrzewka\n• |')
    expect(continueList(value, caret)).toEqual({ value: '• rozgrzewka\n', caret: 13 })
  })

  it('should treat a hand-typed dash as a list so pasted text behaves like typed text', () => {
    const { value, caret } = at('- rozgrzewka|')
    expect(continueList(value, caret)?.value).toBe('- rozgrzewka\n• ')
  })

  it('should do nothing on a plain line, leaving the key its normal behaviour', () => {
    const { value, caret } = at('zwykłe zdanie|')
    expect(continueList(value, caret)).toBeNull()
  })

  it('should do nothing while text is selected, since the newline replaces the selection', () => {
    expect(continueList('• rozgrzewka', 2, 8)).toBeNull()
  })

  it('should split the item when the caret sits mid-line', () => {
    const { value, caret } = at('• rozgrzewka| i mobilizacja')
    expect(continueList(value, caret)?.value).toBe('• rozgrzewka\n•  i mobilizacja')
  })
})

describe('normalizeBulletMarker', () => {
  it('should convert a dash typed at the start of a line into the bullet the renderer draws', () => {
    const { value, caret } = at('- |')
    expect(normalizeBulletMarker(value, caret)).toEqual({ value: '• ', caret: 2 })
  })

  it('should convert an asterisk the same way, since both are what people type', () => {
    const { value, caret } = at('• rozgrzewka\n* |')
    expect(normalizeBulletMarker(value, caret)?.value).toBe('• rozgrzewka\n• ')
  })

  it('should leave a dash alone mid-line, where it is a dash', () => {
    const { value, caret } = at('serie 3 - |')
    expect(normalizeBulletMarker(value, caret)).toBeNull()
  })

  it('should not fire once the item has text, so editing an old line stays put', () => {
    const { value, caret } = at('- rozgrzewka|')
    expect(normalizeBulletMarker(value, caret)).toBeNull()
  })
})

describe('continueList — length limit', () => {
  it('should decline rather than push the field past a limit the server enforces', () => {
    const value = '• ' + 'x'.repeat(96)
    expect(continueList(value, value.length, value.length, 100)).toBeNull()
  })

  it('should still fire with room to spare', () => {
    const value = '• x'
    expect(continueList(value, value.length, value.length, 100)).not.toBeNull()
  })

  it('should still leave a list at the limit, since that edit only removes characters', () => {
    const value = '• a\n• '
    expect(continueList(value, value.length, value.length, value.length)?.value).toBe('• a\n')
  })
})
