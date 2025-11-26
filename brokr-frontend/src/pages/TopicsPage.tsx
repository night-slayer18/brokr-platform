import {useState, useMemo} from 'react';
import {useNavigate, useParams} from 'react-router-dom';
import {GET_TOPICS} from '@/graphql/queries';
import {DELETE_TOPIC_MUTATION} from '@/graphql/mutations';
import type {DeleteTopicMutation, GetTopicsQuery, GetTopicsVariables} from '@/graphql/types';
import type {Topic} from '@/types';
import {Card, CardContent, CardDescription, CardHeader, CardTitle} from '@/components/ui/card';
import {Button} from '@/components/ui/button';
import {Badge} from '@/components/ui/badge';
import {Skeleton} from '@/components/ui/skeleton';
import {Input} from '@/components/ui/input';
import {Eye, MessageSquareText, Plus, Trash2, Search} from 'lucide-react';
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
import {
    Pagination,
    PaginationContent,
    PaginationItem,
    PaginationLink,
    PaginationNext,
    PaginationPrevious
} from "@/components/ui/pagination";
import {useGraphQLQuery} from '@/hooks/useGraphQLQuery';
import {useGraphQLMutation} from '@/hooks/useGraphQLMutation';
import {useQueryClient, keepPreviousData} from '@tanstack/react-query';
import {useDebounce} from '@/hooks/useDebounce';

