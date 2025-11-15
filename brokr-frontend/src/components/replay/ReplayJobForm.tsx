import {useForm} from 'react-hook-form';
import {zodResolver} from '@hookform/resolvers/zod';
import * as z from 'zod';
import {Button} from '@/components/ui/button';
import {Input} from '@/components/ui/input';
import {Label} from '@/components/ui/label';
import {
    Dialog,
    DialogContent,
    DialogDescription,
    DialogFooter,
    DialogHeader,
    DialogTitle
} from '@/components/ui/dialog';
import {REPLAY_MESSAGES_MUTATION, SCHEDULE_REPLAY_MUTATION} from '@/graphql/mutations';
import type {
    ReplayMessagesMutation,
    ReplayMessagesMutationVariables,
    ScheduleReplayMutation,
    ScheduleReplayMutationVariables
} from '@/graphql/types';
import {toast} from 'sonner';
import {Loader2} from 'lucide-react';
import {useGraphQLMutation} from '@/hooks/useGraphQLMutation';
import {useQueryClient} from '@tanstack/react-query';
import {GET_REPLAY_JOBS} from '@/graphql/queries';
import {Tabs, TabsContent, TabsList, TabsTrigger} from '@/components/ui/tabs';
import {Select, SelectContent, SelectItem, SelectTrigger, SelectValue} from '@/components/ui/select';
import type {MessageReplayInput} from '@/types';

const replayJobSchema = z.object({
    sourceTopic: z.string().min(1, 'Source topic is required'),
    targetTopic: z.string().optional(),
    consumerGroupId: z.string().optional(),
    startType: z.enum(['offset', 'timestamp']).default('offset'),
    startOffset: z.number().int().min(0).optional().nullable(),
    startTimestamp: z.string().optional().nullable(),
    endType: z.enum(['offset', 'timestamp', 'none']).default('none'),
    endOffset: z.number().int().min(0).optional().nullable(),
    endTimestamp: z.string().optional().nullable(),
    partitions: z.string().optional(), // Comma-separated list
    scheduleType: z.enum(['immediate', 'scheduled', 'recurring']).default('immediate'),
    scheduleTime: z.string().optional().nullable(),
    scheduleCron: z.string().optional().nullable(),
    scheduleTimezone: z.string().default('UTC'),
    maxRetries: z.number().int().min(0).max(10).optional().nullable(),
    retryDelaySeconds: z.number().int().min(1).optional().nullable(),
}).refine((data) => {
    // Either targetTopic or consumerGroupId must be provided
    return data.targetTopic || data.consumerGroupId;
}, {
    message: 'Either target topic or consumer group ID must be specified',
    path: ['targetTopic']
}).refine((data) => {
    // If startType is offset, startOffset must be provided and valid
    if (data.startType === 'offset') {
        return data.startOffset !== undefined && data.startOffset !== null && !isNaN(data.startOffset) && data.startOffset >= 0;
    }
    // If startType is timestamp, startTimestamp must be provided and non-empty
    if (data.startType === 'timestamp') {
        const timestamp = data.startTimestamp;
        return timestamp !== undefined && 
               timestamp !== null && 
               timestamp !== '' &&
               typeof timestamp === 'string' &&
               timestamp.trim().length > 0;
    }
    return true;
}, {
    message: 'Start offset or timestamp is required',
    path: ['startType'] // Use startType as the error path so it shows on the right field
}).refine((data) => {
    // If scheduleType is scheduled, scheduleTime must be provided
    if (data.scheduleType === 'scheduled') {
        const scheduleTime = data.scheduleTime;
        return scheduleTime !== undefined && 
               scheduleTime !== null && 
               scheduleTime !== '' &&
               typeof scheduleTime === 'string' &&
               scheduleTime.trim().length > 0;
    }
    // If scheduleType is recurring, scheduleCron must be provided
    if (data.scheduleType === 'recurring') {
        const scheduleCron = data.scheduleCron;
        return scheduleCron !== undefined && 
               scheduleCron !== null && 
               scheduleCron !== '' &&
               typeof scheduleCron === 'string' &&
               scheduleCron.trim().length > 0;
    }
    return true;
}, {
    message: 'Schedule time or cron expression is required',
    path: ['scheduleType'] // Use scheduleType as the error path
});

