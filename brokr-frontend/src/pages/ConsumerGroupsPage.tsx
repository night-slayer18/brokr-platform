import {Link, useParams} from 'react-router-dom';
import {GET_CONSUMER_GROUPS} from '@/graphql/queries';
import type {GetConsumerGroupsQuery} from '@/graphql/types';
import {useGraphQLQuery} from '@/hooks/useGraphQLQuery';
import {Card, CardContent, CardDescription, CardHeader, CardTitle} from '@/components/ui/card';
import {Button} from '@/components/ui/button';
import {Badge} from '@/components/ui/badge';
import {Skeleton} from '@/components/ui/skeleton';
import {Table, TableBody, TableCell, TableHead, TableHeader, TableRow} from '@/components/ui/table';
import {RefreshCw, Users} from 'lucide-react';
import {useState} from 'react';
import {ResetOffsetForm} from '@/components/consumer-groups/ResetOffsetForm';
import {useAuth} from '@/hooks/useAuth';

export default function ConsumerGroupsPage() {
    const {clusterId} = useParams<{ clusterId: string }>();
    const {canManageTopics} = useAuth();
    const [isResetOffsetFormOpen, setIsResetOffsetFormOpen] = useState(false);
    const [selectedGroup, setSelectedGroup] = useState<string | undefined>(undefined);

    const {data, isLoading: loading, error, refetch} = useGraphQLQuery<GetConsumerGroupsQuery, {clusterId: string}>(GET_CONSUMER_GROUPS, 
        clusterId ? {clusterId} : undefined,
        {
            enabled: !!clusterId,
        }
    );

    const handleResetOffsetClick = (groupId: string) => {
        setSelectedGroup(groupId);
        setIsResetOffsetFormOpen(true);
    };

    if (!clusterId) {
        return <div className="text-destructive">Cluster ID is missing. Please select a cluster.</div>;
    }

    if (loading) {
        return (
            <div className="space-y-6">
                <Skeleton className="h-10 w-64"/>
                <Skeleton className="h-4 w-96"/>
                <div className="grid gap-4">
                    {[1, 2, 3, 4, 5].map((i) => (
                        <Skeleton key={i} className="h-20"/>
                    ))}
                </div>
            </div>
        );
    }

    if (error) {
        return <div className="text-destructive">Error loading consumer groups: {error.message}</div>;
    }

    const consumerGroups = data?.consumerGroups || [];

    return (
        <div className="space-y-6">
            <div className="flex items-center justify-between">
                <div>
                    <h2 className="text-4xl font-bold tracking-tight bg-linear-to-r from-primary to-primary/80 bg-clip-text text-transparent">
                        Consumer Groups
                    </h2>
                    <p className="text-muted-foreground mt-2">
                        Manage consumer groups for cluster <span className="font-mono">{clusterId}</span>
                    </p>
                </div>
            </div>

            {consumerGroups.length === 0 ? (
                <Card className="border-dashed border-2 border-primary/30 bg-card/30">
                    <CardContent className="flex flex-col items-center justify-center py-16">
                        <div className="relative mb-6">
                            <div className="absolute inset-0 bg-primary/20 rounded-full blur-2xl"></div>
                            <Users className="relative h-16 w-16 text-primary"/>
                        </div>
                        <h3 className="text-xl font-semibold mb-2 text-foreground">No consumer groups found</h3>
                        <p className="text-muted-foreground text-center max-w-md mb-6">
                            There are no active consumer groups in this Kafka cluster.
                        </p>
                    </CardContent>
                </Card>
            ) : (
                <Card>
                    <CardHeader>
                        <CardTitle>All Consumer Groups</CardTitle>
                        <CardDescription>A list of all consumer groups in this cluster.</CardDescription>
                    </CardHeader>
                    <CardContent className="p-0">
                        <Table>
                            <TableHeader>
                                <TableRow>
                                    <TableHead>Group ID</TableHead>
                                    <TableHead>State</TableHead>
                                    <TableHead>Coordinator</TableHead>
                                    <TableHead>Members</TableHead>
                                    <TableHead>Topics Consuming</TableHead>
                                    {canManageTopics() && <TableHead className="text-right">Actions</TableHead>}
                                </TableRow>
                            </TableHeader>
                            <TableBody>
                                {consumerGroups.map((group) => (
                                    <TableRow key={group.groupId}>
                                        <TableCell className="font-medium">
                                            <Link to={`/clusters/${clusterId}/consumer-groups/${group.groupId}`}
                                                  className="text-primary hover:underline">
                                                {group.groupId}
                                            </Link>
                                        </TableCell>
                                        <TableCell><Badge>{group.state}</Badge></TableCell>
                                        <TableCell>{group.coordinator || 'N/A'}</TableCell>
                                        <TableCell>{group.members.length}</TableCell>
                                        <TableCell>
                                            {Object.keys(group.topicOffsets || {}).map(topicName => (
                                                <Badge key={topicName} variant="secondary" className="mr-1 mb-1">
                                                    {topicName}
                                                </Badge>
                                            ))}
                                        </TableCell>
                                        {canManageTopics() && (
                                            <TableCell className="text-right">
                                                <Button
                                                    variant="outline"
                                                    size="sm"
                                                    onClick={() => handleResetOffsetClick(group.groupId)}
                                                >
                                                    <RefreshCw className="h-4 w-4"/>
                                                </Button>
                                            </TableCell>
                                        )}
                                    </TableRow>
                                ))}
                            </TableBody>
                        </Table>
                    </CardContent>
                </Card>
            )}

            {selectedGroup && (
                <ResetOffsetForm
                    clusterId={clusterId!}
                    groupId={selectedGroup}
                    isOpen={isResetOffsetFormOpen}
                    onOpenChange={setIsResetOffsetFormOpen}
                    onOffsetReset={refetch}
                />
            )}
        </div>
    );
}
