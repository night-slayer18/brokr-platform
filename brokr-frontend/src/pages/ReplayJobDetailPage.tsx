import {useParams, useNavigate} from 'react-router-dom';
import {useState} from 'react';
import {GET_REPLAY_JOB, GET_REPLAY_HISTORY} from '@/graphql/queries';
import type {GetReplayJobQuery, GetReplayHistoryQuery} from '@/graphql/types';
import {useGraphQLQuery} from '@/hooks/useGraphQLQuery';
import {Card, CardContent, CardDescription, CardHeader, CardTitle} from '@/components/ui/card';
import {Skeleton} from '@/components/ui/skeleton';
import {Badge} from '@/components/ui/badge';
import {Table, TableBody, TableCell, TableHead, TableHeader, TableRow} from '@/components/ui/table';
import {Tabs, TabsContent, TabsList, TabsTrigger} from '@/components/ui/tabs';
import {formatDate, formatRelativeTime} from '@/lib/formatters';
import {formatNumber} from '@/lib/utils';
import {Button} from '@/components/ui/button';
import {RefreshCw, X, RotateCw, ArrowLeft, Loader2, Trash2} from 'lucide-react';
import {useAuth} from '@/hooks/useAuth';
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
import {CANCEL_REPLAY_MUTATION, RETRY_REPLAY_MUTATION, DELETE_REPLAY_MUTATION} from '@/graphql/mutations';
import type {CancelReplayMutation, RetryReplayMutation, DeleteReplayMutation} from '@/graphql/types';
import {useGraphQLMutation} from '@/hooks/useGraphQLMutation';
import {toast} from 'sonner';
import {Progress} from '@/components/ui/progress';
import {useQueryClient} from '@tanstack/react-query';
import {print} from 'graphql';

const statusColors: Record<string, string> = {
    PENDING: 'bg-yellow-500/10 text-yellow-500 border-yellow-500/20',
    RUNNING: 'bg-blue-500/10 text-blue-500 border-blue-500/20',
    COMPLETED: 'bg-green-500/10 text-green-500 border-green-500/20',
    FAILED: 'bg-red-500/10 text-red-500 border-red-500/20',
    CANCELLED: 'bg-gray-500/10 text-gray-500 border-gray-500/20',
};

