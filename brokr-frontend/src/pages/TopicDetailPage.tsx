import {useQuery, useLazyQuery} from '@apollo/client/react';
import {useParams} from 'react-router-dom';
import {GET_TOPIC, GET_MESSAGES} from '@/graphql/queries';
import type {GetTopicQuery, GetTopicVariables, GetMessagesQuery, GetMessagesVariables} from '@/graphql/types';
import {Card, CardContent, CardDescription, CardHeader, CardTitle} from '@/components/ui/card';
import {Skeleton} from '@/components/ui/skeleton';
import {Badge} from '@/components/ui/badge';
import {Table, TableBody, TableCell, TableHead, TableHeader, TableRow} from '@/components/ui/table';
import {formatDate, formatRelativeTime} from '@/lib/formatters';
import {formatNumber} from '@/lib/utils';
import {useState} from "react";
import {Select, SelectContent, SelectItem, SelectTrigger, SelectValue} from "@/components/ui/select";
import {Button} from "@/components/ui/button";
import {Label} from "@/components/ui/label";
import {Loader2} from "lucide-react";
import {toast} from "sonner";
import Editor from "@monaco-editor/react";
import type {Message} from "@/types";
import {
    Pagination,
    PaginationContent,
    PaginationItem,
    PaginationLink,
    PaginationNext,
    PaginationPrevious
} from "@/components/ui/pagination";

const MESSAGES_PER_PAGE = 10;

