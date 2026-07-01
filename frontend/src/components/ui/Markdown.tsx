import ReactMarkdown from 'react-markdown'
import remarkGfm from 'remark-gfm'

/**
 * Renders an LLM response as formatted markdown (bold, lists, headings, code,
 * tables via GFM) instead of raw text with literal `**` markers. Styled with
 * Tailwind Typography's `prose`, tuned to the dark Observation Deck theme.
 */
export function Markdown({ children }: { children: string }) {
  return (
    <div className="prose prose-sm prose-invert max-w-none break-words prose-headings:font-display prose-headings:text-bright prose-strong:text-bright prose-a:text-ignition prose-code:text-ignition prose-pre:bg-black/40 prose-pre:text-bright">
      <ReactMarkdown remarkPlugins={[remarkGfm]}>{children}</ReactMarkdown>
    </div>
  )
}
