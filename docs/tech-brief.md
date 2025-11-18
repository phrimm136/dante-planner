# Technical Overview

## Technology Stack

### Frontend
- **Framework**: React 19
- **Build Tool**: Vite
- **Package Manager**: Yarn
- **Styling**: Tailwind CSS
- **State Management**: TanStack Query (React Query)
- **Routing**: TanStack Router
- **Testing**: Vitest
- **UI**: Shadcn

### Backend
- **Framework**: Spring Boot 3.x
- **Language**: Java 17+
- **Database**: MySQL 8.0+
- **ORM**: Spring Data JPA with Hibernate
- **Caching**: Redis
- **Authentication**: OAuth 2.0 with JWT

### Infrastructure
- **Application & Database**: AWS (EC2/ECS + RDS)
- **Static Assets (Game Data)**: Cloudflare R2 (S3-compatible)
- **Monitoring**: Prometheus + Grafana (optional)
- **Deployment**: Docker Compose (Kubernetes optional)

## Naming Conventions

### General Principles
- **PascalCase**: All file names, component names, class names, interface names
- **Descriptive**: Names must explicitly convey purpose and responsibility
- **Consistency**: Maintain parallel structure across similar entities

### Frontend (React)
- **Components**: `{Entity}{Purpose}Component.tsx` or `{Entity}{Purpose}.tsx`
  - Examples: `IdentityDetailCard.tsx`, `FilterSidebarPanel.tsx`, `CommentThread.tsx`
- **Hooks**: `use{Capability}.ts`
  - Examples: `useIdentityData.ts`, `useAuthState.ts`, `useFilteredList.ts`
- **Types**: `{Entity}Types.ts` or `{Entity}.types.ts`
  - Examples: `IdentityTypes.ts`, `PlannerTypes.ts`

### Backend (Spring Boot)
- **Entities**: `{Domain}Entity.java`
  - Examples: `UserEntity.java`, `PostEntity.java`, `CommentEntity.java`
- **Repositories**: `{Domain}Repository.java`
  - Examples: `UserRepository.java`, `PostRepository.java`
- **Services**: `{Domain}Service.java` (interface) + `{Domain}ServiceImpl.java`
  - Examples: `UserService.java`, `UserServiceImpl.java`
- **Controllers**: `{Domain}Controller.java`
  - Examples: `AuthController.java`, `PostController.java`
- **DTOs**: `{Domain}{Action}{Request|Response}DTO.java`
  - Examples: `CreatePostRequestDTO.java`, `PostDetailResponseDTO.java`
