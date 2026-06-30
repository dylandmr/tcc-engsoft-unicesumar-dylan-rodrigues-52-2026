import { describe, expect, it } from 'vitest'
import { act, renderHook } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { useComposer } from './useComposer'

const wrapper = ({ children }: { children: React.ReactNode }) => (
  <MemoryRouter>{children}</MemoryRouter>
)

describe('useComposer.toggle', () => {
  it('ignores a fifth selection once the limit is reached', () => {
    const { result } = renderHook(() => useComposer(), { wrapper })

    act(() => {
      result.current.toggle('GEMINI')
      result.current.toggle('CHATGPT')
      result.current.toggle('CLAUDE')
      result.current.toggle('GROK')
    })
    expect(result.current.selected).toHaveLength(4)
    expect(result.current.full).toBe(true)

    act(() => result.current.toggle('DEEPSEEK'))
    expect(result.current.selected).toHaveLength(4)
    expect(result.current.selected).not.toContain('DEEPSEEK')
  })
})