type ReplayJobFormData = z.infer<typeof replayJobSchema>;

interface ReplayJobFormProps {
    clusterId: string;
    sourceTopic?: string;
    isOpen: boolean;
    onOpenChange: (isOpen: boolean) => void;
    onReplayCreated: () => void;
}

export function ReplayJobForm({
    clusterId,
    sourceTopic,
    isOpen,
    onOpenChange,
    onReplayCreated
}: ReplayJobFormProps) {
    const queryClient = useQueryClient();
    
    const {mutate: replayMessages, isPending: replayLoading} = useGraphQLMutation<
        ReplayMessagesMutation,
        ReplayMessagesMutationVariables
    >(REPLAY_MESSAGES_MUTATION);
    
    const {mutate: scheduleReplay, isPending: scheduleLoading} = useGraphQLMutation<
        ScheduleReplayMutation,
        ScheduleReplayMutationVariables
    >(SCHEDULE_REPLAY_MUTATION);

    const {
        register,
        handleSubmit,
        formState: {errors},
        reset,
        watch,
        setValue,
    } = useForm({
        resolver: zodResolver(replayJobSchema),
        defaultValues: {
            sourceTopic: sourceTopic || '',
            startType: 'offset' as const,
            endType: 'none' as const,
            scheduleType: 'immediate' as const,
            scheduleTimezone: 'UTC',
        },
    });

    const startType = watch('startType');
    const endType = watch('endType');
    const scheduleType = watch('scheduleType');
    const targetTopic = watch('targetTopic');
    const consumerGroupId = watch('consumerGroupId');

    const loading = replayLoading || scheduleLoading;

    const onSubmit = async (data: ReplayJobFormData) => {
        // Parse partitions
        const partitions = data.partitions
            ? data.partitions.split(',').map(p => parseInt(p.trim())).filter(p => !isNaN(p))
            : undefined;

        // Build input
        const input: MessageReplayInput = {
            clusterId,
            sourceTopic: data.sourceTopic,
            targetTopic: data.targetTopic || undefined,
            consumerGroupId: data.consumerGroupId || undefined,
            startOffset: data.startType === 'offset' ? data.startOffset : undefined,
            startTimestamp: data.startType === 'timestamp' ? data.startTimestamp : undefined,
            endOffset: data.endType === 'offset' ? data.endOffset : undefined,
            endTimestamp: data.endType === 'timestamp' ? data.endTimestamp : undefined,
            partitions: partitions && partitions.length > 0 ? partitions : undefined,
            scheduleTime: data.scheduleType === 'scheduled' ? data.scheduleTime : undefined,
            scheduleCron: data.scheduleType === 'recurring' ? data.scheduleCron : undefined,
            scheduleTimezone: data.scheduleType !== 'immediate' ? data.scheduleTimezone : undefined,
            maxRetries: data.maxRetries || undefined,
            retryDelaySeconds: data.retryDelaySeconds || undefined,
        };

        if (data.scheduleType === 'immediate') {
            replayMessages(
                {input},
                {
                    onSuccess: () => {
                        toast.success('Replay job created successfully');
                        queryClient.invalidateQueries({queryKey: ['graphql', GET_REPLAY_JOBS]});
                        onReplayCreated();
                        onOpenChange(false);
                        reset();
                    },
                    onError: (error: unknown) => {
                        const err = error instanceof Error ? error : {message: 'Failed to create replay job'};
                        toast.error(err.message || 'Failed to create replay job');
                    },
                }
            );
        } else {
            scheduleReplay(
                {input},
                {
                    onSuccess: () => {
                        toast.success('Replay job scheduled successfully');
                        queryClient.invalidateQueries({queryKey: ['graphql', GET_REPLAY_JOBS]});
                        onReplayCreated();
                        onOpenChange(false);
                        reset();
                    },
                    onError: (error: unknown) => {
                        const err = error instanceof Error ? error : {message: 'Failed to schedule replay job'};
                        toast.error(err.message || 'Failed to schedule replay job');
                    },
                }
            );
        }
    };

    return (
        <Dialog open={isOpen} onOpenChange={onOpenChange}>
            <DialogContent className="max-w-4xl max-h-[90vh] overflow-y-auto">
                <DialogHeader>
                    <DialogTitle>Create Message Replay Job</DialogTitle>
                    <DialogDescription>
                        Configure a message replay or reprocessing job for cluster <span className="font-mono">{clusterId}</span>.
                    </DialogDescription>
                </DialogHeader>
                <form onSubmit={handleSubmit(onSubmit)} className="space-y-6">
                    <Tabs defaultValue="basic" className="w-full">
                        <TabsList className="grid w-full grid-cols-4">
                            <TabsTrigger value="basic">Basic</TabsTrigger>
                            <TabsTrigger value="range">Range</TabsTrigger>
                            <TabsTrigger value="schedule">Schedule</TabsTrigger>
                            <TabsTrigger value="advanced">Advanced</TabsTrigger>
                        </TabsList>

                        <TabsContent value="basic" className="space-y-4">
                            <div className="space-y-2">
                                <Label htmlFor="sourceTopic">Source Topic *</Label>
                                <Input
                                    id="sourceTopic"
                                    {...register('sourceTopic')}
                                    disabled={loading || !!sourceTopic}
                                    placeholder="e.g., my-topic"
                                />
                                {errors.sourceTopic && (
                                    <p className="text-sm text-destructive">{errors.sourceTopic.message}</p>
                                )}
                            </div>

                            <div className="space-y-2">
                                <Label htmlFor="targetTopic">Target Topic</Label>
                                <Input
                                    id="targetTopic"
                                    {...register('targetTopic')}
                                    disabled={loading}
                                    placeholder="Leave empty for offset reset only"
                                />
                                <p className="text-xs text-muted-foreground">
                                    Reprocess messages to this topic. Leave empty if only resetting consumer group offset.
                                </p>
                            </div>

                            <div className="space-y-2">
                                <Label htmlFor="consumerGroupId">Consumer Group ID</Label>
                                <Input
                                    id="consumerGroupId"
                                    {...register('consumerGroupId')}
                                    disabled={loading}
                                    placeholder="e.g., my-consumer-group"
                                />
                                <p className="text-xs text-muted-foreground">
                                    Reset offset for this consumer group. Leave empty if reprocessing to a topic.
                                </p>
                            </div>

                            {!targetTopic && !consumerGroupId && (
                                <p className="text-sm text-destructive">
                                    Either target topic or consumer group ID must be specified
                                </p>
                            )}

                            <div className="space-y-2">
                                <Label htmlFor="partitions">Partitions (optional)</Label>
                                <Input
                                    id="partitions"
                                    {...register('partitions')}
                                    disabled={loading}
                                    placeholder="e.g., 0,1,2 (leave empty for all partitions)"
                                />
                                <p className="text-xs text-muted-foreground">
                                    Comma-separated list of partition IDs. Leave empty to replay from all partitions.
                                </p>
                            </div>
                        </TabsContent>

                        <TabsContent value="range" className="space-y-4">
                            <div className="space-y-2">
                                <Label>Start Point</Label>
                                <Select
                                    value={startType}
                                    onValueChange={(value) => {
                                        setValue('startType', value as 'offset' | 'timestamp');
                                        // Clear the other field when switching types
                                        if (value === 'offset') {
                                            setValue('startTimestamp', undefined, { shouldValidate: true });
                                        } else {
                                            setValue('startOffset', undefined, { shouldValidate: true });
                                        }
                                    }}
                                    disabled={loading}
                                >
                                    <SelectTrigger>
                                        <SelectValue/>
                                    </SelectTrigger>
                                    <SelectContent>
                                        <SelectItem value="offset">Offset</SelectItem>
                                        <SelectItem value="timestamp">Timestamp</SelectItem>
                                    </SelectContent>
                                </Select>
                                {errors.startType && (
                                    <p className="text-sm text-destructive">{errors.startType.message}</p>
                                )}
                            </div>

                            {startType === 'offset' && (
                                <div className="space-y-2">
                                    <Label htmlFor="startOffset">Start Offset *</Label>
                                    <Input
                                        id="startOffset"
                                        type="number"
                                        {...register('startOffset', {valueAsNumber: true})}
                                        disabled={loading}
                                        placeholder="0"
                                    />
                                    {errors.startOffset && (
                                        <p className="text-sm text-destructive">{errors.startOffset.message}</p>
                                    )}
                                </div>
                            )}

                            {startType === 'timestamp' && (
                                <div className="space-y-2">
                                    <Label htmlFor="startTimestamp">Start Timestamp *</Label>
                                    <Input
                                        id="startTimestamp"
                                        type="datetime-local"
                                        {...register('startTimestamp')}
                                        disabled={loading}
                                    />
                                    {errors.startTimestamp && (
                                        <p className="text-sm text-destructive">{errors.startTimestamp.message}</p>
                                    )}
                                </div>
                            )}

                            <div className="space-y-2">
                                <Label>End Point (optional)</Label>
                                <Select
                                    value={endType}
                                    onValueChange={(value) => setValue('endType', value as 'offset' | 'timestamp' | 'none')}
                                    disabled={loading}
                                >
                                    <SelectTrigger>
                                        <SelectValue/>
                                    </SelectTrigger>
                                    <SelectContent>
                                        <SelectItem value="none">No end limit</SelectItem>
                                        <SelectItem value="offset">Offset</SelectItem>
                                        <SelectItem value="timestamp">Timestamp</SelectItem>
                                    </SelectContent>
                                </Select>
                            </div>

                            {endType === 'offset' && (
                                <div className="space-y-2">
                                    <Label htmlFor="endOffset">End Offset</Label>
                                    <Input
                                        id="endOffset"
                                        type="number"
                                        {...register('endOffset', {valueAsNumber: true})}
                                        disabled={loading}
                                        placeholder="1000"
                                    />
                                </div>
                            )}

                            {endType === 'timestamp' && (
                                <div className="space-y-2">
                                    <Label htmlFor="endTimestamp">End Timestamp</Label>
                                    <Input
                                        id="endTimestamp"
                                        type="datetime-local"
                                        {...register('endTimestamp')}
                                        disabled={loading}
                                    />
                                </div>
                            )}
                        </TabsContent>

                        <TabsContent value="schedule" className="space-y-4">
                            <div className="space-y-2">
                                <Label>Schedule Type</Label>
                                <Select
                                    value={scheduleType}
                                    onValueChange={(value) => {
                                        setValue('scheduleType', value as 'immediate' | 'scheduled' | 'recurring');
                                        // Clear the other fields when switching types
                                        if (value === 'immediate') {
                                            setValue('scheduleTime', undefined, { shouldValidate: true });
                                            setValue('scheduleCron', undefined, { shouldValidate: true });
                                        } else if (value === 'scheduled') {
                                            setValue('scheduleCron', undefined, { shouldValidate: true });
                                        } else {
                                            setValue('scheduleTime', undefined, { shouldValidate: true });
                                        }
                                    }}
                                    disabled={loading}
                                >
                                    <SelectTrigger>
                                        <SelectValue/>
                                    </SelectTrigger>
                                    <SelectContent>
                                        <SelectItem value="immediate">Run Immediately</SelectItem>
                                        <SelectItem value="scheduled">Schedule Once</SelectItem>
                                        <SelectItem value="recurring">Recurring (Cron)</SelectItem>
                                    </SelectContent>
                                </Select>
                                {errors.scheduleType && (
                                    <p className="text-sm text-destructive">{errors.scheduleType.message}</p>
                                )}
                            </div>

                            {scheduleType === 'scheduled' && (
                                <>
                                    <div className="space-y-2">
                                        <Label htmlFor="scheduleTime">Schedule Time *</Label>
                                        <Input
                                            id="scheduleTime"
                                            type="datetime-local"
                                            {...register('scheduleTime')}
                                            disabled={loading}
                                        />
                                        {errors.scheduleTime && (
                                            <p className="text-sm text-destructive">{errors.scheduleTime.message}</p>
                                        )}
                                    </div>
                                    <div className="space-y-2">
                                        <Label htmlFor="scheduleTimezone">Timezone</Label>
                                        <Input
                                            id="scheduleTimezone"
                                            {...register('scheduleTimezone')}
                                            disabled={loading}
                                            placeholder="UTC"
                                        />
                                    </div>
                                </>
                            )}

                            {scheduleType === 'recurring' && (
                                <>
                                    <div className="space-y-2">
                                        <Label htmlFor="scheduleCron">Cron Expression *</Label>
                                        <Input
                                            id="scheduleCron"
                                            {...register('scheduleCron')}
                                            disabled={loading}
                                            placeholder="0 0 * * * (daily at midnight)"
                                        />
                                        {errors.scheduleCron && (
                                            <p className="text-sm text-destructive">{errors.scheduleCron.message}</p>
                                        )}
                                        <p className="text-xs text-muted-foreground">
                                            Format: minute hour day month day-of-week (e.g., "0 0 * * *" for daily at midnight)
                                        </p>
                                    </div>
                                    <div className="space-y-2">
                                        <Label htmlFor="scheduleTimezone">Timezone</Label>
                                        <Input
                                            id="scheduleTimezone"
                                            {...register('scheduleTimezone')}
                                            disabled={loading}
                                            placeholder="UTC"
                                        />
                                    </div>
                                </>
                            )}
                        </TabsContent>

                        <TabsContent value="advanced" className="space-y-4">
                            <div className="space-y-2">
                                <Label htmlFor="maxRetries">Max Retries</Label>
                                <Input
                                    id="maxRetries"
                                    type="number"
                                    {...register('maxRetries', {valueAsNumber: true})}
                                    disabled={loading}
                                    placeholder="0 (no retry)"
                                    min={0}
                                    max={10}
                                />
                                <p className="text-xs text-muted-foreground">
                                    Maximum number of retry attempts if the job fails (0-10)
                                </p>
                            </div>

                            <div className="space-y-2">
                                <Label htmlFor="retryDelaySeconds">Retry Delay (seconds)</Label>
                                <Input
                                    id="retryDelaySeconds"
                                    type="number"
                                    {...register('retryDelaySeconds', {valueAsNumber: true})}
                                    disabled={loading}
                                    placeholder="60"
                                    min={1}
                                />
                                <p className="text-xs text-muted-foreground">
                                    Delay between retry attempts in seconds
                                </p>
                            </div>
                        </TabsContent>
                    </Tabs>

                    <DialogFooter>
                        <Button type="button" variant="outline" onClick={() => onOpenChange(false)} disabled={loading}>
                            Cancel
                        </Button>
                        <Button 
                            type="submit" 
                            disabled={loading}
                        >
                            {loading && <Loader2 className="mr-2 h-4 w-4 animate-spin"/>}
                            {scheduleType === 'immediate' ? 'Start Replay' : 'Schedule Replay'}
                        </Button>
                    </DialogFooter>
                </form>
            </DialogContent>
        </Dialog>
    );
}

