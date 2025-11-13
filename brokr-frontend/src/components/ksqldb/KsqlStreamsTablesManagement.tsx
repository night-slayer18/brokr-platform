import {useState} from 'react'
import {Card, CardContent, CardDescription, CardHeader, CardTitle} from '@/components/ui/card'
import {Button} from '@/components/ui/button'
import {Badge} from '@/components/ui/badge'
import {Table, TableBody, TableCell, TableHead, TableHeader, TableRow} from '@/components/ui/table'
import {Tabs, TabsContent, TabsList, TabsTrigger} from '@/components/ui/tabs'
import {ScrollArea} from '@/components/ui/scroll-area'
import {Database, Trash2, Eye, Waves, Table as TableIcon, RefreshCw} from 'lucide-react'
import {useGraphQLQuery} from '@/hooks/useGraphQLQuery'
import {GET_KSQL_STREAMS, GET_KSQL_TABLES} from '@/graphql/queries'
import {useGraphQLMutation} from '@/hooks/useGraphQLMutation'
import {DROP_KSQL_STREAM, DROP_KSQL_TABLE} from '@/graphql/mutations'
import {toast} from 'sonner'
import {format} from 'date-fns'
import {
    Dialog,
    DialogContent,
    DialogDescription,
    DialogHeader,
    DialogTitle,
} from '@/components/ui/dialog'
import {
    AlertDialog,
    AlertDialogAction,
    AlertDialogCancel,
    AlertDialogContent,
    AlertDialogDescription,
    AlertDialogFooter,
    AlertDialogHeader,
    AlertDialogTitle,
} from '@/components/ui/alert-dialog'

interface KsqlStreamsTablesManagementProps {
    ksqlDBId: string
}

