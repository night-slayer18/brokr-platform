import {Link, useParams} from 'react-router-dom';
import {GET_CONSUMER_GROUP} from '@/graphql/queries';
import type {GetConsumerGroupQuery} from '@/graphql/types';
import {useGraphQLQuery} from '@/hooks/useGraphQLQuery';
import {Card, CardContent, CardDescription, CardHeader, CardTitle} from '@/components/ui/card';
import {Skeleton} from '@/components/ui/skeleton';
import {Badge} from '@/components/ui/badge';
import {Table, TableBody, TableCell, TableHead, TableHeader, TableRow} from '@/components/ui/table';
import {Tabs, TabsContent, TabsList, TabsTrigger} from '@/components/ui/tabs';
import {formatNumber} from '@/lib/utils';
import {useState} from 'react';
import {ResetOffsetForm} from '@/components/consumer-groups/ResetOffsetForm';
import {Button} from '@/components/ui/button';
import {RefreshCw} from 'lucide-react';
import {useAuth} from '@/hooks/useAuth';
import {ConsumerGroupMetricsChart} from '@/components/metrics/ConsumerGroupMetricsChart';
import {TimeRangeSelector} from '@/components/metrics/TimeRangeSelector';

export default function ConsumerGroupDetailPage() {
    const {clusterId, groupId} = useParams<{ clusterId: string; groupId: string }>();
    const {canManageTopics} = useAuth();
    const [isResetOffsetFormOpen, setIsResetOffsetFormOpen] = useState(false);
    const [timeRange, setTimeRange] = useState(() => {
        const endTime = Date.now();
        const startTime = endTime - (24 * 60 * 60 * 1000); // Last 24 hours
        return { startTime, endTime };
    });

    const {
        data,
        isLoading: loading,
        error,
        refetch
    } = useGraphQLQuery<GetConsumerGroupQuery, {clusterId: string; groupId: string}>(GET_CONSUMER_GROUP, 
        clusterId && groupId ? {clusterId, groupId} : undefined,
        {
            enabled: !!clusterId && !!groupId,
        }
    );

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
        return <div className="text-destructive">Error loading consumer group details: {error.message}</div>;
    }

    const consumerGroup = data?.consumerGroup;

    if (!consumerGroup) {
        return <div>Consumer group not found.</div>;
    }

    return (
        <div className="space-y-6">
            <div className="flex items-center justify-between">
                <div>
                    <h2 className="text-3xl font-bold tracking-tight flex items-center gap-2">
                        {consumerGroup.groupId}
                        <Badge>{consumerGroup.state}</Badge>
                    </h2>
                    <p className="text-muted-foreground">Details for consumer group <span
                        className="font-mono">{consumerGroup.groupId}</span> in cluster <span
                        className="font-mono">{clusterId}</span></p>
                </div>
                {canManageTopics() && (
                    <Button onClick={() => setIsResetOffsetFormOpen(true)}>
                        <RefreshCw className="mr-2 h-4 w-4"/>
                        Reset Offset
                    </Button>
                )}
            </div>

            <div className="grid gap-4 md:grid-cols-3">
                <Card>
                    <CardHeader>
                        <CardTitle>State</CardTitle>
                    </CardHeader>
                    <CardContent>
                        <p className="text-2xl font-bold">{consumerGroup.state}</p>
                    </CardContent>
                </Card>
                <Card>
                    <CardHeader>
                        <CardTitle>Coordinator</CardTitle>
                    </CardHeader>
                    <CardContent>
                        <p className="text-2xl font-bold">{consumerGroup.coordinator || 'N/A'}</p>
                    </CardContent>
                </Card>
                <Card>
                    <CardHeader>
                        <CardTitle>Members</CardTitle>
                    </CardHeader>
                    <CardContent>
                        <p className="text-2xl font-bold">{consumerGroup.members.length}</p>
                    </CardContent>
                </Card>
            </div>

            <Tabs defaultValue="details" className="space-y-4">
                <TabsList>
                    <TabsTrigger value="details">Details</TabsTrigger>
                    <TabsTrigger value="metrics">Metrics</TabsTrigger>
                </TabsList>

                <TabsContent value="details" className="space-y-4">
                    <Card>
                        <CardHeader>
                            <CardTitle>Topic Lag</CardTitle>
                            <CardDescription>Total lag per topic for this consumer group.</CardDescription>
                        </CardHeader>
                        <CardContent>
                            <Table>
                                <TableHeader>
                                    <TableRow>
                                        <TableHead>Topic</TableHead>
                                        <TableHead className="text-right">Total Lag</TableHead>
                                    </TableRow>
                                </TableHeader>
                                <TableBody>
                                    {consumerGroup.topicOffsets && Object.entries(consumerGroup.topicOffsets).map(([topic, lag]) => (
                                        <TableRow key={topic}>
                                            <TableCell>
                                                <Link to={`/clusters/${clusterId}/topics/${topic}`}
                                                      className="text-primary hover:underline">
                                                    {topic}
                                                </Link>
                                            </TableCell>
                                            <TableCell className="text-right">{formatNumber(lag as number)}</TableCell>
                                        </TableRow>
                                    ))}
                                </TableBody>
                            </Table>
                        </CardContent>
                    </Card>

                    <Card>
                        <CardHeader>
                            <CardTitle>Members</CardTitle>
                            <CardDescription>Active members of this consumer group.</CardDescription>
                        </CardHeader>
                        <CardContent>
                            <Table>
                                <TableHeader>
                                    <TableRow>
                                        <TableHead>Member ID</TableHead>
                                        <TableHead>Client ID</TableHead>
                                        <TableHead>Host</TableHead>
                                        <TableHead>Assigned Partitions</TableHead>
                                    </TableRow>
                                </TableHeader>
                                <TableBody>
                                    {consumerGroup.members.map(member => (
                                        <TableRow key={member.memberId}>
                                            <TableCell>{member.memberId}</TableCell>
                                            <TableCell>{member.clientId}</TableCell>
                                            <TableCell>{member.host}</TableCell>
                                            <TableCell>
                                                {member.assignment.map(assignment => (
                                                    <Badge key={`${assignment.topic}-${assignment.partition}`}
                                                           variant="secondary" className="mr-1 mb-1">
                                                        {assignment.topic}[{assignment.partition}]
                                                    </Badge>
                                                ))}
                                            </TableCell>
                                        </TableRow>
                                    ))}
                                </TableBody>
                            </Table>
                        </CardContent>
                    </Card>
                </TabsContent>

                <TabsContent value="metrics" className="space-y-4">
                    {clusterId && groupId && (
                        <>
                            <TimeRangeSelector 
                                onTimeRangeChange={setTimeRange}
                            />
                            <ConsumerGroupMetricsChart
                                clusterId={clusterId}
                                consumerGroupId={groupId}
                                timeRange={timeRange}
                            />
                        </>
                    )}
                </TabsContent>
            </Tabs>

            <ResetOffsetForm
                clusterId={clusterId!}
                groupId={groupId!}
                isOpen={isResetOffsetFormOpen}
                onOpenChange={setIsResetOffsetFormOpen}
                onOffsetReset={refetch}
            />
        </div>
    );
}

