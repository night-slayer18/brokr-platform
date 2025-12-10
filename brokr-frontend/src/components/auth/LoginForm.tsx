import {useState} from 'react'
import {useNavigate} from 'react-router-dom'
import {useForm} from 'react-hook-form'
import {zodResolver} from '@hookform/resolvers/zod'
import * as z from 'zod'
import {toast} from 'sonner'
import {Button} from '@/components/ui/button'
import {Input} from '@/components/ui/input'
import {Label} from '@/components/ui/label'
import {Card, CardContent, CardDescription, CardHeader, CardTitle} from '@/components/ui/card'
import {LOGIN_MUTATION} from '@/graphql/mutations'
import {useAuthStore} from '@/store/authStore'
import {Loader2} from 'lucide-react'
import {useGraphQLMutation} from '@/hooks/useGraphQLMutation'
import type {LoginMutation} from '@/graphql/types'
import {MfaVerificationForm} from './MfaVerificationForm'

const loginSchema = z.object({
    username: z.string().email('Please enter a valid email address'),
    password: z.string().min(1, 'Password is required'),
})

type LoginFormData = z.infer<typeof loginSchema>

export function LoginForm() {
    const navigate = useNavigate()
    const login = useAuthStore((state) => state.login)
    const [mfaChallenge, setMfaChallenge] = useState<{token: string; mfaType: string} | null>(null)
    const {mutate: loginMutation, isPending: loading} = useGraphQLMutation<LoginMutation, {input: {username: string; password: string}}>(LOGIN_MUTATION)

    const {
        register,
        handleSubmit,
        formState: {errors},
    } = useForm<LoginFormData>({
        resolver: zodResolver(loginSchema),
    })

    const onSubmit = async (data: LoginFormData) => {
        loginMutation(
            {
                input: {
                    username: data.username,
                    password: data.password,
                },
            },
            {
                onSuccess: (result) => {
                    const {user, mfaRequired, token, mfaType} = result.login
                    
                    if (mfaRequired && token && mfaType) {
                        // MFA challenge required - show MFA verification form
                        setMfaChallenge({token, mfaType})
                    } else if (user) {
                        // No MFA - proceed with normal login
                        login(user)
                        toast.success('Logged in successfully')
                        navigate('/dashboard')
                    } else {
                        toast.error('Login failed - invalid response')
                    }
                },

            }
        )
    }

    const handleMfaSuccess = () => {
        setMfaChallenge(null)
        navigate('/dashboard')
    }

    const handleMfaCancel = () => {
        setMfaChallenge(null)
    }

    // Show MFA verification form if challenge is active
    if (mfaChallenge) {
        return (
            <MfaVerificationForm
                challengeToken={mfaChallenge.token}
                mfaType={mfaChallenge.mfaType}
                onSuccess={handleMfaSuccess}
                onCancel={handleMfaCancel}
            />
        )
    }

    return (
        <Card
            className="w-full min-w-[400px] max-w-lg mx-auto backdrop-blur-xl bg-card/50 border-2 border-primary/20 shadow-2xl shadow-primary/20">
            <CardHeader className="space-y-1 pb-6">
                <div className="flex items-center justify-center mb-6">
                    <img src="/brokr-icon.svg" alt="Brokr Logo" className="h-16 w-16"/>
                </div>
                <CardTitle
                    className="text-3xl font-bold text-center bg-linear-to-r from-primary to-primary/80 bg-clip-text text-transparent">
                    Welcome to Brokr
                </CardTitle>
                <CardDescription className="text-center text-base">
                    Manage your Kafka clusters with ease
                </CardDescription>
            </CardHeader>
            <CardContent className="pb-6">
                <form onSubmit={handleSubmit(onSubmit)} className="space-y-5">
                    <div className="space-y-2">
                        <Label htmlFor="username" className="text-foreground/90">Username</Label>
                        <Input
                            id="username"
                            type="email"
                            {...register('username')}
                            placeholder="Enter your username"
                            disabled={loading}
                            className="h-11 bg-secondary/50 border-border/50 focus:border-primary transition-all"
                        />
                        {errors.username && (
                            <p className="text-sm text-destructive">{errors.username.message}</p>
                        )}
                    </div>
                    <div className="space-y-2">
                        <Label htmlFor="password" className="text-foreground/90">Password</Label>
                        <Input
                            id="password"
                            type="password"
                            {...register('password')}
                            placeholder="Enter your password"
                            disabled={loading}
                            className="h-11 bg-secondary/50 border-border/50 focus:border-primary transition-all"
                        />
                        {errors.password && (
                            <p className="text-sm text-destructive">{errors.password.message}</p>
                        )}
                    </div>
                    <Button type="submit"
                            className="w-full h-11 bg-linear-to-r from-primary to-primary/80 hover:from-primary/90 hover:to-primary/70 text-white font-semibold shadow-lg shadow-primary/50 transition-all"
                            disabled={loading}>
                        {loading && <Loader2 className="mr-2 h-5 w-5 animate-spin"/>}
                        {loading ? 'Signing In...' : 'Sign In'}
                    </Button>
                </form>
            </CardContent>
        </Card>
    )
}