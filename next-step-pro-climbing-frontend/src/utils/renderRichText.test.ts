import { describe, it, expect } from 'vitest'
import { renderRichText, toPlainText } from './renderRichText'

describe('renderRichText', () => {
  it('should render the inline markers', () => {
    expect(renderRichText('**mocno** *lekko* __pod__ ~~odpuść~~')).toBe(
      '<strong>mocno</strong> <em>lekko</em> <u>pod</u> <s>odpuść</s>')
  })

  it('should accept dashes and asterisks as bullets, not just the toolbar character', () => {
    const html = renderRichText('• jeden\n- dwa\n* trzy')
    expect(html).toContain('<li>jeden</li><li>dwa</li><li>trzy</li>')
    expect(html.match(/<ul/g)).toHaveLength(1)
  })

  it('should keep an inline emphasis off the list, since it has no space after the marker', () => {
    expect(renderRichText('*ważne* dziś')).toBe('<em>ważne</em> dziś')
  })

  it('should render every heading marker at one level, because a plan needs sections not a hierarchy', () => {
    const one = renderRichText('# Rozgrzewka')
    expect(one).toContain('<h4')
    expect(one).toContain('Rozgrzewka')
    expect(renderRichText('### Rozgrzewka')).toBe(one)
  })

  it('should not wrap block elements in <br>, which would double the gap around a list', () => {
    expect(renderRichText('Plan:\n• jeden')).not.toContain('<br><ul')
    expect(renderRichText('• jeden\nPotem')).not.toContain('</ul><br>')
  })

  it('should still break between two lines of running text', () => {
    expect(renderRichText('pierwsza\ndruga')).toBe('pierwsza<br>druga')
  })

  it('should escape HTML before applying any marker', () => {
    const html = renderRichText('<img src=x onerror=alert(1)> **ok**')
    expect(html).not.toContain('<img')
    expect(html).toContain('&lt;img')
    expect(html).toContain('<strong>ok</strong>')
  })

  it('should escape HTML inside a list item too', () => {
    expect(renderRichText('• <script>alert(1)</script>')).toContain('&lt;script&gt;')
  })

  it('should escape HTML inside a heading too', () => {
    expect(renderRichText('## <script>alert(1)</script>')).toContain('&lt;script&gt;')
  })

  it('should keep a numbered list separate from a lettered one', () => {
    const html = renderRichText('1. seria\na) wariant')
    expect(html).toContain('<ol class=')
    expect(html).toContain('<ol type="a"')
  })
})

describe('renderRichText — numbering', () => {
  it('should keep the numbers the writer typed when a list does not start at one', () => {
    expect(renderRichText('3. seria\n4. seria')).toContain('<ol start="3"')
  })

  it('should leave a list that starts at one as plain markup', () => {
    expect(renderRichText('1. seria')).not.toContain('start=')
  })

  it('should carry the same rule to lettered lists', () => {
    expect(renderRichText('c) wariant')).toContain('start="3"')
  })

  it('should preserve runs of spaces for the hosts that render pre-wrap', () => {
    expect(renderRichText('a    b')).toBe('a    b')
  })

  // The invariant the pre-wrap hosts depend on: every break is a <br>, never a raw newline,
  // so pre-wrap can only affect spaces. A \r surviving here would double-space pasted text.
  it('should never emit a line break of its own, whatever the input line endings', () => {
    expect(renderRichText('a\r\nb\r\n• c')).not.toMatch(/[\r\n]/)
    expect(renderRichText('a\r\nb')).toBe('a<br>b')
  })
})

describe('toPlainText', () => {
  it('should strip every inline marker, since a calendar app renders none of them', () => {
    expect(toPlainText('**mocno** *lekko* __pod__ ~~odpuść~~')).toBe('mocno lekko pod odpuść')
  })

  it('should drop the heading marker but keep the heading', () => {
    expect(toPlainText('## Co zabrać')).toBe('Co zabrać')
  })

  it('should keep bullets as a character, because an item still reads as a list', () => {
    expect(toPlainText('- uprząż\n* buty\n• kask')).toBe('• uprząż\n• buty\n• kask')
  })

  it('should leave numbered and lettered items alone — they spell their own label', () => {
    expect(toPlainText('1. zbiórka\na) wariant')).toBe('1. zbiórka\na) wariant')
  })

  // The reason this lives beside renderRichText: both read INLINE_MARKERS, so a marker taught
  // to the renderer cannot silently skip the stripper and surface raw in somebody's calendar.
  it('should leave no marker character behind for a description using all of them', () => {
    const plain = toPlainText('## Plan\n- **ważne** i *pilne*\n~~odwołane~~ __uwaga__')
    expect(plain).not.toMatch(/\*|~~|__|^#/m)
  })
})
