/**
 * True when the OS asks for reduced motion. framer-motion work is already
 * covered by `MotionConfig reducedMotion="user"`; this guards JS-driven
 * timers/delays (e.g. the launch-sequence hold) the same way.
 */
export function prefersReducedMotion(): boolean {
  return (
    window.matchMedia?.('(prefers-reduced-motion: reduce)').matches ?? false
  )
}
