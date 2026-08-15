import { useRef, type InputHTMLAttributes } from 'react'

/**
 * The one date field in the app — a native `<input type="date">` that actually closes its picker.
 *
 * Safari on macOS leaves the calendar popover open after you click a day. The value IS committed
 * at that moment (the input event fires straight away — verified in Safari, which is the only way
 * to check: a native popover is browser chrome, so no automation can click it), but the calendar
 * keeps hanging over the form, so the pick reads as "nothing happened" and you learn to click
 * somewhere else to make the date appear. Blurring the field on commit dismisses it, and focus
 * goes straight back so nothing changes for Chrome and Firefox, which close the popover
 * themselves and would otherwise pay for this with a field that loses focus on every pick.
 *
 * This is a component rather than a remembered habit because the habit already failed once: the
 * workaround was added to a slot form in Feb 2026, that form was later extracted into
 * `CreateSlotModal`, and the two lines did not come along — leaving the most-used date field in
 * the app as the broken one while the events panel stayed fixed. A gate
 * (`__architecture__/dateInput.test.ts`) now keeps raw date inputs out of the codebase.
 *
 * Two interactions are exempt from the blur, because for them the picker closing early is the bug:
 *
 * - **Typing.** Editing a complete date by keyboard fires input on every keystroke, so blurring
 *   would rip focus away after the day segment and before the month.
 * - **Touch.** The iOS wheel fires input on every spin and is dismissed by its own Done button;
 *   blurring would snap it shut on the first nudge.
 */
interface DateInputProps
  extends Omit<InputHTMLAttributes<HTMLInputElement>, 'type' | 'value' | 'onChange'> {
  /** `yyyy-MM-dd`, or '' for an empty field. */
  value: string
  /** Receives the new `yyyy-MM-dd`, or '' when the field is cleared. */
  onChange: (value: string) => void
}

export function DateInput({ value, onChange, onPointerDown, onKeyDown, ...rest }: DateInputProps) {
  // Defaults to true: should a browser ever commit a day without a pointerdown reaching the input,
  // the failure to fall back into is the stolen focus, not the popover that never closes.
  const dismissOnCommit = useRef(true)

  return (
    <input
      {...rest}
      type="date"
      value={value}
      onPointerDown={(event) => {
        dismissOnCommit.current = event.pointerType !== 'touch'
        onPointerDown?.(event)
      }}
      onKeyDown={(event) => {
        dismissOnCommit.current = false
        onKeyDown?.(event)
      }}
      onChange={(event) => {
        onChange(event.target.value)
        if (!dismissOnCommit.current) return
        // The blur is what dismisses the popover; the focus put straight back is what keeps this
        // free on browsers that had nothing wrong with them — otherwise picking a date would
        // leave focus on <body>, so Enter no longer submits and Tab restarts from the top of the
        // page. Restoring focus does not reopen the popover (verified in Safari; a native popover
        // opens on a click, not on focus).
        event.target.blur()
        event.target.focus({ preventScroll: true })
      }}
    />
  )
}
