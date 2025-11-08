import {useLazyQuery, useQuery} from '@apollo/client/react';
import {useParams} from 'react-router-dom';
import {GET_MESSAGES, GET_TOPIC} from '@/graphql/queries';
import type {GetMessagesQuery, GetMessagesVariables, GetTopicQuery, GetTopicVariables} from '@/graphql/types';
import {Card, CardContent, CardDescription, CardHeader, CardTitle} from '@/components/ui/card';
import {Skeleton} from '@/components/ui/skeleton';
import {Badge} from '@/components/ui/badge';
import {Table, TableBody, TableCell, TableHead, TableHeader, TableRow} from '@/components/ui/table';
import {Tabs, TabsContent, TabsList, TabsTrigger} from '@/components/ui/tabs';
import {formatDate, formatRelativeTime} from '@/lib/formatters';
import {formatNumber} from '@/lib/utils';
import {useEffect, useState} from "react";
import {Select, SelectContent, SelectItem, SelectTrigger, SelectValue} from "@/components/ui/select";
import {Button} from "@/components/ui/button";
import {Label} from "@/components/ui/label";
import {Loader2, RefreshCw} from "lucide-react";
import {toast} from "sonner";
import type {Message} from "@/types";
import {
    Pagination,
    PaginationContent,
    PaginationItem,
    PaginationLink,
    PaginationNext,
    PaginationPrevious
} from "@/components/ui/pagination";