export default function TopicDetailPage() {
    const {clusterId, topicName} = useParams<{ clusterId: string; topicName: string }>();
    const [selectedPartition, setSelectedPartition] = useState<string>('');
    const [messageLimit, setMessageLimit] = useState<string>('100');
    const [currentPage, setCurrentPage] = useState(1);

    const {data, loading, error} = useQuery<GetTopicQuery, GetTopicVariables>(GET_TOPIC, {
        variables: {clusterId: clusterId!, name: topicName!},
    });

    const [getMessages, {data: messagesData, loading: messagesLoading, error: messagesError}] = useLazyQuery<GetMessagesQuery, GetMessagesVariables>(GET_MESSAGES);

    const topic = data?.topic;
    const messages = messagesData?.messages || [];

    const handleFetchMessages = () => {
        if (!clusterId || !topicName) {
            toast.error("Cluster ID or Topic Name is missing.");
            return;
        }

        const partitions = selectedPartition ? [parseInt(selectedPartition)] : topic?.partitionsInfo?.map(p => p.id);

        getMessages({
            variables: {
                clusterId,
                input: {
                    topic: topicName,
                    partitions: partitions,
                    limit: parseInt(messageLimit),
                },
            },
        });
    };

    const paginatedMessages = messages.slice(
        (currentPage - 1) * MESSAGES_PER_PAGE,
        currentPage * MESSAGES_PER_PAGE
    );

    const totalPages = Math.ceil(messages.length / MESSAGES_PER_PAGE);

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
        return <div className="text-destructive">Error loading topic details: {error.message}</div>;
    }

    if (!topic) {
        return <div>Topic not found.</div>;
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

            <Card>
                <CardHeader>
                    <CardTitle>Partitions</CardTitle>
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
                            {topic.partitionsInfo?.map((p) => (
                                <TableRow key={p.id}>
                                    <TableCell>{p.id}</TableCell>
                                    <TableCell>{p.leader}</TableCell>
                                    <TableCell>{p.replicas.join(', ')}</TableCell>
                                    <TableCell>{p.isr.join(', ')}</TableCell>
                                    <TableCell>{formatNumber(p.earliestOffset)}</TableCell>
                                    <TableCell>{formatNumber(p.latestOffset)}</TableCell>
                                    <TableCell>{formatNumber(p.size)} bytes</TableCell>
                                </TableRow>
                            ))}
                        </TableBody>
                    </Table>
                </CardContent>
            </Card>

            <Card>
                <CardHeader>
                    <CardTitle>Configuration</CardTitle>
                    <CardDescription>Topic-level configurations.</CardDescription>
                </CardHeader>
                <CardContent>
                    <div className="space-y-2">
                        {topic.configs && Object.entries(topic.configs).map(([key, value]) => (
                            <div key={key}
                                 className="flex justify-between items-center p-2 rounded-lg bg-secondary/30 text-sm">
                                <span className="text-muted-foreground">{key}:</span>
                                <span className="font-mono text-foreground">{String(value)}</span>
                            </div>
                        ))}
                    </div>
                </CardContent>
            </Card>

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
                            <Select value={selectedPartition} onValueChange={setSelectedPartition}>
                                <SelectTrigger id="partitionFilter">
                                    <SelectValue placeholder="All Partitions"/>
                                </SelectTrigger>
                                <SelectContent>
                                    <SelectItem value="">All Partitions</SelectItem>
                                    {topic.partitionsInfo?.map(p => (
                                        <SelectItem key={p.id} value={String(p.id)}>{p.id}</SelectItem>
                                    ))}
                                </SelectContent>
                            </Select>
                        </div>
                        <Button onClick={handleFetchMessages} disabled={messagesLoading}>
                            {messagesLoading && <Loader2 className="mr-2 h-4 w-4 animate-spin"/>}
                            Fetch Messages
                        </Button>
                    </div>

                    {messagesError && (
                        <div className="text-destructive mb-4">Error fetching messages: {messagesError.message}</div>
                    )}

                    {messages.length > 0 && (
                        <>
                            <Table>
                                <TableHeader>
                                    <TableRow>
                                        <TableHead>Partition</TableHead>
                                        <TableHead>Offset</TableHead>
                                        <TableHead>Timestamp</TableHead>
                                        <TableHead>Key</TableHead>
                                        <TableHead>Value</TableHead>
                                    </TableRow>
                                </TableHeader>
                                <TableBody>
                                    {paginatedMessages.map((message: Message, index: number) => (
                                        <TableRow key={`${message.partition}-${message.offset}-${index}`}>
                                            <TableCell>{message.partition}</TableCell>
                                            <TableCell>{formatNumber(message.offset)}</TableCell>
                                            <TableCell>
                                                <span title={formatDate(message.timestamp)}>
                                                    {formatRelativeTime(message.timestamp)}
                                                </span>
                                            </TableCell>
                                            <TableCell>
                                                <Editor
                                                    height="50px"
                                                    language="json"
                                                    value={message.key || ''}
                                                    options={{
                                                        readOnly: true,
                                                        minimap: {enabled: false},
                                                        wordWrap: "on",
                                                        lineNumbers: "off",
                                                        folding: false,
                                                        scrollBeyondLastLine: false,
                                                        overviewRulerLanes: 0,
                                                        scrollbar: {vertical: "hidden", horizontal: "hidden"},
                                                        padding: {top: 0, bottom: 0},
                                                    }}
                                                />
                                            </TableCell>
                                            <TableCell>
                                                <Editor
                                                    height="50px"
                                                    language="json"
                                                    value={message.value || ''}
                                                    options={{
                                                        readOnly: true,
                                                        minimap: {enabled: false},
                                                        wordWrap: "on",
                                                        lineNumbers: "off",
                                                        folding: false,
                                                        scrollBeyondLastLine: false,
                                                        overviewRulerLanes: 0,
                                                        scrollbar: {vertical: "hidden", horizontal: "hidden"},
                                                        padding: {top: 0, bottom: 0},
                                                    }}
                                                />
                                            </TableCell>
                                        </TableRow>
                                    ))}
                                </TableBody>
                            </Table>
                            <Pagination className="mt-4">
                                <PaginationContent>
                                    <PaginationItem>
                                        <PaginationPrevious
                                            onClick={() => setCurrentPage(prev => Math.max(1, prev - 1))}
                                            disabled={currentPage === 1}
                                        />
                                    </PaginationItem>
                                    {Array.from({length: totalPages}, (_, i) => (
                                        <PaginationItem key={i}>
                                            <PaginationLink
                                                onClick={() => setCurrentPage(i + 1)}
                                                isActive={currentPage === i + 1}
                                            >
                                                {i + 1}
                                            </PaginationLink>
                                        </PaginationItem>
                                    ))}
                                    <PaginationItem>
                                        <PaginationNext
                                            onClick={() => setCurrentPage(prev => Math.min(totalPages, prev + 1))}
                                            disabled={currentPage === totalPages}
                                        />
                                    </PaginationItem>
                                </PaginationContent>
                            </Pagination>
                        </>
                    )}
                    {messages.length === 0 && !messagesLoading && (
                        <p className="text-muted-foreground">No messages fetched yet. Use the controls above to fetch messages.</p>
                    )}
                </CardContent>
            </Card>
        </div>
    );
}
