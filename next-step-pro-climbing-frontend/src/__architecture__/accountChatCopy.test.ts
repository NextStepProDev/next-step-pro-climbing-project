import { describe, it, expect } from 'vitest'
import { readFileSync } from 'node:fs'
import { join } from 'node:path'

/**
 * An account holder's "Write to us" conversation is an in-app chat that sends no mail in either
 * direction (decision 2026-11-24). The copy shown around it must not promise otherwise: a client
 * told "we'll reply by e-mail" waits in their inbox for a message that never comes, and a coach
 * told "it also goes by e-mail" stops checking whether the client ever opened the app.
 *
 * Component tests mock `t()` to return keys, so nothing else reads these strings.
 */

const LOCALES_DIR = join(__dirname, '..', 'locales')
const LANGUAGES = ['pl', 'en', 'es'] as const

/** Strings shown ONLY for an account holder's conversation, as [file, key path]. */
const ACCOUNT_ONLY_COPY: Array<[string, string]> = [
  ['common.json', 'contactForm.sentInApp'],
  ['common.json', 'contactForm.signedInAs'],
  ['reservations.json', 'messages.hint'],
  ['admin.json', 'contactMessages.replyHintUser'],
  ['admin.json', 'contactMessages.sentInAppOnly'],
]

/**
 * "mail" in any of the three languages, but not when the sentence says the opposite
 * ("bez maila", "no e-mail", "sin correo", "nie wysyłamy jej mailem", "don't send it by e-mail",
 * "no la enviamos por correo").
 */
const MAIL_WORD = /e-?mail|mailem|maila|correo/i
// The negation must be a whole word (`no` inside "now" or "nowa" is not one) and stand right
// before the mail word: a few short words in between, never a whole clause ("No spam — we reply
// by e-mail" promises mail).
const NEGATED = /(?<!\p{L})(bez|no|sin|nie wysyłamy|don't send|no la enviamos)(?!\p{L})[^.—]{0,12}(e-?mail|mailem|maila|correo)/iu

function promisesMail(text: string): boolean {
  // `{{email}}` is the writer's address shown back to them, not a promise of mail.
  const prose = text.replace(/\{\{[^}]+\}\}/g, '')
  return MAIL_WORD.test(prose) && !NEGATED.test(prose)
}

function lookup(file: string, lang: string, path: string): unknown {
  const json = JSON.parse(readFileSync(join(LOCALES_DIR, lang, file), 'utf8'))
  return path.split('.').reduce<unknown>((node, key) => (node as Record<string, unknown> | undefined)?.[key], json)
}

describe('account chat copy', () => {
  for (const lang of LANGUAGES) {
    for (const [file, path] of ACCOUNT_ONLY_COPY) {
      it(`${lang}/${file} ${path} does not promise an e-mail`, () => {
        const text = lookup(file, lang, path)
        expect(typeof text, `missing ${lang}/${file} ${path}`).toBe('string')
        expect(promisesMail(text as string), `"${text}"`).toBe(false)
      })
    }
  }
})

// The gate is only as good as its matcher: a negation that matched too eagerly would wave every
// promise through and the gate would stay green while checking nothing.
describe('account chat copy matcher', () => {
  it.each([
    'We will now e-mail you the answer.',
    'You will know by e-mail.',
    'Odpowiedź przyjdzie mailem.',
    'Odpowiedź nowa przyjdzie mailem.',
    'No spam — we reply by e-mail.',
    'Te responderemos por correo.',
  ])('should catch a promise of mail: %s', (text) => {
    expect(promisesMail(text)).toBe(true)
  })

  it.each([
    'Saved in the chat (no e-mail).',
    'Zapisano w czacie (bez maila).',
    'Nie wysyłamy jej mailem.',
    "We don't send it by e-mail.",
    'No la enviamos por correo.',
    'Writing as {{name}} ({{email}}).',
  ])('should let a denial of mail through: %s', (text) => {
    expect(promisesMail(text)).toBe(false)
  })
})
