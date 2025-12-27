#!/usr/bin/env node
import { readFileSync } from 'fs';
import { join } from 'path';

interface HookInput {
  session_id: string;
  tool_name: string;
  tool_input: {
    file_path?: string;
    content?: string;
    command?: string;
  };
}

interface FileTriggers {
  pathPatterns?: string[];
  pathExclusions?: string[];
  contentPatterns?: string[];
}

interface SkillRule {
  type: 'guardrail' | 'domain';
  enforcement: 'block' | 'suggest' | 'warn';
  priority: 'critical' | 'high' | 'medium' | 'low';
  fileTriggers?: FileTriggers;
  blockMessage?: string;
}

interface SkillRules {
  version: string;
  skills: Record<string, SkillRule>;
}

function matchesPattern(path: string, pattern: string): boolean {
  // Convert glob pattern to regex
  const regexPattern = pattern
    .replace(/\*\*/g, '§DOUBLESTAR§')
    .replace(/\*/g, '[^/]*')
    .replace(/§DOUBLESTAR§/g, '.*')
    .replace(/\?/g, '.');
  const regex = new RegExp(`^${regexPattern}$`);
  return regex.test(path);
}

function matchesContentPattern(content: string, pattern: string): boolean {
  const regex = new RegExp(pattern);
  return regex.test(content);
}

