#!/usr/bin/env node
/**
 * Pattern Read Tracker - PostToolUse Hook
 *
 * Tracks files read during the session to enable pattern enforcement.
 * Runs after Read tool to record which files have been read.
 */
import { readFileSync, writeFileSync, mkdirSync, existsSync } from 'fs';
import { join, dirname } from 'path';

interface HookInput {
  session_id: string;
  tool_name: string;
  tool_input: {
    file_path?: string;
  };
  tool_output?: {
    content?: string;
  };
}

function main() {
  try {
    const input = readFileSync(0, 'utf-8');
    const data: HookInput = JSON.parse(input);

    // Only track Read tool
    if (data.tool_name !== 'Read') {
      process.exit(0);
    }

    const filePath = data.tool_input.file_path;
    if (!filePath) {
      process.exit(0);
    }

    const sessionId = data.session_id || 'default';
    const projectDir = process.env.CLAUDE_PROJECT_DIR || '';
    const cacheDir = join(projectDir, '.claude', 'tsc-cache', sessionId);

    // Ensure cache directory exists
    if (!existsSync(cacheDir)) {
      mkdirSync(cacheDir, { recursive: true });
    }

    const readFilesPath = join(cacheDir, 'read-files.log');

    // Append read file to log (with timestamp)
    const timestamp = Math.floor(Date.now() / 1000);
    const logEntry = `${timestamp}:${filePath}\n`;

    // Read existing log and append
    let existingLog = '';
    if (existsSync(readFilesPath)) {
      existingLog = readFileSync(readFilesPath, 'utf-8');
    }

    // Avoid duplicate entries for same file
    if (!existingLog.includes(`:${filePath}\n`) && !existingLog.endsWith(`:${filePath}`)) {
      writeFileSync(readFilesPath, existingLog + logEntry);
    }

    process.exit(0);
  } catch (err) {
    // Silently exit on errors
    process.exit(0);
  }
}

main().catch(() => process.exit(0));
