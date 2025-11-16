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
import KsqlDBPage from './pages/KsqlDBPage'
import TopicDetailPage from './pages/TopicDetailPage'
import ConsumerGroupDetailPage from './pages/ConsumerGroupDetailPage'
import ReplayJobsPage from './pages/ReplayJobsPage'
import ReplayJobDetailPage from './pages/ReplayJobDetailPage'
import SchemaRegistryDetailPage from './pages/SchemaRegistryDetailPage'
import KafkaConnectDetailPage from './pages/KafkaConnectDetailPage'
import KafkaStreamsDetailPage from './pages/KafkaStreamsDetailPage'
import KsqlDBDetailPage from './pages/KsqlDBDetailPage'
import CreateClusterPage from './pages/CreateClusterPage'

import { AdminRoute } from "./components/auth/AdminRoute";
import { SuperAdminRoute } from "./components/auth/SuperAdminRoute";
import { ClusterLayout } from './components/layout/ClusterLayout';
import ClusterOverviewPage from './pages/ClusterOverviewPage';
import BrokersPage from './pages/BrokersPage';
import AdminDashboardPage from './pages/admin/AdminDashboardPage';
import OrganizationsPage from './pages/admin/OrganizationsPage';
import OrganizationDetailPage from './pages/admin/OrganizationDetailPage';
import AuditLogsPage from './pages/admin/AuditLogsPage';
import ProfilePage from './pages/ProfilePage';
import SettingsPage from './pages/SettingsPage';
import ApiKeysPage from './pages/ApiKeysPage';
import ApiKeyDetailPage from './pages/ApiKeyDetailPage';

function App() {
    return (
        <>
            <Routes>
                <Route path="/login" element={<LoginPage />} />
                <Route
                    path="/"
                    element={
                        <ProtectedRoute>
                            <AppLayout />
                        </ProtectedRoute>
                    }
                >
                    <Route index element={<Navigate to="/dashboard" replace />} />
                    <Route path="dashboard" element={<DashboardPage />} />
                    <Route path="profile" element={<ProfilePage />} />
                    <Route path="settings" element={<SettingsPage />} />
                    <Route path="api-keys" element={<ApiKeysPage />} />
                    <Route path="api-keys/:id" element={<ApiKeyDetailPage />} />
                    <Route path="clusters" element={<ClustersPage />} />
                    <Route path="clusters/new" element={<AdminRoute><CreateClusterPage /></AdminRoute>} />
                </Route>
                <Route
                    path="/clusters/:clusterId"
                    element={
                        <ProtectedRoute>
                            <ClusterLayout />
                        </ProtectedRoute>
                    }
                >
                    <Route index element={<ClusterOverviewPage />} />
                    <Route path="brokers" element={<BrokersPage />} />
                    <Route path="topics" element={<TopicsPage />} />
                    <Route path="topics/:topicName" element={<TopicDetailPage />} />
                    <Route path="consumer-groups" element={<ConsumerGroupsPage />} />
                    <Route path="consumer-groups/:groupId" element={<ConsumerGroupDetailPage />} />
                    <Route path="replay" element={<ReplayJobsPage />} />
                    <Route path="replay/:jobId" element={<ReplayJobDetailPage />} />
                    <Route path="schema-registry" element={<SchemaRegistryPage />} />
                    <Route path="schema-registry/:srId" element={<SchemaRegistryDetailPage />} />
                    <Route path="kafka-connect" element={<KafkaConnectPage />} />
                    <Route path="kafka-connect/:kcId" element={<KafkaConnectDetailPage />} />
                    <Route path="kafka-streams" element={<KafkaStreamsPage />} />
                    <Route path="kafka-streams/:ksId" element={<KafkaStreamsDetailPage />} />
                    <Route path="ksqldb" element={<KsqlDBPage />} />
                    <Route path="ksqldb/:ksqlDBId" element={<KsqlDBDetailPage />} />
                </Route>
                <Route
                    path="/admin"
                    element={
                        <ProtectedRoute>
                            <SuperAdminRoute>
                                <AppLayout />
                            </SuperAdminRoute>
                        </ProtectedRoute>
                    }
                >
                    <Route path="dashboard" element={<AdminDashboardPage />} />
                    <Route path="organizations" element={<OrganizationsPage />} />
                    <Route path="organizations/:orgId" element={<OrganizationDetailPage />} />
                    <Route path="audit-logs" element={<AuditLogsPage />} />
                </Route>
            </Routes>
            <Toaster position="top-right" richColors />
        </>
    )
}

export default App;