function validateFrontendCode(filePath: string, content: string): string[] {
  const errors: string[] = [];

  // Only validate .tsx and .ts files in frontend/src
  if (!filePath.match(/frontend\/src\/.*\.(tsx?)/)) {
    return errors;
  }

  // CRITICAL: Check for Zod schema usage in schema files
  if (filePath.includes('/schemas/') && !content.includes('import') && !content.includes('zod')) {
    errors.push('❌ Schema files MUST import and use Zod (import { z } from "zod")');
  }

  // CRITICAL: Forbid PropTypes
  if (content.match(/import.*PropTypes|PropTypes\./)) {
    errors.push('❌ FORBIDDEN: PropTypes detected. Use Zod schemas instead.');
  }

  // CRITICAL: Check for proper TanStack Query usage
  if (filePath.includes('/hooks/use') && content.includes('Query')) {
    if (!content.includes('useSuspenseQuery') && content.match(/useQuery[^A-Z]/)) {
      errors.push('⚠️ WARNING: Use useSuspenseQuery instead of useQuery for better SSR support');
    }
  }

  // CRITICAL: Component files should use proper type imports
  if (filePath.match(/\/components\/.*\.tsx$/) && content.includes('interface') && content.includes('Props')) {
    if (!content.includes('from "@/types') && !content.includes('import type')) {
      errors.push('⚠️ Consider importing types from @/types directory or using "import type"');
    }
  }

  // CRITICAL: Check for shadcn/ui imports when using UI components
  const commonUIElements = ['Button', 'Dialog', 'Card', 'Input', 'Select', 'Table'];
  for (const element of commonUIElements) {
    const elementRegex = new RegExp(`<${element}[\\s>]`);
    if (content.match(elementRegex) && !content.includes(`from '@/components/ui/${element.toLowerCase()}'`)) {
      errors.push(`⚠️ Using <${element}> but missing import from '@/components/ui/${element.toLowerCase()}'`);
    }
  }

  // CRITICAL: Detect hardcoded hex colors
  const hexColorRegex = /#[0-9a-fA-F]{3,6}\b/g;
  const hexMatches = content.match(hexColorRegex);
  if (hexMatches && hexMatches.length > 0) {
    // Exclude common cases like in comments or imports
    const codeLines = content.split('\n').filter(line =>
      !line.trim().startsWith('//') &&
      !line.trim().startsWith('*') &&
      !line.includes('import')
    ).join('\n');

    if (codeLines.match(hexColorRegex)) {
      errors.push(`❌ FORBIDDEN: Hardcoded hex colors found (${hexMatches.slice(0, 3).join(', ')}). Use constants from constants.ts or Tailwind classes.`);
    }
  }

  // CRITICAL: Detect magic numbers (numbers > 10 that aren't array indices or common values)
  // Skip checking in: comments, imports, type definitions, tailwind classes
  const magicNumberRegex = /(?<![\w.#-])\b(1[1-9]|[2-9]\d+)\b(?!\s*(?:px|%|rem|em|vh|vw|ms|s|deg))/g;
  const codeWithoutComments = content.split('\n')
    .filter(line =>
      !line.trim().startsWith('//') &&
      !line.trim().startsWith('*') &&
      !line.includes('className=') &&
      !line.includes('interface ') &&
      !line.includes('type ')
    ).join('\n');

  const magicMatches = codeWithoutComments.match(magicNumberRegex);
  if (magicMatches && magicMatches.length > 0) {
    const uniqueNumbers = [...new Set(magicMatches)].slice(0, 3);
    errors.push(`⚠️ Possible magic numbers found (${uniqueNumbers.join(', ')}). Consider adding to constants.ts if these are reusable values.`);
  }

  // CRITICAL: Detect uppercase constant names with hardcoded string/number values
  // Match: const UPPERCASE_NAME = "string" or 123
  // Suggests the constant should be in constants.ts instead
  if (!filePath.includes('/constants.ts')) {
    const hardcodedConstantRegex = /(?:const|let|var)\s+([A-Z][A-Z0-9_]+)\s*=\s*(?:"[^"]+"|'[^']+'|\d+)/g;
    const hardcodedMatches = [...content.matchAll(hardcodedConstantRegex)];

    if (hardcodedMatches.length > 0) {
      const constantNames = hardcodedMatches.map(m => m[1]).slice(0, 3);
      errors.push(`⚠️ Uppercase constants with hardcoded values found (${constantNames.join(', ')}). Move to constants.ts for centralized management.`);
    }
  }

  // CRITICAL: Detect hardcoded asset paths (.webp files)
  // Must implement helper function in assetPaths.ts instead
  if (!filePath.includes('/assetPaths.ts')) {
    const assetPathRegex = /['"`]\/images\/[^'"`]*\.webp['"`]/g;
    const assetPathMatches = content.match(assetPathRegex);

    if (assetPathMatches && assetPathMatches.length > 0) {
      const uniquePaths = [...new Set(assetPathMatches)].slice(0, 3);
      errors.push(`❌ FORBIDDEN: Hardcoded asset paths found (${uniquePaths.join(', ')}). Implement helper function in assetPaths.ts and use it instead.`);
    }
  }

  return errors;
}

async function main() {
  try {
    // Read input from stdin
    const input = readFileSync(0, 'utf-8');
    const data: HookInput = JSON.parse(input);

    // Only check Write and Edit tools
    if (!['Write', 'Edit', 'MultiEdit'].includes(data.tool_name)) {
      process.exit(0);
    }

    const filePath = data.tool_input.file_path;
    const content = data.tool_input.content || '';

    if (!filePath) {
      process.exit(0);
    }

    // Load skill rules
    const projectDir = process.env.CLAUDE_PROJECT_DIR || '';
    const rulesPath = join(projectDir, '.claude', 'skills', 'skill-rules.json');
    const rules: SkillRules = JSON.parse(readFileSync(rulesPath, 'utf-8'));

    const blockedSkills: Array<{ name: string; message: string }> = [];

    // Run custom validation for frontend code
    if (filePath && content) {
      const validationErrors = validateFrontendCode(filePath, content);
      if (validationErrors.length > 0) {
        const errorMessage = validationErrors.join('\n   ');
        blockedSkills.push({
          name: 'frontend-dev-guidelines',
          message: `Frontend validation failed:\n   ${errorMessage}\n\n   Use Skill tool: 'frontend-dev-guidelines' to review patterns.`
        });
      }
    }

    // Check each skill for file trigger matches
    for (const [skillName, config] of Object.entries(rules.skills)) {
      if (config.enforcement !== 'block') continue;

      const triggers = config.fileTriggers;
      if (!triggers) continue;

      let pathMatched = false;
      let contentMatched = false;
      let excluded = false;

      // Check path patterns
      if (triggers.pathPatterns) {
        pathMatched = triggers.pathPatterns.some(pattern =>
          matchesPattern(filePath, pattern)
        );
      }

      // Check exclusions
      if (triggers.pathExclusions && pathMatched) {
        excluded = triggers.pathExclusions.some(pattern =>
          matchesPattern(filePath, pattern)
        );
      }

      // Check content patterns
      if (triggers.contentPatterns && content) {
        contentMatched = triggers.contentPatterns.some(pattern =>
          matchesContentPattern(content, pattern)
        );
      }

      // Trigger if path matches (and not excluded), OR content matches
      // Skip if already added by custom validation
      if ((pathMatched && !excluded) || contentMatched) {
        if (!blockedSkills.some(s => s.name === skillName)) {
          const message = config.blockMessage ||
            `⚠️ BLOCKED: Use Skill tool with '${skillName}' before this operation.\nFile: ${filePath}`;
          blockedSkills.push({ name: skillName, message });
        }
      }
    }

    // Output block message if any skills need to be used
    if (blockedSkills.length > 0) {
      let output = '━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n';
      output += '🚫 SKILL REQUIRED BEFORE PROCEEDING\n';
      output += '━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n\n';

      blockedSkills.forEach(skill => {
        output += `📋 Required Skill: ${skill.name}\n`;
        output += `   ${skill.message.replace('{file_path}', filePath)}\n\n`;
      });

      output += 'ACTION: Use Skill tool FIRST, then retry this operation\n';
      output += '━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n';

      console.log(output);
    }

    process.exit(0);
  } catch (err) {
    // Silently exit on errors to not block operations
    process.exit(0);
  }
}

main().catch(() => process.exit(0));
