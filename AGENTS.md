# Project Agent Instructions — lob-matrix

## Project Context
Real-time market microstructure ingestion and ML inference pipeline
(Java 21 / Spring Boot 3.3.3 core, Python quant research lab, ONNX bridge,
live dashboard). Full milestone roadmap lives in `README.md`. Story IDs
follow the pattern `M<milestone>P<phase>S<step>` (e.g. `M4P3S2`).

## CRITICAL: Do Not Trust Commit Message Labels
Past commits in this repo have mislabeled story IDs relative to their
actual content (e.g. a commit labeled `M4P2S1` implements what the
roadmap defines as `M4P1S1`). Before starting or resuming any story:
1. Read the literal Objective + Verification text for that story ID
   directly from `README.md`.
2. Check actual file contents/diffs against that text — never assume a
   commit message's story ID label is accurate.
3. If a mismatch is found, report it explicitly before writing new code.

## Build Environment (settled, do not relitigate)
- JDK 21 at `/usr/lib/jvm/java-21-openjdk-amd64` is the ONLY correct
  target. `pom.xml` has `<release>21</release>` explicitly set in the
  maven-compiler-plugin configuration — this is intentional and final.
- NEVER modify pom.xml's Java version, compiler plugin version, or
  release/source/target settings under any circumstance. If a build
  fails and looks like a Java-version issue, investigate the actual code
  error first — do not touch version configuration as a "fix."
- Verification command is always: `./mvnw clean compile` (or the
  story-specific test command). Report the literal exit status.

## API Verification Rule
Before calling any Spring/third-party method not already used elsewhere
in this file, verify its real signature — run `mvn dependency:sources`
once, then grep the extracted sources jar, or check an already-working
usage elsewhere in the codebase. Do not call a method based on a
memorized/assumed signature.

## Anti-Rationalization Rule
Never use phrases like "would work," "should integrate," "in a proper
environment," or similar hedged-success language as a substitute for an
actual passing command result. If the stated verification command does
not exit 0, the status is FAIL or BLOCKED — full stop. Report the exact
error. Do not describe untested code as "complete" or "successfully
implemented."

## Scope Discipline
Work exactly one story ID per session. Do not create, modify, or
"helpfully" finish files unrelated to the current story's literal
Objective without explicitly flagging them and getting approval first.
If files already changed in the working tree look out-of-scope for the
current story, report them and ask whether to revert before proceeding.

## Completion Protocol (mandatory, every story)
1. Run `git status --short` and report it verbatim.
2. Run `git diff --stat` for changed files and report it.
3. Run the story's actual Verification command and report literal PASS/
   FAIL/BLOCKED with real output — never a paraphrase.
4. State: "Story `<ID>` complete and validated. Ready to hand off for
   commit — I will not run git add/commit/push myself."
5. Ask whether to proceed to the next story. Wait. Do not auto-continue.

## Git Boundary
Never run git add, commit, push, checkout, branch, merge, rebase, or any
history-modifying command. All git actions are the user's responsibility.

## README Access Rule
README.md is large (~45KB). NEVER read the entire file in one call —
it consumes the majority of the context window and degrades reasoning
quality for the rest of the session. Always extract only the relevant
story section using `grep -n -A 15 "<StoryID>" README.md` or an
offset/limit-bounded read targeting just those line numbers.
