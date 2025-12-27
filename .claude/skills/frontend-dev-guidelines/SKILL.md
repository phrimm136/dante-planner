---
name: frontend-dev-guidelines
description: LimbusPlanner frontend development guidelines. Static JSON data loading with TanStack Query, Zod runtime validation, shadcn/ui + Tailwind styling, TanStack Router (code-based), React Compiler for automatic optimization, SSR-ready architecture. Use when creating components, pages, hooks, types, schemas, or working with frontend code.
---

# LimbusPlanner Frontend Development Guidelines

## Purpose

Frontend development guide for LimbusPlanner. This project uses static JSON data with fetch, TanStack Query for caching, Zod for runtime validation, shadcn/ui + Tailwind for styling, and React Compiler for automatic optimization. Supports SSR-ready patterns.

## ⚠️ CRITICAL: MANDATORY REQUIREMENTS

**These are NOT suggestions - they are STRICT REQUIREMENTS that WILL be enforced by pre-tool-use hooks:**

1. **MUST use Zod schemas** - NO PropTypes, NO runtime type guards except Zod
2. **MUST use useSuspenseQuery/useSuspenseQueries** - NO plain useQuery
3. **MUST follow existing patterns** - Search and read similar files BEFORE writing
4. **MUST use shadcn/ui components** - NO custom UI primitives without approval
5. **MUST use existing color constants** - NO hardcoded hex values
6. **MUST validate with Zod** - ALL JSON data MUST pass through Zod validation

**Violations will be BLOCKED by the pre-tool-use hook.**

## When to Use This Skill

- Creating new components or pages
- Building new features
- Writing data fetching hooks
- Fetching data with TanStack Query
- Creating TypeScript types and Zod schemas
- Setting up routing with TanStack Router (code-based)
- Styling with shadcn/ui + Tailwind CSS
- Performance optimization (React Compiler handles most cases)
- Organizing frontend code
- TypeScript best practices

---

## Quick Start

### ⚠️ MANDATORY: Pattern Check Before Writing

**Before creating ANY new file, you MUST:**

1. **MUST search for similar existing files** using Glob/Grep (NOT optional)
2. **MUST read 1-2 similar files** to understand established patterns (NOT optional)
3. **MUST state your pattern reference** before writing:
   ```
   **Pattern Reference:** [filename]
   - Using: [specific patterns from that file]
   ```
4. **If no similar file exists**, state: `**New Pattern:** [reason]`

**This is ENFORCED - skipping pattern check will result in rejected code.**

**Pattern Check by File Type:**
| File Type | Search | Example Reference |
|-----------|--------|-------------------|
| Component | `components/**/*.tsx` | `IdentityCard.tsx`, `EGOGiftCard.tsx` |
| Hook | `hooks/use*.ts` | `useEntityListData.ts`, `useStartBuffData.ts` |
| Type | `types/*Types.ts` | `IdentityTypes.ts`, `EGOGiftTypes.ts` |
| Schema | `schemas/*Schemas.ts` | `IdentitySchemas.ts`, `EGOGiftSchemas.ts` |
| Utility | `lib/*.ts` | `assetPaths.ts`, `utils.ts`, `validation.ts` |
| Page | `routes/*Page.tsx` | `IdentityPage.tsx`, `EGOGiftDetailPage.tsx` |

**❌ FORBIDDEN (Will be BLOCKED):**
- ❌ Hardcode colors - MUST use existing constants or theme variables
- ❌ Hardcode magic numbers/strings - MUST check/add to constants.ts first
- ❌ Create new patterns when existing ones apply
- ❌ Skip pattern check for "simple" components
- ❌ Use PropTypes (MUST use Zod instead)
- ❌ Use plain useQuery (MUST use useSuspenseQuery)
- ❌ Skip Zod validation for JSON data

**⚠️ CONSTANTS WORKFLOW (MANDATORY):**
Before using ANY hardcoded value (colors, numbers, strings):
1. **Check `frontend/src/lib/constants.ts`** - Does the constant already exist?
2. **If YES**: Import and use it
3. **If NO**: Add it to `constants.ts` FIRST, then import and use
4. **NEVER**: Write hardcoded values directly in components/hooks

---

### New Component Checklist

Creating a component? Follow this checklist:

- [ ] **✅ MANDATORY: Check existing similar components for patterns**
- [ ] **✅ MANDATORY: Check `constants.ts` before using any hardcoded values**
- [ ] **✅ MANDATORY: Define Props interface with TypeScript (NOT PropTypes)**
- [ ] **✅ MANDATORY: Use shadcn/ui primitives (Button, Card, Dialog, etc.)**
- [ ] **✅ MANDATORY: Use existing color constants (NO hardcoded hex values)**
- [ ] React Compiler handles memoization automatically (no manual memo needed)
- [ ] Lazy load if heavy component: `React.lazy(() => import())`
- [ ] Wrap in Suspense boundary for loading states
- [ ] **✅ MUST use `useSuspenseQuery` or `useSuspenseQueries`** for data fetching (see [data-fetching.md](resources/data-fetching.md))
- [ ] Import alias: `@/` for src, `@static/` for static data
- [ ] Tailwind classes inline with `cn()` utility; extract only if complex
- [ ] Event handlers don't need useCallback (React Compiler optimizes)
- [ ] **❌ FORBIDDEN: No early returns with loading spinners** (use Suspense boundary instead)
- [ ] Use `sonner` for toast notifications

