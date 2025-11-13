import {useState, useEffect} from 'react'
import {Card, CardContent, CardDescription, CardHeader, CardTitle} from '@/components/ui/card'
import {Button} from '@/components/ui/button'
import {Badge} from '@/components/ui/badge'
import {Table, TableBody, TableCell, TableHead, TableHeader, TableRow} from '@/components/ui/table'
import {ScrollArea} from '@/components/ui/scroll-area'
import {Select, SelectContent, SelectItem, SelectTrigger, SelectValue} from '@/components/ui/select'
import {History, Trash2, Eye, Clock, CheckCircle2, XCircle, Loader2} from 'lucide-react'
import {format} from 'date-fns'
import {useGraphQLQuery} from '@/hooks/useGraphQLQuery'
import {GET_KSQL_QUERY_HISTORY} from '@/graphql/queries'
import {useGraphQLMutation} from '@/hooks/useGraphQLMutation'
import {DELETE_KSQL_QUERY_HISTORY} from '@/graphql/mutations'
import {toast} from 'sonner'
import {
    Dialog,
    DialogContent,
    DialogDescription,
    DialogHeader,
    DialogTitle,
} from '@/components/ui/dialog'

interface KsqlQueryHistoryProps {
    ksqlDBId: string
}

export function KsqlQueryHistory({ksqlDBId}: KsqlQueryHistoryProps) {
    const [page, setPage] = useState(0)
    const [pageSize] = useState(50)
    const [queryTypeFilter, setQueryTypeFilter] = useState<string>('')
    const [statusFilter, setStatusFilter] = useState<string>('')
    const [selectedHistory, setSelectedHistory] = useState<{
        id: string;
        queryText: string;
        queryType: string;
        status: string;
        executionTimeMs?: number;
        rowsReturned?: number;
        errorMessage?: string;
        startedAt?: number;
        completedAt?: number;
        user?: {id: string; username: string; email: string};
    } | null>(null)
    const [showDetails, setShowDetails] = useState(false)
    const [error, setError] = useState<string | null>(null)

    const {data, isLoading, error: queryError, refetch} = useGraphQLQuery(
        GET_KSQL_QUERY_HISTORY,
        ksqlDBId ? {
            ksqlDBId,
            filter: {
                queryType: queryTypeFilter || null,
                status: statusFilter || null,
            },
            pagination: {
                page,
                size: pageSize,
            },
        } : undefined,
        {
            enabled: !!ksqlDBId,
            onError: (err) => {
                console.error('Query history error:', err)
                setError(err.message || 'Failed to load query history')
            },
        }
    )

    const deleteHistoryMutation = useGraphQLMutation(DELETE_KSQL_QUERY_HISTORY, {
        onSuccess: () => {
            toast.success('Query history deleted successfully')
            refetch()
        },
        onError: (error) => {
            toast.error(`Failed to delete history: ${error.message}`)
        },
    })

    const history = data?.ksqlQueryHistory || null

    // Reset error and page when component mounts or ksqlDBId changes
    useEffect(() => {
        setError(null)
        setPage(0)
    }, [ksqlDBId, queryTypeFilter, statusFilter])

    const handleDeleteOld = async () => {
        if (!confirm('Delete query history older than 30 days?')) return
        await deleteHistoryMutation.mutate({
            ksqlDBId,
            olderThanDays: 30,
        })
    }

    const getStatusBadge = (status: string) => {
        switch (status) {
            case 'SUCCESS':
                return (
                    <Badge variant="default" className="bg-green-500/10 text-green-600 dark:text-green-400">
                        <CheckCircle2 className="h-3 w-3 mr-1" />
                        Success
                    </Badge>
                )
            case 'FAILURE':
                return (
                    <Badge variant="destructive">
                        <XCircle className="h-3 w-3 mr-1" />
                        Failed
                    </Badge>
                )
            case 'RUNNING':
                return (
                    <Badge variant="secondary" className="bg-yellow-500/10 text-yellow-600 dark:text-yellow-400">
                        <Loader2 className="h-3 w-3 mr-1 animate-spin" />
                        Running
                    </Badge>
                )
            case 'CANCELLED':
                return (
                    <Badge variant="outline">
                        Cancelled
                    </Badge>
                )
            default:
                return <Badge variant="outline">{status}</Badge>
        }
    }

    return (
        <>
            <Card className="border-primary/20 bg-linear-to-br from-card to-muted/10">
                <CardHeader className="border-b border-border/50 bg-muted/20">
                    <div className="flex items-center justify-between">
                        <div>
                            <CardTitle className="flex items-center gap-2">
                                <History className="h-5 w-5 text-primary" />
                                Query History
                            </CardTitle>
                            <CardDescription className="mt-1">
                                View and manage your ksqlDB query execution history
                            </CardDescription>
                        </div>
                        <div className="flex items-center gap-2">
                            <Button
                                variant="outline"
                                size="sm"
                                onClick={handleDeleteOld}
                                disabled={deleteHistoryMutation.isPending}
                            >
                                <Trash2 className="h-4 w-4 mr-2" />
                                Clean Old
                            </Button>
                            <Button
                                variant="outline"
                                size="sm"
                                onClick={() => refetch()}
                                disabled={isLoading}
                            >
                                Refresh
                            </Button>
                        </div>
                    </div>
                </CardHeader>
                <CardContent className="p-0">
                    {/* Filters */}
                    <div className="p-4 border-b border-border/50 bg-muted/10">
                        <div className="flex items-center gap-4">
                            <div className="flex-1">
                                <Select value={queryTypeFilter} onValueChange={setQueryTypeFilter}>
                                    <SelectTrigger className="w-[180px]">
                                        <SelectValue placeholder="Query Type" />
                                    </SelectTrigger>
                                    <SelectContent>
                                        <SelectItem value="">All Types</SelectItem>
                                        <SelectItem value="SELECT">SELECT</SelectItem>
                                        <SelectItem value="CREATE_STREAM">CREATE STREAM</SelectItem>
                                        <SelectItem value="CREATE_TABLE">CREATE TABLE</SelectItem>
                                        <SelectItem value="DROP_STREAM">DROP STREAM</SelectItem>
                                        <SelectItem value="DROP_TABLE">DROP TABLE</SelectItem>
                                        <SelectItem value="TERMINATE">TERMINATE</SelectItem>
                                    </SelectContent>
                                </Select>
                            </div>
                            <div className="flex-1">
                                <Select value={statusFilter} onValueChange={setStatusFilter}>
                                    <SelectTrigger className="w-[180px]">
                                        <SelectValue placeholder="Status" />
                                    </SelectTrigger>
                                    <SelectContent>
                                        <SelectItem value="">All Statuses</SelectItem>
                                        <SelectItem value="SUCCESS">Success</SelectItem>
                                        <SelectItem value="FAILURE">Failure</SelectItem>
                                        <SelectItem value="RUNNING">Running</SelectItem>
                                        <SelectItem value="CANCELLED">Cancelled</SelectItem>
                                    </SelectContent>
                                </Select>
                            </div>
                        </div>
                    </div>

                    {/* Table */}
                    {queryError || error ? (
                        <div className="flex flex-col items-center justify-center py-12">
                            <XCircle className="h-12 w-12 text-destructive mb-4" />
                            <p className="text-destructive text-center">
                                {error || queryError?.message || 'Failed to load query history'}
                            </p>
                            <Button
                                variant="outline"
                                size="sm"
                                onClick={() => {
                                    setError(null)
                                    refetch()
                                }}
                                className="mt-4"
                            >
                                Retry
                            </Button>
                        </div>
                    ) : isLoading ? (
                        <div className="flex items-center justify-center py-12">
                            <Loader2 className="h-6 w-6 animate-spin text-primary" />
                        </div>
                    ) : !history || !history.content || history.content.length === 0 ? (
                        <div className="flex flex-col items-center justify-center py-12">
                            <History className="h-12 w-12 text-muted-foreground mb-4 opacity-50" />
                            <p className="text-muted-foreground text-center">
                                No query history found
                            </p>
                        </div>
                    ) : (
                        <ScrollArea className="h-[600px] w-full">
                            <Table>
                                <TableHeader className="sticky top-0 z-10 bg-muted/50 backdrop-blur-sm">
                                    <TableRow>
                                        <TableHead>Query</TableHead>
                                        <TableHead>Type</TableHead>
                                        <TableHead>Status</TableHead>
                                        <TableHead>User</TableHead>
                                        <TableHead>Execution Time</TableHead>
                                        <TableHead>Rows</TableHead>
                                        <TableHead>Started</TableHead>
                                        <TableHead>Actions</TableHead>
                                    </TableRow>
                                </TableHeader>
                                <TableBody>
                                    {(history.content || []).map((item: {
                                        id: string;
                                        queryText: string;
                                        queryType: string;
                                        status: string;
                                        executionTimeMs?: number;
                                        rowsReturned?: number;
                                        errorMessage?: string;
                                        startedAt?: number;
                                        completedAt?: number;
                                        user?: {id: string; username: string; email: string};
                                    }) => (
                                        <TableRow key={item.id} className="hover:bg-accent/50">
                                            <TableCell className="max-w-[300px]">
                                                <div className="truncate font-mono text-sm">
                                                    {item.queryText}
                                                </div>
                                            </TableCell>
                                            <TableCell>
                                                <Badge variant="outline">{item.queryType}</Badge>
                                            </TableCell>
                                            <TableCell>{getStatusBadge(item.status)}</TableCell>
                                            <TableCell>
                                                <div className="text-sm">
                                                    <div className="font-medium">{item.user?.username}</div>
                                                    <div className="text-muted-foreground text-xs">
                                                        {item.user?.email}
                                                    </div>
                                                </div>
                                            </TableCell>
                                            <TableCell>
                                                {item.executionTimeMs ? (
                                                    <span className="text-sm font-mono">
                                                        {item.executionTimeMs}ms
                                                    </span>
                                                ) : (
                                                    <span className="text-muted-foreground">-</span>
                                                )}
                                            </TableCell>
                                            <TableCell>
                                                {item.rowsReturned !== null && item.rowsReturned !== undefined ? (
                                                    <span className="text-sm font-mono">
                                                        {item.rowsReturned.toLocaleString()}
                                                    </span>
                                                ) : (
                                                    <span className="text-muted-foreground">-</span>
                                                )}
                                            </TableCell>
                                            <TableCell>
                                                <div className="flex items-center gap-1 text-sm text-muted-foreground">
                                                    <Clock className="h-3 w-3" />
                                                    {item.startedAt
                                                        ? format(new Date(item.startedAt), 'MMM d, HH:mm:ss')
                                                        : '-'}
                                                </div>
                                            </TableCell>
                                            <TableCell>
                                                <Button
                                                    variant="ghost"
                                                    size="sm"
                                                    onClick={() => {
                                                        setSelectedHistory(item)
                                                        setShowDetails(true)
                                                    }}
                                                >
                                                    <Eye className="h-4 w-4" />
                                                </Button>
                                            </TableCell>
                                        </TableRow>
                                    ))}
                                </TableBody>
                            </Table>
                        </ScrollArea>
                    )}

                    {/* Pagination */}
                    {history && history.totalPages != null && history.totalPages > 1 && (
                        <div className="p-4 border-t border-border/50 bg-muted/10 flex items-center justify-between">
                            <div className="text-sm text-muted-foreground">
                                Showing {history.content?.length || 0} of {history.totalElements || 0} queries
                            </div>
                            <div className="flex items-center gap-2">
                                <Button
                                    variant="outline"
                                    size="sm"
                                    onClick={() => setPage((p) => Math.max(0, p - 1))}
                                    disabled={page === 0 || isLoading}
                                >
                                    Previous
                                </Button>
                                <span className="text-sm">
                                    Page {(history.currentPage || 0) + 1} of {history.totalPages || 1}
                                </span>
                                <Button
                                    variant="outline"
                                    size="sm"
                                    onClick={() => setPage((p) => p + 1)}
                                    disabled={page >= (history.totalPages || 1) - 1 || isLoading}
                                >
                                    Next
                                </Button>
                            </div>
                        </div>
                    )}
                </CardContent>
            </Card>

            {/* Details Dialog */}
            <Dialog open={showDetails} onOpenChange={setShowDetails}>
                <DialogContent className="max-w-4xl max-h-[80vh] overflow-y-auto">
                    <DialogHeader>
                        <DialogTitle>Query Details</DialogTitle>
                        <DialogDescription>
                            Full details of the executed query
                        </DialogDescription>
                    </DialogHeader>
                    {selectedHistory ? (
                        <div className="space-y-4">
                            <div>
                                <label className="text-sm font-medium">Query Text</label>
                                <pre className="mt-2 p-4 bg-muted rounded-lg font-mono text-sm overflow-x-auto">
                                    {selectedHistory.queryText}
                                </pre>
                            </div>
                            <div className="grid grid-cols-2 gap-4">
                                <div>
                                    <label className="text-sm font-medium">Query Type</label>
                                    <p className="mt-1 text-sm">{selectedHistory.queryType}</p>
                                </div>
                                <div>
                                    <label className="text-sm font-medium">Status</label>
                                    <div className="mt-1">{getStatusBadge(selectedHistory.status)}</div>
                                </div>
                                <div>
                                    <label className="text-sm font-medium">Execution Time</label>
                                    <p className="mt-1 text-sm font-mono">
                                        {selectedHistory.executionTimeMs || '-'}ms
                                    </p>
                                </div>
                                <div>
                                    <label className="text-sm font-medium">Rows Returned</label>
                                    <p className="mt-1 text-sm font-mono">
                                        {selectedHistory.rowsReturned?.toLocaleString() || '-'}
                                    </p>
                                </div>
                                <div>
                                    <label className="text-sm font-medium">Started At</label>
                                    <p className="mt-1 text-sm">
                                        {selectedHistory.startedAt
                                            ? format(new Date(selectedHistory.startedAt), 'PPpp')
                                            : '-'}
                                    </p>
                                </div>
                                <div>
                                    <label className="text-sm font-medium">Completed At</label>
                                    <p className="mt-1 text-sm">
                                        {selectedHistory.completedAt
                                            ? format(new Date(selectedHistory.completedAt), 'PPpp')
                                            : '-'}
                                    </p>
                                </div>
                            </div>
                            {selectedHistory.errorMessage && (
                                <div>
                                    <label className="text-sm font-medium text-destructive">Error Message</label>
                                    <pre className="mt-2 p-4 bg-destructive/10 border border-destructive/20 rounded-lg font-mono text-sm text-destructive overflow-x-auto">
                                        {selectedHistory.errorMessage}
                                    </pre>
                                </div>
                            )}
                        </div>
                    ) : (
                        <div className="text-center py-8 text-muted-foreground">
                            No details available
                        </div>
                    )}
                </DialogContent>
            </Dialog>
        </>
    )
}

