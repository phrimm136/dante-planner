import type { Editor } from '@tiptap/react'
import { useTranslation } from 'react-i18next'
import {
  Bold,
  Italic,
  Strikethrough,
  Heading1,
  Heading2,
  Heading3,
  List,
  ListOrdered,
  Quote,
  Code,
  FileCode,
  Link,
  Image,
  EyeOff,
} from 'lucide-react'
import { Button } from '@/components/ui/button'
import { Separator } from '@/components/ui/separator'
import {
  Tooltip,
  TooltipContent,
  TooltipTrigger,
} from '@/components/ui/tooltip'
import { cn } from '@/lib/utils'

interface ToolbarProps {
  editor: Editor
  visible: boolean
  onLinkClick: () => void
  onImageClick: () => void
}

interface ToolbarButtonProps {
  icon: React.ReactNode
  label: string
  isActive?: boolean
  onClick: () => void
  disabled?: boolean
}

function ToolbarButton({ icon, label, isActive, onClick, disabled }: ToolbarButtonProps) {
  return (
    <Tooltip>
      <TooltipTrigger asChild>
        <Button
          type="button"
          variant="ghost"
          size="sm"
          className={cn(
            'h-8 w-8 p-0',
            isActive && 'bg-muted'
          )}
          onClick={onClick}
          disabled={disabled}
          aria-label={label}
        >
          {icon}
        </Button>
      </TooltipTrigger>
      <TooltipContent side="bottom" className="text-xs">
        {label}
      </TooltipContent>
    </Tooltip>
  )
}

/**
 * Toolbar - Formatting toolbar for NoteEditor
 *
 * Buttons for: bold, italic, strikethrough, headings (H1-H3),
 * bullet list, ordered list, blockquote, code, code block,
 * link, image, spoiler
 */
export function Toolbar({
  editor,
  visible,
  onLinkClick,
  onImageClick,
}: ToolbarProps) {
  const { t } = useTranslation()

  if (!visible) {
    return null
  }

  return (
    <div className="flex flex-wrap items-center gap-0.5 border-b border-input p-1">
      {/* Text formatting */}
      <ToolbarButton
        icon={<Bold className="h-4 w-4" />}
        label={t('pages.plannerMD.noteEditor.toolbar.bold')}
        isActive={editor.isActive('bold')}
        onClick={() => editor.chain().focus().toggleBold().run()}
      />
      <ToolbarButton
        icon={<Italic className="h-4 w-4" />}
        label={t('pages.plannerMD.noteEditor.toolbar.italic')}
        isActive={editor.isActive('italic')}
        onClick={() => editor.chain().focus().toggleItalic().run()}
      />
      <ToolbarButton
        icon={<Strikethrough className="h-4 w-4" />}
        label={t('pages.plannerMD.noteEditor.toolbar.strikethrough')}
        isActive={editor.isActive('strike')}
        onClick={() => editor.chain().focus().toggleStrike().run()}
      />

      <Separator orientation="vertical" className="mx-1 h-6" />

      {/* Headings */}
      <ToolbarButton
        icon={<Heading1 className="h-4 w-4" />}
        label={t('pages.plannerMD.noteEditor.toolbar.heading1')}
        isActive={editor.isActive('heading', { level: 1 })}
        onClick={() => editor.chain().focus().toggleHeading({ level: 1 }).run()}
      />
      <ToolbarButton
        icon={<Heading2 className="h-4 w-4" />}
        label={t('pages.plannerMD.noteEditor.toolbar.heading2')}
        isActive={editor.isActive('heading', { level: 2 })}
        onClick={() => editor.chain().focus().toggleHeading({ level: 2 }).run()}
      />
      <ToolbarButton
        icon={<Heading3 className="h-4 w-4" />}
        label={t('pages.plannerMD.noteEditor.toolbar.heading3')}
        isActive={editor.isActive('heading', { level: 3 })}
        onClick={() => editor.chain().focus().toggleHeading({ level: 3 }).run()}
      />

      <Separator orientation="vertical" className="mx-1 h-6" />

      {/* Lists */}
      <ToolbarButton
        icon={<List className="h-4 w-4" />}
        label={t('pages.plannerMD.noteEditor.toolbar.bulletList')}
        isActive={editor.isActive('bulletList')}
        onClick={() => editor.chain().focus().toggleBulletList().run()}
      />
      <ToolbarButton
        icon={<ListOrdered className="h-4 w-4" />}
        label={t('pages.plannerMD.noteEditor.toolbar.orderedList')}
        isActive={editor.isActive('orderedList')}
        onClick={() => editor.chain().focus().toggleOrderedList().run()}
      />

      <Separator orientation="vertical" className="mx-1 h-6" />

      {/* Block elements */}
      <ToolbarButton
        icon={<Quote className="h-4 w-4" />}
        label={t('pages.plannerMD.noteEditor.toolbar.blockquote')}
        isActive={editor.isActive('blockquote')}
        onClick={() => editor.chain().focus().toggleBlockquote().run()}
      />
      <ToolbarButton
        icon={<Code className="h-4 w-4" />}
        label={t('pages.plannerMD.noteEditor.toolbar.code')}
        isActive={editor.isActive('code')}
        onClick={() => editor.chain().focus().toggleCode().run()}
      />
      <ToolbarButton
        icon={<FileCode className="h-4 w-4" />}
        label={t('pages.plannerMD.noteEditor.toolbar.codeBlock')}
        isActive={editor.isActive('codeBlock')}
        onClick={() => editor.chain().focus().toggleCodeBlock().run()}
      />

      <Separator orientation="vertical" className="mx-1 h-6" />

      {/* Link and Image */}
      <ToolbarButton
        icon={<Link className="h-4 w-4" />}
        label={t('pages.plannerMD.noteEditor.toolbar.link')}
        isActive={editor.isActive('link')}
        onClick={onLinkClick}
      />
      <ToolbarButton
        icon={<Image className="h-4 w-4" />}
        label={t('pages.plannerMD.noteEditor.toolbar.image')}
        onClick={onImageClick}
      />

      <Separator orientation="vertical" className="mx-1 h-6" />

      {/* Spoiler */}
      <ToolbarButton
        icon={<EyeOff className="h-4 w-4" />}
        label={t('pages.plannerMD.noteEditor.toolbar.spoiler')}
        isActive={editor.isActive('spoiler')}
        onClick={() => editor.chain().focus().toggleSpoiler().run()}
      />
    </div>
  )
}

export default Toolbar