export default function TopicsPage() {
    const {clusterId} = useParams<{ clusterId: string }>();
    const navigate = useNavigate();
    const {canManageTopics} = useAuth();
    const [isCreateTopicFormOpen, setIsCreateTopicFormOpen] = useState(false);
    const [isDeleteDialogOpen, setIsDeleteDialogOpen] = useState(false);
    const [topicToDelete, setTopicToDelete] = useState<string | null>(null);
    
    // Pagination and Search State
    const [page, setPage] = useState(1);
    const [pageSize] = useState(10);
    const [searchQuery, setSearchQuery] = useState('');
    const debouncedSearch = useDebounce(searchQuery, 500);

    const queryClient = useQueryClient();
    
    // Reset page when search changes
    useMemo(() => {
        setPage(1);
    }, [debouncedSearch]);

    const {data, isLoading: loading, error, refetch} = useGraphQLQuery<GetTopicsQuery, GetTopicsVariables>(GET_TOPICS, 
        clusterId ? {
            clusterId,
            page: page - 1,
            size: pageSize,
            search: debouncedSearch
        } : undefined,
        {
            enabled: !!clusterId,
            placeholderData: keepPreviousData,
        }
    );

    const topics = data?.topics.content || [];
    const totalElements = data?.topics.totalElements || 0;
    const totalPages = data?.topics.totalPages || 0;

    const {mutate: deleteTopic} = useGraphQLMutation<DeleteTopicMutation, {clusterId: string; name: string}>(DELETE_TOPIC_MUTATION);

    const openDeleteDialog = (topicName: string) => {
        setTopicToDelete(topicName);
        setIsDeleteDialogOpen(true);
    };

    const handleDeleteTopic = async () => {
        if (!topicToDelete || !clusterId) return;

        deleteTopic(
            {clusterId, name: topicToDelete},
            {
                onSuccess: () => {
                    toast.success(`Topic "${topicToDelete}" deleted successfully`);
                    queryClient.invalidateQueries({queryKey: ['graphql', GET_TOPICS]});
                    setIsDeleteDialogOpen(false);
                    setTopicToDelete(null);
                },
                onError: (err: Error) => {
                    toast.error(err.message || 'Failed to delete topic');
                },
            }
        );
    };

    if (!clusterId) {
        return <div className="text-destructive">Cluster ID is missing. Please select a cluster.</div>;
    }

    if (loading && !data) {
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

    return (
        <div className="space-y-6">
            <div className="flex items-center justify-between">
                <div>
                    <h2 className="text-4xl font-bold tracking-tight bg-linear-to-r from-primary to-primary/80 bg-clip-text text-transparent">
                        Topics
                    </h2>
                    <p className="text-muted-foreground mt-2">
                        Manage topics for cluster <span className="font-mono">{clusterId}</span>
                    </p>
                </div>
                {canManageTopics() && (
                    <Button
                        onClick={() => setIsCreateTopicFormOpen(true)}
                        className="bg-linear-to-r from-primary to-primary/80 hover:from-primary/90 hover:to-primary/70 shadow-lg shadow-primary/50"
                    >
                        <Plus className="mr-2 h-4 w-4"/>
                        Create Topic
                    </Button>
                )}
            </div>

            {topics.length === 0 && !searchQuery ? (
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
                                className="bg-linear-to-r from-primary to-primary/80 hover:from-primary/90 hover:to-primary/70 shadow-lg shadow-primary/50"
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
                        <div className="flex items-center justify-between">
                            <div>
                                <CardTitle>All Topics</CardTitle>
                                <CardDescription>
                                    {searchQuery ? `Found ${totalElements} topics matching "${searchQuery}"` : `A list of all ${totalElements} topics in this cluster.`}
                                </CardDescription>
                            </div>
                            <div className="relative w-64">
                                <Search className="absolute left-3 top-1/2 transform -translate-y-1/2 h-4 w-4 text-muted-foreground" />
                                <Input
                                    type="text"
                                    placeholder="Search topics..."
                                    value={searchQuery}
                                    onChange={(e) => setSearchQuery(e.target.value)}
                                    className="pl-9"
                                />
                            </div>
                        </div>
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
                                {topics.length === 0 ? (
                                    <TableRow>
                                        <TableCell colSpan={5} className="text-center py-8 text-muted-foreground">
                                            {searchQuery ? `No topics found matching "${searchQuery}"` : 'No topics found'}
                                        </TableCell>
                                    </TableRow>
                                ) : (
                                    topics.map((topic: Topic) => (
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
                                    ))
                                )}
                            </TableBody>
                        </Table>
                        
                        {/* Pagination Controls */}
                        {totalPages > 1 && (
                            <div className="py-4 border-t">
                                <Pagination>
                                    <PaginationContent>
                                        <PaginationItem>
                                            <PaginationPrevious
                                                onClick={() => setPage(prev => Math.max(1, prev - 1))}
                                                disabled={page === 1 || loading}
                                            />
                                        </PaginationItem>

                                        {/* Show first page */}
                                        {totalPages > 0 && (
                                            <PaginationItem>
                                                <PaginationLink
                                                    onClick={() => setPage(1)}
                                                    isActive={page === 1}
                                                >
                                                    1
                                                </PaginationLink>
                                            </PaginationItem>
                                        )}

                                        {/* Show ellipsis if current page is far from start */}
                                        {page > 3 && totalPages > 5 && (
                                            <PaginationItem>
                                                <span className="px-4">...</span>
                                            </PaginationItem>
                                        )}

                                        {/* Show pages around current page */}
                                        {Array.from({length: totalPages}, (_, i) => i + 1)
                                            .filter(p => {
                                                if (totalPages <= 5) return p > 1 && p < totalPages;
                                                return p > 1 && p < totalPages && Math.abs(p - page) <= 1;
                                            })
                                            .map(p => (
                                                <PaginationItem key={p}>
                                                    <PaginationLink
                                                        onClick={() => setPage(p)}
                                                        isActive={page === p}
                                                    >
                                                        {p}
                                                    </PaginationLink>
                                                </PaginationItem>
                                            ))
                                        }

                                        {/* Show ellipsis if current page is far from end */}
                                        {page < totalPages - 2 && totalPages > 5 && (
                                            <PaginationItem>
                                                <span className="px-4">...</span>
                                            </PaginationItem>
                                        )}

                                        {/* Show last page */}
                                        {totalPages > 1 && (
                                            <PaginationItem>
                                                <PaginationLink
                                                    onClick={() => setPage(totalPages)}
                                                    isActive={page === totalPages}
                                                >
                                                    {totalPages}
                                                </PaginationLink>
                                            </PaginationItem>
                                        )}

                                        <PaginationItem>
                                            <PaginationNext
                                                onClick={() => setPage(prev => Math.min(totalPages, prev + 1))}
                                                disabled={page === totalPages || loading}
                                            />
                                        </PaginationItem>
                                    </PaginationContent>
                                </Pagination>
                                <div className="text-center text-sm text-muted-foreground mt-2">
                                    Page {page} of {totalPages}
                                </div>
                            </div>
                        )}
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
