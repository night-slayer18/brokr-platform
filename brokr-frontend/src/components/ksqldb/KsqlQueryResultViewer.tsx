import {useState} from 'react'
import {Card, CardContent, CardDescription, CardHeader, CardTitle} from '@/components/ui/card'
import {Button} from '@/components/ui/button'
import {Table, TableBody, TableCell, TableHead, TableHeader, TableRow} from '@/components/ui/table'
import {ScrollArea} from '@/components/ui/scroll-area'
import {Download, AlertCircle, CheckCircle2, Clock, Database} from 'lucide-react'
import {cn} from '@/lib/utils'
import {toast} from 'sonner'

interface KsqlQueryResult {
    queryId?: string
    columns: string[]
    rows: string[][]
    executionTimeMs?: number
    errorMessage?: string
}

interface KsqlQueryResultViewerProps {
    result: KsqlQueryResult | null
    isLoading?: boolean
    className?: string
}

export function KsqlQueryResultViewer({
    result,
    isLoading = false,
    className
}: KsqlQueryResultViewerProps) {
    const [selectedRows, setSelectedRows] = useState<Set<number>>(new Set())

    if (isLoading) {
        return (
            <Card className={cn('border-primary/20 bg-gradient-to-br from-card to-muted/10', className)}>
                <CardHeader>
                    <CardTitle className="flex items-center gap-2">
                        <Clock className="h-5 w-5 animate-spin text-primary" />
                        Executing Query...
                    </CardTitle>
                    <CardDescription>Please wait while your query is being processed</CardDescription>
                </CardHeader>
            </Card>
        )
    }

    if (!result) {
        return (
            <Card className={cn('border-dashed border-2 border-muted-foreground/20', className)}>
                <CardContent className="flex flex-col items-center justify-center py-16">
                    <Database className="h-12 w-12 text-muted-foreground mb-4 opacity-50" />
                    <p className="text-muted-foreground text-center">
                        No results yet. Execute a query to see results here.
                    </p>
                </CardContent>
            </Card>
        )
    }

    if (result.errorMessage) {
        return (
            <Card className={cn('border-destructive/50 bg-destructive/5', className)}>
                <CardHeader>
                    <CardTitle className="flex items-center gap-2 text-destructive">
                        <AlertCircle className="h-5 w-5" />
                        Query Error
                    </CardTitle>
                    <CardDescription>An error occurred while executing your query</CardDescription>
                </CardHeader>
                <CardContent>
                    <div className="rounded-lg bg-destructive/10 border border-destructive/20 p-4">
                        <pre className="text-sm font-mono text-destructive whitespace-pre-wrap break-words">
                            {result.errorMessage}
                        </pre>
                    </div>
                </CardContent>
            </Card>
        )
    }

    const handleExportCSV = () => {
        if (!result.columns.length || !result.rows.length) {
            toast.error('No data to export')
            return
        }

        const csvContent = [
            result.columns.join(','),
            ...result.rows.map(row => row.map(cell => {
                // Escape commas and quotes in CSV
                const cellStr = String(cell || '')
                if (cellStr.includes(',') || cellStr.includes('"') || cellStr.includes('\n')) {
                    return `"${cellStr.replace(/"/g, '""')}"`
                }
                return cellStr
            }).join(','))
        ].join('\n')

        const blob = new Blob([csvContent], {type: 'text/csv;charset=utf-8;'})
        const url = URL.createObjectURL(blob)
        const a = document.createElement('a')
        a.href = url
        a.download = `ksql-query-result-${Date.now()}.csv`
        document.body.appendChild(a)
        a.click()
        document.body.removeChild(a)
        URL.revokeObjectURL(url)
        toast.success('Results exported to CSV')
    }

    const handleExportJSON = () => {
        if (!result.columns.length || !result.rows.length) {
            toast.error('No data to export')
            return
        }

        const jsonData = result.rows.map(row => {
            const obj: Record<string, string> = {}
            result.columns.forEach((col, idx) => {
                obj[col] = row[idx] || ''
            })
            return obj
        })

        const jsonContent = JSON.stringify(jsonData, null, 2)
        const blob = new Blob([jsonContent], {type: 'application/json;charset=utf-8;'})
        const url = URL.createObjectURL(blob)
        const a = document.createElement('a')
        a.href = url
        a.download = `ksql-query-result-${Date.now()}.json`
        document.body.appendChild(a)
        a.click()
        document.body.removeChild(a)
        URL.revokeObjectURL(url)
        toast.success('Results exported to JSON')
    }

    const toggleRowSelection = (index: number) => {
        const newSelection = new Set(selectedRows)
        if (newSelection.has(index)) {
            newSelection.delete(index)
        } else {
            newSelection.add(index)
        }
        setSelectedRows(newSelection)
    }

    const hasData = result.columns.length > 0 && result.rows.length > 0

    return (
        <Card className={cn('flex flex-col h-full', className)}>
            <CardHeader className="border-b shrink-0">
                <div className="flex items-center justify-between gap-4">
                    <div className="min-w-0">
                        <CardTitle className="flex items-center gap-2">
                            <CheckCircle2 className="h-5 w-5 text-green-500 shrink-0" />
                            Query Results
                        </CardTitle>
                        <CardDescription className="mt-1">
                            {hasData ? (
                                <>
                                    {result.rows.length.toLocaleString()} row{result.rows.length !== 1 ? 's' : ''} returned
                                    {result.executionTimeMs && (
                                        <> in {result.executionTimeMs}ms</>
                                    )}
                                </>
                            ) : (
                                'No rows returned'
                            )}
                        </CardDescription>
                    </div>
                    {hasData && (
                        <div className="flex items-center gap-2 shrink-0">
                            <Button
                                variant="outline"
                                size="sm"
                                onClick={handleExportCSV}
                                className="h-8 text-xs"
                            >
                                <Download className="h-3.5 w-3.5 mr-1.5" />
                                CSV
                            </Button>
                            <Button
                                variant="outline"
                                size="sm"
                                onClick={handleExportJSON}
                                className="h-8 text-xs"
                            >
                                <Download className="h-3.5 w-3.5 mr-1.5" />
                                JSON
                            </Button>
                        </div>
                    )}
                </div>
            </CardHeader>
            <CardContent className="p-0 flex-1 min-h-0 flex flex-col">
                {hasData ? (
                    <ScrollArea className="flex-1 w-full">
                        <div className="rounded-lg border border-border/50 m-4 overflow-hidden bg-background">
                            <Table>
                                <TableHeader className="sticky top-0 z-10 bg-muted/50 backdrop-blur-sm">
                                    <TableRow className="border-b border-border/50">
                                        {result.columns.map((column, idx) => (
                                            <TableHead
                                                key={idx}
                                                className="font-semibold text-foreground bg-muted/30"
                                            >
                                                {column}
                                            </TableHead>
                                        ))}
                                    </TableRow>
                                </TableHeader>
                                <TableBody>
                                    {result.rows.map((row, rowIdx) => (
                                        <TableRow
                                            key={rowIdx}
                                            className={cn(
                                                'hover:bg-accent/50 transition-colors cursor-pointer',
                                                selectedRows.has(rowIdx) && 'bg-primary/10 border-primary/20'
                                            )}
                                            onClick={() => toggleRowSelection(rowIdx)}
                                        >
                                            {row.map((cell, cellIdx) => (
                                                <TableCell
                                                    key={cellIdx}
                                                    className="font-mono text-sm max-w-[300px] break-words"
                                                >
                                                    {cell !== null && cell !== undefined ? (
                                                        <span className="text-foreground">
                                                            {String(cell)}
                                                        </span>
                                                    ) : (
                                                        <span className="text-muted-foreground italic">NULL</span>
                                                    )}
                                                </TableCell>
                                            ))}
                                        </TableRow>
                                    ))}
                                </TableBody>
                            </Table>
                        </div>
                    </ScrollArea>
                ) : (
                    <div className="flex flex-col items-center justify-center py-12 px-4 flex-1">
                        <Database className="h-10 w-10 text-muted-foreground mb-3 opacity-50" />
                        <p className="text-muted-foreground text-center text-sm">
                            Query executed successfully but returned no rows.
                        </p>
                    </div>
                )}
            </CardContent>
            {result.queryId && (
                <div className="border-t shrink-0 px-4 py-2 bg-muted/50">
                    <div className="flex items-center justify-between text-xs text-muted-foreground">
                        <span>Query ID: <span className="font-mono">{result.queryId}</span></span>
                        {result.executionTimeMs && (
                            <span>Execution time: {result.executionTimeMs}ms</span>
                        )}
                    </div>
                </div>
            )}
        </Card>
    )
}

