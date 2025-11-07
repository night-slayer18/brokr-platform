import {useQuery} from '@apollo/client/react';
import {useParams} from 'react-router-dom';
import {GET_TOPIC} from '@/graphql/queries';
import type {GetTopicQuery, GetTopicVariables} from '@/graphql/types';
import {Card, CardContent, CardDescription, CardHeader, CardTitle} from '@/components/ui/card';
import {Skeleton} from '@/components/ui/skeleton';
import {Badge} from '@/components/ui/badge';
import {Table, TableBody, TableCell, TableHead, TableHeader, TableRow} from '@/components/ui/table';
import {formatNumber} from '@/lib/utils';

export default function TopicDetailPage() {
    const {clusterId, topicName} = useParams<{ clusterId: string; topicName: string }>();
    const {data, loading, error} = useQuery<GetTopicQuery, GetTopicVariables>(GET_TOPIC, {
        variables: {clusterId: clusterId!, name: topicName!},
    });

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

    const topic = data?.topic;

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
        </div>
    );
}
