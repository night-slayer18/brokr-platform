import {useState} from 'react'
import {useForm} from 'react-hook-form'
import {zodResolver} from '@hookform/resolvers/zod'
import * as z from 'zod'
import {toast} from 'sonner'
import {Button} from '@/components/ui/button'
import {Input} from '@/components/ui/input'
import {Label} from '@/components/ui/label'
import {
    Dialog,
    DialogContent,
    DialogDescription,
    DialogHeader,
    DialogTitle,
} from '@/components/ui/dialog'
import {SETUP_MFA_MUTATION, VERIFY_MFA_SETUP_MUTATION} from '@/graphql/mutations'
import {Loader2, Shield, Copy} from 'lucide-react'
import {useGraphQLMutation} from '@/hooks/useGraphQLMutation'
import {extractErrorMessage} from '@/lib/error-utils'
import type {SetupMfaMutation, VerifyMfaSetupMutation} from '@/graphql/types'
import {Alert, AlertDescription} from '@/components/ui/alert'
import {BackupCodesDialog} from './BackupCodesDialog'

const verifyCodeSchema = z.object({
    code: z.string().min(6, 'Code must be 6 digits').max(6, 'Code must be 6 digits'),
})

type VerifyCodeFormData = z.infer<typeof verifyCodeSchema>

interface MfaSetupDialogProps {
    open: boolean
    onOpenChange: (open: boolean) => void
    onSuccess: () => void
}

