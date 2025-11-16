import {useState} from 'react'
import {useForm} from 'react-hook-form'
import {zodResolver} from '@hookform/resolvers/zod'
import * as z from 'zod'
import {Card, CardContent, CardDescription, CardHeader, CardTitle} from '@/components/ui/card'
import {Button} from '@/components/ui/button'
import {Input} from '@/components/ui/input'
import {Label} from '@/components/ui/label'
import {Key, ArrowLeft} from 'lucide-react'
import {useNavigate} from 'react-router-dom'
import {GET_MFA_STATUS} from '@/graphql/queries'
import type {GetMfaStatusQuery} from '@/graphql/types'
import {useGraphQLQuery} from '@/hooks/useGraphQLQuery'
import {useAuth} from '@/hooks/useAuth'
import {MfaSetupDialog} from '@/components/admin/MfaSetupDialog'
import {BackupCodesDialog} from '@/components/admin/BackupCodesDialog'
import {DISABLE_MFA_MUTATION, REGENERATE_BACKUP_CODES_MUTATION} from '@/graphql/mutations'
import {useGraphQLMutation} from '@/hooks/useGraphQLMutation'
import {toast} from 'sonner'
import {
    AlertDialog,
    AlertDialogCancel,
    AlertDialogContent,
    AlertDialogDescription,
    AlertDialogFooter,
    AlertDialogHeader,
    AlertDialogTitle,
} from '@/components/ui/alert-dialog'
import {GET_ME} from '@/graphql/queries'
import type {GetMeQuery} from '@/graphql/types'

const passwordSchema = z.object({
    password: z.string().min(1, 'Password is required'),
})

type PasswordFormData = z.infer<typeof passwordSchema>

