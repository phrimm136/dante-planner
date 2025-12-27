#!/usr/bin/env node
/**
 * Prompt Quality Gate Hook
 *
 * Hybrid validation: Fast rule-based checks + LLM fallback for ambiguous cases.
 * Based on principles from: https://claude.ai/share/d4541bf3-48ee-4d02-9cf7-9d1dbb3887d0
 *
 * Validates:
 * 1. Context provision - technical signals (files, errors, code blocks)
 * 2. Prompt clarity - specific verbs, not vague commands
 * 3. Scope control - prevents overly broad requests (vertical slicing)
 */

import { readFileSync } from 'fs';

interface HookInput {
    session_id: string;
    transcript_path: string;
    cwd: string;
    prompt: string;
}

interface ValidationResult {
    decision: 'approve' | 'block' | 'needs_llm';
    reason?: string;
    suggestions?: string[];
}

interface HookOutput {
    decision: 'block';
    reason: string;
}

// ============================================================
// EXCEPTION PATTERNS (Always Allow)
// ============================================================

const EXCEPTION_PATTERNS = {
    // Conversation continuations
    continuations: [
        /^(continue|next|proceed|keep going|go ahead|go on)/i,
        /^(yes|yeah|yep|sure|ok|okay|alright|right|correct)/i,
        /^(good|great|perfect|sounds good|looks good|fine)/i,
        /^(계속|다음|진행|네|예|응|좋아|됐어|괜찮아)/,
    ],

    // Skill/command invocations
    skillInvocations: [
        /^\//,  // Slash commands
        /^run\s+\//i,
    ],

    // Simple file references (implicit context)
    fileReferences: [
        /\.(ts|tsx|js|jsx|java|json|md|css|scss|html|xml|yaml|yml|sh|py)(\s|$|:)/i,
        /\/[a-zA-Z0-9_-]+\//,  // Path patterns
    ],

    // Code blocks (has context)
    codeBlocks: [
        /```[\s\S]*```/,
        /`[^`]+`/,
    ],

    // Error messages (has context)
    errorPatterns: [
        /error:|Error:|ERROR:/i,
        /exception|Exception|EXCEPTION/i,
        /failed|Failed|FAILED/i,
        /TypeError|SyntaxError|ReferenceError|NullPointer/i,
        /at line \d+|:\d+:\d+/,
    ],

    // Questions about existing code (exploratory)
    exploratoryQuestions: [
        /where|how does|what is|what's|explain|show me|find/i,
        /어디|어떻게|뭐야|설명/,
    ],
};

// ============================================================
// BLOCK PATTERNS (Definitely Block)
// ============================================================

const BLOCK_PATTERNS = {
    // Too vague - single word commands
    tooShort: {
        test: (prompt: string) => prompt.trim().split(/\s+/).length <= 2 && prompt.length < 15,
        reason: 'Prompt is too short',
        suggestions: [
            'Specify what you want to accomplish',
            'Include relevant file or component names',
            'Describe the expected outcome or current problem',
        ],
    },

    // Vague verbs without context
    vagueVerbs: {
        test: (prompt: string) => {
            const vagueStart = /^(help|fix|make|do|improve|update|change|modify)\s+/i;
            const hasContext = hasAnyContextSignal(prompt);
            return vagueStart.test(prompt.trim()) && !hasContext && prompt.length < 50;
        },
        reason: 'Starts with vague verb and lacks specific context',
        suggestions: [
            'Instead of "fix" → "Fix the Y error in X file"',
            'Instead of "help" → "Help implement Z feature, currently at A state, want B"',
            'Include error messages or code snippets',
        ],
    },

    // Overly broad scope (violates vertical slicing)
    broadScope: {
        test: (prompt: string) => {
            const broadPatterns = [
                /all\s+(the\s+)?(files?|components?|pages?)/i,
                /entire\s+(system|app|application|codebase)/i,
                /everything|everywhere/i,
                /refactor\s+(the\s+)?(whole|entire|all)/i,
                /모든|전체|전부/,
            ];
            const hasSpecificTarget = /\.(ts|tsx|js|java|json)\b|\/[a-z]+\//i.test(prompt);
            return broadPatterns.some(p => p.test(prompt)) && !hasSpecificTarget;
        },
        reason: 'Scope is too broad (violates Vertical Slicing principle)',
        suggestions: [
            'Start with a single file or component',
            'Instead of "refactor everything" → "Improve state management in UserCard component"',
            'Focus on one user flow at a time',
        ],
    },
};

// ============================================================
// CONTEXT SIGNAL DETECTION
// ============================================================

function hasAnyContextSignal(prompt: string): boolean {
    const signals = [
        ...EXCEPTION_PATTERNS.fileReferences,
        ...EXCEPTION_PATTERNS.codeBlocks,
        ...EXCEPTION_PATTERNS.errorPatterns,
        /function\s+\w+/i,
        /component\s+\w+/i,
        /class\s+\w+/i,
        /const\s+\w+/i,
        /import\s+/i,
        /api|endpoint|route/i,
        /database|db|query/i,
        /hook|use[A-Z]/i,
    ];

    return signals.some(pattern => pattern.test(prompt));
}

function isException(prompt: string): boolean {
    const allExceptions = [
        ...EXCEPTION_PATTERNS.continuations,
        ...EXCEPTION_PATTERNS.skillInvocations,
        ...EXCEPTION_PATTERNS.fileReferences,
        ...EXCEPTION_PATTERNS.codeBlocks,
        ...EXCEPTION_PATTERNS.errorPatterns,
        ...EXCEPTION_PATTERNS.exploratoryQuestions,
    ];

    return allExceptions.some(pattern => pattern.test(prompt));
}

// ============================================================
// VALIDATION LOGIC
// ============================================================

function validatePrompt(prompt: string): ValidationResult {
    const trimmed = prompt.trim();

    // Fast path: Check exceptions first (always allow)
    if (isException(trimmed)) {
        return { decision: 'approve' };
    }

    // Fast path: Check definite blocks
    for (const [_name, rule] of Object.entries(BLOCK_PATTERNS)) {
        if (rule.test(trimmed)) {
            return {
                decision: 'block',
                reason: rule.reason,
                suggestions: rule.suggestions,
            };
        }
    }

    // Medium-length prompts without clear context signals → needs LLM
    if (trimmed.length >= 15 && trimmed.length < 80 && !hasAnyContextSignal(trimmed)) {
        return { decision: 'needs_llm' };
    }

    // Long prompts or those with context signals → approve
    return { decision: 'approve' };
}

function formatBlockMessage(result: ValidationResult): HookOutput {
    let message = `\n━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n`;
    message += `🚫 PROMPT BLOCKED: Quality Gate\n`;
    message += `━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n\n`;
    message += `❌ Issue: ${result.reason}\n\n`;

    if (result.suggestions && result.suggestions.length > 0) {
        message += `💡 How to improve:\n`;
        result.suggestions.forEach(s => {
            message += `   • ${s}\n`;
        });
        message += `\n`;
    }

    message += `📚 Reference: Context Provision, Clear Prompts, Vertical Slicing\n`;
    message += `━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n`;

    return {
        decision: 'block',
        reason: message,
    };
}

// ============================================================
// LLM FALLBACK (for ambiguous cases)
// ============================================================

function createLLMPrompt(userPrompt: string): string {
    return `You are a prompt quality validator. Evaluate if the following prompt has sufficient context and clarity.

User prompt:
"""
${userPrompt}
"""

Evaluation criteria:
1. Context: Does it include file names, error messages, code snippets, or technical details?
2. Clarity: Is it specific about what is wanted? Does it avoid vague verbs only?
3. Scope: Is it an appropriate size that can be handled at once?

Respond ONLY in the following JSON format:
{"decision": "approve" or "block", "reason": "judgment reason"}`;
}

// ============================================================
// MAIN
// ============================================================

async function main() {
    try {
        const input = readFileSync(0, 'utf-8');
        const data: HookInput = JSON.parse(input);
        const prompt = data.prompt;

        const result = validatePrompt(prompt);

        if (result.decision === 'approve') {
            // Allow prompt to proceed
            process.exit(0);
        }

        if (result.decision === 'block') {
            // Definitely block
            const output = formatBlockMessage(result);
            console.log(JSON.stringify(output));
            process.exit(0);
        }

        if (result.decision === 'needs_llm') {
            // For now, approve ambiguous cases with a warning
            // TODO: Implement actual LLM call when needed
            const warningOutput = {
                continueThread: true,
                suppressOutput: false,
                message: `⚠️ Adding more context to your prompt may yield better results.`,
            };
            console.log(JSON.stringify(warningOutput));
            process.exit(0);
        }

        process.exit(0);
    } catch (err) {
        console.error('Error in prompt-quality-gate:', err);
        process.exit(1);
    }
}

main();
