#!/usr/bin/env node
/**
 * Prompt Quality Gate Hook
 *
 * LLM-based semantic validation: Assesses prompt quality holistically
 *
 * Validates:
 * 1. Intent clarity - Is it clear what the user wants to achieve?
 * 2. Outcome likelihood - Will this produce good results or waste tokens?
 * 3. Context appropriateness - Is sufficient context provided for the request type?
 */

import { readFileSync } from 'fs';
import { join } from 'path';

interface HookInput {
    session_id: string;
    transcript_path: string;
    cwd: string;
    prompt: string;
}

interface ConversationMessage {
    role: 'user' | 'assistant';
    content: string;
}

interface AssessmentResult {
    decision: 'approve' | 'block';
    reason: string;
    clarity_score?: number;
    outcome_likelihood?: 'high' | 'medium' | 'low';
}

interface HookOutput {
    decision: 'block';
    reason: string;
}

// ============================================================
// FAST-PATH EXCEPTIONS (Skip LLM call for obvious cases)
// ============================================================

function shouldSkipAssessment(prompt: string): boolean {
    const trimmed = prompt.trim();

    // Always allow slash commands
    if (/^\//i.test(trimmed)) {
        return true;
    }

    // Always allow conversation continuations
    if (/^(continue|next|proceed|yes|ok|good|right)/i.test(trimmed) && trimmed.length < 20) {
        return true;
    }

    return false;
}

// ============================================================
// CONVERSATION CONTEXT EXTRACTION
// ============================================================

function extractRecentContext(transcriptPath: string, maxMessages: number = 10): ConversationMessage[] {
    try {
        const transcript = readFileSync(transcriptPath, 'utf-8');
        const lines = transcript.split('\n');
        const messages: ConversationMessage[] = [];

        let currentRole: 'user' | 'assistant' | null = null;
        let currentContent: string[] = [];

        for (const line of lines) {
            if (line.startsWith('user: ')) {
                if (currentRole && currentContent.length > 0) {
                    messages.push({ role: currentRole, content: currentContent.join('\n') });
                }
                currentRole = 'user';
                currentContent = [line.substring(6)];
            } else if (line.startsWith('assistant: ')) {
                if (currentRole && currentContent.length > 0) {
                    messages.push({ role: currentRole, content: currentContent.join('\n') });
                }
                currentRole = 'assistant';
                currentContent = [line.substring(11)];
            } else if (currentRole && line.trim()) {
                currentContent.push(line);
            }
        }

        if (currentRole && currentContent.length > 0) {
            messages.push({ role: currentRole, content: currentContent.join('\n') });
        }

        return messages.slice(-maxMessages);
    } catch (err) {
        console.error('Error reading transcript:', err);
        return [];
    }
}

// ============================================================
// LLM-BASED QUALITY ASSESSMENT
// ============================================================

async function assessPromptQuality(
    prompt: string,
    conversationContext: ConversationMessage[]
): Promise<AssessmentResult> {
    const apiKey = process.env.ANTHROPIC_API_KEY;

    if (!apiKey) {
        // No API key - fail open
        return { decision: 'approve', reason: 'No API key configured' };
    }

    // Build context summary
    const contextSummary = conversationContext.length > 0
        ? `Recent conversation (${conversationContext.length} messages):\n${conversationContext.slice(-5).map(m => `${m.role}: ${m.content.substring(0, 200)}...`).join('\n')}`
        : 'No prior conversation context (this is the first message)';

    const assessmentPrompt = `You are a prompt quality evaluator. Assess if this prompt will produce good results or waste tokens.

User prompt:
"""
${prompt}
"""

${contextSummary}

Evaluate based on:
1. **Intent Clarity**: Can you understand what the user wants to achieve? (Consider conversation context - "continue" is clear after discussion, unclear as first message)
2. **Outcome Likelihood**: Will this produce useful results? Is it actionable or too vague?
3. **Context Appropriateness**: Is sufficient context provided for the request type? (Bug fix needs errors, feature needs requirements, refactor needs scope)

Decision criteria:
- BLOCK if: Intent is unclear AND no actionable path exists AND conversation context doesn't help
- APPROVE if: Intent is clear enough to produce useful work OR conversation context provides clarity

Respond in JSON:
{
  "decision": "approve" or "block",
  "reason": "1-2 sentence explanation",
  "clarity_score": 1-10,
  "outcome_likelihood": "high" or "medium" or "low"
}`;

    try {
        const response = await fetch('https://api.anthropic.com/v1/messages', {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json',
                'x-api-key': apiKey,
                'anthropic-version': '2023-06-01',
            },
            body: JSON.stringify({
                model: 'claude-3-haiku-20240307',
                max_tokens: 500,
                messages: [
                    {
                        role: 'user',
                        content: assessmentPrompt,
                    },
                ],
            }),
        });

        if (!response.ok) {
            console.error('API error:', response.status, await response.text());
            return { decision: 'approve', reason: 'API error - fail open' };
        }

        const data = await response.json();
        const content = data.content[0].text;

        // Extract JSON from response (handle markdown code blocks)
        const jsonMatch = content.match(/\{[\s\S]*\}/);
        if (!jsonMatch) {
            return { decision: 'approve', reason: 'Failed to parse assessment' };
        }

        const assessment = JSON.parse(jsonMatch[0]);

        return {
            decision: assessment.decision === 'block' ? 'block' : 'approve',
            reason: assessment.reason || 'No reason provided',
            clarity_score: assessment.clarity_score,
            outcome_likelihood: assessment.outcome_likelihood,
        };
    } catch (err) {
        console.error('Assessment error:', err);
        return { decision: 'approve', reason: 'Assessment failed - fail open' };
    }
}

function formatBlockMessage(result: AssessmentResult): HookOutput {
    let message = `\n━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n`;
    message += `🚫 PROMPT BLOCKED: Quality Gate\n`;
    message += `━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n\n`;
    message += `❌ ${result.reason}\n\n`;

    if (result.clarity_score !== undefined) {
        message += `📊 Clarity Score: ${result.clarity_score}/10\n`;
    }

    if (result.outcome_likelihood) {
        message += `🎯 Outcome Likelihood: ${result.outcome_likelihood}\n`;
    }

    message += `\n💡 Tip: Provide clear intent, relevant context, and specific goals\n`;
    message += `━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n`;

    return {
        decision: 'block',
        reason: message,
    };
}

// ============================================================
// MAIN
// ============================================================

async function main() {
    try {
        const input = readFileSync(0, 'utf-8');
        const data: HookInput = JSON.parse(input);
        const prompt = data.prompt;

        // Fast path: Skip assessment for obvious cases
        if (shouldSkipAssessment(prompt)) {
            process.exit(0);
        }

        // Extract conversation context for holistic assessment
        const context = extractRecentContext(data.transcript_path);

        // Perform LLM-based quality assessment
        const result = await assessPromptQuality(prompt, context);

        if (result.decision === 'approve') {
            process.exit(0);
        }

        if (result.decision === 'block') {
            const output = formatBlockMessage(result);
            console.log(JSON.stringify(output));
            process.exit(0);
        }

        // Default: allow
        process.exit(0);
    } catch (err) {
        console.error('Error in prompt-quality-gate:', err);
        // On error, allow prompt through (fail open)
        process.exit(0);
    }
}

main();