export function KsqlStreamsTablesManagement({ksqlDBId}: KsqlStreamsTablesManagementProps) {
    const [selectedItem, setSelectedItem] = useState<any>(null)
    const [showDetails, setShowDetails] = useState(false)
    const [deleteItem, setDeleteItem] = useState<{type: string; name: string} | null>(null)

    const {
        data: streamsData,
        isLoading: streamsLoading,
        refetch: refetchStreams,
    } = useGraphQLQuery(GET_KSQL_STREAMS, {ksqlDBId}, {enabled: !!ksqlDBId})

    const {
        data: tablesData,
        isLoading: tablesLoading,
        refetch: refetchTables,
    } = useGraphQLQuery(GET_KSQL_TABLES, {ksqlDBId}, {enabled: !!ksqlDBId})

    const dropStreamMutation = useGraphQLMutation(DROP_KSQL_STREAM, {
        onSuccess: () => {
            toast.success('Stream dropped successfully')
            refetchStreams()
            setDeleteItem(null)
        },
        onError: (error) => {
            toast.error(`Failed to drop stream: ${error.message}`)
        },
    })

    const dropTableMutation = useGraphQLMutation(DROP_KSQL_TABLE, {
        onSuccess: () => {
            toast.success('Table dropped successfully')
            refetchTables()
            setDeleteItem(null)
        },
        onError: (error) => {
            toast.error(`Failed to drop table: ${error.message}`)
        },
    })

    const handleDrop = async () => {
        if (!deleteItem) return

        if (deleteItem.type === 'STREAM') {
            await dropStreamMutation.mutate({
                ksqlDBId,
                streamName: deleteItem.name,
            })
        } else {
            await dropTableMutation.mutate({
                ksqlDBId,
                tableName: deleteItem.name,
            })
        }
    }

    const streams = streamsData?.ksqlStreams || []
    const tables = tablesData?.ksqlTables || []

    return (
        <>
            <Card className="border-primary/20 bg-gradient-to-br from-card to-muted/10">
                <CardHeader className="border-b border-border/50 bg-muted/20">
                    <div className="flex items-center justify-between">
                        <div>
                            <CardTitle className="flex items-center gap-2">
                                <Database className="h-5 w-5 text-primary" />
                                Streams & Tables
                            </CardTitle>
                            <CardDescription className="mt-1">
                                Manage ksqlDB streams and tables
                            </CardDescription>
                        </div>
                        <Button
                            variant="outline"
                            size="sm"
                            onClick={() => {
                                refetchStreams()
                                refetchTables()
                            }}
                            disabled={streamsLoading || tablesLoading}
                        >
                            <RefreshCw className="h-4 w-4 mr-2" />
                            Refresh
                        </Button>
                    </div>
                </CardHeader>
                <CardContent className="p-0">
                    <Tabs defaultValue="streams" className="w-full">
                        <TabsList className="w-full justify-start rounded-none border-b bg-muted/10">
                            <TabsTrigger value="streams" className="flex items-center gap-2">
                                <Waves className="h-4 w-4" />
                                Streams ({streams.length})
                            </TabsTrigger>
                            <TabsTrigger value="tables" className="flex items-center gap-2">
                                <TableIcon className="h-4 w-4" />
                                Tables ({tables.length})
                            </TabsTrigger>
                        </TabsList>

                        <TabsContent value="streams" className="m-0">
                            {streamsLoading ? (
                                <div className="flex items-center justify-center py-12">
                                    <RefreshCw className="h-6 w-6 animate-spin text-primary" />
                                </div>
                            ) : streams.length === 0 ? (
                                <div className="flex flex-col items-center justify-center py-12">
                                    <Waves className="h-12 w-12 text-muted-foreground mb-4 opacity-50" />
                                    <p className="text-muted-foreground text-center">
                                        No streams found
                                    </p>
                                </div>
                            ) : (
                                <ScrollArea className="h-[600px] w-full">
                                    <Table>
                                        <TableHeader className="sticky top-0 z-10 bg-muted/50 backdrop-blur-sm">
                                            <TableRow>
                                                <TableHead>Name</TableHead>
                                                <TableHead>Topic</TableHead>
                                                <TableHead>Key Format</TableHead>
                                                <TableHead>Value Format</TableHead>
                                                <TableHead>Created</TableHead>
                                                <TableHead>Actions</TableHead>
                                            </TableRow>
                                        </TableHeader>
                                        <TableBody>
                                            {streams.map((stream: any) => (
                                                <TableRow key={stream.id} className="hover:bg-accent/50">
                                                    <TableCell className="font-mono font-medium">
                                                        {stream.name}
                                                    </TableCell>
                                                    <TableCell className="font-mono text-sm">
                                                        {stream.topicName || '-'}
                                                    </TableCell>
                                                    <TableCell>
                                                        <Badge variant="outline">{stream.keyFormat || '-'}</Badge>
                                                    </TableCell>
                                                    <TableCell>
                                                        <Badge variant="outline">{stream.valueFormat || '-'}</Badge>
                                                    </TableCell>
                                                    <TableCell className="text-sm text-muted-foreground">
                                                        {stream.createdAt
                                                            ? format(new Date(stream.createdAt), 'MMM d, yyyy')
                                                            : '-'}
                                                    </TableCell>
                                                    <TableCell>
                                                        <div className="flex items-center gap-2">
                                                            <Button
                                                                variant="ghost"
                                                                size="sm"
                                                                onClick={() => {
                                                                    setSelectedItem(stream)
                                                                    setShowDetails(true)
                                                                }}
                                                            >
                                                                <Eye className="h-4 w-4" />
                                                            </Button>
                                                            <Button
                                                                variant="ghost"
                                                                size="sm"
                                                                onClick={() =>
                                                                    setDeleteItem({type: 'STREAM', name: stream.name})
                                                                }
                                                                className="text-destructive hover:text-destructive"
                                                            >
                                                                <Trash2 className="h-4 w-4" />
                                                            </Button>
                                                        </div>
                                                    </TableCell>
                                                </TableRow>
                                            ))}
                                        </TableBody>
                                    </Table>
                                </ScrollArea>
                            )}
                        </TabsContent>

                        <TabsContent value="tables" className="m-0">
                            {tablesLoading ? (
                                <div className="flex items-center justify-center py-12">
                                    <RefreshCw className="h-6 w-6 animate-spin text-primary" />
                                </div>
                            ) : tables.length === 0 ? (
                                <div className="flex flex-col items-center justify-center py-12">
                                    <TableIcon className="h-12 w-12 text-muted-foreground mb-4 opacity-50" />
                                    <p className="text-muted-foreground text-center">
                                        No tables found
                                    </p>
                                </div>
                            ) : (
                                <ScrollArea className="h-[600px] w-full">
                                    <Table>
                                        <TableHeader className="sticky top-0 z-10 bg-muted/50 backdrop-blur-sm">
                                            <TableRow>
                                                <TableHead>Name</TableHead>
                                                <TableHead>Topic</TableHead>
                                                <TableHead>Key Format</TableHead>
                                                <TableHead>Value Format</TableHead>
                                                <TableHead>Created</TableHead>
                                                <TableHead>Actions</TableHead>
                                            </TableRow>
                                        </TableHeader>
                                        <TableBody>
                                            {tables.map((table: any) => (
                                                <TableRow key={table.id} className="hover:bg-accent/50">
                                                    <TableCell className="font-mono font-medium">
                                                        {table.name}
                                                    </TableCell>
                                                    <TableCell className="font-mono text-sm">
                                                        {table.topicName || '-'}
                                                    </TableCell>
                                                    <TableCell>
                                                        <Badge variant="outline">{table.keyFormat || '-'}</Badge>
                                                    </TableCell>
                                                    <TableCell>
                                                        <Badge variant="outline">{table.valueFormat || '-'}</Badge>
                                                    </TableCell>
                                                    <TableCell className="text-sm text-muted-foreground">
                                                        {table.createdAt
                                                            ? format(new Date(table.createdAt), 'MMM d, yyyy')
                                                            : '-'}
                                                    </TableCell>
                                                    <TableCell>
                                                        <div className="flex items-center gap-2">
                                                            <Button
                                                                variant="ghost"
                                                                size="sm"
                                                                onClick={() => {
                                                                    setSelectedItem(table)
                                                                    setShowDetails(true)
                                                                }}
                                                            >
                                                                <Eye className="h-4 w-4" />
                                                            </Button>
                                                            <Button
                                                                variant="ghost"
                                                                size="sm"
                                                                onClick={() =>
                                                                    setDeleteItem({type: 'TABLE', name: table.name})
                                                                }
                                                                className="text-destructive hover:text-destructive"
                                                            >
                                                                <Trash2 className="h-4 w-4" />
                                                            </Button>
                                                        </div>
                                                    </TableCell>
                                                </TableRow>
                                            ))}
                                        </TableBody>
                                    </Table>
                                </ScrollArea>
                            )}
                        </TabsContent>
                    </Tabs>
                </CardContent>
            </Card>

            {/* Details Dialog */}
            <Dialog open={showDetails} onOpenChange={setShowDetails}>
                <DialogContent className="max-w-4xl max-h-[80vh] overflow-y-auto">
                    <DialogHeader>
                        <DialogTitle>
                            {selectedItem?.type === 'STREAM' ? 'Stream' : 'Table'} Details
                        </DialogTitle>
                        <DialogDescription>
                            Full details of the {selectedItem?.type?.toLowerCase()}
                        </DialogDescription>
                    </DialogHeader>
                    {selectedItem && (
                        <div className="space-y-4">
                            <div className="grid grid-cols-2 gap-4">
                                <div>
                                    <label className="text-sm font-medium">Name</label>
                                    <p className="mt-1 text-sm font-mono">{selectedItem.name}</p>
                                </div>
                                <div>
                                    <label className="text-sm font-medium">Type</label>
                                    <p className="mt-1">
                                        <Badge>{selectedItem.type}</Badge>
                                    </p>
                                </div>
                                <div>
                                    <label className="text-sm font-medium">Topic Name</label>
                                    <p className="mt-1 text-sm font-mono">{selectedItem.topicName || '-'}</p>
                                </div>
                                <div>
                                    <label className="text-sm font-medium">Key Format</label>
                                    <p className="mt-1">
                                        <Badge variant="outline">{selectedItem.keyFormat || '-'}</Badge>
                                    </p>
                                </div>
                                <div>
                                    <label className="text-sm font-medium">Value Format</label>
                                    <p className="mt-1">
                                        <Badge variant="outline">{selectedItem.valueFormat || '-'}</Badge>
                                    </p>
                                </div>
                                <div>
                                    <label className="text-sm font-medium">Created At</label>
                                    <p className="mt-1 text-sm">
                                        {selectedItem.createdAt
                                            ? format(new Date(selectedItem.createdAt), 'PPpp')
                                            : '-'}
                                    </p>
                                </div>
                            </div>
                            {selectedItem.schema && (
                                <div>
                                    <label className="text-sm font-medium">Schema</label>
                                    <pre className="mt-2 p-4 bg-muted rounded-lg font-mono text-sm overflow-x-auto">
                                        {selectedItem.schema}
                                    </pre>
                                </div>
                            )}
                            {selectedItem.queryText && (
                                <div>
                                    <label className="text-sm font-medium">Query Text</label>
                                    <pre className="mt-2 p-4 bg-muted rounded-lg font-mono text-sm overflow-x-auto">
                                        {selectedItem.queryText}
                                    </pre>
                                </div>
                            )}
                        </div>
                    )}
                </DialogContent>
            </Dialog>

            {/* Delete Confirmation Dialog */}
            <AlertDialog open={!!deleteItem} onOpenChange={(open) => !open && setDeleteItem(null)}>
                <AlertDialogContent>
                    <AlertDialogHeader>
                        <AlertDialogTitle>Drop {deleteItem?.type}</AlertDialogTitle>
                        <AlertDialogDescription>
                            Are you sure you want to drop {deleteItem?.type?.toLowerCase()}{' '}
                            <span className="font-mono font-semibold">{deleteItem?.name}</span>? This action
                            cannot be undone.
                        </AlertDialogDescription>
                    </AlertDialogHeader>
                    <AlertDialogFooter>
                        <AlertDialogCancel>Cancel</AlertDialogCancel>
                        <AlertDialogAction
                            onClick={handleDrop}
                            disabled={dropStreamMutation.isPending || dropTableMutation.isPending}
                            className="bg-destructive text-destructive-foreground hover:bg-destructive/90"
                        >
                            Drop
                        </AlertDialogAction>
                    </AlertDialogFooter>
                </AlertDialogContent>
            </AlertDialog>
        </>
    )
}

