/**
 * Ambient radial-glow backdrop for Login / Composer so the surfaces don't
 * float in pure void. Pointer-events-none, sits behind content.
 */
export function Backdrop() {
  return (
    <div
      aria-hidden="true"
      className="pointer-events-none fixed inset-0 -z-10"
      style={{
        background:
          'radial-gradient(60% 50% at 50% 35%, rgba(255,138,61,0.12), transparent 70%)',
      }}
    />
  )
}
