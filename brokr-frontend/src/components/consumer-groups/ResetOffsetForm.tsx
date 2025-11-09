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
import {RESET_CONSUMER_OFFSET_MUTATION} from '@/graphql/mutations';
import type {
    GetTopicsQuery,
    ResetConsumerOffsetMutation
} from '@/graphql/types';
import {useGraphQLQuery} from '@/hooks/useGraphQLQuery';
import {useGraphQLMutation} from '@/hooks/useGraphQLMutation';
import {useQueryClient} from '@tanstack/react-query';
import {GET_CONSUMER_GROUPS} from '@/graphql/queries';
import {toast} from 'sonner';
import {Loader2} from 'lucide-react';
import {GET_TOPICS} from "@/graphql/queries";
import {Select, SelectContent, SelectItem, SelectTrigger, SelectValue} from "@/components/ui/select";

const resetOffsetSchema = z.object({
    topic: z.string().min(1, 'Topic is required'),
    partition: z.number().int().min(0, 'Partition must be a non-negative number'),
    offset: z.number().int().min(0, 'Offset must be a non-negative number'),
});

type ResetOffsetFormData = z.infer<typeof resetOffsetSchema>;

interface ResetOffsetFormProps {
    clusterId: string;
    groupId: string;
    isOpen: boolean;
    onOpenChange: (isOpen: boolean) => void;
    onOffsetReset: () => void;
}

export function ResetOffsetForm({
                                    clusterId,
                                    groupId,
                                    isOpen,
                                    onOpenChange,
                                    onOffsetReset,
                                }: ResetOffsetFormProps) {
    const queryClient = useQueryClient();
    const {mutate: resetConsumerGroupOffset, isPending: mutationLoading} = useGraphQLMutation<ResetConsumerOffsetMutation, {clusterId: string; groupId: string; topic: string; partition: number; offset: number}>(RESET_CONSUMER_OFFSET_MUTATION);
    const {data: topicsData, isLoading: topicsLoading} = useGraphQLQuery<GetTopicsQuery, {clusterId: string}>(GET_TOPICS, 
        clusterId && isOpen ? {clusterId} : undefined,
        {
            enabled: !!clusterId && isOpen, // Only fetch when the dialog is open
        }
    );

    const {
        register,
        handleSubmit,
        formState: {errors},
        reset,
        setValue,
        watch,
    } = useForm<ResetOffsetFormData>({
        resolver: zodResolver(resetOffsetSchema),
        defaultValues: {
            partition: 0,
            offset: 0,
        },
    });

    const onSubmit = async (data: ResetOffsetFormData) => {
        resetConsumerGroupOffset(
            {
                clusterId,
                groupId,
                topic: data.topic,
                partition: Number(data.partition),
                offset: Number(data.offset),
            },
            {
                onSuccess: () => {
                    toast.success(`Offset for topic "${data.topic}" partition ${data.partition} reset successfully`);
                    queryClient.invalidateQueries({queryKey: ['graphql', GET_CONSUMER_GROUPS]});
                    onOffsetReset();
                    onOpenChange(false);
                    reset();
                },
                onError: (error: any) => {
                    toast.error(error.message || 'Failed to reset offset');
                },
            }
        );
    };

    const loading = mutationLoading || topicsLoading;

    return (
        <Dialog open={isOpen} onOpenChange={onOpenChange}>
            <DialogContent>
                <DialogHeader>
                    <DialogTitle>Reset Consumer Group Offset</DialogTitle>
                    <DialogDescription>
                        Reset the offset for a specific topic and partition in consumer group <span
                        className="font-mono">{groupId}</span>.
                    </DialogDescription>
                </DialogHeader>
                <form onSubmit={handleSubmit(onSubmit)} className="space-y-4">
                    <div className="space-y-2">
                        <Label htmlFor="topic">Topic Name</Label>
                        <Select
                            onValueChange={(value) => setValue('topic', value)}
                            value={watch('topic')}
                            disabled={loading}
                        >
                            <SelectTrigger>
                                <SelectValue placeholder="Select a topic to reset"/>
                            </SelectTrigger>
                            <SelectContent>
                                {topicsData?.topics?.map((topic) => (
                                    <SelectItem key={topic.name} value={topic.name}>
                                        {topic.name}
                                    </SelectItem>
                                ))}
                            </SelectContent>
                        </Select>
                        {errors.topic && <p className="text-sm text-destructive">{errors.topic.message}</p>}
                    </div>
                    <div className="grid grid-cols-2 gap-4">
                        <div className="space-y-2">
                            <Label htmlFor="partition">Partition</Label>
                            <Input id="partition" type="number" {...register('partition', { valueAsNumber: true })} disabled={loading}/>
                            {errors.partition && <p className="text-sm text-destructive">{errors.partition.message}</p>}
                        </div>
                        <div className="space-y-2">
                            <Label htmlFor="offset">New Offset</Label>
                            <Input id="offset" type="number" {...register('offset', { valueAsNumber: true })} disabled={loading}/>
                            {errors.offset && <p className="text-sm text-destructive">{errors.offset.message}</p>}
                        </div>
                    </div>
                    <DialogFooter>
                        <Button type="button" variant="outline" onClick={() => onOpenChange(false)} disabled={loading}>
                            Cancel
                        </Button>
                        <Button type="submit" disabled={loading}>
                            {loading && <Loader2 className="mr-2 h-4 w-4 animate-spin"/>}
                            Reset Offset
                        </Button>
                    </DialogFooter>
                </form>
            </DialogContent>
        </Dialog>
    );
}
