#!/usr/bin/env node
/**
 * Forbidden Patterns Check - PostToolUse Hook
 *
 * Checks edited/written files for forbidden patterns and warns.
 * Patterns are defined in forbidden-patterns.json for easy maintenance.
 */
import { readFileSync, existsSync } from 'fs';
import { join, extname } from 'path';

interface HookInput {
  session_id: string;
  tool_name: string;
  tool_input: {
    file_path?: string;
    content?: string;
    new_string?: string;
  };
  tool_result?: string;
}

interface PatternRule {
  regex: string;
  message: string;
  severity: 'error' | 'warning';
  exclude?: string | string[];
}

interface DomainConfig {
  patterns: PatternRule[];
  extensions: string[];
  fileSpecificPatterns?: Record<string, PatternRule[]>;
}

interface ForbiddenPatternsConfig {
  frontend: DomainConfig;
  backend: DomainConfig;
}

function loadConfig(projectDir: string): ForbiddenPatternsConfig | null {
  const configPath = join(projectDir, '.claude', 'hooks', 'forbidden-patterns.json');
  if (!existsSync(configPath)) {
    return null;
  }
  return JSON.parse(readFileSync(configPath, 'utf-8'));
}

function getDomain(filePath: string): 'frontend' | 'backend' | null {
  if (filePath.includes('/frontend/') || filePath.startsWith('frontend/')) {
    return 'frontend';
  }
  if (filePath.includes('/backend/') || filePath.startsWith('backend/')) {
    return 'backend';
  }
  return null;
}

function checkPatterns(content: string, filePath: string, patterns: PatternRule[]): string[] {
  const violations: string[] = [];

  for (const pattern of patterns) {
    const regex = new RegExp(pattern.regex, 'g');
    const matches = content.match(regex);

    if (matches && matches.length > 0) {
      // Check exclusions
      let excluded = false;
      if (pattern.exclude) {
        const exclusions = Array.isArray(pattern.exclude) ? pattern.exclude : [pattern.exclude];
        excluded = exclusions.some(exc =>
          content.includes(exc) || filePath.includes(exc)
        );
      }

      if (!excluded) {
        const icon = pattern.severity === 'error' ? '❌' : '⚠️';
        violations.push(`${icon} ${pattern.message}`);
      }
    }
  }

  return violations;
}

function main() {
  try {
    const input = readFileSync(0, 'utf-8');
    const data: HookInput = JSON.parse(input);

    // Only check Write and Edit tools
    if (!['Write', 'Edit', 'MultiEdit'].includes(data.tool_name)) {
      process.exit(0);
    }

    const filePath = data.tool_input.file_path;
    if (!filePath) {
      process.exit(0);
    }

    // Determine domain
    const domain = getDomain(filePath);
    if (!domain) {
      process.exit(0);
    }

    // Load config
    const projectDir = process.env.CLAUDE_PROJECT_DIR || '';
    const config = loadConfig(projectDir);
    if (!config) {
      process.exit(0);
    }

    const domainConfig = config[domain];

    // Check file extension
    const ext = extname(filePath);
    if (!domainConfig.extensions.includes(ext)) {
      process.exit(0);
    }

    // Get content (for Write) or new_string (for Edit)
    const content = data.tool_input.content || data.tool_input.new_string || '';
    if (!content) {
      process.exit(0);
    }

    // Check patterns
    const violations = checkPatterns(content, filePath, domainConfig.patterns);

    // Check file-specific patterns (e.g., Controller-specific, Service-specific)
    if (domainConfig.fileSpecificPatterns) {
      for (const [fileType, patterns] of Object.entries(domainConfig.fileSpecificPatterns)) {
        if (filePath.includes(fileType)) {
          violations.push(...checkPatterns(content, filePath, patterns));
        }
      }
    }

    if (violations.length > 0) {
      let output = '\n┌─ PATTERN CHECK ─────────────────────────────┐\n';
      output += `│ File: ${filePath.split('/').slice(-2).join('/')}\n`;
      output += '├────────────────────────────────────────────────┤\n';
      violations.forEach(v => {
        output += `│ ${v}\n`;
      });
      output += '└────────────────────────────────────────────────┘\n';

      console.log(output);
    }

    process.exit(0);
  } catch (err) {
    // Silently exit on errors
    process.exit(0);
  }
}

main().catch(() => process.exit(0));
