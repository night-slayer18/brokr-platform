import {Navigate, Route, Routes} from 'react-router-dom'
import {Toaster} from 'sonner'
import {ProtectedRoute} from './components/auth/ProtectedRoute'
import {AppLayout} from './components/layout/AppLayout'
import LoginPage from './pages/LoginPage'
import DashboardPage from './pages/DashboardPage'
import ClustersPage from './pages/ClustersPage'
import TopicsPage from './pages/TopicsPage'
import ConsumerGroupsPage from './pages/ConsumerGroupsPage'
import SchemaRegistryPage from './pages/SchemaRegistryPage'
import KafkaConnectPage from './pages/KafkaConnectPage'
import KafkaStreamsPage from './pages/KafkaStreamsPage'

function App() {
    return (
        <>
            <Routes>
                <Route path="/login" element={<LoginPage/>}/>
                <Route
                    path="/"
                    element={
                        <ProtectedRoute>
                            <AppLayout/>
                        </ProtectedRoute>
                    }
                >
                    <Route index element={<Navigate to="/dashboard" replace/>}/>
                    <Route path="dashboard" element={<DashboardPage/>}/>
                    <Route path="clusters" element={<ClustersPage/>}/>
                    <Route path="clusters/:clusterId/topics" element={<TopicsPage/>}/>
                    <Route path="clusters/:clusterId/consumer-groups" element={<ConsumerGroupsPage/>}/>
                    <Route path="clusters/:clusterId/schema-registry" element={<SchemaRegistryPage/>}/>
                    <Route path="clusters/:clusterId/kafka-connect" element={<KafkaConnectPage/>}/>
                    <Route path="clusters/:clusterId/kafka-streams" element={<KafkaStreamsPage/>}/>
                </Route>
            </Routes>
            <Toaster position="top-right" richColors/>
        </>
    )
}

export default App