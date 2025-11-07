import {useAuth} from '@/hooks/useAuth';
import {Navigate, useLocation} from 'react-router-dom';
import {toast} from 'sonner';
import {JSX} from "react";

export function AdminRoute({children}: { children: JSX.Element }) {
    const {user, canManageClusters} = useAuth();
    const location = useLocation();

    // If auth state is loading, user might be undefined. Render nothing until resolved.
    if (user === undefined) {
        return null; // Or a loading spinner
    }

    // If user is null, they are not authenticated.
    if (user === null) {
        return <Navigate to="/login" state={{from: location}} replace/>;
    }

    // Now we have a user, check permissions.
    if (!canManageClusters()) {
        toast.error("You don't have permission to access this page.");
        return <Navigate to="/clusters" state={{from: location}} replace/>;
    }

    return children;
}
