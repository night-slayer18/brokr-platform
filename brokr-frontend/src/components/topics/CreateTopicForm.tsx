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
import {useMutation} from '@apollo/client/react';
import {CREATE_TOPIC_MUTATION} from '@/graphql/mutations';
import type {CreateTopicMutation, CreateTopicMutationVariables} from '@/graphql/types';
import {toast} from 'sonner';
import {Loader2} from 'lucide-react';

const topicSchema = z.object({
    name: z.string().min(1, 'Topic name is required'),
    partitions: z.number().int().min(1, 'Partitions must be at least 1'),
    replicationFactor: z.number().int().min(1, 'Replication factor must be at least 1'),
});

type TopicFormData = z.infer<typeof topicSchema>;

interface CreateTopicFormProps {
    clusterId: string;
    isOpen: boolean;
    onOpenChange: (isOpen: boolean) => void;
    onTopicCreated: () => void;
}

export function CreateTopicForm({clusterId, isOpen, onOpenChange, onTopicCreated}: CreateTopicFormProps) {
    const [createTopic, {loading}] = useMutation<CreateTopicMutation, CreateTopicMutationVariables>(CREATE_TOPIC_MUTATION);

    const {
        register,
        handleSubmit,
        formState: {errors},
        reset,
    } = useForm<TopicFormData>({
        resolver: zodResolver(topicSchema),
        defaultValues: {
            partitions: 1,
            replicationFactor: 1,
        },
    });

    const onSubmit = async (data: TopicFormData) => {
        try {
            await createTopic({
                variables: {
                    clusterId,
                    input: {
                        name: data.name,
                        partitions: data.partitions,
                        replicationFactor: data.replicationFactor,
                    },
                },
            });
            toast.success(`Topic "${data.name}" created successfully`);
            onTopicCreated();
            onOpenChange(false);
            reset();
        } catch (error: unknown) {
            const err = error instanceof Error ? error : {message: 'Failed to create topic'}
            toast.error(err.message || 'Failed to create topic');
        }
    };

    return (
        <Dialog open={isOpen} onOpenChange={onOpenChange}>
            <DialogContent>
                <DialogHeader>
                    <DialogTitle>Create New Topic</DialogTitle>
                    <DialogDescription>
                        Configure and create a new topic in cluster <span className="font-mono">{clusterId}</span>.
                    </DialogDescription>
                </DialogHeader>
                <form onSubmit={handleSubmit(onSubmit)} className="space-y-4">
                    <div className="space-y-2">
                        <Label htmlFor="name">Topic Name</Label>
                        <Input id="name" {...register('name')} disabled={loading}/>
                        {errors.name && <p className="text-sm text-destructive">{errors.name.message}</p>}
                    </div>
                    <div className="grid grid-cols-2 gap-4">
                        <div className="space-y-2">
                            <Label htmlFor="partitions">Partitions</Label>
                            <Input id="partitions" type="number" {...register('partitions', {valueAsNumber: true})}
                                   disabled={loading}/>
                            {errors.partitions &&
                                <p className="text-sm text-destructive">{errors.partitions.message}</p>}
                        </div>
                        <div className="space-y-2">
                            <Label htmlFor="replicationFactor">Replication Factor</Label>
                            <Input id="replicationFactor"
                                   type="number" {...register('replicationFactor', {valueAsNumber: true})}
                                   disabled={loading}/>
                            {errors.replicationFactor &&
                                <p className="text-sm text-destructive">{errors.replicationFactor.message}</p>}
                        </div>
                    </div>
                    <DialogFooter>
                        <Button type="button" variant="outline" onClick={() => onOpenChange(false)} disabled={loading}>
                            Cancel
                        </Button>
                        <Button type="submit" disabled={loading}>
                            {loading && <Loader2 className="mr-2 h-4 w-4 animate-spin"/>}
                            Create Topic
                        </Button>
                    </DialogFooter>
                </form>
            </DialogContent>
        </Dialog>
    );
}
