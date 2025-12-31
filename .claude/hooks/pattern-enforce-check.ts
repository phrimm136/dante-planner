#!/usr/bin/env node
/**
 * Pattern Enforce Check - PreToolUse Hook
 *
 * BLOCKS Write operations for NEW files if the required pattern file
 * hasn't been read in this session.
 *
 * Sources of pattern requirements:
 * 1. research.md "Pattern Enforcement" table (task-specific)
 * 2. Fallback: Same-directory existing files (generic)
 */
import { readFileSync, existsSync, readdirSync } from 'fs';
import { join, dirname, basename, extname } from 'path';

interface HookInput {
  session_id: string;
  tool_name: string;
  tool_input: {
    file_path?: string;
    content?: string;
  };
}

interface PatternRequirement {
  newFile: string;
  mustReadFirst: string;
  patternToCopy: string;
}

function parsePatternEnforcementTable(content: string): PatternRequirement[] {
  const requirements: PatternRequirement[] = [];

  // Find "Pattern Enforcement" section
  const sectionMatch = content.match(/## Pattern Enforcement[^\n]*\n([\s\S]*?)(?=\n## |$)/i);
  if (!sectionMatch) return requirements;

  const section = sectionMatch[1];

  // Parse markdown table rows
  // Format: | NewFile | MUST Read First | Pattern to Copy |
  const tableRowRegex = /\|\s*`?([^`|]+)`?\s*\|\s*`?([^`|]+)`?\s*\|\s*([^|]+)\s*\|/g;
  let match;

  while ((match = tableRowRegex.exec(section)) !== null) {
    const newFile = match[1].trim();
    const mustReadFirst = match[2].trim();
    const patternToCopy = match[3].trim();

    // Skip header row
    if (newFile.toLowerCase().includes('new file') || newFile.includes('---')) {
      continue;
    }

    requirements.push({ newFile, mustReadFirst, patternToCopy });
  }

  return requirements;
}

function findResearchMdFiles(projectDir: string): string[] {
  const researchFiles: string[] = [];
  const docsDir = join(projectDir, 'docs');

  if (!existsSync(docsDir)) return researchFiles;

  // Recursively find research.md files
  function walkDir(dir: string, depth: number = 0) {
    if (depth > 4) return; // Limit depth

    try {
      const entries = readdirSync(dir, { withFileTypes: true });
      for (const entry of entries) {
        const fullPath = join(dir, entry.name);
        if (entry.isDirectory()) {
          walkDir(fullPath, depth + 1);
        } else if (entry.name === 'research.md') {
          researchFiles.push(fullPath);
        }
      }
    } catch {
      // Ignore permission errors
    }
  }

  walkDir(docsDir);
  return researchFiles;
}

function getReadFilesInSession(sessionId: string, projectDir: string): Set<string> {
  const readFiles = new Set<string>();
  const cacheDir = join(projectDir, '.claude', 'tsc-cache', sessionId);
  const readFilesPath = join(cacheDir, 'read-files.log');

  if (existsSync(readFilesPath)) {
    const content = readFileSync(readFilesPath, 'utf-8');
    const lines = content.split('\n').filter(l => l.length > 0);

    for (const line of lines) {
      // Format: timestamp:filepath
      const colonIndex = line.indexOf(':');
      if (colonIndex > 0) {
        const filePath = line.substring(colonIndex + 1);
        readFiles.add(filePath);
        // Also add just the filename for fuzzy matching
        readFiles.add(basename(filePath));
      }
    }
  }

  return readFiles;
}

function findSimilarFilesInDirectory(filePath: string, projectDir: string): string[] {
  const dir = dirname(filePath);
  const ext = extname(filePath);
  const similarFiles: string[] = [];

  // Check if directory exists
  const fullDir = filePath.startsWith('/') ? dir : join(projectDir, dir);
  if (!existsSync(fullDir)) {
    return similarFiles;
  }

  try {
    const files = readdirSync(fullDir);
    for (const file of files) {
      // Same extension, not the target file
      if (extname(file) === ext && file !== basename(filePath)) {
        similarFiles.push(join(dir, file));
      }
    }
  } catch {
    // Ignore errors
  }

  return similarFiles.slice(0, 3); // Limit to 3 suggestions
}

function normalizeFilePath(filePath: string): string {
  // Extract just the filename for comparison
  return basename(filePath);
}

function checkPatternRequirement(
  targetFile: string,
  requirements: PatternRequirement[],
  readFiles: Set<string>
): { blocked: boolean; reason: string; requiredFile: string; patternToCopy: string } | null {
  const targetBasename = normalizeFilePath(targetFile);

  for (const req of requirements) {
    // Check if this requirement applies to the target file
    const reqBasename = normalizeFilePath(req.newFile);

    if (targetBasename === reqBasename || targetFile.includes(req.newFile)) {
      // Check if required file was read
      const requiredBasename = normalizeFilePath(req.mustReadFirst);
      const wasRead = readFiles.has(req.mustReadFirst) ||
                      readFiles.has(requiredBasename) ||
                      [...readFiles].some(f => f.includes(requiredBasename));

      if (!wasRead) {
        return {
          blocked: true,
          reason: `Pattern file not read before creating this file`,
          requiredFile: req.mustReadFirst,
          patternToCopy: req.patternToCopy
        };
      }
    }
  }

  return null;
}

function main() {
  try {
    const input = readFileSync(0, 'utf-8');
    const data: HookInput = JSON.parse(input);

    // Only check Write tool (new files)
    if (data.tool_name !== 'Write') {
      process.exit(0);
    }

    const filePath = data.tool_input.file_path;
    if (!filePath) {
      process.exit(0);
    }

    // Skip non-code files
    const codeExtensions = ['.java', '.ts', '.tsx', '.js', '.jsx', '.kt', '.py'];
    const ext = extname(filePath);
    if (!codeExtensions.includes(ext)) {
      process.exit(0);
    }

    // Skip test files
    if (filePath.includes('/test/') || filePath.includes('.test.') || filePath.includes('.spec.')) {
      process.exit(0);
    }

    // Skip hooks directory itself
    if (filePath.includes('/.claude/hooks/')) {
      process.exit(0);
    }

    const sessionId = data.session_id || 'default';
    const projectDir = process.env.CLAUDE_PROJECT_DIR || '';

    // Get files read in this session
    const readFiles = getReadFilesInSession(sessionId, projectDir);

    // Check if file already exists (Edit vs Write for new file)
    const fullPath = filePath.startsWith('/') ? filePath : join(projectDir, filePath);
    if (existsSync(fullPath)) {
      // File exists, this is an overwrite - less strict
      process.exit(0);
    }

    // NEW FILE CREATION - Apply strict pattern checking

    let blocked = false;
    let blockReason = '';
    let requiredFile = '';
    let patternToCopy = '';
    let source = '';
    let matchedInResearch = false;  // Track if file was found in research.md

    // 1. Check research.md Pattern Enforcement table
    const researchFiles = findResearchMdFiles(projectDir);
    for (const researchMd of researchFiles) {
      if (existsSync(researchMd)) {
        const researchContent = readFileSync(researchMd, 'utf-8');
        const requirements = parsePatternEnforcementTable(researchContent);

        if (requirements.length > 0) {
          const check = checkPatternRequirement(filePath, requirements, readFiles);
          if (check) {
            // File matched in research.md but pattern not read
            blocked = true;
            blockReason = check.reason;
            requiredFile = check.requiredFile;
            patternToCopy = check.patternToCopy;
            source = `research.md`;
            matchedInResearch = true;
            break;
          } else {
            // Check if file was even in the requirements (matched and passed)
            const targetBasename = basename(filePath);
            const wasInRequirements = requirements.some(req =>
              targetBasename === basename(req.newFile) || filePath.includes(req.newFile)
            );
            if (wasInRequirements) {
              matchedInResearch = true;
              break;
            }
          }
        }
      }
    }

    // 2. Fallback: Check if ANY file in same directory was read
    //    ONLY if file was NOT matched in research.md
    if (!blocked && !matchedInResearch) {
      const similarFiles = findSimilarFilesInDirectory(filePath, projectDir);

      if (similarFiles.length > 0) {
        // Check if at least one similar file was read
        const readAnySimilar = similarFiles.some(f => {
          const fBasename = basename(f);
          return readFiles.has(f) || readFiles.has(fBasename) ||
                 [...readFiles].some(rf => rf.includes(fBasename));
        });

        if (!readAnySimilar) {
          blocked = true;
          blockReason = 'No existing file in this directory was read';
          requiredFile = similarFiles.join(' or ');
          patternToCopy = 'class structure, imports, naming conventions';
          source = 'directory scan';
        }
      }
    }

    // Output block decision as JSON only
    if (blocked) {
      const reason = [
        '🚫 PATTERN FILE NOT READ',
        '',
        `📄 Creating: ${basename(filePath)}`,
        `❌ Reason: ${blockReason}`,
        '',
        `📋 MUST Read First: ${requiredFile}`,
        `📝 Pattern to Copy: ${patternToCopy}`,
        `📍 Source: ${source}`,
        '',
        'ACTION: Use Read tool on the required file FIRST, then retry.'
      ].join('\n');

      // PreToolUse hook MUST output only JSON
      console.log(JSON.stringify({ decision: 'block', reason }));
    }

    process.exit(0);
  } catch (err) {
    // Silently exit on errors to not block operations
    process.exit(0);
  }
}

main().catch(() => process.exit(0));
