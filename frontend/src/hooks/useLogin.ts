import { useState, type FormEvent } from 'react'
import { useNavigate } from 'react-router-dom'
import { useSession } from '../auth/SessionContext'

/** Login form state + submit, routing to the composer on success. */
export function useLogin() {
  const { signIn } = useSession()
  const navigate = useNavigate()
  const [username, setUsername] = useState('')
  const [password, setPassword] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [submitting, setSubmitting] = useState(false)

  const submit = async (event: FormEvent) => {
    event.preventDefault()
    setSubmitting(true)
    setError(null)
    try {
      await signIn(username, password)
      navigate('/', { replace: true })
    } catch {
      // Non-revealing message (FR-003).
      setError('Usuário ou senha inválidos.')
      setSubmitting(false)
    }
  }

  return {
    username,
    setUsername,
    password,
    setPassword,
    error,
    submitting,
    submit,
  }
}
