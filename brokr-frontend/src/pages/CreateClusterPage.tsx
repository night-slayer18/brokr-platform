import {useQueryClient} from '@tanstack/react-query'
import {useNavigate} from 'react-router-dom'
import {useForm, Controller} from 'react-hook-form'
import {zodResolver} from '@hookform/resolvers/zod'
import * as z from 'zod'
import {toast} from 'sonner'
import {Button} from '@/components/ui/button'
import {Input} from '@/components/ui/input'
import {Label} from '@/components/ui/label'
import {Card, CardContent, CardDescription, CardHeader, CardTitle} from '@/components/ui/card'
import {CREATE_CLUSTER_MUTATION} from '@/graphql/mutations'
import type {
    CreateClusterMutation,
    GetEnvironmentsByOrganizationQuery,
    GetOrganizationQuery,
    GetOrganizationsQuery,
} from '@/graphql/types'
import {useGraphQLQuery} from '@/hooks/useGraphQLQuery';
import {useGraphQLMutation} from '@/hooks/useGraphQLMutation';
import {Loader2} from 'lucide-react'
import {Select, SelectContent, SelectItem, SelectTrigger, SelectValue} from '@/components/ui/select'
import {Switch} from '@/components/ui/switch'
import {Textarea} from '@/components/ui/textarea'
import {GET_CLUSTERS, GET_ENVIRONMENTS_BY_ORGANIZATION, GET_ORGANIZATION, GET_ORGANIZATIONS} from '@/graphql/queries'
import {SECURITY_PROTOCOLS} from '@/lib/constants'
import {useEffect} from 'react'
import {useAuth} from '@/hooks/useAuth'

const kafkaClusterSchema = z.object({
    name: z.string().min(1, 'Name is required'),
    bootstrapServers: z.string().min(1, 'Bootstrap Servers are required'),
    description: z.string().optional(),
    isActive: z.boolean(),
    securityProtocol: z.enum(SECURITY_PROTOCOLS).optional(),
    saslMechanism: z.string().optional(),
    saslUsername: z.string().optional(),
    saslPassword: z.string().optional(),
    sslTruststoreLocation: z.string().optional(),
    sslTruststorePassword: z.string().optional(),
    sslKeystoreLocation: z.string().optional(),
    sslKeystorePassword: z.string().optional(),
    sslKeyPassword: z.string().optional(),
    // JMX Configuration
    jmxEnabled: z.boolean().optional(),
    jmxPort: z.string().optional(),
    jmxAuthentication: z.boolean().optional(),
    jmxUsername: z.string().optional(),
    jmxPassword: z.string().optional(),
    jmxSsl: z.boolean().optional(),
    organizationId: z.string().min(1, 'Organization is required'),
    environmentId: z.string().min(1, 'Environment is required'),
});

type KafkaClusterFormData = z.infer<typeof kafkaClusterSchema>;