export default function ReplayJobDetailPage() {
    const {jobId} = useParams<{ clusterId: string; jobId: string }>();
    const navigate = useNavigate();
    const {canManageTopics} = useAuth();
    const queryClient = useQueryClient();

    const {
        data,
        isLoading: loading,
        isFetching,
        error,
        refetch
    } = useGraphQLQuery<GetReplayJobQuery, {id: string}>(
        GET_REPLAY_JOB,
        jobId ? {id: jobId} : undefined,
        {
            enabled: !!jobId,
            refetchInterval: (query) => {
                // Poll every 2 seconds if job is running or pending
                const job = query.state.data?.replayJob;
                if (job && (job.status === 'RUNNING' || job.status === 'PENDING')) {
                    return 2000;
                }
                return false;
            },
        }
    );

    const {
        data: historyData,
        isLoading: historyLoading
    } = useGraphQLQuery<GetReplayHistoryQuery, {jobId: string; page?: number | null; size?: number | null}>(
        GET_REPLAY_HISTORY,
        jobId ? {jobId, page: 0, size: 50} : undefined,
        {
            enabled: !!jobId,
        }
    );

    const {mutate: cancelReplay, isPending: cancelLoading} = useGraphQLMutation<
        CancelReplayMutation,
        {id: string}
    >(CANCEL_REPLAY_MUTATION);

    const {mutate: retryReplay, isPending: retryLoading} = useGraphQLMutation<
        RetryReplayMutation,
        {id: string}
    >(RETRY_REPLAY_MUTATION);

    const {mutate: deleteReplay, isPending: deleteLoading} = useGraphQLMutation<
        DeleteReplayMutation,
        {id: string}
    >(DELETE_REPLAY_MUTATION);

    const [isDeleteDialogOpen, setIsDeleteDialogOpen] = useState(false);

    const job = data?.replayJob;
    const history = historyData?.replayHistory || [];
    const progress = job?.progress;

    if (loading && !data) {
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
        return <div className="text-destructive">Error loading replay job: {error.message}</div>;
    }

    if (!job) {
        return <div>Replay job not found.</div>;
    }

    const progressPercent =
        progress && progress.messagesTotal
            ? Math.round((progress.messagesProcessed / progress.messagesTotal) * 100)
            : null;

    const handleCancel = () => {
        if (!jobId) return;
        cancelReplay(
            {id: jobId},
            {
                onSuccess: () => {
                    toast.success('Replay job cancelled successfully');
                    refetch();
                },
                onError: (error: unknown) => {
                    const err = error instanceof Error ? error : {message: 'Failed to cancel replay job'};
                    toast.error(err.message || 'Failed to cancel replay job');
                },
            }
        );
    };

    const handleRetry = () => {
        if (!jobId) return;
        retryReplay(
            {id: jobId},
            {
                onSuccess: () => {
                    toast.success('Replay job retried successfully');
                    refetch();
                },
                onError: (error: unknown) => {
                    const err = error instanceof Error ? error : {message: 'Failed to retry replay job'};
                    toast.error(err.message || 'Failed to retry replay job');
                },
            }
        );
    };

    const handleDelete = () => {
        setIsDeleteDialogOpen(true);
    };

    const handleDeleteConfirm = () => {
        if (!jobId) return;
        deleteReplay(
            {id: jobId},
            {
                onSuccess: () => {
                    toast.success('Replay job deleted successfully');
                    navigate(`/clusters/${job.clusterId}/replay`);
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
                <div className="flex items-center gap-4">
                    <Button variant="ghost" size="sm" onClick={() => navigate(-1)}>
                        <ArrowLeft className="mr-2 h-4 w-4"/>
                        Back
                    </Button>
                    <div>
                        <h2 className="text-3xl font-bold tracking-tight flex items-center gap-2">
                            Replay Job
                            <Badge className={statusColors[job.status]}>{job.status}</Badge>
                        </h2>
                        <p className="text-muted-foreground">
                            Job ID: <span className="font-mono">{job.id}</span>
                        </p>
                    </div>
                </div>
                <div className="flex items-center gap-2">
                    <Button 
                        onClick={async () => {
                            if (!jobId) return;
                            try {
                                // Use queryClient.refetchQueries to ensure it works even with refetchInterval
                                await queryClient.refetchQueries({
                                    queryKey: ['graphql', print(GET_REPLAY_JOB), {id: jobId}],
                                });
                            } catch (error) {
                                console.error('Failed to refetch replay job:', error);
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
                    {canManageTopics() && job.status === 'RUNNING' && (
                        <Button variant="destructive" onClick={handleCancel} disabled={cancelLoading}>
                            <X className="mr-2 h-4 w-4"/>
                            Cancel
                        </Button>
                    )}
                    {canManageTopics() && job.status === 'FAILED' && (
                        <Button onClick={handleRetry} disabled={retryLoading}>
                            <RotateCw className="mr-2 h-4 w-4"/>
                            Retry
                        </Button>
                    )}
                    {canManageTopics() && job.status !== 'RUNNING' && job.status !== 'PENDING' && (
                        <Button variant="destructive" onClick={handleDelete} disabled={deleteLoading}>
                            <Trash2 className="mr-2 h-4 w-4"/>
                            Delete
                        </Button>
                    )}
                </div>
            </div>

            {progress && (job.status === 'RUNNING' || job.status === 'PENDING') && (
                <Card>
                    <CardHeader>
                        <CardTitle>Progress</CardTitle>
                    </CardHeader>
                    <CardContent className="space-y-4">
                        {progressPercent !== null && (
                            <div className="space-y-2">
                                <div className="flex items-center justify-between text-sm">
                                    <span>Overall Progress</span>
                                    <span>{progressPercent}%</span>
                                </div>
                                <Progress value={progressPercent}/>
                            </div>
                        )}
                        <div className="grid grid-cols-2 md:grid-cols-4 gap-4">
                            <div>
                                <div className="text-sm text-muted-foreground">Messages Processed</div>
                                <div className="text-2xl font-bold">{formatNumber(progress.messagesProcessed)}</div>
                            </div>
                            {progress.messagesTotal && (
                                <div>
                                    <div className="text-sm text-muted-foreground">Total Messages</div>
                                    <div className="text-2xl font-bold">{formatNumber(progress.messagesTotal)}</div>
                                </div>
                            )}
                            {progress.throughput && (
                                <div>
                                    <div className="text-sm text-muted-foreground">Throughput</div>
                                    <div className="text-2xl font-bold">{progress.throughput.toFixed(2)} msg/s</div>
                                </div>
                            )}
                            {progress.estimatedTimeRemainingSeconds && (
                                <div>
                                    <div className="text-sm text-muted-foreground">Time Remaining</div>
                                    <div className="text-2xl font-bold">
                                        {Math.round(progress.estimatedTimeRemainingSeconds / 60)} min
                                    </div>
                                </div>
                            )}
                        </div>
                    </CardContent>
                </Card>
            )}

            <Tabs defaultValue="details" className="w-full">
                <TabsList>
                    <TabsTrigger value="details">Details</TabsTrigger>
                    <TabsTrigger value="history">History</TabsTrigger>
                </TabsList>

                <TabsContent value="details" className="space-y-4">
                    <div className="grid gap-4 md:grid-cols-2">
                        <Card>
                            <CardHeader>
                                <CardTitle>Source Configuration</CardTitle>
                            </CardHeader>
                            <CardContent className="space-y-2">
                                <div className="flex justify-between">
                                    <span className="text-muted-foreground">Source Topic:</span>
                                    <span className="font-medium">{job.sourceTopic}</span>
                                </div>
                                {job.partitions && job.partitions.length > 0 && (
                                    <div className="flex justify-between">
                                        <span className="text-muted-foreground">Partitions:</span>
                                        <span className="font-medium">{job.partitions.join(', ')}</span>
                                    </div>
                                )}
                                {job.startOffset !== null && job.startOffset !== undefined && (
                                    <div className="flex justify-between">
                                        <span className="text-muted-foreground">Start Offset:</span>
                                        <span className="font-medium">{formatNumber(job.startOffset)}</span>
                                    </div>
                                )}
                                {job.startTimestamp && (
                                    <div className="flex justify-between">
                                        <span className="text-muted-foreground">Start Timestamp:</span>
                                        <span className="font-medium">{formatDate(new Date(job.startTimestamp))}</span>
                                    </div>
                                )}
                                {job.endOffset !== null && job.endOffset !== undefined && (
                                    <div className="flex justify-between">
                                        <span className="text-muted-foreground">End Offset:</span>
                                        <span className="font-medium">{formatNumber(job.endOffset)}</span>
                                    </div>
                                )}
                                {job.endTimestamp && (
                                    <div className="flex justify-between">
                                        <span className="text-muted-foreground">End Timestamp:</span>
                                        <span className="font-medium">{formatDate(new Date(job.endTimestamp))}</span>
                                    </div>
                                )}
                            </CardContent>
                        </Card>

                        <Card>
                            <CardHeader>
                                <CardTitle>Target Configuration</CardTitle>
                            </CardHeader>
                            <CardContent className="space-y-2">
                                {job.targetTopic && (
                                    <div className="flex justify-between">
                                        <span className="text-muted-foreground">Target Topic:</span>
                                        <span className="font-medium">{job.targetTopic}</span>
                                    </div>
                                )}
                                {job.consumerGroupId && (
                                    <div className="flex justify-between">
                                        <span className="text-muted-foreground">Consumer Group:</span>
                                        <span className="font-medium">{job.consumerGroupId}</span>
                                    </div>
                                )}
                            </CardContent>
                        </Card>

                        <Card>
                            <CardHeader>
                                <CardTitle>Schedule</CardTitle>
                            </CardHeader>
                            <CardContent className="space-y-2">
                                {job.isRecurring ? (
                                    <>
                                        <div className="flex justify-between">
                                            <span className="text-muted-foreground">Type:</span>
                                            <span className="font-medium">Recurring</span>
                                        </div>
                                        {job.scheduleCron && (
                                            <div className="flex justify-between">
                                                <span className="text-muted-foreground">Cron:</span>
                                                <span className="font-mono text-sm">{job.scheduleCron}</span>
                                            </div>
                                        )}
                                        {job.scheduleTimezone && (
                                            <div className="flex justify-between">
                                                <span className="text-muted-foreground">Timezone:</span>
                                                <span className="font-medium">{job.scheduleTimezone}</span>
                                            </div>
                                        )}
                                        {job.nextScheduledRun && (
                                            <div className="flex justify-between">
                                                <span className="text-muted-foreground">Next Run:</span>
                                                <span className="font-medium">
                                                    {formatRelativeTime(new Date(job.nextScheduledRun))}
                                                </span>
                                            </div>
                                        )}
                                        {job.lastScheduledRun && (
                                            <div className="flex justify-between">
                                                <span className="text-muted-foreground">Last Run:</span>
                                                <span className="font-medium">
                                                    {formatRelativeTime(new Date(job.lastScheduledRun))}
                                                </span>
                                            </div>
                                        )}
                                    </>
                                ) : job.nextScheduledRun ? (
                                    <>
                                        <div className="flex justify-between">
                                            <span className="text-muted-foreground">Type:</span>
                                            <span className="font-medium">Scheduled Once</span>
                                        </div>
                                        <div className="flex justify-between">
                                            <span className="text-muted-foreground">Scheduled Time:</span>
                                            <span className="font-medium">
                                                {formatRelativeTime(new Date(job.nextScheduledRun))}
                                            </span>
                                        </div>
                                    </>
                                ) : (
                                    <div className="flex justify-between">
                                        <span className="text-muted-foreground">Type:</span>
                                        <span className="font-medium">Immediate</span>
                                    </div>
                                )}
                            </CardContent>
                        </Card>

                        <Card>
                            <CardHeader>
                                <CardTitle>Retry Configuration</CardTitle>
                            </CardHeader>
                            <CardContent className="space-y-2">
                                <div className="flex justify-between">
                                    <span className="text-muted-foreground">Max Retries:</span>
                                    <span className="font-medium">{job.maxRetries}</span>
                                </div>
                                <div className="flex justify-between">
                                    <span className="text-muted-foreground">Retry Count:</span>
                                    <span className="font-medium">{job.retryCount}</span>
                                </div>
                                <div className="flex justify-between">
                                    <span className="text-muted-foreground">Retry Delay:</span>
                                    <span className="font-medium">{job.retryDelaySeconds}s</span>
                                </div>
                            </CardContent>
                        </Card>

                        <Card>
                            <CardHeader>
                                <CardTitle>Timestamps</CardTitle>
                            </CardHeader>
                            <CardContent className="space-y-2">
                                <div className="flex justify-between">
                                    <span className="text-muted-foreground">Created:</span>
                                    <span className="font-medium">{formatRelativeTime(new Date(job.createdAt))}</span>
                                </div>
                                {job.startedAt && (
                                    <div className="flex justify-between">
                                        <span className="text-muted-foreground">Started:</span>
                                        <span className="font-medium">{formatRelativeTime(new Date(job.startedAt))}</span>
                                    </div>
                                )}
                                {job.completedAt && (
                                    <div className="flex justify-between">
                                        <span className="text-muted-foreground">Completed:</span>
                                        <span className="font-medium">{formatRelativeTime(new Date(job.completedAt))}</span>
                                    </div>
                                )}
                                <div className="flex justify-between">
                                    <span className="text-muted-foreground">Created By:</span>
                                    <span className="font-medium">{job.createdBy}</span>
                                </div>
                            </CardContent>
                        </Card>

                        {job.errorMessage && (
                            <Card>
                                <CardHeader>
                                    <CardTitle>Error</CardTitle>
                                </CardHeader>
                                <CardContent>
                                    <div className="text-sm text-destructive font-mono bg-destructive/10 p-3 rounded">
                                        {job.errorMessage}
                                    </div>
                                </CardContent>
                            </Card>
                        )}
                    </div>
                </TabsContent>

                <TabsContent value="history" className="space-y-4">
                    <Card>
                        <CardHeader>
                            <CardTitle>Job History</CardTitle>
                            <CardDescription>Detailed history of job execution events</CardDescription>
                        </CardHeader>
                        <CardContent>
                            {historyLoading ? (
                                <Skeleton className="h-32"/>
                            ) : history.length === 0 ? (
                                <p className="text-muted-foreground text-center py-8">No history available</p>
                            ) : (
                                <Table>
                                    <TableHeader>
                                        <TableRow>
                                            <TableHead>Timestamp</TableHead>
                                            <TableHead>Action</TableHead>
                                            <TableHead>Message Count</TableHead>
                                            <TableHead>Throughput</TableHead>
                                            <TableHead>Details</TableHead>
                                        </TableRow>
                                    </TableHeader>
                                    <TableBody>
                                        {history.map((entry) => (
                                            <TableRow key={entry.id}>
                                                <TableCell>{formatDate(new Date(entry.timestamp))}</TableCell>
                                                <TableCell>
                                                    <Badge variant="outline">{entry.action}</Badge>
                                                </TableCell>
                                                <TableCell>{formatNumber(entry.messageCount)}</TableCell>
                                                <TableCell>
                                                    {entry.throughput ? `${entry.throughput.toFixed(2)} msg/s` : '-'}
                                                </TableCell>
                                                <TableCell>
                                                    {entry.details ? (
                                                        <pre className="text-xs font-mono bg-muted p-2 rounded max-w-md overflow-auto">
                                                            {JSON.stringify(entry.details, null, 2)}
                                                        </pre>
                                                    ) : (
                                                        '-'
                                                    )}
                                                </TableCell>
                                            </TableRow>
                                        ))}
                                    </TableBody>
                                </Table>
                            )}
                        </CardContent>
                    </Card>
                </TabsContent>
            </Tabs>

            <AlertDialog open={isDeleteDialogOpen} onOpenChange={setIsDeleteDialogOpen}>
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

