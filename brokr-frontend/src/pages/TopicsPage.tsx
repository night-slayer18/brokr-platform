import {useState} from 'react';
import {useMutation, useQuery} from '@apollo/client/react';
import {useNavigate, useParams} from 'react-router-dom';
import {GET_TOPICS} from '@/graphql/queries';
import {DELETE_TOPIC_MUTATION} from '@/graphql/mutations';
import type {DeleteTopicMutation, DeleteTopicMutationVariables, GetTopicsQuery} from '@/graphql/types';
import type {Topic} from '@/types';
import {Card, CardContent, CardDescription, CardHeader, CardTitle} from '@/components/ui/card';
import {Button} from '@/components/ui/button';
import {Badge} from '@/components/ui/badge';
import {Skeleton} from '@/components/ui/skeleton';
import {Eye, MessageSquareText, Plus, Trash2} from 'lucide-react';
import {toast} from 'sonner';
import {Table, TableBody, TableCell, TableHead, TableHeader, TableRow} from '@/components/ui/table';
import {CreateTopicForm} from '@/components/topics/CreateTopicForm';
import {useAuth} from '@/hooks/useAuth';
import {
    Dialog,
    DialogContent,
    DialogDescription,
    DialogFooter,
    DialogHeader,
    DialogTitle
} from "@/components/ui/dialog";

export default function TopicsPage() {
    const {clusterId} = useParams<{ clusterId: string }>();
    const navigate = useNavigate();
    const {canManageTopics} = useAuth();
    const [isCreateTopicFormOpen, setIsCreateTopicFormOpen] = useState(false);
    const [isDeleteDialogOpen, setIsDeleteDialogOpen] = useState(false);
    const [topicToDelete, setTopicToDelete] = useState<string | null>(null);

    const {data, loading, error, refetch} = useQuery<GetTopicsQuery>(GET_TOPICS, {
        variables: {clusterId: clusterId!},
        skip: !clusterId,
    });

    const [deleteTopic] = useMutation<DeleteTopicMutation, DeleteTopicMutationVariables>(DELETE_TOPIC_MUTATION);

    const openDeleteDialog = (topicName: string) => {
        setTopicToDelete(topicName);
        setIsDeleteDialogOpen(true);
    };

    const handleDeleteTopic = async () => {
        if (!topicToDelete) return;

        try {
            await deleteTopic({variables: {clusterId: clusterId!, name: topicToDelete}});
            toast.success(`Topic "${topicToDelete}" deleted successfully`);
            refetch();
        } catch (err: any) {
            toast.error(err.message || 'Failed to delete topic');
        } finally {
            setIsDeleteDialogOpen(false);
            setTopicToDelete(null);
        }
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
        return <div className="text-destructive">Error loading topics: {error.message}</div>;
    }

    const topics = data?.topics || [];

    return (
        <div className="space-y-6">
            <div className="flex items-center justify-between">
                <div>
                    <h2 className="text-4xl font-bold tracking-tight bg-gradient-to-r from-primary to-primary/80 bg-clip-text text-transparent">
                        Topics
                    </h2>
                    <p className="text-muted-foreground mt-2">
                        Manage topics for cluster <span className="font-mono">{clusterId}</span>
                    </p>
                </div>
                {canManageTopics() && (
                    <Button
                        onClick={() => setIsCreateTopicFormOpen(true)}
                        className="bg-gradient-to-r from-primary to-primary/80 hover:from-primary/90 hover:to-primary/70 shadow-lg shadow-primary/50"
                    >
                        <Plus className="mr-2 h-4 w-4"/>
                        Create Topic
                    </Button>
                )}
            </div>

            {topics.length === 0 ? (
                <Card className="border-dashed border-2 border-primary/30 bg-card/30">
                    <CardContent className="flex flex-col items-center justify-center py-16">
                        <div className="relative mb-6">
                            <div className="absolute inset-0 bg-primary/20 rounded-full blur-2xl"></div>
                            <MessageSquareText className="relative h-16 w-16 text-primary"/>
                        </div>
                        <h3 className="text-xl font-semibold mb-2 text-foreground">No topics found</h3>
                        <p className="text-muted-foreground text-center max-w-md mb-6">
                            Get started by creating your first topic in this Kafka cluster.
                        </p>
                        {canManageTopics() && (
                            <Button
                                onClick={() => setIsCreateTopicFormOpen(true)}
                                className="bg-gradient-to-r from-primary to-primary/80 hover:from-primary/90 hover:to-primary/70 shadow-lg shadow-primary/50"
                            >
                                <Plus className="mr-2 h-4 w-4"/>
                                Create Your First Topic
                            </Button>
                        )}
                    </CardContent>
                </Card>
            ) : (
                <Card>
                    <CardHeader>
                        <CardTitle>All Topics</CardTitle>
                        <CardDescription>A list of all topics in this cluster.</CardDescription>
                    </CardHeader>
                    <CardContent className="p-0">
                        <Table>
                            <TableHeader>
                                <TableRow>
                                    <TableHead>Name</TableHead>
                                    <TableHead>Partitions</TableHead>
                                    <TableHead>Replication Factor</TableHead>
                                    <TableHead>Internal</TableHead>
                                    <TableHead className="text-right">Actions</TableHead>
                                </TableRow>
                            </TableHeader>
                            <TableBody>
                                {topics.map((topic: Topic) => (
                                    <TableRow key={topic.name}>
                                        <TableCell className="font-medium">{topic.name}</TableCell>
                                        <TableCell>{topic.partitions}</TableCell>
                                        <TableCell>{topic.replicationFactor}</TableCell>
                                        <TableCell>
                                            <Badge variant={topic.isInternal ? 'secondary' : 'outline'}>
                                                {topic.isInternal ? 'Yes' : 'No'}
                                            </Badge>
                                        </TableCell>
                                        <TableCell className="text-right">
                                            <div className="flex justify-end gap-2">
                                                <Button
                                                    variant="outline"
                                                    size="sm"
                                                    onClick={() => navigate(`/clusters/${clusterId}/topics/${encodeURIComponent(topic.name)}`)}
                                                >
                                                    <Eye className="h-4 w-4"/>
                                                </Button>
                                                {canManageTopics() && (
                                                    <Button
                                                        variant="destructive"
                                                        size="sm"
                                                        onClick={() => openDeleteDialog(topic.name)}
                                                    >
                                                        <Trash2 className="h-4 w-4"/>
                                                    </Button>
                                                )}
                                            </div>
                                        </TableCell>
                                    </TableRow>
                                ))}
                            </TableBody>
                        </Table>
                    </CardContent>
                </Card>
            )}

            <CreateTopicForm
                clusterId={clusterId!}
                isOpen={isCreateTopicFormOpen}
                onOpenChange={setIsCreateTopicFormOpen}
                onTopicCreated={refetch}
            />

            <Dialog open={isDeleteDialogOpen} onOpenChange={setIsDeleteDialogOpen}>
                <DialogContent>
                    <DialogHeader>
                        <DialogTitle>Are you sure you want to delete this topic?</DialogTitle>
                        <DialogDescription>
                            This action cannot be undone. This will permanently delete the topic
                            <span className="font-bold"> {topicToDelete}</span> and all of its data.
                        </DialogDescription>
                    </DialogHeader>
                    <DialogFooter>
                        <Button variant="outline" onClick={() => setIsDeleteDialogOpen(false)}>
                            Cancel
                        </Button>
                        <Button variant="destructive" onClick={handleDeleteTopic}>
                            Delete Topic
                        </Button>
                    </DialogFooter>
                </DialogContent>
            </Dialog>
        </div>
    );
}
