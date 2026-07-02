/** Full-bleed loading indicator used while the session bootstraps. */
export function LoadingScreen() {
  return (
    <div
      role="status"
      aria-label="Carregando"
      className="flex min-h-screen items-center justify-center bg-void"
    >
      <span className="size-3 animate-ping rounded-full bg-ignition" />
    </div>
  )
}
