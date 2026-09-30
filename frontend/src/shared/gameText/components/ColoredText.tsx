import { applyTextStyles } from '../lib/unityRichText'

export function ColoredText({ text }: { text: string }) {
  return <>{applyTextStyles(text)}</>
}