### New Data Hook Checklist

Creating a data fetching hook? Follow this checklist:

- [ ] **✅ MANDATORY: Check `useEntityListData.ts` or `useEntityDetailData.ts` patterns**
- [ ] **✅ MUST use `useSuspenseQueries`** for parallel loading (SSR-ready, NOT plain useQuery)
- [ ] **✅ MUST use Zod schema runtime validation** (REQUIRED, not optional)
- [ ] **✅ MUST use `validateData()` utility** from `@/lib/validation`
- [ ] Query key format: `[entityType, id]` or `[entityType, 'list']`
- [ ] Set appropriate `staleTime` based on data volatility

### New Type/Schema Checklist

Creating types or schemas? Follow this checklist:

- [ ] **✅ MANDATORY: TypeScript interface** in `types/{Entity}Types.ts`
- [ ] **✅ MANDATORY: Zod schema** in `schemas/{Entity}Schemas.ts` (NO PropTypes)
- [ ] **✅ MUST use `.strict()`** to reject extra fields
- [ ] **✅ MUST add export** to `schemas/index.ts`
- [ ] See [schemas-and-validation.md](resources/schemas-and-validation.md) for patterns

---

## Import Aliases Quick Reference

| Alias | Resolves To | Example |
|-------|-------------|---------|
| `@/` | `src/` | `import { cn } from '@/lib/utils'` |
| `@static/` | `static/` | `import('@static/data/identitySpecList.json')` |

Defined in: `vite.config.ts`

---

## Common Imports

```ts
// React (memo, useCallback, useMemo not needed with React Compiler)
import { useState, Suspense } from 'react';

// TanStack Query (SSR-ready)
import { useSuspenseQuery, useSuspenseQueries, useQueryClient } from '@tanstack/react-query';

// TanStack Router
import { Link, useParams } from '@tanstack/react-router';

// shadcn/ui
import { Button } from '@/components/ui/button';
import { Card, CardContent } from '@/components/ui/card';

// Project utilities
import { cn } from '@/lib/utils';
import { validateData } from '@/lib/validation';
import { getIdentityImagePath } from '@/lib/assetPaths';

// Constants (MANDATORY: Import before using hardcoded values)
import { MAX_LEVEL, SINNERS, AFFINITIES } from '@/lib/constants';

// Types & Schemas
import type { Identity } from '@/types/IdentityTypes';
import { IdentityDataSchema } from '@/schemas/IdentitySchemas';

// i18n
import { useTranslation } from 'react-i18next';

// Toast
import { toast } from 'sonner';
```

---

## Package Manager

Use yarn instead npm. Since the yarn version is v1, `yarn dlx` is not supported. Use `yarn run` instead.

## Resources

Detailed guides for specific topics. **Read the required resources before starting work.**

### When to Read Resources

| Task | Required Reading |
|------|------------------|
| Creating data hook | `data-fetching.md`, `schemas-and-validation.md` |
| Creating type/schema | `schemas-and-validation.md` |
| Creating component | `component-patterns.md` |
| Creating page | `routing-guide.md`, `component-patterns.md` |
| Styling work | `styling-guide.md` |
| Loading JSON data | `schemas-and-validation.md` (full pipeline) |
| Writing tests | `testing-guide.md` |

### Resource List

**Core:**
- [data-fetching.md](resources/data-fetching.md) - TanStack Query patterns (useQuery vs useSuspenseQueries)
- [component-patterns.md](resources/component-patterns.md) - Component structure, React Compiler
- [schemas-and-validation.md](resources/schemas-and-validation.md) - JSON → Types → Schemas → Hook pipeline
- [file-organization.md](resources/file-organization.md) - Directory structure

**UI & Routing:**
- [styling-guide.md](resources/styling-guide.md) - Tailwind + shadcn/ui
- [routing-guide.md](resources/routing-guide.md) - TanStack Router (code-based)
- [loading-and-error-states.md](resources/loading-and-error-states.md) - Loading/error handling

**Patterns & Best Practices:**
- [common-patterns.md](resources/common-patterns.md) - Forms, dialogs, mutations
- [complete-examples.md](resources/complete-examples.md) - Full working examples
- [performance.md](resources/performance.md) - Performance optimization
- [typescript-standards.md](resources/typescript-standards.md) - TypeScript best practices

**Testing:**
- [testing-guide.md](resources/testing-guide.md) - Vitest + React Testing Library patterns
