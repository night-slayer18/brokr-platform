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
import TopicDetailPage from './pages/TopicDetailPage'
import ConsumerGroupDetailPage from './pages/ConsumerGroupDetailPage'
import SchemaRegistryDetailPage from './pages/SchemaRegistryDetailPage'
import KafkaConnectDetailPage from './pages/KafkaConnectDetailPage'
import KafkaStreamsDetailPage from './pages/KafkaStreamsDetailPage'
import CreateClusterPage from './pages/CreateClusterPage'

import {AdminRoute} from "./components/auth/AdminRoute";

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
                    <Route path="clusters/new" element={<AdminRoute><CreateClusterPage/></AdminRoute>}/>
                    <Route path="clusters/:clusterId/topics" element={<TopicsPage/>}/>
                    <Route path="clusters/:clusterId/topics/:topicName" element={<TopicDetailPage/>}/>
                    <Route path="clusters/:clusterId/consumer-groups" element={<ConsumerGroupsPage/>}/>
                    <Route path="clusters/:clusterId/consumer-groups/:groupId" element={<ConsumerGroupDetailPage/>}/>
                    <Route path="clusters/:clusterId/schema-registry" element={<SchemaRegistryPage/>}/>
                    <Route path="clusters/:clusterId/schema-registry/:srId" element={<SchemaRegistryDetailPage/>}/>
                    <Route path="clusters/:clusterId/kafka-connect" element={<KafkaConnectPage/>}/>
                    <Route path="clusters/:clusterId/kafka-connect/:kcId" element={<KafkaConnectDetailPage/>}/>
                    <Route path="clusters/:clusterId/kafka-streams" element={<KafkaStreamsPage/>}/>
                    <Route path="clusters/:clusterId/kafka-streams/:ksId" element={<KafkaStreamsDetailPage/>}/>
                </Route>
            </Routes>
            <Toaster position="top-right" richColors/>
        </>
    )
}

export default App