export function MfaSetupDialog({open, onOpenChange, onSuccess}: MfaSetupDialogProps) {
    const [setupResult, setSetupResult] = useState<SetupMfaMutation['setupMfa'] | null>(null)
    const [backupCodes, setBackupCodes] = useState<string[] | null>(null)
    const [backupCodesDialogOpen, setBackupCodesDialogOpen] = useState(false)
    const [step, setStep] = useState<'setup' | 'verify' | 'backup-codes'>('setup')

    const {mutate: setupMfaMutation, isPending: setupLoading} = useGraphQLMutation<SetupMfaMutation>(SETUP_MFA_MUTATION)
    const {mutate: verifySetupMutation, isPending: verifyLoading} = useGraphQLMutation<
        VerifyMfaSetupMutation,
        {deviceId: string; code: string}
    >(VERIFY_MFA_SETUP_MUTATION)

    const {
        register,
        handleSubmit,
        formState: {errors},
    } = useForm<VerifyCodeFormData>({
        resolver: zodResolver(verifyCodeSchema),
    })

    const handleSetup = () => {
        setupMfaMutation(undefined, {
            onSuccess: (result) => {
                setSetupResult(result.setupMfa)
                setStep('verify')
            },
            onError: (error: Error) => {
                const errorMessage = extractErrorMessage(error)
                toast.error(errorMessage || 'Failed to setup MFA')
            },
        })
    }

    const handleVerify = async (data: VerifyCodeFormData) => {
        if (!setupResult) return

        verifySetupMutation(
            {
                deviceId: setupResult.deviceId,
                code: data.code,
            },
            {
                onSuccess: (result) => {
                    setBackupCodes(result.verifyMfaSetup.backupCodes)
                    setBackupCodesDialogOpen(true)
                    setStep('backup-codes')
                },
                onError: (error: Error) => {
                    const errorMessage = extractErrorMessage(error)
                    toast.error(errorMessage || 'Invalid code. Please try again.')
                },
            }
        )
    }

    const handleComplete = () => {
        setSetupResult(null)
        setBackupCodes(null)
        setStep('setup')
        setBackupCodesDialogOpen(false)
        onSuccess()
        onOpenChange(false)
    }

    const handleClose = () => {
        if (step === 'backup-codes') {
            // Warn user if they haven't saved backup codes
            toast.warning('Make sure you have saved your backup codes. You will not be able to see them again.')
        }
        setSetupResult(null)
        setBackupCodes(null)
        setStep('setup')
        onOpenChange(false)
    }

    return (
        <Dialog open={open} onOpenChange={handleClose}>
            <DialogContent className="max-w-2xl max-h-[90vh] overflow-y-auto">
                <DialogHeader>
                    <DialogTitle className="flex items-center gap-2">
                        <Shield className="h-5 w-5"/>
                        Setup Multi-Factor Authentication
                    </DialogTitle>
                    <DialogDescription>
                        Secure your account with TOTP-based two-factor authentication
                    </DialogDescription>
                </DialogHeader>

                {step === 'setup' && (
                    <div className="space-y-4">
                        <Alert>
                            <AlertDescription>
                                To enable MFA, you'll need an authenticator app like Google Authenticator, Authy, or
                                Microsoft Authenticator installed on your mobile device.
                            </AlertDescription>
                        </Alert>
                        <Button onClick={handleSetup} disabled={setupLoading} className="w-full">
                            {setupLoading && <Loader2 className="mr-2 h-4 w-4 animate-spin"/>}
                            {setupLoading ? 'Generating QR Code...' : 'Start MFA Setup'}
                        </Button>
                    </div>
                )}

                {step === 'verify' && setupResult && (
                    <div className="space-y-6">
                        <div className="space-y-4">
                            <div>
                                <Label className="text-base font-semibold">Step 1: Scan QR Code</Label>
                                <p className="text-sm text-muted-foreground mt-1">
                                    Scan this QR code with your authenticator app
                                </p>
                            </div>
                            <div className="flex justify-center p-4 bg-white rounded-lg border-2 border-border">
                                <img
                                    src={setupResult.qrCodeDataUrl}
                                    alt="MFA QR Code"
                                    className="w-64 h-64"
                                />
                            </div>
                            <div className="text-center">
                                <p className="text-sm text-muted-foreground mb-2">Or enter this code manually:</p>
                                <div className="flex items-center justify-center gap-2">
                                    <code className="px-3 py-2 bg-secondary rounded-md font-mono text-sm">
                                        {setupResult.secretKey}
                                    </code>
                                    <Button
                                        variant="outline"
                                        size="sm"
                                        onClick={() => {
                                            navigator.clipboard.writeText(setupResult.secretKey)
                                            toast.success('Secret key copied to clipboard')
                                        }}
                                    >
                                        <Copy className="h-4 w-4"/>
                                    </Button>
                                </div>
                            </div>
                        </div>

                        <div className="space-y-4">
                            <div>
                                <Label className="text-base font-semibold">Step 2: Verify Setup</Label>
                                <p className="text-sm text-muted-foreground mt-1">
                                    Enter the 6-digit code from your authenticator app to verify
                                </p>
                            </div>
                            <form onSubmit={handleSubmit(handleVerify)} className="space-y-4">
                                <div className="space-y-2">
                                    <Label htmlFor="code">Verification Code</Label>
                                    <Input
                                        id="code"
                                        type="text"
                                        {...register('code')}
                                        placeholder="000000"
                                        disabled={verifyLoading}
                                        className="text-center text-2xl tracking-widest"
                                        maxLength={6}
                                        autoComplete="one-time-code"
                                        autoFocus
                                    />
                                    {errors.code && (
                                        <p className="text-sm text-destructive">{errors.code.message}</p>
                                    )}
                                </div>
                                <Button type="submit" disabled={verifyLoading} className="w-full">
                                    {verifyLoading && <Loader2 className="mr-2 h-4 w-4 animate-spin"/>}
                                    {verifyLoading ? 'Verifying...' : 'Verify & Enable MFA'}
                                </Button>
                            </form>
                        </div>
                    </div>
                )}

                {step === 'backup-codes' && (
                    <div className="space-y-6">
                        <Alert>
                            <AlertDescription>
                                MFA has been successfully enabled! Please save your backup codes before continuing.
                            </AlertDescription>
                        </Alert>
                        <Button onClick={() => setBackupCodesDialogOpen(true)} className="w-full">
                            View Backup Codes
                        </Button>
                        <Button onClick={handleComplete} variant="outline" className="w-full">
                            Continue
                        </Button>
                    </div>
                )}
            </DialogContent>

            {backupCodes && (
                <BackupCodesDialog
                    open={backupCodesDialogOpen}
                    onOpenChange={(open) => {
                        setBackupCodesDialogOpen(open)
                        if (!open && step === 'backup-codes') {
                            // Allow closing the backup codes dialog, but keep the main dialog open
                            // User can click "Continue" to close the main dialog
                        }
                    }}
                    backupCodes={backupCodes}
                    title="Your Backup Codes"
                    description="Save these backup codes in a secure location. You can use them to access your account if you lose your authenticator device. Each code can only be used once."
                />
            )}
        </Dialog>
    )
}

