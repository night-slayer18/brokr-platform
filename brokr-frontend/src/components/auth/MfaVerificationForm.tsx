import {useState} from 'react'
import {useForm} from 'react-hook-form'
import {zodResolver} from '@hookform/resolvers/zod'
import * as z from 'zod'
import {toast} from 'sonner'
import {Button} from '@/components/ui/button'
import {Input} from '@/components/ui/input'
import {Label} from '@/components/ui/label'
import {Card, CardContent, CardDescription, CardHeader, CardTitle} from '@/components/ui/card'
import {VERIFY_MFA_CODE_MUTATION} from '@/graphql/mutations'
import {useAuthStore} from '@/store/authStore'
import {Loader2, Shield} from 'lucide-react'
import {useGraphQLMutation} from '@/hooks/useGraphQLMutation'
import {extractErrorMessage} from '@/lib/error-utils'
import type {VerifyMfaCodeMutation} from '@/graphql/types'
import {Checkbox} from '@/components/ui/checkbox'

const mfaCodeSchema = z.object({
    code: z.string().min(6, 'Code must be 6 digits').max(8, 'Code must be 6-8 characters'),
    isBackupCode: z.boolean(),
})

type MfaCodeFormData = z.infer<typeof mfaCodeSchema>

interface MfaVerificationFormProps {
    challengeToken: string
    mfaType: string
    onSuccess: () => void
    onCancel: () => void
}

export function MfaVerificationForm({challengeToken, onSuccess, onCancel}: MfaVerificationFormProps) {
    const login = useAuthStore((state) => state.login)
    const [isBackupCode, setIsBackupCode] = useState(false)
    const {mutate: verifyMfaMutation, isPending: loading} = useGraphQLMutation<
        VerifyMfaCodeMutation,
        {challengeToken: string; code: string; isBackupCode: boolean}
    >(VERIFY_MFA_CODE_MUTATION)

    const {
        register,
        handleSubmit,
        formState: {errors},
        setValue,
        watch,
    } = useForm<MfaCodeFormData>({
        resolver: zodResolver(mfaCodeSchema),
        defaultValues: {
            isBackupCode: false,
        },
    })

    const backupCodeChecked = watch('isBackupCode', false)

    const onSubmit = async (data: MfaCodeFormData) => {
        verifyMfaMutation(
            {
                challengeToken,
                code: data.code,
                isBackupCode: data.isBackupCode,
            },
            {
                onSuccess: (result) => {
                    const {user} = result.verifyMfaCode
                    if (user) {
                        login(user)
                        toast.success('MFA verified successfully')
                        onSuccess()
                    } else {
                        toast.error('Authentication failed')
                    }
                },
                onError: (error: Error) => {
                    const errorMessage = extractErrorMessage(error)
                    toast.error(errorMessage || 'Invalid MFA code. Please try again.')
                },
            }
        )
    }

    return (
        <Card className="w-full min-w-[400px] max-w-lg mx-auto backdrop-blur-xl bg-card/50 border-2 border-primary/20 shadow-2xl shadow-primary/20">
            <CardHeader className="space-y-1 pb-6">
                <div className="flex items-center justify-center mb-6">
                    <div className="p-3 rounded-full bg-primary/10">
                        <Shield className="h-8 w-8 text-primary"/>
                    </div>
                </div>
                <CardTitle className="text-2xl font-bold text-center">Multi-Factor Authentication</CardTitle>
                <CardDescription className="text-center">
                    {isBackupCode
                        ? 'Enter your backup code to continue'
                        : `Enter the 6-digit code from your authenticator app`}
                </CardDescription>
            </CardHeader>
            <CardContent className="pb-6">
                <form onSubmit={handleSubmit(onSubmit)} className="space-y-5">
                    <div className="space-y-2">
                        <Label htmlFor="code" className="text-foreground/90">
                            {isBackupCode ? 'Backup Code' : 'Verification Code'}
                        </Label>
                        <Input
                            id="code"
                            type="text"
                            {...register('code')}
                            placeholder={isBackupCode ? 'Enter backup code' : 'Enter 6-digit code'}
                            disabled={loading}
                            className="h-11 bg-secondary/50 border-border/50 focus:border-primary transition-all text-center text-2xl tracking-widest"
                            maxLength={8}
                            autoComplete="one-time-code"
                            autoFocus
                        />
                        {errors.code && <p className="text-sm text-destructive">{errors.code.message}</p>}
                    </div>

                    <div className="flex items-center space-x-2">
                        <Checkbox
                            id="backupCode"
                            checked={backupCodeChecked}
                            onCheckedChange={(checked) => {
                                const isChecked = checked === true
                                setIsBackupCode(isChecked)
                                setValue('isBackupCode', isChecked)
                            }}
                        />
                        <Label
                            htmlFor="backupCode"
                            className="text-sm font-normal cursor-pointer"
                        >
                            Use backup code instead
                        </Label>
                    </div>

                    <div className="flex gap-3">
                        <Button
                            type="button"
                            variant="outline"
                            onClick={onCancel}
                            className="flex-1"
                            disabled={loading}
                        >
                            Cancel
                        </Button>
                        <Button
                            type="submit"
                            className="flex-1 bg-linear-to-r from-primary to-primary/80 hover:from-primary/90 hover:to-primary/70 text-white font-semibold shadow-lg shadow-primary/50 transition-all"
                            disabled={loading}
                        >
                            {loading && <Loader2 className="mr-2 h-5 w-5 animate-spin"/>}
                            {loading ? 'Verifying...' : 'Verify'}
                        </Button>
                    </div>
                </form>
            </CardContent>
        </Card>
    )
}