export default function SettingsPage() {
    const navigate = useNavigate()
    const {user: authUser} = useAuth()
    const [mfaSetupOpen, setMfaSetupOpen] = useState(false)
    const [disableMfaOpen, setDisableMfaOpen] = useState(false)
    const [regenerateCodesOpen, setRegenerateCodesOpen] = useState(false)
    const [regeneratedBackupCodes, setRegeneratedBackupCodes] = useState<string[] | null>(null)
    const [backupCodesDialogOpen, setBackupCodesDialogOpen] = useState(false)
    
    const disableMfaForm = useForm<PasswordFormData>({
        resolver: zodResolver(passwordSchema),
    })
    
    const regenerateCodesForm = useForm<PasswordFormData>({
        resolver: zodResolver(passwordSchema),
    })
    
    const {data, isLoading, refetch: refetchMe} = useGraphQLQuery<GetMeQuery>(GET_ME)
    const {data: mfaStatusData, refetch: refetchMfaStatus} = useGraphQLQuery<GetMfaStatusQuery>(GET_MFA_STATUS, undefined, {
        enabled: !!data?.me,
    })
    
    const {mutate: disableMfaMutation, isPending: isDisablingMfa} = useGraphQLMutation(DISABLE_MFA_MUTATION)
    const {mutate: regenerateCodesMutation, isPending: isRegeneratingCodes} = useGraphQLMutation<{regenerateBackupCodes: string[]}>(REGENERATE_BACKUP_CODES_MUTATION)
    
    const user = data?.me || authUser
    const mfaStatus = mfaStatusData?.mfaStatus

    if (isLoading) {
        return (
            <div className="space-y-6">
                <div className="flex items-center gap-4">
                    <div className="h-10 w-10 rounded-md bg-secondary animate-pulse"/>
                    <div className="h-8 w-64 bg-secondary animate-pulse rounded"/>
                </div>
                <Card>
                    <CardHeader>
                        <div className="h-6 w-32 bg-secondary animate-pulse rounded"/>
                    </CardHeader>
                    <CardContent>
                        <div className="h-20 w-full bg-secondary animate-pulse rounded"/>
                    </CardContent>
                </Card>
            </div>
        )
    }

    return (
        <div className="space-y-6">
            <div className="flex items-center gap-4">
                <Button variant="ghost" size="icon" onClick={() => navigate(-1)}>
                    <ArrowLeft className="h-4 w-4"/>
                </Button>
                <h2 className="text-4xl font-bold tracking-tight bg-linear-to-r from-primary to-primary/80 bg-clip-text text-transparent">
                    Settings
                </h2>
            </div>

            <div className="grid gap-6 md:grid-cols-1">
                {/* Security Settings Card */}
                <Card>
                    <CardHeader>
                        <CardTitle className="flex items-center gap-2">
                            <Key className="h-5 w-5 text-primary"/>
                            Security Settings
                        </CardTitle>
                        <CardDescription>Manage your account security and MFA settings</CardDescription>
                    </CardHeader>
                    <CardContent className="space-y-6">
                        <div className="space-y-4">
                            <div className="flex items-center justify-between">
                                <div className="flex-1">
                                    <p className="text-sm font-medium">Multi-Factor Authentication</p>
                                    <p className="text-xs text-muted-foreground mt-1">
                                        {user?.mfaEnabled
                                            ? 'MFA is enabled for your account'
                                            : 'Add an extra layer of security to your account'}
                                    </p>
                                    {user?.mfaEnabled && mfaStatus && (
                                        <p className="text-xs text-muted-foreground mt-1">
                                            {mfaStatus.unusedBackupCodesCount} backup codes remaining
                                        </p>
                                    )}
                                </div>
                                <div className="flex gap-2">
                                    {user?.mfaEnabled ? (
                                        <>
                                            <Button
                                                variant="outline"
                                                size="sm"
                                                onClick={() => setRegenerateCodesOpen(true)}
                                            >
                                                Regenerate Codes
                                            </Button>
                                            <Button
                                                variant="destructive"
                                                size="sm"
                                                onClick={() => setDisableMfaOpen(true)}
                                            >
                                                Disable MFA
                                            </Button>
                                        </>
                                    ) : (
                                        <Button
                                            size="sm"
                                            onClick={() => setMfaSetupOpen(true)}
                                        >
                                            Enable MFA
                                        </Button>
                                    )}
                                </div>
                            </div>
                        </div>
                    </CardContent>
                </Card>
            </div>

            <MfaSetupDialog
                open={mfaSetupOpen}
                onOpenChange={setMfaSetupOpen}
                onSuccess={() => {
                    refetchMe()
                    refetchMfaStatus()
                    toast.success('MFA enabled successfully')
                }}
            />

            <AlertDialog open={disableMfaOpen} onOpenChange={(open) => {
                setDisableMfaOpen(open)
                if (!open) {
                    disableMfaForm.reset()
                }
            }}>
                <AlertDialogContent>
                    <AlertDialogHeader>
                        <AlertDialogTitle>Disable Multi-Factor Authentication?</AlertDialogTitle>
                        <AlertDialogDescription>
                            Are you sure you want to disable MFA? This will make your account less secure. You can
                            re-enable it at any time. Please enter your password to confirm.
                        </AlertDialogDescription>
                    </AlertDialogHeader>
                    <form onSubmit={disableMfaForm.handleSubmit((data) => {
                        if (!data.password || data.password.trim().length === 0) {
                            toast.error('Password is required')
                            return
                        }
                        disableMfaMutation({password: data.password}, {
                            onSuccess: () => {
                                refetchMe()
                                refetchMfaStatus()
                                setDisableMfaOpen(false)
                                disableMfaForm.reset()
                                toast.success('MFA disabled successfully')
                            },
                            onError: (error: Error) => {
                                const errorMessage = error?.message || 'Failed to disable MFA'
                                toast.error(errorMessage)
                            },
                        })
                    })}>
                        <div className="space-y-4 py-4">
                            <div className="space-y-2">
                                <Label htmlFor="disable-password">Password</Label>
                                <Input
                                    id="disable-password"
                                    type="password"
                                    {...disableMfaForm.register('password')}
                                    placeholder="Enter your password"
                                    autoFocus
                                />
                                {disableMfaForm.formState.errors.password && (
                                    <p className="text-sm text-destructive">
                                        {disableMfaForm.formState.errors.password.message}
                                    </p>
                                )}
                            </div>
                        </div>
                        <AlertDialogFooter>
                            <AlertDialogCancel type="button" disabled={isDisablingMfa}>Cancel</AlertDialogCancel>
                            <Button type="submit" disabled={isDisablingMfa}>
                                {isDisablingMfa ? 'Disabling...' : 'Disable MFA'}
                            </Button>
                        </AlertDialogFooter>
                    </form>
                </AlertDialogContent>
            </AlertDialog>

            <AlertDialog open={regenerateCodesOpen} onOpenChange={(open) => {
                setRegenerateCodesOpen(open)
                if (!open) {
                    regenerateCodesForm.reset()
                }
            }}>
                <AlertDialogContent>
                    <AlertDialogHeader>
                        <AlertDialogTitle>Regenerate Backup Codes?</AlertDialogTitle>
                        <AlertDialogDescription>
                            This will invalidate all your existing backup codes and generate new ones. Make sure to save
                            the new codes in a secure location. Please enter your password to confirm.
                        </AlertDialogDescription>
                    </AlertDialogHeader>
                    <form 
                        onSubmit={regenerateCodesForm.handleSubmit((data) => {
                            if (!data.password || data.password.trim().length === 0) {
                                toast.error('Password is required')
                                return
                            }
                            regenerateCodesMutation({password: data.password}, {
                                onSuccess: async (result) => {
                                    if (!result || !result.regenerateBackupCodes) {
                                        toast.error('Failed to regenerate backup codes: Invalid response')
                                        return
                                    }
                                    const codes = result.regenerateBackupCodes
                                    setRegeneratedBackupCodes(codes)
                                    setRegenerateCodesOpen(false)
                                    regenerateCodesForm.reset()
                                    // Refetch MFA status to update the backup codes count
                                    await refetchMfaStatus()
                                    setBackupCodesDialogOpen(true)
                                    toast.success('Backup codes regenerated successfully')
                                },
                                onError: (error: Error) => {
                                    const errorMessage = error?.message || 'Failed to regenerate backup codes'
                                    toast.error(errorMessage)
                                },
                            })
                        })}
                    >
                        <div className="space-y-4 py-4">
                            <div className="space-y-2">
                                <Label htmlFor="regenerate-password">Password</Label>
                                <Input
                                    id="regenerate-password"
                                    type="password"
                                    {...regenerateCodesForm.register('password')}
                                    placeholder="Enter your password"
                                    autoFocus
                                />
                                {regenerateCodesForm.formState.errors.password && (
                                    <p className="text-sm text-destructive">
                                        {regenerateCodesForm.formState.errors.password.message}
                                    </p>
                                )}
                            </div>
                        </div>
                        <AlertDialogFooter>
                            <AlertDialogCancel 
                                type="button" 
                                disabled={isRegeneratingCodes}
                            >
                                Cancel
                            </AlertDialogCancel>
                            <Button 
                                type="submit" 
                                disabled={isRegeneratingCodes}
                            >
                                {isRegeneratingCodes ? 'Regenerating...' : 'Regenerate'}
                            </Button>
                        </AlertDialogFooter>
                    </form>
                </AlertDialogContent>
            </AlertDialog>

            {regeneratedBackupCodes && (
                <BackupCodesDialog
                    open={backupCodesDialogOpen}
                    onOpenChange={(open) => {
                        setBackupCodesDialogOpen(open)
                        if (!open) {
                            setRegeneratedBackupCodes(null)
                        }
                    }}
                    backupCodes={regeneratedBackupCodes}
                    title="Your New Backup Codes"
                    description="Your old backup codes have been invalidated. Save these new backup codes in a secure location. Each code can only be used once."
                />
            )}
        </div>
    )
}

