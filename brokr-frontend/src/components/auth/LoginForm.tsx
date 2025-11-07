import {useMutation} from '@apollo/client/react'
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

const loginSchema = z.object({
    username: z.string().min(1, 'Username is required'),
    password: z.string().min(1, 'Password is required'),
})

type LoginFormData = z.infer<typeof loginSchema>

export function LoginForm() {
    const navigate = useNavigate()
    const login = useAuthStore((state) => state.login)
    const [loginMutation, {loading}] = useMutation(LOGIN_MUTATION)

    const {
        register,
        handleSubmit,
        formState: {errors},
    } = useForm<LoginFormData>({
        resolver: zodResolver(loginSchema),
    })

    const onSubmit = async (data: LoginFormData) => {
        try {
            const result = await loginMutation({
                variables: {
                    input: {
                        username: data.username,
                        password: data.password,
                    },
                },
            })

            const {token, user} = (result.data as { login: { token: string; user: any } }).login
            login(token, user)
            toast.success('Logged in successfully')
            navigate('/dashboard')
        } catch (error: any) {
            toast.error(error.message || 'Login failed')
        }
    }

    return (
        <Card
            className="w-full max-w-md backdrop-blur-xl bg-card/50 border-2 border-orange-500/20 shadow-2xl shadow-orange-500/20">
            <CardHeader className="space-y-1 pb-6">
                <div className="flex items-center justify-center mb-6">
                    <div className="relative">
                        <div
                            className="absolute inset-0 rounded-xl bg-gradient-to-br from-orange-600 to-teal-600 blur-lg opacity-75 animate-pulse"></div>
                        <div
                            className="relative h-16 w-16 rounded-xl bg-gradient-to-br from-orange-600 to-teal-600 flex items-center justify-center shadow-lg">
                            <svg xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24" strokeWidth={2.5}
                                 stroke="currentColor" className="w-10 h-10 text-white">
                                <path strokeLinecap="round" strokeLinejoin="round"
                                      d="M5.25 14.25h13.5m-13.5 0a3 3 0 01-3-3m3 3a3 3 0 100 6h13.5a3 3 0 100-6m-16.5-3a3 3 0 013-3h13.5a3 3 0 013 3m-19.5 0a4.5 4.5 0 01.9-2.7L5.737 5.1a3.375 3.375 0 012.7-1.35h7.126c1.062 0 2.062.5 2.7 1.35l2.587 3.45a4.5 4.5 0 01.9 2.7m0 0a3 3 0 01-3 3m0 3h.008v.008h-.008v-.008zm0-6h.008v.008h-.008v-.008zm-3 6h.008v.008h-.008v-.008zm0-6h.008v.008h-.008v-.008z"/>
                            </svg>
                        </div>
                    </div>
                </div>
                <CardTitle
                    className="text-3xl font-bold text-center bg-gradient-to-r from-orange-400 to-teal-400 bg-clip-text text-transparent">
                    Welcome to Brokr
                </CardTitle>
                <CardDescription className="text-center text-base">
                    Manage your Kafka clusters with ease
                </CardDescription>
            </CardHeader>
            <CardContent>
                <form onSubmit={handleSubmit(onSubmit)} className="space-y-5">
                    <div className="space-y-2">
                        <Label htmlFor="username" className="text-foreground/90">Username</Label>
                        <Input
                            id="username"
                            {...register('username')}
                            placeholder="Enter your username"
                            disabled={loading}
                            className="h-11 bg-secondary/50 border-border/50 focus:border-orange-500 transition-all"
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
                            className="h-11 bg-secondary/50 border-border/50 focus:border-orange-500 transition-all"
                        />
                        {errors.password && (
                            <p className="text-sm text-destructive">{errors.password.message}</p>
                        )}
                    </div>
                    <Button type="submit"
                            className="w-full h-11 bg-gradient-to-r from-orange-600 to-teal-600 hover:from-orange-700 hover:to-teal-700 text-white font-semibold shadow-lg shadow-orange-500/50 transition-all"
                            disabled={loading}>
                        {loading && <Loader2 className="mr-2 h-5 w-5 animate-spin"/>}
                        {loading ? 'Signing In...' : 'Sign In'}
                    </Button>
                </form>
                <div className="mt-6 text-center">
                    <p className="text-sm text-muted-foreground mb-2">Demo Credentials</p>
                    <div className="flex flex-wrap gap-2 justify-center">
                        <span
                            className="text-xs px-3 py-1.5 rounded-full bg-orange-500/10 text-orange-400 border border-orange-500/20">admin/admin123</span>
                        <span
                            className="text-xs px-3 py-1.5 rounded-full bg-teal-500/10 text-teal-400 border border-teal-500/20">orgadmin/orgadmin123</span>
                        <span
                            className="text-xs px-3 py-1.5 rounded-full bg-purple-500/10 text-purple-400 border border-purple-500/20">developer/developer123</span>
                    </div>
                </div>
            </CardContent>
        </Card>
    )
}