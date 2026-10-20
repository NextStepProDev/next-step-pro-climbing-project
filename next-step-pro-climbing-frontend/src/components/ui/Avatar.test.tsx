import { describe, it, expect } from 'vitest'
import { render, fireEvent } from '@testing-library/react'
import { Avatar } from './Avatar'

describe('Avatar', () => {
  it('tries a new photo after the previous one failed to load', () => {
    const { container, rerender } = render(<Avatar src="/old.jpg" name="Ala" />)
    fireEvent.error(container.querySelector('img')!)
    expect(container.querySelector('img')).toBeNull()

    rerender(<Avatar src="/new.jpg" name="Ala" />)

    expect(container.querySelector('img')?.getAttribute('src')).toBe('/new.jpg')
  })
})
