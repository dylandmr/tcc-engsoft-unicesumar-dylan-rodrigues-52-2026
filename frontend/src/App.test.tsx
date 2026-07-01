import { describe, expect, it } from 'vitest'
import { render, screen, waitFor } from '@testing-library/react'
import App from './App'

describe('App', () => {
  it('boots a signed-in user straight into the composer at "/"', async () => {
    render(<App />)
    await waitFor(() =>
      expect(
        screen.getByRole('heading', {
          name: /O que os modelos devem responder/,
        }),
      ).toBeInTheDocument(),
    )
  })
})
