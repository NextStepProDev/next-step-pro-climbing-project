import { describe, expect, it, vi } from 'vitest'
import { renderHook } from '@testing-library/react'
import { fireEvent } from '@testing-library/react'
import { useClickOutside } from './useClickOutside'

function setup(enabled = true) {
  const inside = document.createElement('div')
  const outside = document.createElement('div')
  document.body.append(inside, outside)
  const onOutside = vi.fn()
  const hook = renderHook(({ on }) => useClickOutside({ current: inside }, onOutside, on), {
    initialProps: { on: enabled },
  })
  return { inside, outside, onOutside, hook }
}

describe('useClickOutside', () => {
  it('fires for a press outside the element', () => {
    const { outside, onOutside } = setup()
    fireEvent.mouseDown(outside)
    expect(onOutside).toHaveBeenCalledTimes(1)
  })

  it('ignores a press inside the element', () => {
    const { inside, onOutside } = setup()
    fireEvent.mouseDown(inside)
    expect(onOutside).not.toHaveBeenCalled()
  })

  it('stays silent while disabled and wakes up when enabled', () => {
    const { outside, onOutside, hook } = setup(false)
    fireEvent.mouseDown(outside)
    expect(onOutside).not.toHaveBeenCalled()

    hook.rerender({ on: true })
    fireEvent.mouseDown(outside)
    expect(onOutside).toHaveBeenCalledTimes(1)
  })

  it('stops listening on unmount', () => {
    const { outside, onOutside, hook } = setup()
    hook.unmount()
    fireEvent.mouseDown(outside)
    expect(onOutside).not.toHaveBeenCalled()
  })
})
