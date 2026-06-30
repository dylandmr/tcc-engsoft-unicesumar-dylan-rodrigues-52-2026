import { render, screen } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import App from './App'

describe('App', () => {
  it('renders the Prompt Arena heading and tagline', () => {
    render(<App />)
    expect(screen.getByRole('heading', { name: 'Prompt Arena' })).toBeTruthy()
    expect(screen.getByText(/generative AI/i)).toBeTruthy()
  })
})
