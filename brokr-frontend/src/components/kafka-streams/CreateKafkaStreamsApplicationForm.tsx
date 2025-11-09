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
import {CREATE_KAFKA_STREAMS_APPLICATION_MUTATION} from '@/graphql/mutations';
import {toast} from 'sonner';
import {Loader2} from 'lucide-react';
import {Textarea} from '@/components/ui/textarea';
import type {
    CreateKafkaStreamsApplicationMutation
} from "@/graphql/types";
import {useGraphQLMutation} from '@/hooks/useGraphQLMutation';
import {useQueryClient} from '@tanstack/react-query';
import {GET_KAFKA_STREAMS} from '@/graphql/queries';

const kafkaStreamsApplicationSchema = z.object({
    name: z.string().min(1, 'Name is required'),
    applicationId: z.string().min(1, 'Application ID is required'),
    topics: z.string().optional(), // Comma-separated topics
    configuration: z.string().optional(), // JSON string
    isActive: z.boolean(),
});

type KafkaStreamsApplicationFormData = z.infer<typeof kafkaStreamsApplicationSchema>;

interface CreateKafkaStreamsApplicationFormProps {
    clusterId: string;
    isOpen: boolean;
    onOpenChange: (isOpen: boolean) => void;
    onKafkaStreamsApplicationCreated: () => void;
}

export function CreateKafkaStreamsApplicationForm({
                                                      clusterId,
                                                      isOpen,
                                                      onOpenChange,
                                                      onKafkaStreamsApplicationCreated
                                                  }: CreateKafkaStreamsApplicationFormProps) {
    const queryClient = useQueryClient();
    const {mutate: createKafkaStreamsApplication, isPending: loading} = useGraphQLMutation<CreateKafkaStreamsApplicationMutation, {input: any}>(CREATE_KAFKA_STREAMS_APPLICATION_MUTATION);

    const {
        register,
        handleSubmit,
        formState: {errors},
        reset,
    } = useForm<KafkaStreamsApplicationFormData>({
        resolver: zodResolver(kafkaStreamsApplicationSchema),
        defaultValues: {
            name: '',
            applicationId: '',
            topics: '',
            configuration: '{}',
            isActive: true,
        },
    });

    const onSubmit = async (data: KafkaStreamsApplicationFormData) => {
        const topicsArray = data.topics ? data.topics.split(',').map(s => s.trim()) : [];
        const configurationObject = data.configuration ? JSON.parse(data.configuration) : {};

        createKafkaStreamsApplication(
            {
                input: {
                    clusterId,
                    name: data.name,
                    applicationId: data.applicationId,
                    topics: topicsArray,
                    configuration: configurationObject,
                    isActive: data.isActive,
                },
            },
            {
                onSuccess: () => {
                    toast.success(`Kafka Streams Application "${data.name}" created successfully`);
                    queryClient.invalidateQueries({queryKey: ['graphql', GET_KAFKA_STREAMS]});
                    onKafkaStreamsApplicationCreated();
                    onOpenChange(false);
                    reset();
                },
                onError: (error: unknown) => {
                    const err = error instanceof Error ? error : {message: 'Failed to create Kafka Streams Application'}
                    toast.error(err.message || 'Failed to create Kafka Streams Application');
                },
            }
        );
    };

    return (
        <Dialog open={isOpen} onOpenChange={onOpenChange}>
            <DialogContent>
                <DialogHeader>
                    <DialogTitle>Create New Kafka Streams Application</DialogTitle>
                    <DialogDescription>
                        Configure and create a new Kafka Streams Application for cluster <span
                        className="font-mono">{clusterId}</span>.
                    </DialogDescription>
                </DialogHeader>
                <form onSubmit={handleSubmit(onSubmit)} className="space-y-4">
                    <div className="space-y-2">
                        <Label htmlFor="name">Name</Label>
                        <Input id="name" {...register('name')} disabled={loading}/>
                        {errors.name && <p className="text-sm text-destructive">{errors.name.message}</p>}
                    </div>
                    <div className="space-y-2">
                        <Label htmlFor="applicationId">Application ID</Label>
                        <Input id="applicationId" {...register('applicationId')} disabled={loading}/>
                        {errors.applicationId &&
                            <p className="text-sm text-destructive">{errors.applicationId.message}</p>}
                    </div>
                    <div className="space-y-2">
                        <Label htmlFor="topics">Topics (comma-separated)</Label>
                        <Input id="topics" {...register('topics')} disabled={loading} placeholder="topic1, topic2"/>
                        {errors.topics && <p className="text-sm text-destructive">{errors.topics.message}</p>}
                    </div>
                    <div className="space-y-2">
                        <Label htmlFor="configuration">Configuration (JSON)</Label>
                        <Textarea id="configuration" {...register('configuration')} disabled={loading} rows={5}/>
                        {errors.configuration &&
                            <p className="text-sm text-destructive">{errors.configuration.message}</p>}
                    </div>

                    <DialogFooter>
                        <Button type="button" variant="outline" onClick={() => onOpenChange(false)} disabled={loading}>
                            Cancel
                        </Button>
                        <Button type="submit" disabled={loading}>
                            {loading && <Loader2 className="mr-2 h-4 w-4 animate-spin"/>}
                            Create Application
                        </Button>
                    </DialogFooter>
                </form>
            </DialogContent>
        </Dialog>
    );
}