export default function CreateClusterPage() {
    const navigate = useNavigate();
    const {user} = useAuth();
    const isSuperAdmin = user?.role === 'SUPER_ADMIN';

    const {
        register,
        handleSubmit,
        formState: {errors},
        setValue,
        watch,
        reset,
        control,
    } = useForm<KafkaClusterFormData>({
        resolver: zodResolver(kafkaClusterSchema),
        defaultValues: {
            name: '',
            bootstrapServers: '',
            description: '',
            isActive: true,
            securityProtocol: 'PLAINTEXT',
            jmxEnabled: false,
            jmxPort: '',
            jmxAuthentication: false,
            jmxSsl: false,
            organizationId: user?.organizationId || '',
            environmentId: '',
        },
    });

    const selectedOrganizationId = watch('organizationId');
    const securityProtocol = watch('securityProtocol');
    const jmxEnabled = watch('jmxEnabled');
    const jmxAuthentication = watch('jmxAuthentication');

    const queryClient = useQueryClient();
    
    const {
        data: organizationsData,
        isLoading: organizationsLoading
    } = useGraphQLQuery<GetOrganizationsQuery>(GET_ORGANIZATIONS, undefined, {
        enabled: isSuperAdmin,
    });

    const {
        data: singleOrganizationData,
        isLoading: singleOrganizationLoading
    } = useGraphQLQuery<GetOrganizationQuery, {id: string}>(GET_ORGANIZATION, 
        user?.organizationId ? {id: user.organizationId} : undefined,
        {
            enabled: !isSuperAdmin && !!user?.organizationId,
        }
    );

    const {
        data: environmentsData,
        isLoading: environmentsLoading
    } = useGraphQLQuery<GetEnvironmentsByOrganizationQuery, {organizationId: string}>(GET_ENVIRONMENTS_BY_ORGANIZATION, 
        selectedOrganizationId ? {organizationId: selectedOrganizationId} : undefined,
        {
            enabled: !!selectedOrganizationId,
        }
    );

    const {mutate: createCluster, isPending: createClusterLoading} = useGraphQLMutation<CreateClusterMutation, any>(CREATE_CLUSTER_MUTATION, {
        onSuccess: () => {
            queryClient.invalidateQueries({queryKey: ['graphql', GET_CLUSTERS]});
        },
    });

    useEffect(() => {
        // Pre-fill the organization for non-super-admins once the user object is available.
        if (user?.organizationId && !isSuperAdmin) {
            setValue('organizationId', user.organizationId);
        }
        // When the user changes (e.g. on login), reset the form
        reset({
            organizationId: user?.organizationId || '',
            environmentId: '',
        });
    }, [user, isSuperAdmin, setValue, reset]);


    const onSubmit = async (formData: KafkaClusterFormData) => {
        const input = {
            ...formData,
            jmxPort: formData.jmxPort ? parseInt(formData.jmxPort, 10) : undefined,
        };
        createCluster(
            {
                input,
            },
            {
                onSuccess: () => {
                    toast.success(`Cluster "${formData.name}" created successfully`);
                    queryClient.invalidateQueries({queryKey: ['graphql', GET_CLUSTERS]});
                    navigate('/clusters');
                },
                onError: (error: unknown) => {
                    const err = error instanceof Error ? error : {message: 'Failed to create cluster'}
                    toast.error(err.message || 'Failed to create cluster');
                },
            }
        );
    };

    const loading = createClusterLoading || organizationsLoading || singleOrganizationLoading || environmentsLoading;
    const organizations = organizationsData?.organizations || [];
    const environments = environmentsData?.environments || [];

    return (
        <div className="space-y-6">
            <div>
                <h2 className="text-4xl font-bold tracking-tight bg-linear-to-r from-primary to-primary/80 bg-clip-text text-transparent">
                    Create New Kafka Cluster
                </h2>
                <p className="text-muted-foreground mt-2">
                    Configure and add a new Kafka cluster to your organization.
                </p>
            </div>

            <Card className="p-6">
                <CardHeader>
                    <CardTitle>Cluster Details</CardTitle>
                    <CardDescription>Enter the basic information for your new Kafka cluster.</CardDescription>
                </CardHeader>
                <CardContent>
                    <form onSubmit={handleSubmit(onSubmit)} className="space-y-6">
                        <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
                            <div className="space-y-2">
                                <Label htmlFor="name">Cluster Name</Label>
                                <Input id="name" {...register('name')} disabled={loading}/>
                                {errors.name && <p className="text-sm text-destructive">{errors.name.message}</p>}
                            </div>
                            <div className="space-y-2">
                                <Label htmlFor="bootstrapServers">Bootstrap Servers</Label>
                                <Input id="bootstrapServers" {...register('bootstrapServers')} disabled={loading}
                                       placeholder="localhost:9092"/>
                                {errors.bootstrapServers &&
                                    <p className="text-sm text-destructive">{errors.bootstrapServers.message}</p>}
                            </div>
                        </div>

                        <div className="space-y-2">
                            <Label htmlFor="description">Description</Label>
                            <Textarea id="description" {...register('description')} disabled={loading} rows={3}/>
                            {errors.description &&
                                <p className="text-sm text-destructive">{errors.description.message}</p>}
                        </div>

                        <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
                            <div className="space-y-2">
                                <Label htmlFor="organizationId">Organization</Label>
                                <Select
                                    onValueChange={(value) => {
                                        setValue('organizationId', value);
                                        setValue('environmentId', ''); // Reset environment on org change
                                    }}
                                    value={selectedOrganizationId}
                                    disabled={loading || !isSuperAdmin}
                                >
                                    <SelectTrigger>
                                        <SelectValue placeholder="Select an organization"/>
                                    </SelectTrigger>
                                    <SelectContent>
                                        {isSuperAdmin ? (
                                            organizations.map((org) => (
                                                <SelectItem key={org.id} value={org.id}>
                                                    {org.name}
                                                </SelectItem>
                                            ))
                                        ) : (
                                            singleOrganizationData?.organization && (
                                                <SelectItem
                                                    key={singleOrganizationData.organization.id}
                                                    value={singleOrganizationData.organization.id}
                                                >
                                                    {singleOrganizationData.organization.name}
                                                </SelectItem>
                                            )
                                        )}
                                    </SelectContent>
                                </Select>
                                {errors.organizationId &&
                                    <p className="text-sm text-destructive">{errors.organizationId.message}</p>}
                            </div>
                            <div className="space-y-2">
                                <Label htmlFor="environmentId">Environment</Label>
                                <Select
                                    onValueChange={(value) => setValue('environmentId', value)}
                                    value={watch('environmentId')}
                                    disabled={loading || !selectedOrganizationId}
                                >
                                    <SelectTrigger>
                                        <SelectValue placeholder="Select an environment"/>
                                    </SelectTrigger>
                                    <SelectContent>
                                        {environments.map((env) => (
                                            <SelectItem key={env.id} value={env.id}>
                                                {env.name}
                                            </SelectItem>
                                        ))}
                                    </SelectContent>
                                </Select>
                                {errors.environmentId &&
                                    <p className="text-sm text-destructive">{errors.environmentId.message}</p>}
                            </div>
                        </div>

                        <div className="flex items-center space-x-2">
                            <Controller
                                name="isActive"
                                control={control}
                                render={({field}) => (
                                    <Switch
                                        id="isActive"
                                        checked={field.value}
                                        onCheckedChange={field.onChange}
                                        disabled={loading}
                                    />
                                )}
                            />
                            <Label htmlFor="isActive">Active</Label>
                        </div>

                        <CardTitle className="mt-8">Security Settings</CardTitle>
                        <CardDescription>Configure security protocols for connecting to the cluster.</CardDescription>

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
                            <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
                                <div className="space-y-2">
                                    <Label htmlFor="saslMechanism">SASL Mechanism</Label>
                                    <Input id="saslMechanism" {...register('saslMechanism')} disabled={loading}/>
                                    {errors.saslMechanism &&
                                        <p className="text-sm text-destructive">{errors.saslMechanism.message}</p>}
                                </div>
                                <div className="space-y-2">
                                    <Label htmlFor="saslUsername">SASL Username</Label>
                                    <Input id="saslUsername" {...register('saslUsername')} disabled={loading}/>
                                    {errors.saslUsername &&
                                        <p className="text-sm text-destructive">{errors.saslUsername.message}</p>}
                                </div>
                                <div className="space-y-2">
                                    <Label htmlFor="saslPassword">SASL Password</Label>
                                    <Input id="saslPassword" type="password" {...register('saslPassword')}
                                           disabled={loading}/>
                                    {errors.saslPassword &&
                                        <p className="text-sm text-destructive">{errors.saslPassword.message}</p>}
                                </div>
                            </div>
                        )}

                        {(securityProtocol === 'SSL' || securityProtocol === 'SASL_SSL') && (
                            <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
                                <div className="space-y-2">
                                    <Label htmlFor="sslTruststoreLocation">SSL Truststore Location</Label>
                                    <Input id="sslTruststoreLocation" {...register('sslTruststoreLocation')}
                                           disabled={loading}/>
                                    {errors.sslTruststoreLocation &&
                                        <p className="text-sm text-destructive">{errors.sslTruststoreLocation.message}</p>}
                                </div>
                                <div className="space-y-2">
                                    <Label htmlFor="sslTruststorePassword">SSL Truststore Password</Label>
                                    <Input id="sslTruststorePassword" type="password"
                                           {...register('sslTruststorePassword')} disabled={loading}/>
                                    {errors.sslTruststorePassword &&
                                        <p className="text-sm text-destructive">{errors.sslTruststorePassword.message}</p>}
                                </div>
                                <div className="space-y-2">
                                    <Label htmlFor="sslKeystoreLocation">SSL Keystore Location</Label>
                                    <Input id="sslKeystoreLocation" {...register('sslKeystoreLocation')}
                                           disabled={loading}/>
                                    {errors.sslKeystoreLocation &&
                                        <p className="text-sm text-destructive">{errors.sslKeystoreLocation.message}</p>}
                                </div>
                                <div className="space-y-2">
                                    <Label htmlFor="sslKeystorePassword">SSL Keystore Password</Label>
                                    <Input id="sslKeystorePassword" type="password"
                                           {...register('sslKeystorePassword')} disabled={loading}/>
                                    {errors.sslKeystorePassword &&
                                        <p className="text-sm text-destructive">{errors.sslKeystorePassword.message}</p>}
                                </div>
                                <div className="space-y-2">
                                    <Label htmlFor="sslKeyPassword">SSL Key Password</Label>
                                    <Input id="sslKeyPassword" type="password" {...register('sslKeyPassword')}
                                           disabled={loading}/>
                                    {errors.sslKeyPassword &&
                                        <p className="text-sm text-destructive">{errors.sslKeyPassword.message}</p>}
                                </div>
                            </div>
                        )}

                        <CardTitle className="mt-8">JMX Monitoring Settings</CardTitle>
                        <CardDescription>Configure JMX for broker-level monitoring metrics (CPU, memory, disk, throughput).</CardDescription>

                        <div className="flex items-center space-x-2 mt-4">
                            <Controller
                                name="jmxEnabled"
                                control={control}
                                render={({field}) => (
                                    <Switch
                                        id="jmxEnabled"
                                        checked={field.value}
                                        onCheckedChange={field.onChange}
                                        disabled={loading}
                                    />
                                )}
                            />
                            <Label htmlFor="jmxEnabled">Enable JMX Monitoring</Label>
                        </div>

                        {jmxEnabled && (
                            <div className="space-y-4 mt-4 p-4 rounded-lg bg-muted/50 border">
                                <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
                                    <div className="space-y-2">
                                        <Label htmlFor="jmxPort">JMX Port</Label>
                                        <Input 
                                            id="jmxPort" 
                                            type="text" 
                                            inputMode="numeric"
                                            pattern="[0-9]*"
                                            {...register('jmxPort')} 
                                            disabled={loading}
                                            placeholder="9999"
                                        />
                                    </div>
                                </div>

                                <div className="flex items-center space-x-2">
                                    <Controller
                                        name="jmxSsl"
                                        control={control}
                                        render={({field}) => (
                                            <Switch
                                                id="jmxSsl"
                                                checked={field.value}
                                                onCheckedChange={field.onChange}
                                                disabled={loading}
                                            />
                                        )}
                                    />
                                    <Label htmlFor="jmxSsl">Use SSL for JMX</Label>
                                </div>

                                <div className="flex items-center space-x-2">
                                    <Controller
                                        name="jmxAuthentication"
                                        control={control}
                                        render={({field}) => (
                                            <Switch
                                                id="jmxAuthentication"
                                                checked={field.value}
                                                onCheckedChange={field.onChange}
                                                disabled={loading}
                                            />
                                        )}
                                    />
                                    <Label htmlFor="jmxAuthentication">JMX Authentication Required</Label>
                                </div>

                                {jmxAuthentication && (
                                    <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
                                        <div className="space-y-2">
                                            <Label htmlFor="jmxUsername">JMX Username</Label>
                                            <Input 
                                                id="jmxUsername" 
                                                {...register('jmxUsername')} 
                                                disabled={loading}
                                            />
                                        </div>
                                        <div className="space-y-2">
                                            <Label htmlFor="jmxPassword">JMX Password</Label>
                                            <Input 
                                                id="jmxPassword" 
                                                type="password" 
                                                {...register('jmxPassword')} 
                                                disabled={loading}
                                            />
                                        </div>
                                    </div>
                                )}
                            </div>
                        )}

                        <div className="flex justify-end gap-2">
                            <Button type="button" variant="outline" onClick={() => navigate(-1)} disabled={loading}>
                                Cancel
                            </Button>
                            <Button type="submit" disabled={loading}>
                                {loading && <Loader2 className="mr-2 h-4 w-4 animate-spin"/>}
                                Create Cluster
                            </Button>
                        </div>
                    </form>
                </CardContent>
            </Card>
        </div>
    );
}
