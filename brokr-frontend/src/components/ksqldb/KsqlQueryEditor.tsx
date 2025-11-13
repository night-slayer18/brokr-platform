import {useEffect, useState} from 'react'
import CodeMirror from '@uiw/react-codemirror'
import {sql} from '@codemirror/lang-sql'
import {EditorView} from '@codemirror/view'
import {autocompletion, completionKeymap} from '@codemirror/autocomplete'
import {keymap} from '@codemirror/view'
import {Button} from '@/components/ui/button'
import {Play, Square, RotateCcw, Download, Upload, Sparkles} from 'lucide-react'
import {cn} from '@/lib/utils'
import {toast} from 'sonner'

// ksqlDB-specific keywords for autocomplete
const ksqlKeywords = [
    'SELECT', 'FROM', 'WHERE', 'GROUP BY', 'HAVING', 'ORDER BY', 'LIMIT',
    'CREATE', 'STREAM', 'TABLE', 'AS SELECT', 'WITH', 'PARTITION BY',
    'DROP', 'SHOW', 'DESCRIBE', 'EXPLAIN', 'TERMINATE',
    'INSERT INTO', 'VALUES', 'SET', 'UNSET',
    'EMIT', 'CHANGES', 'FINAL', 'RETENTION', 'GRACE PERIOD',
    'WINDOW', 'TUMBLING', 'HOPPING', 'SESSION', 'SIZE', 'ADVANCE BY',
    'JOIN', 'INNER', 'LEFT', 'RIGHT', 'FULL', 'OUTER', 'ON',
    'AND', 'OR', 'NOT', 'IN', 'LIKE', 'BETWEEN', 'IS NULL', 'IS NOT NULL',
    'COUNT', 'SUM', 'AVG', 'MIN', 'MAX', 'COLLECT_LIST', 'COLLECT_SET',
    'CAST', 'EXTRACTJSONFIELD', 'ARRAY', 'MAP', 'STRUCT',
    'STRING', 'INTEGER', 'BIGINT', 'DOUBLE', 'BOOLEAN', 'ARRAY', 'MAP', 'STRUCT',
    'KAFKA', 'AVRO', 'JSON', 'JSON_SR', 'PROTOBUF', 'PROTOBUF_NOSR',
    'KEY', 'VALUE', 'HEADERS', 'PARTITION', 'OFFSET', 'TIMESTAMP', 'ROWTIME', 'ROWPARTITION', 'ROWOFFSET'
]

interface KsqlQueryEditorProps {
    ksqlDBId?: string
    onExecute: (query: string) => void | Promise<void>
    initialQuery?: string
    readOnly?: boolean
    className?: string
    isExecuting?: boolean
    onCancel?: () => void
}

