import {Link, useParams} from 'react-router-dom';
import {GET_REPLAY_JOBS} from '@/graphql/queries';
import type {GetReplayJobsQuery} from '@/graphql/types';
import {useGraphQLQuery} from '@/hooks/useGraphQLQuery';
import {DELETE_REPLAY_MUTATION} from '@/graphql/mutations';
import type {DeleteReplayMutation} from '@/graphql/types';
import {useGraphQLMutation} from '@/hooks/useGraphQLMutation';
import {toast} from 'sonner';
import {useAuth} from '@/hooks/useAuth';
import {Card, CardContent, CardDescription, CardHeader, CardTitle} from '@/components/ui/card';
import {Button} from '@/components/ui/button';
import {Badge} from '@/components/ui/badge';
import {Skeleton} from '@/components/ui/skeleton';
import {Table, TableBody, TableCell, TableHead, TableHeader, TableRow} from '@/components/ui/table';
import {RefreshCw, Play, Plus, Eye, Loader2, Trash2} from 'lucide-react';
import {useState} from 'react';
import {
    AlertDialog,
    AlertDialogAction,
    AlertDialogCancel,
    AlertDialogContent,
    AlertDialogDescription,
    AlertDialogFooter,
    AlertDialogHeader,
    AlertDialogTitle,
} from '@/components/ui/alert-dialog';
import {ReplayJobForm} from '@/components/replay/ReplayJobForm';
import {formatDate, formatRelativeTime} from '@/lib/formatters';
import {Select, SelectContent, SelectItem, SelectTrigger, SelectValue} from '@/components/ui/select';
import type {ReplayJobStatus} from '@/types';
import {useQueryClient} from '@tanstack/react-query';
import {print} from 'graphql';

const statusColors: Record<ReplayJobStatus, string> = {
    PENDING: 'bg-yellow-500/10 text-yellow-500 border-yellow-500/20',
    RUNNING: 'bg-blue-500/10 text-blue-500 border-blue-500/20',
    COMPLETED: 'bg-green-500/10 text-green-500 border-green-500/20',
    FAILED: 'bg-red-500/10 text-red-500 border-red-500/20',
    CANCELLED: 'bg-gray-500/10 text-gray-500 border-gray-500/20',
};

