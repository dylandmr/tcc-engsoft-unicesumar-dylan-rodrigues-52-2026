import ReactMarkdown from 'react-markdown'
import remarkGfm from 'remark-gfm'

/**
 * Renders an LLM response as formatted markdown (bold, lists, headings, code,
 * tables via GFM) instead of raw text with literal `**` markers. Styled with
 * Tailwind Typography's `prose`, tuned to the dark Observation Deck theme.
 */
export function Markdown({ children }: { children: string }) {
  return (
    <div className="prose prose-sm prose-invert max-w-none break-words prose-headings:font-display prose-headings:tracking-tight prose-headings:text-bright prose-p:text-bright/85 prose-a:text-ignition prose-blockquote:border-l-line prose-strong:text-bright prose-code:text-ignition prose-code:before:content-none prose-code:after:content-none prose-pre:rounded-lg prose-pre:border prose-pre:border-line prose-pre:bg-black/40 prose-pre:text-bright prose-li:text-bright/85 prose-li:marker:text-mist prose-td:text-bright/85 prose-hr:border-line">
      <ReactMarkdown remarkPlugins={[remarkGfm]}>{children}</ReactMarkdown>
    </div>
  )
}