export function KsqlQueryEditor({
    onExecute,
    initialQuery = '',
    readOnly = false,
    className,
    isExecuting = false,
    onCancel
}: KsqlQueryEditorProps) {
    const [query, setQuery] = useState(initialQuery)
    const [isDark, setIsDark] = useState(false)

    useEffect(() => {
        // Detect dark mode from document root class (matches website theme)
        const checkDarkMode = () => {
            setIsDark(document.documentElement.classList.contains('dark'))
        }
        
        // Initial check
        checkDarkMode()
        
        // Watch for theme changes
        const observer = new MutationObserver(checkDarkMode)
        observer.observe(document.documentElement, {
            attributes: true,
            attributeFilter: ['class']
        })
        
        return () => observer.disconnect()
    }, [])

    useEffect(() => {
        if (initialQuery) {
            setQuery(initialQuery)
        }
    }, [initialQuery])

    const handleExecute = async () => {
        const trimmedQuery = query.trim()
        if (!trimmedQuery) {
            toast.error('Please enter a query')
            return
        }
        await onExecute(trimmedQuery)
    }

    const handleCancel = () => {
        if (onCancel) {
            onCancel()
        }
    }

    const handleFormat = () => {
        // Basic SQL formatting
        let formatted = query
            .replace(/\s+/g, ' ')
            .replace(/\s*,\s*/g, ', ')
            .replace(/\s*\(\s*/g, ' (')
            .replace(/\s*\)\s*/g, ') ')
            .replace(/\s*;\s*/g, ';\n')
        
        // Add line breaks after major keywords
        const keywords = ['SELECT', 'FROM', 'WHERE', 'GROUP BY', 'ORDER BY', 'HAVING', 'LIMIT', 'CREATE', 'STREAM', 'TABLE', 'AS SELECT', 'WITH', 'PARTITION BY']
        keywords.forEach(keyword => {
            const regex = new RegExp(`\\s+${keyword}\\s+`, 'gi')
            formatted = formatted.replace(regex, `\n${keyword} `)
        })
        
        setQuery(formatted.trim())
        toast.success('Query formatted')
    }

    const handleClear = () => {
        setQuery('')
        toast.success('Editor cleared')
    }

    const handleSave = () => {
        const blob = new Blob([query], {type: 'text/plain'})
        const url = URL.createObjectURL(blob)
        const a = document.createElement('a')
        a.href = url
        a.download = `ksql-query-${Date.now()}.sql`
        document.body.appendChild(a)
        a.click()
        document.body.removeChild(a)
        URL.revokeObjectURL(url)
        toast.success('Query saved')
    }

    const handleLoad = () => {
        const input = document.createElement('input')
        input.type = 'file'
        input.accept = '.sql,.txt'
        input.onchange = (e) => {
            const file = (e.target as HTMLInputElement).files?.[0]
            if (file) {
                const reader = new FileReader()
                reader.onload = (event) => {
                    const content = event.target?.result as string
                    setQuery(content)
                    toast.success('Query loaded')
                }
                reader.readAsText(file)
            }
        }
        input.click()
    }

    // Custom theme that uses website theme CSS variables
    const customTheme = EditorView.theme({
        '&': {
            fontSize: '14px',
            fontFamily: 'var(--font-mono), "Fira Code", "JetBrains Mono", "Cascadia Code", monospace',
            backgroundColor: 'var(--card)',
            color: 'var(--foreground)',
            height: '100%',
            width: '100%',
            display: 'flex',
            flexDirection: 'column',
        },
        '&.cm-focused': {
            outline: 'none',
        },
        '.cm-content': {
            padding: '1rem',
            minHeight: '100%',
            lineHeight: '1.6',
            letterSpacing: '0.01em',
            color: 'var(--foreground)',
            caretColor: 'var(--foreground)',
        },
        '.cm-editor': {
            height: '100%',
            width: '100%',
            display: 'flex',
            flexDirection: 'column',
            backgroundColor: 'var(--card)',
        },
        '.cm-scroller': {
            fontFamily: 'var(--font-mono), "Fira Code", "JetBrains Mono", "Cascadia Code", monospace',
            overflow: 'auto',
            flex: '1 1 auto',
            height: '100%',
        },
        '.cm-line': {
            color: 'var(--foreground)',
        },
        '.cm-lineWrapping .cm-line': {
            color: 'var(--foreground)',
        },
        // Ensure all text elements have visible color
        '.cm-line span[style*="color"]': {
            color: 'var(--foreground) !important',
        },
        // Default color for all spans without explicit styling
        '.cm-line > span': {
            color: 'var(--foreground)',
        },
        // Override any transparent or invisible colors
        '.cm-line span[style*="transparent"], .cm-line span[style*="rgba(0"]': {
            color: 'var(--foreground) !important',
        },
        '.cm-gutters': {
            backgroundColor: 'var(--muted)',
            borderRight: '1px solid var(--border)',
            color: 'var(--muted-foreground)',
        },
        '.cm-lineNumbers .cm-gutterElement': {
            padding: '0 0.5rem',
            minWidth: '2.5rem',
            width: '2.5rem',
            textAlign: 'right',
            fontVariantNumeric: 'tabular-nums',
            color: 'var(--muted-foreground)',
            fontSize: '13px',
        },
        '.cm-activeLineGutter': {
            backgroundColor: 'var(--accent)',
            color: 'var(--foreground)',
            fontWeight: '600',
        },
        '.cm-activeLine': {
            backgroundColor: 'var(--accent)',
            opacity: '0.1',
        },
        '.cm-selectionMatch': {
            backgroundColor: 'var(--primary)',
            opacity: '0.2',
            borderRadius: '2px',
        },
        '.cm-selection': {
            backgroundColor: 'var(--primary)',
            opacity: '0.2',
        },
        '.cm-cursor': {
            borderLeft: '2px solid var(--foreground)',
            marginLeft: '-1px',
        },
        '.cm-matchingBracket': {
            backgroundColor: 'var(--primary)',
            opacity: '0.3',
            borderRadius: '2px',
            fontWeight: '600',
        },
        '.cm-nonmatchingBracket': {
            backgroundColor: 'var(--destructive)',
            opacity: '0.3',
            borderRadius: '2px',
        },
    }, {dark: isDark})

    // SQL autocomplete
    const sqlAutocomplete = autocompletion({
        override: [
            (context) => {
                const word = context.matchBefore(/\w*/)
                if (!word) return null
                
                const matches = ksqlKeywords.filter(keyword => 
                    keyword.toLowerCase().startsWith(word.text.toLowerCase())
                )
                
                if (matches.length === 0) return null
                
                return {
                    from: word.from,
                    options: matches.map(keyword => ({
                        label: keyword,
                        type: 'keyword',
                        info: `ksqlDB keyword: ${keyword}`
                    }))
                }
            }
        ]
    })

    const extensions = [
        sql(),
        customTheme,
        sqlAutocomplete,
        EditorView.lineWrapping,
        keymap.of([
            ...completionKeymap,
            {
                key: 'Mod-Enter',
                run: () => {
                    handleExecute()
                    return true
                }
            },
            {
                key: 'Mod-Shift-f',
                run: () => {
                    handleFormat()
                    return true
                }
            }
        ])
    ]

    return (
        <div className={cn('flex flex-col h-full w-full', className)}>
            {/* Toolbar */}
            <div className="flex items-center justify-between gap-3 p-3 bg-muted/50 rounded-lg border shrink-0 mb-3">
                <div className="flex items-center gap-2 min-w-0">
                    <div className="flex items-center gap-1.5 px-2.5 py-1.5 bg-primary/10 text-primary rounded-md text-xs font-medium whitespace-nowrap">
                        <Sparkles className="h-3.5 w-3.5 shrink-0" />
                        <span className="hidden sm:inline">ksqlDB Query Editor</span>
                        <span className="sm:hidden">Editor</span>
                    </div>
                    {isExecuting && (
                        <div className="flex items-center gap-2 px-2.5 py-1.5 bg-yellow-500/10 text-yellow-600 dark:text-yellow-400 rounded-md text-xs font-medium whitespace-nowrap">
                            <div className="h-2 w-2 bg-yellow-500 rounded-full animate-pulse shrink-0" />
                            Executing...
                        </div>
                    )}
                </div>
                <div className="flex items-center gap-1.5 shrink-0">
                    <Button
                        variant="ghost"
                        size="sm"
                        onClick={handleFormat}
                        disabled={readOnly || isExecuting}
                        className="h-8 px-2 text-xs"
                    >
                        <span className="hidden sm:inline">Format</span>
                        <span className="sm:hidden">Fmt</span>
                    </Button>
                    <Button
                        variant="ghost"
                        size="sm"
                        onClick={handleLoad}
                        disabled={readOnly || isExecuting}
                        className="h-8 px-2 text-xs"
                        title="Load query from file"
                    >
                        <Upload className="h-3.5 w-3.5" />
                    </Button>
                    <Button
                        variant="ghost"
                        size="sm"
                        onClick={handleSave}
                        disabled={readOnly || isExecuting || !query.trim()}
                        className="h-8 px-2 text-xs"
                        title="Save query to file"
                    >
                        <Download className="h-3.5 w-3.5" />
                    </Button>
                    <Button
                        variant="ghost"
                        size="sm"
                        onClick={handleClear}
                        disabled={readOnly || isExecuting || !query.trim()}
                        className="h-8 px-2 text-xs"
                        title="Clear editor"
                    >
                        <RotateCcw className="h-3.5 w-3.5" />
                    </Button>
                    {isExecuting ? (
                        <Button
                            variant="destructive"
                            size="sm"
                            onClick={handleCancel}
                            className="h-8 px-3 text-xs"
                        >
                            <Square className="h-3.5 w-3.5 mr-1.5" />
                            <span className="hidden sm:inline">Cancel</span>
                        </Button>
                    ) : (
                        <Button
                            onClick={handleExecute}
                            disabled={readOnly || !query.trim() || isExecuting}
                            size="sm"
                            className="h-8 px-3 text-xs"
                        >
                            <Play className="h-3.5 w-3.5 mr-1.5" />
                            <span className="hidden sm:inline">Execute</span>
                            <span className="sm:hidden">Run</span>
                        </Button>
                    )}
                </div>
            </div>

            {/* Editor */}
            <div className="flex-1 min-h-0 w-full border rounded-lg overflow-hidden bg-background">
                <style>{`
                    .cm-editor .cm-line,
                    .cm-editor .cm-line span,
                    .cm-editor .cm-content {
                        color: var(--foreground) !important;
                    }
                    .cm-editor .cm-line > span:not([class*="cm-"]):not([style*="color"]) {
                        color: var(--foreground) !important;
                    }
                `}</style>
                <CodeMirror
                    value={query}
                    onChange={(value) => setQuery(value)}
                    extensions={extensions}
                    theme={customTheme}
                    editable={!readOnly && !isExecuting}
                    basicSetup={{
                        lineNumbers: true,
                        foldGutter: true,
                        dropCursor: false,
                        allowMultipleSelections: false,
                        indentOnInput: true,
                        bracketMatching: true,
                        closeBrackets: true,
                        autocompletion: true,
                        highlightSelectionMatches: true,
                        highlightActiveLine: true,
                        searchKeymap: true,
                        history: true,
                        drawSelection: true,
                    }}
                    className="h-full w-full"
                />
            </div>

            {/* Query Stats */}
            {query && (
                <div className="flex items-center gap-4 text-xs text-muted-foreground px-2 py-1.5 shrink-0 border-t">
                    <span>Lines: {query.split('\n').length}</span>
                    <span>Characters: {query.length}</span>
                    <span>Words: {query.trim().split(/\s+/).filter(w => w).length}</span>
                </div>
            )}
        </div>
    )
}