export default function ReplayJobsPage() {
    const {clusterId} = useParams<{ clusterId: string }>();
    const [statusFilter, setStatusFilter] = useState<ReplayJobStatus | 'ALL'>('ALL');
    const [isFormOpen, setIsFormOpen] = useState(false);
    const [jobToDelete, setJobToDelete] = useState<string | null>(null);
    const queryClient = useQueryClient();
    const {canManageTopics} = useAuth();

    const {mutate: deleteReplay, isPending: deleteLoading} = useGraphQLMutation<
        DeleteReplayMutation,
        {id: string}
    >(DELETE_REPLAY_MUTATION);

    const {data, isLoading: loading, isFetching, error, refetch} = useGraphQLQuery<
        GetReplayJobsQuery,
        {clusterId?: string | null; status?: ReplayJobStatus | null; page?: number | null; size?: number | null}
    >(
        GET_REPLAY_JOBS,
        clusterId
            ? {
                  clusterId,
                  status: statusFilter !== 'ALL' ? statusFilter : undefined,
                  page: 0,
                  size: 50,
              }
            : undefined,
        {
            enabled: !!clusterId,
            refetchInterval: 5000, // Poll every 5 seconds for running jobs
        }
    );

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
        return <div className="text-destructive">Error loading replay jobs: {error.message}</div>;
    }

    const replayJobs = data?.replayJobs || [];
    const runningJobs = replayJobs.filter((job) => job.status === 'RUNNING' || job.status === 'PENDING');

    const handleDelete = (jobId: string) => {
        setJobToDelete(jobId);
    };

    const handleDeleteConfirm = () => {
        if (!jobToDelete) return;
        deleteReplay(
            {id: jobToDelete},
            {
                onSuccess: () => {
                    toast.success('Replay job deleted successfully');
                    setJobToDelete(null);
                    refetch();
                },
                onError: (error: unknown) => {
                    const err = error instanceof Error ? error : {message: 'Failed to delete replay job'};
                    toast.error(err.message || 'Failed to delete replay job');
                },
            }
        );
    };

    return (
        <div className="space-y-6">
            <div className="flex items-center justify-between">
                <div>
                    <h2 className="text-4xl font-bold tracking-tight bg-linear-to-r from-primary to-primary/80 bg-clip-text text-transparent">
                        Message Replay Jobs
                    </h2>
                    <p className="text-muted-foreground mt-2">
                        Manage message replay and reprocessing jobs for cluster <span className="font-mono">{clusterId}</span>
                    </p>
                </div>
                <div className="flex items-center gap-2">
                    <Button 
                        onClick={async () => {
                            if (!clusterId) return;
                            const variables = {
                                clusterId,
                                status: statusFilter !== 'ALL' ? statusFilter : undefined,
                                page: 0,
                                size: 50,
                            };
                            try {
                                // Use queryClient.refetchQueries to ensure it works even with refetchInterval
                                await queryClient.refetchQueries({
                                    queryKey: ['graphql', print(GET_REPLAY_JOBS), variables],
                                });
                            } catch (error) {
                                console.error('Failed to refetch replay jobs:', error);
                            }
                        }} 
                        disabled={loading || isFetching}
                    >
                        {(loading || isFetching) ? (
                            <Loader2 className="mr-2 h-4 w-4 animate-spin"/>
                        ) : (
                            <RefreshCw className="mr-2 h-4 w-4"/>
                        )}
                        Refresh
                    </Button>
                    <Button onClick={() => setIsFormOpen(true)}>
                        <Plus className="mr-2 h-4 w-4"/>
                        Create Replay Job
                    </Button>
                </div>
            </div>

            <div className="flex items-center gap-4">
                <Select value={statusFilter} onValueChange={(value) => setStatusFilter(value as ReplayJobStatus | 'ALL')}>
                    <SelectTrigger className="w-[180px]">
                        <SelectValue placeholder="Filter by status"/>
                    </SelectTrigger>
                    <SelectContent>
                        <SelectItem value="ALL">All Statuses</SelectItem>
                        <SelectItem value="PENDING">Pending</SelectItem>
                        <SelectItem value="RUNNING">Running</SelectItem>
                        <SelectItem value="COMPLETED">Completed</SelectItem>
                        <SelectItem value="FAILED">Failed</SelectItem>
                        <SelectItem value="CANCELLED">Cancelled</SelectItem>
                    </SelectContent>
                </Select>
                {runningJobs.length > 0 && (
                    <Badge variant="outline" className="bg-blue-500/10 text-blue-500 border-blue-500/20">
                        {runningJobs.length} {runningJobs.length === 1 ? 'job' : 'jobs'} running
                    </Badge>
                )}
            </div>

            {replayJobs.length === 0 ? (
                <Card className="border-dashed border-2 border-primary/30 bg-card/30">
                    <CardContent className="flex flex-col items-center justify-center py-16">
                        <div className="relative mb-6">
                            <div className="absolute inset-0 bg-primary/20 rounded-full blur-2xl"></div>
                            <Play className="relative h-16 w-16 text-primary"/>
                        </div>
                        <h3 className="text-xl font-semibold mb-2 text-foreground">No replay jobs found</h3>
                        <p className="text-muted-foreground text-center max-w-md mb-6">
                            {statusFilter !== 'ALL'
                                ? `No replay jobs with status "${statusFilter}" found.`
                                : 'Create your first message replay job to get started.'}
                        </p>
                        <Button onClick={() => setIsFormOpen(true)}>
                            <Plus className="mr-2 h-4 w-4"/>
                            Create Replay Job
                        </Button>
                    </CardContent>
                </Card>
            ) : (
                <Card>
                    <CardHeader>
                        <CardTitle>Replay Jobs</CardTitle>
                        <CardDescription>A list of all message replay jobs in this cluster.</CardDescription>
                    </CardHeader>
                    <CardContent className="p-0">
                        <Table>
                            <TableHeader>
                                <TableRow>
                                    <TableHead>ID</TableHead>
                                    <TableHead>Source Topic</TableHead>
                                    <TableHead>Target</TableHead>
                                    <TableHead>Status</TableHead>
                                    <TableHead>Progress</TableHead>
                                    <TableHead>Schedule</TableHead>
                                    <TableHead>Created</TableHead>
                                    <TableHead className="text-right">Actions</TableHead>
                                </TableRow>
                            </TableHeader>
                            <TableBody>
                                {replayJobs.map((job) => {
                                    const progress = job.progress;
                                    const progressPercent =
                                        progress && progress.messagesTotal
                                            ? Math.round((progress.messagesProcessed / progress.messagesTotal) * 100)
                                            : null;

                                    return (
                                        <TableRow key={job.id}>
                                            <TableCell className="font-mono text-xs">{job.id.substring(0, 8)}...</TableCell>
                                            <TableCell>
                                                <Link
                                                    to={`/clusters/${clusterId}/topics/${job.sourceTopic}`}
                                                    className="text-primary hover:underline"
                                                >
                                                    {job.sourceTopic}
                                                </Link>
                                            </TableCell>
                                            <TableCell>
                                                {job.targetTopic ? (
                                                    <Link
                                                        to={`/clusters/${clusterId}/topics/${job.targetTopic}`}
                                                        className="text-primary hover:underline"
                                                    >
                                                        {job.targetTopic}
                                                    </Link>
                                                ) : job.consumerGroupId ? (
                                                    <span className="text-muted-foreground">{job.consumerGroupId}</span>
                                                ) : (
                                                    <span className="text-muted-foreground">-</span>
                                                )}
                                            </TableCell>
                                            <TableCell>
                                                <Badge className={statusColors[job.status]}>{job.status}</Badge>
                                            </TableCell>
                                            <TableCell>
                                                {progress ? (
                                                    <div className="space-y-1">
                                                        <div className="flex items-center gap-2 text-sm">
                                                            <span>
                                                                {progress.messagesProcessed.toLocaleString()}
                                                                {progress.messagesTotal
                                                                    ? ` / ${progress.messagesTotal.toLocaleString()}`
                                                                    : ''}
                                                            </span>
                                                            {progressPercent !== null && (
                                                                <span className="text-muted-foreground">({progressPercent}%)</span>
                                                            )}
                                                        </div>
                                                        {progress.throughput && (
                                                            <div className="text-xs text-muted-foreground">
                                                                {progress.throughput.toFixed(2)} msg/s
                                                            </div>
                                                        )}
                                                    </div>
                                                ) : (
                                                    <span className="text-muted-foreground">-</span>
                                                )}
                                            </TableCell>
                                            <TableCell>
                                                {job.isRecurring ? (
                                                    <div className="space-y-1">
                                                        <div className="text-sm font-mono">{job.scheduleCron}</div>
                                                        {job.nextScheduledRun && (
                                                            <div className="text-xs text-muted-foreground">
                                                                Next: {formatRelativeTime(new Date(job.nextScheduledRun))}
                                                            </div>
                                                        )}
                                                    </div>
                                                ) : job.nextScheduledRun ? (
                                                    <div className="text-sm">
                                                        {formatRelativeTime(new Date(job.nextScheduledRun))}
                                                    </div>
                                                ) : (
                                                    <span className="text-muted-foreground">-</span>
                                                )}
                                            </TableCell>
                                            <TableCell>
                                                <div className="text-sm">{formatRelativeTime(new Date(job.createdAt))}</div>
                                                <div className="text-xs text-muted-foreground">{formatDate(new Date(job.createdAt))}</div>
                                            </TableCell>
                                            <TableCell className="text-right">
                                                <div className="flex items-center justify-end gap-2">
                                                    <Link to={`/clusters/${clusterId}/replay/${job.id}`}>
                                                        <Button variant="ghost" size="sm">
                                                            <Eye className="h-4 w-4"/>
                                                        </Button>
                                                    </Link>
                                                    {canManageTopics() && job.status !== 'RUNNING' && job.status !== 'PENDING' && (
                                                        <Button 
                                                            variant="ghost" 
                                                            size="sm" 
                                                            onClick={() => handleDelete(job.id)}
                                                            disabled={deleteLoading}
                                                        >
                                                            <Trash2 className="h-4 w-4 text-destructive"/>
                                                        </Button>
                                                    )}
                                                </div>
                                            </TableCell>
                                        </TableRow>
                                    );
                                })}
                            </TableBody>
                        </Table>
                    </CardContent>
                </Card>
            )}

            <ReplayJobForm
                clusterId={clusterId}
                isOpen={isFormOpen}
                onOpenChange={setIsFormOpen}
                onReplayCreated={() => {
                    refetch();
                }}
            />

            <AlertDialog open={!!jobToDelete} onOpenChange={(open) => !open && setJobToDelete(null)}>
                <AlertDialogContent>
                    <AlertDialogHeader>
                        <AlertDialogTitle>Delete Replay Job</AlertDialogTitle>
                        <AlertDialogDescription>
                            Are you sure you want to delete this replay job? This action cannot be undone and will permanently delete the job and all its history.
                        </AlertDialogDescription>
                    </AlertDialogHeader>
                    <AlertDialogFooter>
                        <AlertDialogCancel>Cancel</AlertDialogCancel>
                        <AlertDialogAction
                            onClick={handleDeleteConfirm}
                            className="bg-destructive text-destructive-foreground hover:bg-destructive/90"
                            disabled={deleteLoading}
                        >
                            {deleteLoading ? 'Deleting...' : 'Delete'}
                        </AlertDialogAction>
                    </AlertDialogFooter>
                </AlertDialogContent>
            </AlertDialog>
        </div>
    );
}

