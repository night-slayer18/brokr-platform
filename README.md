brokr-platform/
├── pom.xml
├── docker-compose.yml
├── README.md
├── .gitignore
├── brokr-backend/
│   ├── pom.xml
│   ├── brokr-core/
│   │   ├── pom.xml
│   │   └── src/
│   │       ├── main/
│   │       │   ├── java/
│   │       │   │   └── io/
│   │       │   │       └── brokr/
│   │       │   │           └── core/
│   │       │   │               ├── model/
│   │       │   │               │   ├── Organization.java
│   │       │   │               │   ├── Environment.java
│   │       │   │               │   ├── User.java
│   │       │   │               │   ├── Role.java
│   │       │   │               │   ├── KafkaCluster.java
│   │       │   │               │   ├── Topic.java
│   │       │   │               │   ├── PartitionInfo.java
│   │       │   │               │   ├── ConsumerGroup.java
│   │       │   │               │   ├── MemberInfo.java
│   │       │   │               │   ├── TopicPartition.java
│   │       │   │               │   ├── SchemaRegistry.java
│   │       │   │               │   ├── KafkaConnect.java
│   │       │   │               │   ├── Connector.java
│   │       │   │               │   ├── Task.java
│   │       │   │               │   ├── KafkaStreamsApplication.java
│   │       │   │               │   ├── ThreadMetadata.java
│   │       │   │               │   └── TaskMetadata.java
│   │       │   │               └── dto/
│   │       │   │                   ├── UserDto.java
│   │       │   │                   ├── OrganizationDto.java
│   │       │   │                   ├── EnvironmentDto.java
│   │       │   │                   ├── KafkaClusterDto.java
│   │       │   │                   ├── TopicDto.java
│   │       │   │                   ├── ConsumerGroupDto.java
│   │       │   │                   ├── SchemaRegistryDto.java
│   │       │   │                   ├── KafkaConnectDto.java
│   │       │   │                   └── KafkaStreamsApplicationDto.java
│   │       │   └── resources/
│   │       │       └── application-core.yml
│   │       └── test/
│   │           └── java/
│   │               └── io/
│   │                   └── brokr/
│   │                       └── core/
│   │                           ├── model/
│   │                           │   └── ModelTest.java
│   │                           └── service/
│   │                               └── ServiceTest.java
│   ├── brokr-storage/
│   │   ├── pom.xml
│   │   └── src/
│   │       ├── main/
│   │       │   ├── java/
│   │       │   │   └── io/
│   │       │   │       └── brokr/
│   │       │   │           └── storage/
│   │       │   │               ├── entity/
│   │       │   │               │   ├── OrganizationEntity.java
│   │       │   │               │   ├── EnvironmentEntity.java
│   │       │   │               │   ├── EnvironmentType.java
│   │       │   │               │   ├── UserEntity.java
│   │       │   │               │   ├── KafkaClusterEntity.java
│   │       │   │               │   ├── SchemaRegistryEntity.java
│   │       │   │               │   ├── KafkaConnectEntity.java
│   │       │   │               │   └── KafkaStreamsApplicationEntity.java
│   │       │   │               └── repository/
│   │       │   │                   ├── OrganizationRepository.java
│   │       │   │                   ├── EnvironmentRepository.java
│   │       │   │                   ├── UserRepository.java
│   │       │   │                   ├── KafkaClusterRepository.java
│   │       │   │                   ├── SchemaRegistryRepository.java
│   │       │   │                   ├── KafkaConnectRepository.java
│   │       │   │                   └── KafkaStreamsApplicationRepository.java
│   │       │   └── resources/
│   │       │       └── application-storage.yml
│   │       └── test/
│   │           └── java/
│   │               └── io/
│   │                   └── brokr/
│   │                       └── storage/
│   │                           ├── entity/
│   │                           │   └── EntityTest.java
│   │                           └── repository/
│   │                               └── RepositoryTest.java
│   ├── brokr-kafka/
│   │   ├── pom.xml
│   │   └── src/
│   │       ├── main/
│   │       │   ├── java/
│   │       │   │   └── io/
│   │       │   │       └── brokr/
│   │       │   │           └── kafka/
│   │       │   │               ├── service/
│   │       │   │               │   ├── KafkaConnectionService.java
│   │       │   │               │   ├── KafkaAdminService.java
│   │       │   │               │   ├── SchemaRegistryService.java
│   │       │   │               │   ├── KafkaConnectService.java
│   │       │   │               │   └── KafkaStreamsService.java
│   │       │   │               └── config/
│   │       │   │                   └── KafkaConfig.java
│   │       │   └── resources/
│   │       │       └── application-kafka.yml
│   │       └── test/
│   │           └── java/
│   │               └── io/
│   │                   └── brokr/
│   │                       └── kafka/
│   │                           ├── service/
│   │                           │   └── KafkaServiceTest.java
│   │                           └── integration/
│   │                               └── KafkaIntegrationTest.java
│   ├── brokr-security/
│   │   ├── pom.xml
│   │   └── src/
│   │       ├── main/
│   │       │   ├── java/
│   │       │   │   └── io/
│   │       │   │       └── brokr/
│   │       │   │           └── security/
│   │       │   │               ├── config/
│   │       │   │               │   ├── SecurityConfig.java
│   │       │   │               │   └── PasswordConfig.java
│   │       │   │               ├── service/
│   │       │   │               │   ├── JwtService.java
│   │       │   │               │   ├── JwtAuthenticationFilter.java
│   │       │   │               │   ├── UserDetailsServiceImpl.java
│   │       │   │               │   ├── AuthenticationService.java
│   │       │   │               │   └── AuthorizationService.java
│   │       │   │               └── utils/
│   │       │   │                   ├── SecurityUtils.java
│   │       │   │                   └── PasswordValidator.java
│   │       │   └── resources/
│   │       │       └── application-security.yml
│   │       └── test/
│   │           └── java/
│   │               └── io/
│   │                   └── brokr/
│   │                       └── security/
│   │                           ├── service/
│   │                           │   └── SecurityServiceTest.java
│   │                           └── config/
│   │                               └── SecurityConfigTest.java
│   ├── brokr-api/
│   │   ├── pom.xml
│   │   └── src/
│   │       ├── main/
│   │       │   ├── java/
│   │       │   │   └── io/
│   │       │   │       └── brokr/
│   │       │   │           └── api/
│   │       │   │               ├── graphql/
│   │       │   │               │   ├── AuthResolver.java
│   │       │   │               │   ├── UserResolver.java
│   │       │   │               │   ├── OrganizationResolver.java
│   │       │   │               │   ├── EnvironmentResolver.java
│   │       │   │               │   ├── ClusterResolver.java
│   │       │   │               │   ├── TopicResolver.java
│   │       │   │               │   ├── ConsumerGroupResolver.java
│   │       │   │               │   ├── SchemaRegistryResolver.java
│   │       │   │               │   ├── KafkaConnectResolver.java
│   │       │   │               │   └── KafkaStreamsResolver.java
│   │       │   │               ├── rest/
│   │       │   │               │   ├── controller/
│   │       │   │               │   │   ├── AuthController.java
│   │       │   │               │   │   ├── UserController.java
│   │       │   │               │   │   ├── OrganizationController.java
│   │       │   │               │   │   ├── EnvironmentController.java
│   │       │   │               │   │   ├── ClusterController.java
│   │       │   │               │   │   ├── TopicController.java
│   │       │   │               │   │   ├── ConsumerGroupController.java
│   │       │   │               │   │   ├── SchemaRegistryController.java
│   │       │   │               │   │   ├── KafkaConnectController.java
│   │       │   │               │   │   └── KafkaStreamsController.java
│   │       │   │               │   └── dto/
│   │       │   │               │       ├── ErrorResponse.java
│   │       │   │               │       ├── ApiResponse.java
│   │       │   │               │       └── PagedResponse.java
│   │       │   │               ├── input/
│   │       │   │               │   ├── LoginInput.java
│   │       │   │               │   ├── UserInput.java
│   │       │   │               │   ├── OrganizationInput.java
│   │       │   │               │   ├── EnvironmentInput.java
│   │       │   │               │   ├── KafkaClusterInput.java
│   │       │   │               │   ├── TopicInput.java
│   │       │   │               │   ├── SchemaRegistryInput.java
│   │       │   │               │   ├── KafkaConnectInput.java
│   │       │   │               │   └── KafkaStreamsApplicationInput.java
│   │       │   │               └── exception/
│   │       │   │                   ├── GlobalExceptionHandler.java
│   │       │   │                   ├── ResourceNotFoundException.java
│   │       │   │                   ├── UnauthorizedException.java
│   │       │   │                   └── ValidationException.java
│   │       │   └── resources/
│   │       │       ├── graphql/
│   │       │       │   └── schema.graphqls
│   │       │       └── application-api.yml
│   │       └── test/
│   │           └── java/
│   │               └── io/
│   │                   └── brokr/
│   │                       └── api/
│   │                           ├── graphql/
│   │                           │   └── GraphQLResolverTest.java
│   │                           └── rest/
│   │                               └── RestControllerTest.java
│   └── brokr-app/
│       ├── pom.xml
│       └── src/
│           ├── main/
│           │   ├── java/
│           │   │   └── io/
│           │   │       └── brokr/
│           │   │           ├── BrokrApplication.java
│           │   │           ├── config/
│           │   │           │   ├── DatabaseConfig.java
│           │   │           │   ├── FlywayConfig.java
│           │   │           │   ├── GraphQLConfig.java
│           │   │           │   ├── WebConfig.java
│           │   │           │   └── CorsConfig.java
│           │   │           └── init/
│           │   │               ├── DataInitializer.java
│           │   │               ├── AdminInitializer.java
│           │   │               └── SampleDataInitializer.java
│           │   └── resources/
│           │       ├── application.yml
│           │       ├── application-dev.yml
│           │       ├── application-prod.yml
│           │       ├── db/
│           │       │   └── migration/
│           │       │       ├── V1__Create_organizations_table.sql
│           │       │       ├── V2__Create_environments_table.sql
│           │       │       ├── V3__Create_users_table.sql
│           │       │       ├── V4__Create_user_accessible_environments_table.sql
│           │       │       ├── V5__Create_kafka_clusters_table.sql
│           │       │       ├── V6__Create_schema_registries_table.sql
│           │       │       ├── V7__Create_kafka_connects_table.sql
│           │       │       ├── V8__Create_kafka_streams_applications_table.sql
│           │       │       └── V9__Create_update_updated_at_column_function.sql
│           │       ├── static/
│           │       │   ├── css/
│           │       │   ├── js/
│           │       │   └── images/
│           │       └── templates/
│           │           └── index.html
│           └── test/
│               └── java/
│                   └── io/
│                       └── brokr/
│                           ├── BrokrApplicationTests.java
│                           ├── integration/
│                           │   ├── ApiIntegrationTest.java
│                           │   ├── SecurityIntegrationTest.java
│                           │   └── KafkaIntegrationTest.java
│                           └── e2e/
│                               └── EndToEndTest.java
└── brokr-frontend/
├── pom.xml
├── package.json
├── package-lock.json
├── tsconfig.json
├── .gitignore
├── .eslintrc.js
├── public/
│   ├── index.html
│   ├── favicon.ico
│   ├── manifest.json
│   └── robots.txt
└── src/
├── index.tsx
├── App.tsx
├── index.css
├── App.css
├── reportWebVitals.ts
├── setupTests.ts
├── components/
│   ├── Layout.tsx
│   ├── Header.tsx
│   ├── Sidebar.tsx
│   ├── Footer.tsx
│   ├── common/
│   │   ├── LoadingSpinner.tsx
│   │   ├── ErrorBoundary.tsx
│   │   ├── ConfirmDialog.tsx
│   │   ├── Notification.tsx
│   │   └── Pagination.tsx
│   ├── auth/
│   │   ├── LoginForm.tsx
│   │   ├── RegisterForm.tsx
│   │   └── ProtectedRoute.tsx
│   ├── cluster/
│   │   ├── ClusterList.tsx
│   │   ├── ClusterCard.tsx
│   │   ├── ClusterForm.tsx
│   │   ├── ClusterDetails.tsx
│   │   └── ConnectionTest.tsx
│   ├── topic/
│   │   ├── TopicList.tsx
│   │   ├── TopicCard.tsx
│   │   ├── TopicForm.tsx
│   │   ├── TopicDetails.tsx
│   │   ├── TopicConfig.tsx
│   │   └── TopicMessages.tsx
│   ├── consumer/
│   │   ├── ConsumerGroupList.tsx
│   │   ├── ConsumerGroupCard.tsx
│   │   ├── ConsumerGroupDetails.tsx
│   │   ├── ConsumerOffsets.tsx
│   │   └── MemberDetails.tsx
│   ├── schema/
│   │   ├── SchemaRegistryList.tsx
│   │   ├── SchemaRegistryCard.tsx
│   │   ├── SchemaRegistryForm.tsx
│   │   ├── SchemaList.tsx
│   │   ├── SchemaViewer.tsx
│   │   └── SchemaVersions.tsx
│   ├── connect/
│   │   ├── KafkaConnectList.tsx
│   │   ├── KafkaConnectCard.tsx
│   │   ├── KafkaConnectForm.tsx
│   │   ├── ConnectorList.tsx
│   │   ├── ConnectorDetails.tsx
│   │   └── TaskDetails.tsx
│   └── streams/
│       ├── KafkaStreamsList.tsx
│       ├── KafkaStreamsCard.tsx
│       ├── KafkaStreamsForm.tsx
│       ├── KafkaStreamsDetails.tsx
│       ├── ThreadDetails.tsx
│       └── TaskDetails.tsx
├── pages/
│   ├── LoginPage.tsx
│   ├── DashboardPage.tsx
│   ├── ClustersPage.tsx
│   ├── TopicsPage.tsx
│   ├── ConsumerGroupsPage.tsx
│   ├── SchemaRegistriesPage.tsx
│   ├── KafkaConnectsPage.tsx
│   ├── KafkaStreamsPage.tsx
│   ├── SettingsPage.tsx
│   ├── ProfilePage.tsx
│   └── NotFoundPage.tsx
├── contexts/
│   ├── AuthContext.tsx
│   ├── ThemeContext.tsx
│   └── NotificationContext.tsx
├── hooks/
│   ├── useAuth.ts
│   ├── useLocalStorage.ts
│   ├── useDebounce.ts
│   ├── useWebSocket.ts
│   └── usePolling.ts
├── services/
│   ├── api.ts
│   ├── auth.ts
│   ├── cluster.ts
│   ├── topic.ts
│   ├── consumer.ts
│   ├── schema.ts
│   ├── connect.ts
│   └── streams.ts
├── utils/
│   ├── constants.ts
│   ├── helpers.ts
│   ├── validators.ts
│   ├── formatters.ts
│   └── dateUtils.ts
├── types/
│   ├── index.ts
│   ├── auth.ts
│   ├── cluster.ts
│   ├── topic.ts
│   ├── consumer.ts
│   ├── schema.ts
│   ├── connect.ts
│   ├── streams.ts
│   └── common.ts
├── graphql/
│   ├── queries.ts
│   ├── mutations.ts
│   ├── subscriptions.ts
│   └── client.ts
└── styles/
├── globals.css
├── variables.css
├── components.css
└── themes/
├── light.css
└── dark.css