export default function TopicDetailPage() {
    const {clusterId, topicName} = useParams<{ clusterId: string; topicName: string }>();
    const [selectedPartition, setSelectedPartition] = useState<string>('');
    const [messageLimit, setMessageLimit] = useState<string>('100');
    const [currentPage, setCurrentPage] = useState(1);
    const [rowsPerPage, setRowsPerPage] = useState(10);

    const {data, loading, error} = useQuery<GetTopicQuery, GetTopicVariables>(GET_TOPIC, {
        variables: {clusterId: clusterId!, name: topicName!},
        skip: !clusterId || !topicName,
        fetchPolicy: 'cache-first', // Use cache for faster loads
        nextFetchPolicy: 'cache-first',
    });

    const [getMessages, {
        data: messagesData,
        loading: messagesLoading,
        error: messagesError
    }] = useLazyQuery<GetMessagesQuery, GetMessagesVariables>(GET_MESSAGES);

    const topic = data?.topic;
    const messages = messagesData?.messages || [];

    const handleFetchMessages = () => {
        if (!clusterId || !topicName) {
            toast.error("Cluster ID or Topic Name is missing.");
            return;
        }

        const partitions = selectedPartition === '' || selectedPartition === 'all'
            ? topic?.partitionsInfo?.map(p => p.id)
            : [parseInt(selectedPartition)];

        // Force a fresh fetch by calling getMessages directly
        getMessages({
            variables: {
                clusterId,
                input: {
                    topic: topicName,
                    partitions: partitions,
                    offset: 'latest',
                    limit: parseInt(messageLimit),
                },
            },
        });
    };

    // Automatically fetch messages when page loads and topic data is available
    useEffect(() => {
        if (topic && clusterId && topicName) {
            const partitions = topic.partitionsInfo?.map(p => p.id);
            getMessages({
                variables: {
                    clusterId,
                    input: {
                        topic: topicName,
                        partitions: partitions,
                        offset: 'latest',
                        limit: 100,
                    },
                },
            });
        }
    }, [topic?.name, clusterId, topicName]); // Run when topic is loaded

    const paginatedMessages = messages.slice(
        (currentPage - 1) * rowsPerPage,
        currentPage * rowsPerPage
    );

    const totalPages = Math.ceil(messages.length / rowsPerPage);

    const handleRowsPerPageChange = (value: string) => {
        setRowsPerPage(parseInt(value));
        setCurrentPage(1); // Reset to first page when changing rows per page
    };

    if (!clusterId || !topicName) {
        return <div className="text-destructive">Cluster ID or Topic Name is missing.</div>;
    }

    if (loading) {
        return (
            <div className="space-y-6">
                <Skeleton className="h-10 w-1/2"/>
                <div className="grid gap-4 md:grid-cols-3">
                    <Skeleton className="h-24"/>
                    <Skeleton className="h-24"/>
                    <Skeleton className="h-24"/>
                </div>
                <Skeleton className="h-96"/>
            </div>
        );
    }

    if (error) {
        return (
            <div className="space-y-4">
                <h2 className="text-2xl font-bold text-destructive">Error Loading Topic</h2>
                <Card>
                    <CardContent className="pt-6">
                        <p className="text-destructive">{error.message}</p>
                        <p className="text-muted-foreground mt-2">
                            Please check if the topic exists and try again.
                        </p>
                    </CardContent>
                </Card>
            </div>
        );
    }

    if (!loading && !topic) {
        return (
            <div className="space-y-4">
                <h2 className="text-2xl font-bold">Topic Not Found</h2>
                <Card>
                    <CardContent className="pt-6">
                        <p className="text-muted-foreground">
                            The topic "{topicName}" could not be found in cluster "{clusterId}".
                        </p>
                    </CardContent>
                </Card>
            </div>
        );
    }

    // At this point, topic is guaranteed to be defined (either loading or topic exists)
    if (!topic) {
        return null; // This should never happen due to loading state, but satisfies TypeScript
    }

    return (
        <div className="space-y-6">
            <div>
                <h2 className="text-3xl font-bold tracking-tight flex items-center gap-2">
                    {topic.name}
                    {topic.isInternal && <Badge variant="secondary">Internal</Badge>}
                </h2>
                <p className="text-muted-foreground">Details for topic <span className="font-mono">{topic.name}</span>
                </p>
            </div>

            <div className="grid gap-4 md:grid-cols-3">
                <Card>
                    <CardHeader>
                        <CardTitle>Partitions</CardTitle>
                    </CardHeader>
                    <CardContent>
                        <p className="text-2xl font-bold">{topic.partitions}</p>
                    </CardContent>
                </Card>
                <Card>
                    <CardHeader>
                        <CardTitle>Replication Factor</CardTitle>
                    </CardHeader>
                    <CardContent>
                        <p className="text-2xl font-bold">{topic.replicationFactor}</p>
                    </CardContent>
                </Card>
                <Card>
                    <CardHeader>
                        <CardTitle>Total Size</CardTitle>
                    </CardHeader>
                    <CardContent>
                        <p className="text-2xl font-bold">
                            {formatNumber(topic.partitionsInfo?.reduce((acc, p) => acc + p.size, 0) || 0)} bytes
                        </p>
                    </CardContent>
                </Card>
            </div>

            <Tabs defaultValue="partitions" className="space-y-4">
                <TabsList>
                    <TabsTrigger value="partitions">Partitions</TabsTrigger>
                    <TabsTrigger value="messages">Messages</TabsTrigger>
                    <TabsTrigger value="configuration">Configuration</TabsTrigger>
                </TabsList>

                <TabsContent value="partitions" className="space-y-4">
                    <Card>
                        <CardHeader>
                            <CardTitle>Partition Details</CardTitle>
                            <CardDescription>Detailed information for each partition.</CardDescription>
                        </CardHeader>
                        <CardContent>
                            <Table>
                                <TableHeader>
                                    <TableRow>
                                        <TableHead>Partition</TableHead>
                                        <TableHead>Leader</TableHead>
                                        <TableHead>Replicas</TableHead>
                                        <TableHead>In-Sync Replicas</TableHead>
                                        <TableHead>Earliest Offset</TableHead>
                                        <TableHead>Latest Offset</TableHead>
                                        <TableHead>Size</TableHead>
                                    </TableRow>
                                </TableHeader>
                                <TableBody>
                                    {topic.partitionsInfo && topic.partitionsInfo.length > 0 ? (
                                        topic.partitionsInfo.map((p) => (
                                            <TableRow key={p.id}>
                                                <TableCell>{p.id}</TableCell>
                                                <TableCell>{p.leader}</TableCell>
                                                <TableCell>{p.replicas.join(', ')}</TableCell>
                                                <TableCell>{p.isr.join(', ')}</TableCell>
                                                <TableCell>{formatNumber(p.earliestOffset)}</TableCell>
                                                <TableCell>{formatNumber(p.latestOffset)}</TableCell>
                                                <TableCell>{formatNumber(p.size)} bytes</TableCell>
                                            </TableRow>
                                        ))
                                    ) : (
                                        <TableRow>
                                            <TableCell colSpan={7} className="text-center text-muted-foreground">
                                                No partition information available
                                            </TableCell>
                                        </TableRow>
                                    )}
                                </TableBody>
                            </Table>
                        </CardContent>
                    </Card>
                </TabsContent>

                <TabsContent value="messages" className="space-y-4">
                    <Card>
                        <CardHeader>
                            <CardTitle>Messages</CardTitle>
                            <CardDescription>Fetch and view messages from this topic.</CardDescription>
                        </CardHeader>
                        <CardContent>
                            <div className="flex flex-wrap items-end gap-4 mb-4">
                                <div className="flex-1 min-w-[150px]">
                                    <Label htmlFor="messageLimit">Number of Messages</Label>
                                    <Select value={messageLimit} onValueChange={setMessageLimit}>
                                        <SelectTrigger id="messageLimit">
                                            <SelectValue placeholder="Select limit"/>
                                        </SelectTrigger>
                                        <SelectContent>
                                            <SelectItem value="100">100</SelectItem>
                                            <SelectItem value="500">500</SelectItem>
                                            <SelectItem value="1000">1000</SelectItem>
                                            <SelectItem value="5000">5000</SelectItem>
                                        </SelectContent>
                                    </Select>
                                </div>
                                <div className="flex-1 min-w-[150px]">
                                    <Label htmlFor="partitionFilter">Partition (Optional)</Label>
                                    <Select
                                        value={selectedPartition === '' ? "all" : selectedPartition}
                                        onValueChange={(value) => setSelectedPartition(value === "all" ? "" : value)}
                                    >
                                        <SelectTrigger id="partitionFilter">
                                            <SelectValue>
                                                {selectedPartition === ''
                                                    ? "All Partitions"
                                                    : `Partition ${selectedPartition}`
                                                }
                                            </SelectValue>
                                        </SelectTrigger>
                                        <SelectContent>
                                            <SelectItem value="all">All Partitions</SelectItem>
                                            {topic.partitionsInfo?.map(p => (
                                                <SelectItem key={p.id} value={String(p.id)}>
                                                    Partition {p.id}
                                                </SelectItem>
                                            ))}
                                        </SelectContent>
                                    </Select>
                                </div>
                                <Button onClick={handleFetchMessages} disabled={messagesLoading}>
                                    {messagesLoading ? (
                                        <Loader2 className="mr-2 h-4 w-4 animate-spin"/>
                                    ) : (
                                        <RefreshCw className="mr-2 h-4 w-4"/>
                                    )}
                                    Refresh
                                </Button>
                            </div>

                            {messagesError && (
                                <div className="text-destructive mb-4">Error fetching
                                    messages: {messagesError.message}</div>
                            )}

                            {messages.length > 0 && (
                                <>
                                    <Table>
                                        <TableHeader>
                                            <TableRow>
                                                <TableHead className="w-20">Partition</TableHead>
                                                <TableHead className="w-24">Offset</TableHead>
                                                <TableHead className="w-32">Timestamp</TableHead>
                                                <TableHead className="w-48">Key</TableHead>
                                                <TableHead>Value</TableHead>
                                            </TableRow>
                                        </TableHeader>
                                        <TableBody>
                                            {paginatedMessages.map((message: Message, index: number) => (
                                                <TableRow key={`${message.partition}-${message.offset}-${index}`}>
                                                    <TableCell className="w-20">{message.partition}</TableCell>
                                                    <TableCell
                                                        className="w-24">{formatNumber(message.offset)}</TableCell>
                                                    <TableCell className="w-32">
                                                        <span title={formatDate(message.timestamp)}>
                                                            {formatRelativeTime(message.timestamp)}
                                                        </span>
                                                    </TableCell>
                                                    <TableCell className="w-48">
                                                        <pre
                                                            className="overflow-auto text-xs bg-muted p-2 rounded border max-h-20 whitespace-pre-wrap break-all">
                                                            {message.key || '(null)'}
                                                        </pre>
                                                    </TableCell>
                                                    <TableCell>
                                                        <pre
                                                            className="overflow-auto text-xs bg-muted p-2 rounded border max-h-20 whitespace-pre-wrap break-all">
                                                            {message.value || '(null)'}
                                                        </pre>
                                                    </TableCell>
                                                </TableRow>
                                            ))}
                                        </TableBody>
                                    </Table>
                                    <div className="flex items-center justify-between mt-4">
                                        <div className="flex items-center gap-4">
                                            <div className="flex items-center gap-2">
                                                <Label htmlFor="rowsPerPage" className="text-sm">Rows per page:</Label>
                                                <Select value={String(rowsPerPage)}
                                                        onValueChange={handleRowsPerPageChange}>
                                                    <SelectTrigger id="rowsPerPage" className="w-20">
                                                        <SelectValue/>
                                                    </SelectTrigger>
                                                    <SelectContent>
                                                        <SelectItem value="10">10</SelectItem>
                                                        <SelectItem value="25">25</SelectItem>
                                                        <SelectItem value="50">50</SelectItem>
                                                        <SelectItem value="100">100</SelectItem>
                                                    </SelectContent>
                                                </Select>
                                            </div>
                                            <span className="text-sm text-muted-foreground">
                                                Showing {((currentPage - 1) * rowsPerPage) + 1} to {Math.min(currentPage * rowsPerPage, messages.length)} of {messages.length} messages
                                            </span>
                                        </div>
                                        <Pagination>
                                            <PaginationContent>
                                                <PaginationItem>
                                                    <PaginationPrevious
                                                        onClick={() => setCurrentPage(prev => Math.max(1, prev - 1))}
                                                        disabled={currentPage === 1}
                                                    />
                                                </PaginationItem>

                                                {/* Show first page */}
                                                {totalPages > 0 && (
                                                    <PaginationItem>
                                                        <PaginationLink
                                                            onClick={() => setCurrentPage(1)}
                                                            isActive={currentPage === 1}
                                                        >
                                                            1
                                                        </PaginationLink>
                                                    </PaginationItem>
                                                )}

                                                {/* Show ellipsis if current page is far from start */}
                                                {currentPage > 3 && totalPages > 5 && (
                                                    <PaginationItem>
                                                        <span className="px-4">...</span>
                                                    </PaginationItem>
                                                )}

                                                {/* Show pages around current page */}
                                                {Array.from({length: totalPages}, (_, i) => i + 1)
                                                    .filter(page => {
                                                        if (totalPages <= 5) return page > 1 && page < totalPages;
                                                        return page > 1 && page < totalPages && Math.abs(page - currentPage) <= 1;
                                                    })
                                                    .map(page => (
                                                        <PaginationItem key={page}>
                                                            <PaginationLink
                                                                onClick={() => setCurrentPage(page)}
                                                                isActive={currentPage === page}
                                                            >
                                                                {page}
                                                            </PaginationLink>
                                                        </PaginationItem>
                                                    ))
                                                }

                                                {/* Show ellipsis if current page is far from end */}
                                                {currentPage < totalPages - 2 && totalPages > 5 && (
                                                    <PaginationItem>
                                                        <span className="px-4">...</span>
                                                    </PaginationItem>
                                                )}

                                                {/* Show last page */}
                                                {totalPages > 1 && (
                                                    <PaginationItem>
                                                        <PaginationLink
                                                            onClick={() => setCurrentPage(totalPages)}
                                                            isActive={currentPage === totalPages}
                                                        >
                                                            {totalPages}
                                                        </PaginationLink>
                                                    </PaginationItem>
                                                )}

                                                <PaginationItem>
                                                    <PaginationNext
                                                        onClick={() => setCurrentPage(prev => Math.min(totalPages, prev + 1))}
                                                        disabled={currentPage === totalPages}
                                                    />
                                                </PaginationItem>
                                            </PaginationContent>
                                        </Pagination>
                                    </div>
                                </>
                            )}
                            {messages.length === 0 && !messagesLoading && (
                                <p className="text-muted-foreground text-center py-8">
                                    No messages found in this topic. Messages will appear here as they are produced.
                                </p>
                            )}
                        </CardContent>
                    </Card>
                </TabsContent>

                <TabsContent value="configuration" className="space-y-4">
                    <Card>
                        <CardHeader>
                            <CardTitle>Configuration</CardTitle>
                            <CardDescription>Topic-level configurations in table and JSON format.</CardDescription>
                        </CardHeader>
                        <CardContent>
                            {topic.configs && Object.keys(topic.configs).length > 0 ? (
                                <Tabs defaultValue="table" className="space-y-4">
                                    <TabsList>
                                        <TabsTrigger value="table">Table View</TabsTrigger>
                                        <TabsTrigger value="json">JSON View</TabsTrigger>
                                    </TabsList>

                                    <TabsContent value="table">
                                        <div className="border rounded-lg overflow-auto max-h-[600px]">
                                            <Table>
                                                <TableHeader>
                                                    <TableRow>
                                                        <TableHead className="w-1/3">Property</TableHead>
                                                        <TableHead>Value</TableHead>
                                                    </TableRow>
                                                </TableHeader>
                                                <TableBody>
                                                    {Object.entries(topic.configs).map(([key, value]) => (
                                                        <TableRow key={key}>
                                                            <TableCell
                                                                className="font-mono text-sm font-medium">{key}</TableCell>
                                                            <TableCell>
                                                                <pre
                                                                    className="font-mono text-xs bg-muted p-2 rounded border overflow-auto">
                                                                    {JSON.stringify(value, null, 2)}
                                                                </pre>
                                                            </TableCell>
                                                        </TableRow>
                                                    ))}
                                                </TableBody>
                                            </Table>
                                        </div>
                                    </TabsContent>

                                    <TabsContent value="json">
                                        <div className="border rounded-lg overflow-auto max-h-[600px] bg-muted/30">
                                            <pre className="p-4 font-mono text-sm">
                                                {JSON.stringify(topic.configs, null, 2)}
                                            </pre>
                                        </div>
                                    </TabsContent>
                                </Tabs>
                            ) : (
                                <p className="text-muted-foreground text-center py-4">
                                    No configuration settings available
                                </p>
                            )}
                        </CardContent>
                    </Card>
                </TabsContent>
            </Tabs>
        </div>
    );
}
