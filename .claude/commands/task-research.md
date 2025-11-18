Ultrathink

# Research on the Codebase

**IMPORTANT**: This command reads research instructions from the `instructions.md` file at the task path and conducts research based on the scope in `instructions.md`.

**OUTPUT RULES**:
- MAX 50 lines total
- NO code snippets, examples, or syntax blocks
- Use BULLET POINTS only
- Be descriptive but concise

## Step 1: Read Instructions
Read `$1/instructions.md` to understand the task scope and research requirements.

## Step 2: Conduct Research
Use a Task agent with Explore subagent to research:
- Technologies/frameworks mentioned in instructions
- Relevant patterns in existing code
- Configuration and project setup
- Related code that will be modified or extended

## Step 3: Generate Research Document
Write findings to `$1/research.md` (MAX 50 lines):

### Overview of Codebase (15-20 bullets)
- Existing patterns and practices
- Coding standards and frameworks
- How current code relates to the task

### Codebase Structure (10-15 bullets)
- Project file organization
- Where relevant code lives
- How structure affects the task

### Gotchas and Pitfalls (10-15 bullets)
- Technical debt or legacy patterns to avoid
- Configuration quirks
- Known issues, common mistakes, or anti-patterns
- Dependency constraints
