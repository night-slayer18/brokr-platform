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
import {CREATE_KAFKA_CONNECT_MUTATION} from '@/graphql/mutations';
import type {CreateKafkaConnectMutation, CreateKafkaConnectMutationVariables} from '@/graphql/types';
import {toast} from 'sonner';
import {Loader2} from 'lucide-react';
import {Select, SelectContent, SelectItem, SelectTrigger, SelectValue} from '@/components/ui/select';
import {SECURITY_PROTOCOLS} from '@/lib/constants';

const kafkaConnectSchema = z.object({
    name: z.string().min(1, 'Name is required'),
    url: z.string().url('Must be a valid URL'),
    securityProtocol: z.enum(SECURITY_PROTOCOLS).optional(),
    username: z.string().optional(),
    password: z.string().optional(),
    isActive: z.boolean(),
});

type KafkaConnectFormData = z.infer<typeof kafkaConnectSchema>;

interface CreateKafkaConnectFormProps {
    clusterId: string;
    isOpen: boolean;
    onOpenChange: (isOpen: boolean) => void;
    onKafkaConnectCreated: () => void;
}

export function CreateKafkaConnectForm({
                                           clusterId,
                                           isOpen,
                                           onOpenChange,
                                           onKafkaConnectCreated
                                       }: CreateKafkaConnectFormProps) {
    const [createKafkaConnect, {loading}] = useMutation<CreateKafkaConnectMutation, CreateKafkaConnectMutationVariables>(CREATE_KAFKA_CONNECT_MUTATION);

    const {
        register,
        handleSubmit,
        formState: {errors},
        reset,
        setValue,
        watch,
    } = useForm<KafkaConnectFormData>({
        resolver: zodResolver(kafkaConnectSchema),
        defaultValues: {
            name: '',
            url: '',
            securityProtocol: 'PLAINTEXT',
            username: '',
            password: '',
            isActive: true,
        },
    });

    const securityProtocol = watch('securityProtocol');

    const onSubmit = async (data: KafkaConnectFormData) => {
        try {
            await createKafkaConnect({
                variables: {
                    input: {
                        clusterId,
                        name: data.name,
                        url: data.url,
                        securityProtocol: data.securityProtocol,
                        username: data.username,
                        password: data.password,
                        isActive: data.isActive,
                    },
                },
            });
            toast.success(`Kafka Connect "${data.name}" created successfully`);
            onKafkaConnectCreated();
            onOpenChange(false);
            reset();
        } catch (error: unknown) {
            const err = error instanceof Error ? error : {message: 'Failed to create Kafka Connect'}
            toast.error(err.message || 'Failed to create Kafka Connect');
        }
    };

    return (
        <Dialog open={isOpen} onOpenChange={onOpenChange}>
            <DialogContent>
                <DialogHeader>
                    <DialogTitle>Create New Kafka Connect</DialogTitle>
                    <DialogDescription>
                        Configure and create a new Kafka Connect instance for cluster <span
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
                        <Label htmlFor="url">URL</Label>
                        <Input id="url" {...register('url')} disabled={loading}/>
                        {errors.url && <p className="text-sm text-destructive">{errors.url.message}</p>}
                    </div>

                    <div className="space-y-2">
                        <Label htmlFor="securityProtocol">Security Protocol</Label>
                        <Select
                            onValueChange={(value) => setValue('securityProtocol', value as 'PLAINTEXT' | 'SSL' | 'SASL_PLAINTEXT' | 'SASL_SSL')}
                            value={securityProtocol}
                            disabled={loading}
                        >
                            <SelectTrigger>
                                <SelectValue placeholder="Select a security protocol"/>
                            </SelectTrigger>
                            <SelectContent>
                                {SECURITY_PROTOCOLS.map((protocol) => (
                                    <SelectItem key={protocol} value={protocol}>
                                        {protocol}
                                    </SelectItem>
                                ))}
                            </SelectContent>
                        </Select>
                        {errors.securityProtocol &&
                            <p className="text-sm text-destructive">{errors.securityProtocol.message}</p>}
                    </div>

                    {(securityProtocol === 'SASL_PLAINTEXT' || securityProtocol === 'SASL_SSL') && (
                        <div className="grid grid-cols-2 gap-4">
                            <div className="space-y-2">
                                <Label htmlFor="username">Username</Label>
                                <Input id="username" {...register('username')} disabled={loading}/>
                                {errors.username &&
                                    <p className="text-sm text-destructive">{errors.username.message}</p>}
                            </div>
                            <div className="space-y-2">
                                <Label htmlFor="password">Password</Label>
                                <Input id="password" type="password" {...register('password')} disabled={loading}/>
                                {errors.password &&
                                    <p className="text-sm text-destructive">{errors.password.message}</p>}
                            </div>
                        </div>
                    )}

                    <DialogFooter>
                        <Button type="button" variant="outline" onClick={() => onOpenChange(false)} disabled={loading}>
                            Cancel
                        </Button>
                        <Button type="submit" disabled={loading}>
                            {loading && <Loader2 className="mr-2 h-4 w-4 animate-spin"/>}
                            Create Kafka Connect
                        </Button>
                    </DialogFooter>
                </form>
            </DialogContent>
        </Dialog>
    );
}
