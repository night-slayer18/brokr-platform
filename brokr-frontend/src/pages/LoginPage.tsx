import {LoginForm} from '@/components/auth/LoginForm'

export default function LoginPage() {
    return (
        <div className="relative flex min-h-screen items-center justify-center overflow-hidden bg-[#0f0f16] p-4">
            {/* Animated gradient background */}
            <div
                className="absolute inset-0 bg-gradient-to-br from-orange-600/20 via-teal-500/10 to-amber-500/20 animate-gradient"></div>

            {/* Animated orbs */}
            <div
                className="absolute top-1/4 left-1/4 w-96 h-96 bg-orange-500/30 rounded-full blur-3xl animate-pulse"></div>
            <div
                className="absolute bottom-1/4 right-1/4 w-96 h-96 bg-teal-500/30 rounded-full blur-3xl animate-pulse delay-1000"></div>

            {/* Grid pattern overlay */}
            <div
                className="absolute inset-0 bg-[linear-gradient(to_right,#2a2a3a15_1px,transparent_1px),linear-gradient(to_bottom,#2a2a3a15_1px,transparent_1px)] bg-[size:4rem_4rem]"></div>

            <div className="relative z-10">
                <LoginForm/>
            </div>
        </div>
    )
}