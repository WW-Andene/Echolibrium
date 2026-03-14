---
name: app-audit
description: >
  Perform an exhaustive professional audit of any frontend application — regardless of domain,
  stack, size, or complexity. Trigger for: "audit my app", "deep code review", "security review",
  "performance review", "UX review", "accessibility audit", "review before launch",
  "optimize my app", "improve the design", "make it more professional", "standardize my code",
  "check i18n", "review my AI integration", "improve my features", "my app is messy",
  "restructure my app", "polish my app", "what should I build next", "help me improve my app",
  "my app feels incoherent", "clean up my codebase", "evaluate my existing features",
  "my app grew messy over time", "unify my app's design",
  or when a user shares a source file for serious analysis. Also trigger for PWAs, React/Vue/Svelte/vanilla JS apps,
  calculators, dashboards, trackers, tools, games, medical/fintech/legal/e-commerce/SaaS/creative/AI-powered apps.
  Covers: domain correctness, security, privacy, performance, state management, UI/UX,
  visual design, design language, brand identity, commercial readiness, product aesthetics,
  visual differentiation, polish, standardization, accessibility, browser compat, code quality,
  data integrity, i18n, AI/LLM risks, architecture, existing feature evaluation & evolution,
  competitive research, R&D planning, app coherence restoration, and codebase restructuration.
  For app-specific context files (pre-filled §0), see references/.
---

# Professional App Audit — Universal Framework

---

## TABLE OF CONTENTS — What's in This File, How to Use It

> **How to use this table**: Find the category you care about, then tell Claude:
> - `"Run §A1"` or `"Audit §C2"` — to audit a specific section
> - `"Fix §D5"` or `"Implement §E3 recommendations"` — to fix issues in a specific area
> - `"Audit Category C"` or `"Run the security audit"` — to audit an entire category
> - `"Run P3"` or `"Do Part 3"` — to run a full audit part
> - Or use any **trigger phrase** listed below to jump directly to what you need.

---

### SETUP & ROUTING (read once, then jump to what you need)

| Code | Section | What It Does | Trigger Phrases |
|------|---------|-------------|-----------------|
| — | **QUICK START** | How to invoke this skill (for you and Claude) | — |
| — | **SKILL MAP** | Section index + common execution paths | "what sections are there", "show the skill map" |
| §TRIAGE | **Audit Routing** | Asks which audit mode you want | "audit my app", "review my app", "analyze my app" |
| §0 | **App Context Block** | Captures app identity, tech stack, constraints, design, domain rules | "fill the context", "set up the audit", "describe my app" |

---

### CALIBRATION & PHILOSOPHY (how the audit adapts to YOUR app)

| Code | Section | What It Does | Trigger Phrases |
|------|---------|-------------|-----------------|
| §I.1 | **Domain Classification** | Maps your app type → severity multipliers | "what domain is my app", "classify my app" |
| §I.2 | **Architecture Classification** | Maps your architecture → failure modes to hunt | "what failure modes", "architecture risks" |
| §I.3 | **App Size → Scope** | Determines how many audit parts based on LOC | "how long will this audit be" |
| §I.4 | **Five-Axis Aesthetic Profile** | Classifies aesthetic goals across 5 independent axes | "what aesthetic profile", "classify my design goals" |
| §I.5 | **Domain Rule Extraction** | Extracts constants/formulas from code for verification | "extract domain rules", "find constants" |
| §I.6 | **Knowledge & Source Integrity** | Rules for sourcing domain facts (web, code, user) | — |
| §I.7 | **Adaptive Analysis Protocols** | Mid-audit reclassification triggers | — |
| §II | **10 Iron Laws** | Governing rules for auditor behavior (never violated) | — |

---

### EXECUTION PLAN (how the audit is structured)

| Code | Section | What It Does | Trigger Phrases |
|------|---------|-------------|-----------------|
| §III | **Pre-Flight Checklist** | Mandatory steps before any finding | "start the audit", "run pre-flight" |
| P1 | **Part 1: Inventory & Architecture** | Feature ledger, constraint map, workflow map | "run part 1", "do the inventory" |
| P2 | **Part 2: Domain Logic** | Rule-by-rule verification, formula test vectors | "run part 2", "check my business logic" |
| P3 | **Part 3: Security** | Threat model, attack surface, compliance gaps | "run part 3", "security audit" |
| P4 | **Part 4: State & Data** | State schema, validation gaps, corruption paths | "run part 4", "check state management" |
| P5 | **Part 5: Performance** | Web vitals, resource budget, memory leaks | "run part 5", "performance audit" |
| P6 | **Part 6: Visual Design** | Design tokens, visual rhythm, polish gaps | "run part 6", "design audit" |
| P7 | **Part 7: UX & IA** | Flow analysis, information architecture, copy | "run part 7", "UX audit" |
| P8 | **Part 8: Accessibility** | WCAG 2.1 AA, screen reader, keyboard nav | "run part 8", "accessibility audit" |
| P9 | **Part 9: Compatibility** | Cross-browser, PWA, mobile, network | "run part 9", "compatibility check" |
| P10 | **Part 10: Code Quality** | Dead code, duplication, naming, architecture | "run part 10", "code quality audit" |
| P11 | **Part 11: AI/LLM** | Prompt injection, output sanitization, costs | "run part 11", "audit AI integration" |
| P12 | **Part 12: i18n** | Hardcoded strings, locale formats, RTL | "run part 12", "i18n audit" |
| P13 | **Part 13: Projections** | Scale cliffs, tech debt, dependency decay | "run part 13", "future-proof my app" |
| Final | **Summary Dashboard** | Findings table, root cause, quick wins, roadmap | "show the summary", "give me the dashboard" |

---

### CATEGORY A — DOMAIN LOGIC & CORRECTNESS

> **When to use**: Your app produces wrong numbers, calculations are off, formulas seem broken, or you want to verify business rules are implemented correctly.
>
> **Trigger phrases**: "check my formulas", "verify calculations", "audit business logic", "my numbers are wrong", "check domain rules", "verify correctness"

| Code | Section | What It Audits |
|------|---------|---------------|
| §A1 | Business Rule & Formula Correctness | Constants, formulas, operator precision, rounding, units, invariants |
| §A2 | Probability & Statistical Correctness | RNG, distributions, pity systems, expected value, displayed odds |
| §A3 | Temporal & Timezone Correctness | Timezone handling, DST, date boundaries, scheduling, format |
| §A4 | State Machine Correctness | State transitions, unreachable states, deadlocks, illegal transitions |
| §A5 | Embedded Data Accuracy | Lookup tables, reference data, version currency, fallback values |
| §A6 | Async & Concurrency Bug Patterns | Race conditions, stale closures, debounce, cancellation, ordering |
| §A7 | JS Type Coercion & Implicit Conversion | `==` vs `===`, falsy traps, parseInt pitfalls, NaN propagation |

---

### CATEGORY B — STATE MANAGEMENT & DATA INTEGRITY

> **When to use**: Data gets lost, state behaves unexpectedly, imports/exports are broken, or persistence has issues.
>
> **Trigger phrases**: "fix state management", "data gets lost", "audit state", "persistence issues", "import/export broken", "data integrity"

| Code | Section | What It Audits |
|------|---------|---------------|
| §B1 | State Architecture | Single source of truth, derived state, initialization, schema |
| §B2 | Persistence & Storage | localStorage/SharedPreferences, quota, migration, concurrent access |
| §B3 | Input Validation & Sanitization | Boundary values, type coercion, injection through input |
| §B4 | Import & Export Integrity | Round-trip fidelity, version compatibility, corruption detection |
| §B5 | Data Flow Map | Data lifecycle from entry to display, transformation audit |
| §B6 | Mutation & Reference Integrity | Immutability discipline, shared reference bugs, deep copy |

---

### CATEGORY C — SECURITY & TRUST

> **When to use**: Security review before launch, handling sensitive data, API key management, compliance requirements.
>
> **Trigger phrases**: "security review", "security audit", "check for vulnerabilities", "is my app secure", "privacy audit", "GDPR check", "check permissions"

| Code | Section | What It Audits |
|------|---------|---------------|
| §C1 | Authentication & Authorization | Credential storage, session management, privilege escalation |
| §C2 | Injection & XSS | innerHTML, DOM XSS, eval, URL injection, CSS injection |
| §C3 | Prototype Pollution & Import Safety | JSON.parse safety, prototype pollution, property collision |
| §C4 | Network & Dependencies | HTTPS, SRI, CORS, CSP, third-party tracking |
| §C5 | Privacy & Data Minimization | PII inventory, URL leakage, fingerprinting, export sensitivity |
| §C6 | Compliance & Legal | GDPR/CCPA, age restrictions, IP/copyright, financial/medical disclaimers |
| §C7 | Mobile-Specific Security | Permission audit, exported components, WebView, ProGuard, deep links |

---

### CATEGORY D — PERFORMANCE & RESOURCES

> **When to use**: App is slow, uses too much memory, loads slowly, or has performance issues on mobile.
>
> **Trigger phrases**: "make it faster", "performance issues", "too slow", "memory leak", "optimize performance", "reduce load time", "app freezes", "ANR"

| Code | Section | What It Audits |
|------|---------|---------------|
| §D1 | Runtime Performance | Main thread blocking, worker offloading, re-renders, throttling |
| §D2 | Web Vitals & Loading | LCP, FID, CLS, critical rendering path, code splitting |
| §D3 | Resource Budget | Bundle size, image optimization, font loading, CDN assets |
| §D4 | Memory Management | Closure leaks, event listener leaks, timer leaks, blob URLs |
| §D5 | Mobile-Specific Performance | Coroutine lifecycle, RecyclerView, image loading, process death, ANR |

---

### CATEGORY E — VISUAL DESIGN QUALITY & POLISH

> **When to use**: App looks unprofessional, design feels inconsistent, colors/spacing are off, needs visual polish.
>
> **Trigger phrases**: "improve the design", "make it look professional", "design review", "polish the UI", "fix the colors", "design system", "make it premium", "visual consistency"

| Code | Section | What It Audits |
|------|---------|---------------|
| §E1 | Design Token System | CSS variables / theme attributes coverage, naming, gaps |
| §E2 | Visual Rhythm & Spatial Composition | Spacing consistency, grid alignment, whitespace |
| §E3 | Color Craft & Contrast | Contrast ratios, palette coherence, dark mode quality |
| §E4 | Typography Craft | Font scale, weight hierarchy, tracking, line height, rendering |
| §E5 | Component Visual Quality | Buttons, inputs, cards, modals — visual consistency |
| §E6 | Interaction Design Quality | Hover/active/focus/disabled states, transitions, feedback |
| §E7 | Overall Visual Professionalism | First-impression test, visual noise, alignment, polish gaps |
| §E8 | Product Aesthetics (Axis-Driven) | Commercial credibility, cognitive load, emotional safety, fidelity |
| §E9 | Visual Identity & Recognizability | Distinctiveness, brand signature, memorability |
| §E10 | Data Storytelling & Visual Communication | How data is presented visually, chart quality |

---

### CATEGORY F — UX, INFORMATION ARCHITECTURE & COPY

> **When to use**: Users get confused, flows feel broken, onboarding is bad, copy needs work, app doesn't feel intuitive.
>
> **Trigger phrases**: "UX review", "improve user experience", "fix the flow", "onboarding sucks", "improve copy", "make it intuitive", "information architecture"

| Code | Section | What It Audits |
|------|---------|---------------|
| §F1 | Information Architecture | Navigation clarity, grouping logic, discoverability, depth |
| §F2 | User Flow Quality | Critical path efficiency, dead ends, error recovery, back navigation |
| §F3 | Onboarding & First Use | First-run experience, progressive disclosure, value communication |
| §F4 | Copy Quality | Clarity, consistency, tone, technical jargon, error messages |
| §F5 | Micro-Interaction Quality | Feedback loops, state transitions, gesture responses |
| §F6 | Engagement, Delight & Emotional Design | Personality moments, reward patterns, celebration states |

---

### CATEGORY G — ACCESSIBILITY

> **When to use**: Making the app accessible, screen reader support, keyboard navigation, WCAG compliance.
>
> **Trigger phrases**: "accessibility audit", "a11y", "WCAG", "screen reader", "keyboard navigation", "make it accessible", "color blind support"

| Code | Section | What It Audits |
|------|---------|---------------|
| §G1 | WCAG 2.1 AA Compliance | Semantic HTML/views, ARIA/accessibility labels, focus management, contrast |
| §G2 | Screen Reader Trace | Content order, live regions, meaningful labels, hidden decorative elements |
| §G3 | Keyboard Navigation | Tab order, focus traps, skip links, custom component keyboard support |
| §G4 | Reduced Motion | `prefers-reduced-motion` / `ANIMATOR_DURATION_SCALE` respect, alternatives |

---

### CATEGORY H — BROWSER / PLATFORM COMPATIBILITY

> **When to use**: App breaks on certain browsers/devices, PWA issues, touch interactions don't work, offline problems.
>
> **Trigger phrases**: "browser compatibility", "doesn't work on Safari", "mobile broken", "PWA issues", "offline support", "touch not working"

| Code | Section | What It Audits |
|------|---------|---------------|
| §H1 | Cross-Browser Matrix | Feature detection, API availability, CSS compatibility, polyfills |
| §H2 | PWA & Service Worker | Manifest, SW lifecycle, cache strategy, update flow, install prompt |
| §H3 | Mobile & Touch | Touch targets, viewport, safe areas, gesture conflicts, orientation |
| §H4 | Network Resilience | Offline behavior, retry logic, degraded connectivity, sync |

---

### CATEGORY I — CODE QUALITY & ARCHITECTURE

> **When to use**: Codebase is messy, hard to maintain, has duplication, needs restructuring, poor naming.
>
> **Trigger phrases**: "clean up my code", "code review", "refactor", "reduce duplication", "improve architecture", "code quality", "naming review"

| Code | Section | What It Audits |
|------|---------|---------------|
| §I1 | Dead Code & Waste | Unreachable code, unused imports, disabled features, commented code |
| §I2 | Naming Quality | Variable/function/file naming consistency, domain vocabulary alignment |
| §I3 | Error Handling Coverage | Try/catch completeness, error propagation, user-facing error messages |
| §I4 | Code Duplication | Copy-paste code, near-duplicates, abstraction opportunities |
| §I5 | Component & Module Architecture | Separation of concerns, coupling, cohesion, dependency direction |
| §I6 | Documentation & Maintainability | Comments quality, self-documenting code, onboarding ease |

---

### CATEGORY J — DATA PRESENTATION & PORTABILITY

> **When to use**: Numbers display wrong, charts look bad, assets are poorly managed, real-time data is stale.
>
> **Trigger phrases**: "fix number formatting", "chart looks wrong", "data display issues", "asset management", "real-time data"

| Code | Section | What It Audits |
|------|---------|---------------|
| §J1 | Number & Data Formatting | Precision, locale formatting, currency, percentages, units |
| §J2 | Data Visualization Quality | Chart accuracy, axis labeling, color accessibility, empty states |
| §J3 | Asset Management | Image optimization, lazy loading, fallbacks, format selection |
| §J4 | Real-Time Data Freshness | Polling intervals, stale data indicators, update propagation |

---

### CATEGORY K — SPECIALIZED DOMAIN DEPTHS

> **When to use**: App handles money, health data, gambling mechanics, real-time collaboration, or AI integration.
>
> **Trigger phrases**: "audit financial logic", "medical safety check", "gambling compliance", "audit AI integration", "real-time sync issues"

| Code | Section | What It Audits |
|------|---------|---------------|
| §K1 | Financial Precision | Decimal arithmetic, currency rounding, tax calculation, audit trails |
| §K2 | Medical / Health Precision | Dosage safety, unit conversions, contraindication checks, disclaimers |
| §K3 | Probability & Gambling-Adjacent | RNG fairness, pity system correctness, disclosed rates, age gating |
| §K4 | Real-Time & Collaborative | Conflict resolution, sync ordering, presence, latency handling |
| §K5 | AI / LLM Integration | Prompt injection, output sanitization, streaming errors, token/cost risk, hallucination exposure |

---

### CATEGORY L — OPTIMIZATION, STANDARDIZATION & POLISH

> **When to use**: App works but feels rough, needs consistent standards, polish pass, or optimization sweep.
>
> **Trigger phrases**: "standardize my code", "polish everything", "make it consistent", "optimization pass", "unify the design", "clean up standards"

| Code | Section | What It Audits |
|------|---------|---------------|
| §L1 | Code Optimization Opportunities | Algorithm improvements, caching, lazy evaluation, batching |
| §L2 | Code Standardization | Coding conventions, formatting consistency, lint configuration |
| §L3 | Design System Standardization | Token consistency, component variants, spacing unification |
| §L4 | Copy & Content Standardization | Voice consistency, terminology, label conventions |
| §L5 | Interaction & Experience Polish | Micro-animation refinement, transition consistency, gesture polish |
| §L6 | Performance Polish | Perceived performance, skeleton screens, optimistic updates |
| §L7 | Accessibility Polish | Beyond compliance — toward excellent accessibility |

---

### CATEGORY M — DEPLOYMENT & OPERATIONS

> **When to use**: App needs versioning, monitoring, feature flags, or operational improvements.
>
> **Trigger phrases**: "deployment audit", "add monitoring", "feature flags", "version management", "operational readiness"

| Code | Section | What It Audits |
|------|---------|---------------|
| §M1 | Version & Update Management | Version strategy, changelog, migration paths, update notifications |
| §M2 | Observability | Error tracking, analytics, performance monitoring, crash reporting |
| §M3 | Feature Flags & Gradual Rollout | Flag architecture, rollback capability, A/B testing readiness |

---

### CATEGORY N — INTERNATIONALIZATION & LOCALIZATION

> **When to use**: App needs multi-language support, locale-aware formatting, or RTL layout support.
>
> **Trigger phrases**: "i18n audit", "add translations", "locale formatting", "RTL support", "internationalization", "localization check"

| Code | Section | What It Audits |
|------|---------|---------------|
| §N1 | Hardcoded String Inventory | All user-facing strings not in a translation system |
| §N2 | Locale-Sensitive Formatting | Dates, numbers, currency, pluralization, sort order |
| §N3 | RTL Layout | Bidirectional text, mirrored layouts, directional icons |
| §N4 | Locale Loading & Performance | Translation bundle size, lazy loading, fallback chains |

---

### CATEGORY O — DEVELOPMENT SCENARIO PROJECTION

> **When to use**: Planning for growth, worried about scaling, tech debt accumulating, future-proofing the app.
>
> **Trigger phrases**: "future-proof my app", "scale analysis", "tech debt audit", "dependency check", "what breaks at scale", "maintenance risks"

| Code | Section | What It Audits |
|------|---------|---------------|
| §O1 | Scale Cliff Analysis | Points where current architecture breaks under growth |
| §O2 | Feature Addition Risk Map | How hard it is to add the next likely features |
| §O3 | Technical Debt Compounding Map | Which debts compound over time and their cost trajectory |
| §O4 | Dependency Decay Forecast | Outdated dependencies, EOL risk, upgrade difficulty |
| §O5 | Constraint Evolution Analysis | When current constraints should be relaxed/changed |
| §O6 | Maintenance Trap Inventory | Patterns that are easy now but become costly to maintain |
| §O7 | Bus Factor & Knowledge Concentration | Single points of failure in codebase knowledge |

---

### DELIVERABLES & FORMAT

| Code | Section | What It Does | Trigger Phrases |
|------|---------|-------------|-----------------|
| §V | Finding Format | Standard template for all findings (severity, confidence, specifics) | — |
| §VI | Required Deliverables | Tier 1/2/3 deliverables by audit depth | — |
| §VII | Summary Dashboard | Final findings table, root cause, quick wins, roadmap | "show summary", "give me the roadmap" |
| §VIII | Cross-Cutting Concern Map | Patterns spanning multiple categories | — |
| §IX | Final Mandate | Binding audit contract | — |

---

### R&D & IMPROVEMENT MODE (§X)

> **When to use**: You want to improve existing features, find what to build next, compare with competitors, or plan your roadmap.
>
> **Trigger phrases**: "what should I build next", "improve my features", "competitive analysis", "R&D roadmap", "feature prioritization", "what's missing", "help me improve my app"

| Code | Section | What It Does |
|------|---------|-------------|
| §X.0 | Existing Feature Deep Evaluation | Health audit of every existing feature — what to elevate, evolve, consolidate, or reimagine |
| §X.1 | Competitive & Landscape Research | Competitor inventory, feature gap matrix, user signal synthesis |
| §X.2 | Improvement Prioritization | Ranks all improvements by impact × effort × strategic value |
| §X.3 | R&D Roadmap Deliverable | Structured roadmap: immediate → short → medium → long term |

---

### POLISH & RESTRUCTURATION MODE (§XI)

> **When to use**: App grew messy over time, codebase needs restructuring, design feels incoherent, needs systematic polish.
>
> **Trigger phrases**: "my app is messy", "restructure my app", "polish my app", "clean up my codebase", "my app feels incoherent", "unify my app", "my app grew messy"

| Code | Section | What It Does |
|------|---------|-------------|
| §XI.0 | Deep Comprehension Phase | Reads and understands the app deeply before changing anything (MANDATORY) |
| §XI.1 | Pre-Polish Inventory | Maps all coherence fractures, inconsistencies, rough edges |
| §XI.2 | Systematic Polish Passes | 7 polish passes from structural to fine-grained |
| §XI.3 | Codebase Restructuration | File structure, module boundaries, naming, architecture cleanup |
| §XI.4 | Architecture Evolution | Incremental architecture improvements without rewrites |
| §XI.5 | Quality Gates | Verification that polish and restructuring preserved behavior |
| §XI.6 | Polish & Restructuration Deliverable | Structured delivery of all changes |

---

### QUICK REFERENCE — "I want to..." → Run this

| I want to... | Run this |
|--------------|----------|
| **Full audit of everything** | `"full app audit"` → all parts P1–P14 |
| **Just check security** | `"run Category C"` or `"security audit"` |
| **Just check performance** | `"run Category D"` or `"performance audit"` |
| **Just fix the design** | `"run Category E"` or `"design polish"` |
| **Verify my business logic** | `"run §A1"` or `"check my formulas"` |
| **Check one specific thing** | `"audit §C2"` (injection) or `"run §D5"` (mobile perf) |
| **Fix issues in a section** | `"fix §E3"` (color craft) or `"implement §G1 fixes"` |
| **Plan what to build next** | `"run §X"` or `"R&D mode"` |
| **Clean up a messy codebase** | `"run §XI"` or `"polish mode"` |
| **Compare with competitors** | `"run §X.1"` or `"competitive analysis"` |
| **Future-proof my app** | `"run Category O"` or `"projection analysis"` |
| **Check AI integration** | `"run §K5"` or `"audit AI integration"` |
| **Check mobile-specific issues** | `"run §C7 and §D5"` or `"mobile audit"` |

---

## QUICK START — How to Use This Skill

> **For Claude**: When this skill activates, follow these steps:
> 1. **Use `AskUserQuestion`** to present the §TRIAGE options (unless the user already specified a mode)
> 2. **Use `TodoWrite`** to create a progress tracker with one todo per audit part
> 3. **Use `Agent` (subagent_type: Explore)** to read the entire codebase in parallel before writing any findings
> 4. **Fill §0** by extracting from code first, then asking the user only for what can't be extracted
> 5. **Work in parts** — output one part per response, update the todo list after each
>
> **For the User**: Just say one of the trigger phrases (e.g., "audit my app", "review before launch", "help me improve my features") and Claude will guide you through the options. You can also jump directly to a mode: "do a full audit", "run R&D mode", "polish my app".

---

## SCOPE CONVENTION — "audit/fix" vs "full deep audit/fix"

> **This is a binding instruction.** The words "full deep" in a user request are a scope multiplier. They change what gets audited and fixed.

### The Rule

| User Says | Scope | What Claude Does |
|-----------|-------|-----------------|
| **"audit/fix [subject]"** | **Standard** | Run only the core SKILL or category for that subject. Stay within its boundaries. |
| **"full deep audit/fix [subject]"** | **Expanded — everything the subject touches** | Run the core SKILL/category **PLUS every cross-cutting section from either SKILL that affects the subject**. Follow every dependency chain. Leave nothing UI-adjacent unexamined. |

### The Principle

**"Full deep"** means: if the subject touches it, audit it. Not just the primary category — every section in both `app-audit-SKILL.md` and `design-aesthetic-audit-SKILL.md` where the subject has a dependency, a side effect, or a shared concern.

### Subject-Specific Expansion Map

#### "full deep audit/fix art design aesthetic" — EVERYTHING UI

Standard scope: `design-aesthetic-audit-SKILL.md` 21-step path only.

Full deep adds from `app-audit-SKILL.md`:
| Added Section | Why — What It Touches |
|---------------|----------------------|
| §E (Visual Design Quality) | Structural/code side of design: tokens in code, spacing in layouts, contrast in implementation |
| §F1 (Information Architecture) | Navigation structure, grouping logic, discoverability — design depends on IA |
| §F2 (User Flow Quality) | Dead ends, error recovery, back navigation — aesthetic breaks if flow breaks |
| §F3 (Onboarding & First Use) | First-run experience, permission requests — first visual impression |
| §F4 (Copy Quality) | Labels, error messages, terminology — copy IS part of the visual surface |
| §F5 (Micro-Interactions) | Functional feedback loops — motion architecture needs interaction triggers |
| §F6 (Engagement & Delight) | Personality moments, reward patterns — aesthetic at its most expressive |
| §G1-G4 (Accessibility) | Content descriptions, TalkBack, focus order, contrast, reduced motion — design must be accessible |
| §H3 (Mobile & Touch) | Touch targets (48dp min), safe areas, gesture conflicts — design must be touchable |
| §L3 (Design System Standard.) | Token consistency, component variants, spacing unification — the system under the design |
| §L4 (Copy & Content Standard.) | Voice consistency, terminology — copy-visual alignment at scale |
| §L5 (Interaction & Experience Polish) | Micro-animation refinement, transition consistency — the polish layer |
| §D5 (Mobile Performance) | RecyclerView jank, animation frame drops — design that stutters is broken design |

#### "full deep audit/fix security" — EVERYTHING TRUST

Standard scope: §C (Security & Trust) only.

Full deep adds:
| Added Section | Why |
|---------------|-----|
| §B3 (Input Validation) | Validation gaps are security gaps |
| §C7 (Mobile Security) | Permissions, exported components, WebView, ProGuard |
| §K1-K5 (Domain Depths) | Financial precision, medical safety, AI prompt injection |
| §B2 (Persistence) | Storage security, encryption, concurrent access |
| §B4 (Import/Export) | Malformed import → prototype pollution → state corruption |
| §M2 (Observability) | Can you detect a breach? Crash reporting, error tracking |
| §O4 (Dependency Decay) | Outdated dependencies with known CVEs |
| §H4 (Network Resilience) | Retry logic, degraded connectivity — attack surface |

#### "full deep audit/fix performance" — EVERYTHING SPEED

Standard scope: §D (Performance & Resources) only.

Full deep adds:
| Added Section | Why |
|---------------|-----|
| §D5 (Mobile Performance) | Coroutines, RecyclerView, process death, ANR |
| §L6 (Performance Polish) | Perceived performance, skeleton screens, optimistic updates |
| §O1 (Scale Cliffs) | Where does it break under load? |
| §H4 (Network Resilience) | Retry, offline, degraded connectivity |
| §D3 (Resource Budget) | APK size, image optimization, font loading |
| §I1 (Dead Code) | Dead code = wasted bundle size = slower load |
| §A6 (Async & Concurrency) | Race conditions, stale closures, ordering bugs — perf symptoms |
| §J3 (Asset Management) | Image optimization, lazy loading, format selection |

#### "full deep audit/fix UX" — EVERYTHING USER-FACING

Standard scope: §F (UX, IA & Copy) only.

Full deep adds:
| Added Section | Why |
|---------------|-----|
| §E (Visual Design Quality) | UX and visual design are inseparable |
| §G1-G4 (Accessibility) | Accessibility IS UX for affected users |
| §H3 (Mobile & Touch) | Touch interaction IS UX |
| §L5 (Interaction Polish) | Polish IS the UX quality ceiling |
| §F6 (Engagement & Delight) | Emotional design IS UX at its best |
| Full `design-aesthetic-audit-SKILL.md` | The aesthetic layer IS part of the user experience |

#### "full deep audit/fix code quality" — EVERYTHING MAINTAINABLE

Standard scope: §I (Code Quality & Architecture) only.

Full deep adds:
| Added Section | Why |
|---------------|-----|
| §L1-L2 (Optimization & Standardization) | Standards are code quality infrastructure |
| §O1-O7 (All Projections) | Future maintainability depends on today's architecture |
| §M1-M3 (Deployment & Ops) | Versioning, monitoring, feature flags — operational code quality |
| §B1 (State Architecture) | State bugs are architecture bugs |
| §I5 (Module Architecture) | Coupling, cohesion, dependency direction |

### Claude Execution Note

When "full deep" is detected:
1. **Use `TodoWrite`** to list ALL sections (core + expanded) as individual tasks
2. **Run core SKILL sections first**, then expanded sections in order
3. **Cross-reference findings** — a design finding may reveal a UX gap, a UX gap may reveal an accessibility gap
4. **Follow the Cross-Cutting Concern Map (§VIII)** — it maps exactly these dependency chains

---

## SKILL MAP — Quick Reference

> **Read this first.** This is a 2,800+ line skill. You never need all of it. Use this map.

### Section Index

| Section | Purpose | When to Read |
|---------|---------|-------------|
| §TRIAGE | Route to the right mode | **Always first** — ask user which mode |
| §0 | App identity, tech stack, constraints, design, domain rules | **Always** — fill before any work |
| §I | Classify domain, architecture, size, aesthetic axes | **Full audit** — determines depth |
| §II | 10 Iron Laws governing auditor behavior | **Full audit** — governs every finding |
| §III | Execution plan, part structure, pre-flight checklist | **Full audit** — determines pacing |
| §IV | 120+ checks across 15 categories (A–O) | **Full audit** — the core reference |
| §V–VII | Finding format, deliverables, summary dashboard | **Full audit** — templates |
| §VIII | Cross-cutting patterns spanning multiple categories | **Full audit** — check after all dimensions |
| §IX | Final mandate — binding audit contract | **Full audit** — closing reference |
| §X | Existing feature evaluation + new feature planning | **R&D&I mode** — "what to improve and why" |
| §XI | Deep comprehension + coherence restoration + polish | **Polish mode** — "make it one thing again" |

### Common Execution Paths

```
"Audit my app"
  → §TRIAGE → §0 → §I–IX → §VII
  Claude: read §I-§IX as you work through parts. Output in parts per §III.

"Help me improve my features" / "What should I build next?"
  → §TRIAGE → §0 (lightweight) → §I.1 classification only → §X
  Claude: §X.0 (existing features) is mandatory. §X.1 (competitive) if web search available.

"My app is messy, restructure it" / "Polish my app"
  → §TRIAGE → §0 → §XI
  Claude: §XI.0 (comprehension) is MANDATORY — do not skip. Prior audit recommended.

"Full treatment"
  → §0 → §I–IX → §X → §XI
  Claude: this is 15+ parts. Confirm with user after Part 1.

"Continue from audit → now improve"
  → Load prior audit findings → §X (builds on audit) → §XI (builds on both)
```

### Claude Execution Notes

- **§X and §XI can be run independently.** They don't require a full audit. But they work better with one.
- **§X.0 (existing feature eval) always runs before §X.1 (competitive research).** Look inward first.
- **§XI.0 (comprehension) is non-negotiable.** Never skip it — it's what prevents "clean but soulless" restructuring.
- **When in doubt about which section applies:** ask the user. Use the `AskUserQuestion` tool with the triage options.
- **For apps > 3,000 lines:** always confirm with user after completing §0 + §I before continuing.

### Claude Code Tool Integration Protocol

> **These instructions are specific to Claude Code (CLI/web).** Use the right tool for each audit task.

#### Tool Usage Map

| Audit Task | Tool to Use | Why |
|------------|-------------|-----|
| **Read the codebase** | `Agent` (subagent_type: Explore) | Explores files in parallel without bloating the main context |
| **Search for patterns** (e.g., hardcoded strings, security issues) | `Grep` or `Glob` | Fast, targeted codebase searches |
| **Ask user which mode** | `AskUserQuestion` | Presents triage options with descriptions |
| **Track audit progress** | `TodoWrite` | Creates visible progress tracker for multi-part audits |
| **Research competitors/sources** | `WebSearch` / `WebFetch` | Live web research for §X.1 competitive analysis |
| **Present findings** | Direct text output | Findings are displayed directly to the user |
| **Implement fixes** | `Edit` / `Write` | Apply recommended changes to code |

#### Multi-Part Audit Progress Protocol

At the start of any multi-part audit, create a progress tracker:

```
TodoWrite([
  { content: "Fill §0 App Context Block", status: "in_progress", activeForm: "Filling §0 context" },
  { content: "§I Adaptive Calibration", status: "pending", activeForm: "Classifying domain and architecture" },
  { content: "Part 1: Core Logic & Domain", status: "pending", activeForm: "Auditing core logic" },
  { content: "Part 2: State & Data Integrity", status: "pending", activeForm: "Auditing state management" },
  ...continue for each part
])
```

Update status to `completed` as each part finishes. The user sees real-time progress.

#### Parallel Research Strategy

For large codebases (> 2,000 lines), launch parallel Explore agents to read different modules simultaneously:

```
Agent(subagent_type: Explore, prompt: "Read all UI/fragment files in app/src/main/java/.../ui/")
Agent(subagent_type: Explore, prompt: "Read all utility/service files in app/src/main/java/.../utils/")
Agent(subagent_type: Explore, prompt: "Read all data model files in app/src/main/java/.../data/")
```

This prevents context window bloat while ensuring thorough coverage.

### Platform Awareness

> This skill was originally written for web/frontend apps but applies to **any frontend platform**. When auditing non-web apps, adapt the terminology:

| Web Concept | Android/Kotlin Equivalent | iOS/Swift Equivalent |
|-------------|--------------------------|---------------------|
| CSS variables / design tokens | `colors.xml`, `themes.xml`, `dimens.xml` | Asset catalogs, `UIColor` extensions |
| `localStorage` | `SharedPreferences`, Room DB | `UserDefaults`, CoreData |
| Components / React state | Fragments, ViewModels, LiveData | ViewControllers, SwiftUI State |
| `innerHTML` / XSS | `WebView.loadData()` injection | `WKWebView` injection |
| Bundle size | APK size, DEX method count | IPA size |
| Service Workers | WorkManager, Foreground Services | Background Tasks |
| CSS animations | `MotionLayout`, `ObjectAnimator`, `MotionUtil` | `UIView.animate`, SwiftUI `.animation` |
| `border-radius` | `cornerRadius` in ShapeableImageView / MaterialCardView | `layer.cornerRadius` |
| Tailwind/CSS classes | XML attributes, Material Design 3 theme | SwiftUI modifiers |
| `prefers-reduced-motion` | `Settings.Global.ANIMATOR_DURATION_SCALE` | `UIAccessibility.isReduceMotionEnabled` |

**When auditing Android apps specifically:**
- Check `AndroidManifest.xml` for permission issues (§C)
- Audit `proguard-rules.pro` for security implications
- Review `build.gradle` for dependency versions (§O4)
- Check `values/` and `values-night/` for theme completeness (§E)
- Review navigation graph (`nav_graph.xml`) for flow coherence (§H)
- Check for proper lifecycle handling in Fragments/ViewModels (§B)
- Verify Material Design 3 component usage and theming (§E, §L)

**When auditing iOS apps specifically:**
- Check `Info.plist` for permission descriptions
- Review asset catalogs for theme support
- Audit `Podfile`/`Package.swift` for dependencies
- Check for proper SwiftUI/UIKit lifecycle handling

---

## §TRIAGE — MANDATORY AUDIT ROUTING (execute BEFORE reading the rest of this skill)

**Always ask first** using `AskUserQuestion`. Example invocation:

```
AskUserQuestion({
  questions: [{
    question: "What kind of audit would you like?",
    header: "Audit mode",
    options: [
      { label: "Full App Audit", description: "Code, security, performance, accessibility, UX, design, architecture, domain correctness, i18n, AI risks, future projections" },
      { label: "Design & Aesthetic Audit", description: "Deep visual analysis — color science, typography, motion, brand identity, competitive positioning" },
      { label: "R&D & Improvement", description: "Existing feature health evaluation, competitive analysis, improvement prioritization, R&D roadmap" },
      { label: "Polish & Restructure", description: "Deep app comprehension, coherence fracture healing, systematic polish, codebase restructuring" }
    ],
    multiSelect: false
  }]
})
```

| Option | What You Get |
|--------|-------------|
| **Full App Audit** | Code, security, performance, accessibility, UX, design, architecture, domain correctness, i18n, AI risks, future projections |
| **Design & Aesthetic Audit** | Deep visual analysis — color science, typography, motion, brand identity, competitive positioning |
| **Research, Development & Improvement** | Existing feature health evaluation, competitive analysis, improvement prioritization, R&D roadmap |
| **Polishing & Restructuration** | Deep app comprehension, coherence fracture healing, systematic polish, codebase restructuring |
| **Both (Companion Mode)** | Full audit + design audit. Longest option. |

**Routing:**

| Selection | What Claude Does |
|-----------|-----------------|
| Full App Audit | Continue from §ORCHESTRATION. Do NOT load `design-aesthetic-audit-SKILL.md`. |
| Design & Aesthetic Audit | Stop this skill. Load `design-aesthetic-audit-SKILL.md`. |
| R&D & Improvement | Fill §0 → lightweight §I classification → skip to §X. If prior audit exists, reference findings. |
| Polishing & Restructuration | Fill §0 → skip to §XI. Prior audit strongly recommended. If none, do Parts 1–3 first. |
| Companion Mode | Continue this skill + load `design-aesthetic-audit-SKILL.md`. Follow companion protocol. |

**Skip triage when:** user explicitly names which mode, has already selected, or says "continue" / "next part" during an in-progress session.

---

## ORCHESTRATION — How This Skill Works

This skill adapts to the app's domain, stakes, architecture, and aesthetic identity. The auditor holds all specialist lenses simultaneously:

| Area | Lenses |
|------|--------|
| **Code** | Senior engineer · Security researcher · Performance engineer · QA lead |
| **Domain** | Domain specialist · Compliance officer · Forward-looking architect |
| **Design** | Visual designer · Product designer · Brand strategist · Copywriter |
| **UX** | UX designer · Accessibility specialist · Adversarial tester |
| **Strategy** | R&D strategist · Restructuring engineer · Refactoring expert |

A wrong displayed number is not just a logic bug — it is simultaneously a UX trust failure, a data integrity gap, and potentially a security issue depending on stakes.

---

## §0. APP CONTEXT BLOCK

> **Fill before writing any findings.** Extract what you can from source code. Ask the user only for what cannot be extracted. Verify domain rules with the user — the code may be wrong.

```yaml
# ─── CROSS-AUDIT CONTINUITY ───────────────────────────────────────────────────
# Complete this block only when this is NOT the first audit of this app.
# Its purpose: prevent silent contradiction between audit sessions.
# A finding that contradicts a previously confirmed domain rule is a CONFLICT, not a correction —
# and must be surfaced explicitly, not silently applied.
Prior Audit Reference:
  Version Audited:    # The version number from the previous audit's §0
  Session Date:       # Approximate date of the prior audit (helps identify which session)
  Confirmed Rules:    # List every domain rule confirmed [§0-CONFIRMED] in the prior session
    - # e.g. "BASE_RATE = 0.008 — confirmed by user, session 1"
    - # e.g. "MAX_SESSIONS = 5 — confirmed by user, session 1"
  Confirmed Findings: # Any findings from the prior audit confirmed as real bugs
    - # e.g. "F-007: rounding error in dose calculation — confirmed CRITICAL"
  Conflicts Flagged:  # Any place where this session's findings differ from the prior session's
    - # Format: "CONFLICT: [prior session claimed X] vs [this session finds Y] — needs user confirmation"

# ─── IDENTITY ─────────────────────────────────────────────────────────────────
App Name:      # e.g. "InvoiceFlow" / "HealthTrack" / "PixelEditor"
Version:       # e.g. "v2.1.4" — is this a single source of truth or scattered across the codebase?
Domain:        # e.g. "Invoice creation for EU freelancers" / "Medication dosage calculator"
Audience:      # e.g. "Small business owners" / "Nurses" / "Casual gamers" / "Data analysts"
Stakes:        # LOW (hobby/entertainment) | MEDIUM (productivity, money-adjacent) |
               # HIGH (real financial transactions, legal records) |
               # CRITICAL (medical, safety-critical, legal compliance required)
               # Stakes is a severity multiplier — wrong data in a CRITICAL app is always CRITICAL.

# ─── TECH STACK ───────────────────────────────────────────────────────────────
Framework:     # e.g. "React 18 (CDN)" / "Vue 3 + Vite" / "Vanilla JS" / "Svelte"
Styling:       # e.g. "Tailwind CSS (CDN)" / "CSS Modules" / "Styled Components" / "Plain CSS"
State:         # e.g. "useReducer + localStorage" / "Zustand" / "Redux Toolkit"
Persistence:   # e.g. "localStorage only" / "IndexedDB" / "REST API + localStorage cache"
Workers:       # e.g. "Blob Web Worker + Blob SW" / "Workbox SW" / "None"
Visualization: # e.g. "Recharts" / "D3.js" / "Chart.js" / "None"
Build:         # e.g. "Zero build tools — CDN only" / "Vite 5" / "Webpack 5"
External APIs: # e.g. "None" / "Stripe" / "OpenWeather" / "Anthropic Claude API"
AI/LLM:        # e.g. "None" / "OpenAI GPT-4o via fetch" / "Claude claude-sonnet-4-6, streaming"

# ─── MOBILE / NATIVE STACK (fill if applicable) ──────────────────────────────
Platform:      # e.g. "Android" / "iOS" / "Flutter" / "React Native" / "Web only"
Language:      # e.g. "Kotlin 1.9.0" / "Swift 5.9" / "Dart 3.2" / "TypeScript"
Min SDK:       # e.g. "Android 29 (10)" / "iOS 15.0" / "N/A"
Target SDK:    # e.g. "Android 35 (15)" / "iOS 17.0" / "N/A"
Architecture:  # e.g. "MVVM (ViewModel + LiveData)" / "MVI (Compose)" / "VIPER" / "BLoC"
UI Framework:  # e.g. "Material Design 3 (XML Views)" / "Jetpack Compose" / "SwiftUI" / "UIKit"
Navigation:    # e.g. "Navigation Component 2.7.4" / "NavigationStack" / "go_router"
DI:            # e.g. "Manual" / "Hilt/Dagger" / "Koin" / "Swinject"
Testing:       # e.g. "JUnit 4 + Espresso" / "XCTest" / "flutter_test"
CI/CD:         # e.g. "GitHub Actions" / "Fastlane" / "Bitrise" / "None"

# ─── PLATFORM & LOCALE ────────────────────────────────────────────────────────
Target Platforms: # e.g. "Desktop-first" / "Mobile-first PWA" / "Both (responsive)"
                  # Determines: touch targets, viewport units, safe-area-inset, hover media query
Locale / i18n:    # e.g. "English only, US" / "EN + FR + DE, LTR" / "Multi-locale + RTL (AR, HE)"
                  # None = i18n ignored. Any locale = §N activated.
Performance Budget: # e.g. "None defined" / "LCP < 2.5s, TTI < 5s on 3G" / "Bundle < 200kb gz"

# ─── ARCHITECTURE CONSTRAINTS ─────────────────────────────────────────────────
# Non-negotiable. Every recommendation must respect these — even if they are suboptimal.
# Suggestions to change constraints are welcome but must be clearly marked as architectural
# proposals, not standard findings.
Constraints:
  - # e.g. "Single-file: ALL code in App.jsx — no multi-file imports"
  - # e.g. "Zero build tools — no bundling, minification, tree-shaking"
  - # e.g. "CDN-only: React/libs loaded from CDN at runtime, no npm"
  - # e.g. "localStorage: sole persistence, 5MB limit, no server"

# ─── DESIGN IDENTITY ──────────────────────────────────────────────────────────
# The app's intentional visual and interactive character.
# This protects the aesthetic from being "standardized" into something generic.
# Extract from the code, or ask the user.
Design Identity:
  Theme:         # e.g. "Dark-first with cyan/teal accent" / "Clean minimal light" / "Playful colorful"
  Personality:   # e.g. "Precise and informative" / "Warm and approachable" / "Sleek and premium"
  Signature:     # e.g. "Animated background particles, OLED pitch-black mode, glowing accents"
                 # These are PROTECTED — the audit improves them but never removes them.

  # ── PRODUCT & COMMERCIAL VISUAL IDENTITY ─────────────────────────────────
  # Used by §E8, §E9, §F6, §L4. Inferred from code + domain if not provided.
  # NOTE: Commercial fields (Visual Reference, Monetization Tier, Conversion) are
  # only activated for paid/freemium/professional products. Fan/free/community tools
  # use the aesthetic-fidelity framing instead — see §I.4 Aesthetic Context Analysis.
  Visual Reference:      # 2–3 apps or sources whose visual quality/feel this should match
                         # Paid tools: "Linear, Vercel" / Fan tools: "the official companion for X" or "the visual language of X itself"
  Emotional Target:      # The feeling the app should produce at first glance
                         # e.g. "Fast + trustworthy + premium" / "Warm + delightful + approachable"
                         # e.g. "Feels like it belongs to this world" / "Made by someone who really knows this"
  Visual Differentiator: # What makes this app visually memorable
                         # Paid: distinctive vs competitors / Free/Community: authentic to its subject and audience
  Monetization Tier:     # "Free (no revenue intent)" / "Free (community tool)" / "Freemium SaaS"
                         # "Paid consumer ($5–$30/mo)" / "Professional B2B ($50–$500/mo)" / "Enterprise"
                         # Determines which §E8 framing activates. "Free" = aesthetic framing only.
  Distribution Channel:  # Determines first-impression quality requirements
                         # e.g. "Direct URL share" / "App Store listing" / "Reddit/Discord community share"
                         # / "Product Hunt" / "Enterprise sales demo" / "GitHub README"

# ─── DOMAIN RULES ─────────────────────────────────────────────────────────────
# Every formula, constant, rate, threshold, and business rule the code MUST implement correctly.
# This is the specification. Wrong values here → wrong findings.
Domain Rules:
  - # List each as: RULE_NAME = value / formula / description
  - # e.g. "TAX_RATE = 0.21 (21% VAT, exclusive)"
  - # e.g. "All monetary values stored as integer cents — never float"
  - # e.g. "MAX_RETRY_ATTEMPTS = 3, TIMEOUT_MS = 10000"
  - # e.g. "Server timezone UTC+8, no DST; America: UTC-5/UTC-4 (DST applies)"

# ─── TEST VECTORS ─────────────────────────────────────────────────────────────
# Known input → expected output pairs for the app's core calculations.
# Pre-supply these if you have them — §A2 requires ≥3 for probability/financial apps.
# The audit will verify these against actual code output. Wrong output = finding.
# Format: "Input: {values} → Expected output: {result} — Source: {where this comes from}"
Test Vectors:
  - # e.g. "Input: principal=1000, rate=0.05, years=10 → Expected: 1628.89 — Source: formula spec"
  - # e.g. "Input: weight=70kg, dose_factor=0.5 → Expected: 35mg — Source: medical reference"
  - # e.g. "Input: items=[A,B,C], pity=89 → Expected: guarantee triggers at 90 — Source: official rates"

# ─── CRITICAL USER WORKFLOWS ──────────────────────────────────────────────────
# The 5–10 most important end-to-end user journeys. The audit traces each one step-by-step.
Workflows:
  1: # "New user → onboarding → first core action → result"
  2: # "Returning user → load state → update → verify output updates"
  3: # "Import external data → preview → confirm → verify correctness"
  4: # "Export → fresh device → import → verify round-trip fidelity"
  5: # "Power user with maximum data → performance still acceptable"
  # Add more as needed.

# ─── KNOWN ISSUES ─────────────────────────────────────────────────────────────
Known Issues:
  - # What the developer already suspects is broken

# ─── AUDIT SCOPE ──────────────────────────────────────────────────────────────
Audit Focus:   # e.g. "Full audit" / "Prioritize security and correctness" / "Design + polish only"
Out of Scope:  # e.g. "Backend code (not provided)" / "Third-party payment widget"

# ─── GROWTH CONTEXT ───────────────────────────────────────────────────────────
# Used by §O Projection Analysis. The audit reasons about the app's future, not just its present.
App Maturity:             # e.g. "Prototype/MVP" / "Active development" / "Stable/maintenance mode"
Expected Scale:           # e.g. "Single user forever" / "10–50 users" / "Scaling to thousands"
Likeliest Next Features:  # Top 3–5 features most likely to be added next
                          # e.g. "User accounts", "CSV export", "Collaboration", "Mobile app"
Planned Constraint Changes: # e.g. "Moving from localStorage to a backend in 6 months"
                             # e.g. "None planned" / "Adding authentication next sprint"
```

---

## I. ADAPTIVE CALIBRATION

Before writing any finding, classify the app along three axes. These classifications determine which dimensions get the deepest scrutiny and where severity multipliers apply.

### §I.1. Domain Classification → Severity Amplification

| Domain | Amplify These Dimensions | Stakes Default |
|--------|--------------------------|----------------|
| Medical / Health | §A1 Logic, §A-Rules, §B3 Validation, §C6 Compliance | CRITICAL |
| Financial / Fintech / Billing | §A1 Logic, §J1 Financial Precision, §C1 Auth, §C6 Compliance | HIGH→CRITICAL |
| Gambling-adjacent / Gacha | §A2 Probability, §L5 Ethical Design, §C6 Age/Compliance | MEDIUM→HIGH |
| E-commerce / Payments | §C1 Auth, §C2 XSS, §J1 Financial, §C6 PCI/GDPR | HIGH |
| Web3 / Crypto / Wallet | §C1 Key/Seed handling, §C2 Injection, §A1 Tx math, §C6 Jurisdiction | HIGH→CRITICAL |
| Social / Presence / Multi-user | §C5 Privacy, §J4 Real-time, §C6 GDPR/CCPA | MEDIUM→HIGH |
| Productivity / SaaS | §B State, §D Performance, §H Flows, §K Ops | MEDIUM |
| Creative / Media / Tools | §E Design, §D Assets, §G Compatibility | LOW→MEDIUM |
| Data / Analytics / Dashboards | §A1 Math, §B Data, §I Visualization | MEDIUM→HIGH |
| Game / Companion / Fan Tool | §A Domain Data, §E Design, §C Attribution | LOW→MEDIUM |
| AI / LLM-Powered | §K5 AI Integration, §C2 Prompt Injection, §C5 Privacy, §A1 Output Correctness | MEDIUM→HIGH |

### §I.2. Architecture Classification → Failure Modes

| Architecture | Primary Failure Modes to Hunt |
|--------------|-------------------------------|
| Single-file monolith (CDN React) | Dead code accumulation, blob Worker browser incompatibility, no code-splitting, CSS specificity at scale |
| Multi-file SPA (Vite/Webpack) | Bundle bloat, stale chunks, tree-shaking failures, import cycles |
| SSR / Next.js | Hydration mismatches, server/client state divergence, SEO gaps |
| Vanilla JS | Global scope pollution, event listener leaks, DOM coupling |
| PWA (any) | SW versioning, cache poisoning, offline-first edge cases |
| LocalStorage-only | Quota exhaustion, schema migration gaps, concurrent-tab conflicts |
| Backend-connected | Race conditions, optimistic update failures, token leaks, CORS |
| Android (MVVM/Kotlin) | Fragment lifecycle leaks, ViewModel scope misuse, coroutine cancellation gaps, permission model gaps, process death state loss, ProGuard stripping |
| Android (Compose) | Recomposition storms, state hoisting confusion, side-effect lifecycle, LazyColumn performance |
| iOS (UIKit) | Retain cycles, main-thread violations, lifecycle misuse, deep-link handling |
| iOS (SwiftUI) | @State/@StateObject confusion, view identity issues, NavigationStack complexity |
| Cross-platform (Flutter/RN) | Bridge bottleneck, platform-specific fallback gaps, native module versioning |

### §I.3. App Size → Audit Scope

| Lines of Code | Parts | Depth |
|---------------|-------|-------|
| < 500 | 4–5 | All dimensions, condensed findings |
| 500–2,000 | 6–8 | Full dimensions, moderate findings |
| 2,000–6,000 | 8–12 | Full dimensions, detailed findings |
| 6,000–15,000 | 12–16 | Full dimensions + domain deep dives |
| > 15,000 | 16+ | Full dimensions + per-module sub-audits |

### §I.4. Aesthetic Context Analysis — Five Independent Axes

> Aesthetic goals are derived from five independent dimensions that combine in any configuration. A meditation app, a nurse's calculator, a gacha companion, and a B2B dashboard all require different aesthetic reasoning. **Classify all five axes before writing any §E/§F/§L finding.**

---

#### AXIS 1 — Commercial Intent
*What role does trust-building and conversion play in this app's visual goals?*

| Level | Signal | Aesthetic implication |
|-------|--------|-----------------------|
| **Revenue-generating** | Paid tier, subscription, in-app purchase, ad-supported | Visual trust signals actively matter — every design choice either supports or undermines willingness to pay or engage financially |
| **Institutional / grant-funded** | Non-profit, government, educational, healthcare org | Credibility and legitimacy signals matter — visual design must communicate seriousness and compliance, not commercial appeal |
| **Non-revenue / freely given** | Free tool, open-source, community gift | Commercial signals are irrelevant or actively harmful — visual goals shift entirely to craft, clarity, and authenticity |

---

#### AXIS 2 — Use Intensity & Emotional Context
*What is the user's cognitive and emotional state when using this app? This determines how much the design can demand of their attention.*

| Mode | Examples | Aesthetic implication |
|------|----------|-----------------------|
| **Focus-critical / high-frequency** | Daily work tool, professional instrument, developer IDE companion | Design must be nearly invisible — efficiency, scanability, and zero-distraction above all. Every animation is a tax. |
| **High-stakes / low-frequency** | Medical dosing calculator, legal document tool, emergency reference | Cognitive load reduction is the primary aesthetic goal. Visual noise = danger. Calm, high-contrast, unambiguous. |
| **Emotionally sensitive** | Mental health tool, grief support, therapy companion, crisis resource | Safety, warmth, and calm are structural requirements — not stylistic choices. Harsh colors, abrupt transitions, or playful copy cause harm. |
| **Creative / exploratory** | Art generator, music composition tool, design sandbox, writing tool | The aesthetic can be expressive and surprising — discovery and inspiration are valid goals. Delight is functional, not decorative. |
| **Learning / progressive** | Educational app, tutorial tool, skill trainer | Visual design must communicate progress, reward effort, and reduce intimidation. Pacing and encouragement are aesthetic requirements. |
| **Leisure / casual** | Entertainment tracker, hobby companion, quiz app, idle tool | Delight is primary. Friction that would be tolerable in a work tool is unacceptable here. Polish and playfulness are both appropriate. |
| **Occasional / transactional** | Unit converter, flight tracker, calculator, lookup tool | Get in, get the answer, get out. Visual complexity above "clean and immediate" is waste. |

---

#### AXIS 3 — Audience Relationship to the Subject
*How much does the audience already know? This determines vocabulary, visual complexity, information density, and what "belonging" looks like.*

| Relationship | Examples | Aesthetic implication |
|--------------|----------|-----------------------|
| **Domain expert / practitioner** | Clinicians, engineers, developers, financial analysts | Information density is a feature. Precision vocabulary is required — generic language signals the tool doesn't understand the domain. Visual complexity can be high if it serves the data. |
| **Enthusiast / community member** | Hobbyists, fans, dedicated amateurs | Community vocabulary and aesthetic norms signal insider status. Generic design signals the maker is an outsider. Not knowing the community's natural language is a credibility failure. |
| **Casual / general public** | Anyone unfamiliar with the domain | Progressive disclosure is mandatory. Domain jargon must either be avoided or clearly explained. Intimidation is a design failure. |
| **Mixed / bridging** | A tool that serves both experts and beginners | Progressive disclosure architecture is critical. The design must not condescend to experts or overwhelm novices simultaneously. This is one of the hardest design problems — acknowledge it explicitly. |

---

#### AXIS 4 — Subject Visual Identity
*Does the subject the app is built around have an established visual language? This determines whether visual fidelity is a goal.*

| Identity strength | Examples | Aesthetic implication |
|-------------------|----------|-----------------------|
| **Strong established aesthetic** | A specific game, show, sport league, musical genre, cultural movement | The app has an opportunity — and arguably an obligation — to honor the visual language of its subject. Palette, typography weight, motion character, and copy tone should feel *inspired by* the source, not arbitrarily chosen. |
| **Community-defined aesthetic norms** | Hacker/developer culture, speedrunning community, tabletop RPG players, street photography | The community has visual conventions that signal insider status. Violating them (accidentally going corporate, or too polished, or wrong era) reads as not understanding the culture. |
| **Domain-defined visual conventions** | Medical interfaces, financial terminals, academic tools, engineering dashboards | Professional domains have established visual conventions that communicate seriousness and domain-appropriateness. Departing from them without reason creates distrust. |
| **Neutral / no established identity** | Abstract SaaS, general productivity tool, new utility | Visual language must be invented rather than inherited. This is both more freedom and more responsibility — there's no reference to honor, so coherence must come entirely from within. |
| **The aesthetic IS the subject** | Generative art tool, music visualizer, color palette builder, typography explorer | The product's visual output is its primary value. The UI chrome must recede — the subject is the visual experience, not the container. Overdesigning the interface steals attention from what the tool produces. |

---

#### AXIS 5 — Aesthetic Role in the Product's Value
*Is design serving function, communicating identity, or is it the product itself?*

| Role | Examples | Aesthetic implication |
|------|----------|-----------------------|
| **Aesthetic IS the value** | Generative art, music player, creative tool, animation playground | The interface aesthetic is inseparable from the product. Compromise here is product failure. |
| **Aesthetic amplifies the value** | Well-designed productivity app, thoughtfully crafted community tool | Good aesthetic makes a working product better — more trusted, more enjoyable, more recommended. Standard design investment applies. |
| **Aesthetic communicates identity** | Portfolio, cultural tool, brand-representing product, community-facing gift | The design is a statement about the maker and their relationship to the subject or audience. Authenticity matters as much as polish. |
| **Aesthetic must stay invisible** | Emergency tool, high-stress professional instrument, accessibility-first interface, crisis resource | Design that draws attention to itself is actively harmful here. The goal is zero interference with the user's cognitive task. Every "nice" design touch must be audited against: does this distract? |

---

#### APPLYING THE FIVE AXES

**Step 1**: Classify each axis from §0 signals, code, and domain. Record the classification explicitly at the start of the audit.

**Step 2**: Identify any axis conflicts. Some combinations create genuine tension that must be resolved deliberately:
- *High commercial intent + emotional sensitivity* (wellness app with subscription) → commercial signals must be handled with exceptional care — trust comes from emotional safety first, conversion second
- *Expert audience + strong subject identity* (developer tool with hacker culture norms) → density and terseness ARE the aesthetic — over-polishing violates community norms
- *Aesthetic IS the subject + high-stakes use* (medical imaging tool with visualization) → the visualization must be designed with craft; the UI chrome around it must disappear
- *Mixed audience + domain aesthetic conventions* (medical app for patients AND clinicians) → requires two visual registers in one product — acknowledge this as an architectural design challenge

**Step 3**: Derive the **Aesthetic Goal Profile** — a one-sentence summary for this specific app:
> *"The aesthetic goal for [app] is: [primary goal derived from axes], which means [2–3 specific implications for findings]."*

Example profiles:
- A free community tool for enthusiasts of a visually distinctive subject, used leisurely: *"Craft and subject fidelity — the app should feel made by someone who genuinely knows this world, not a polished product trying to monetize a community."*
- A high-frequency professional tool for domain experts, used in focus-critical contexts: *"Invisible efficiency — every design element must serve information retrieval speed. Delight is a distraction. Density is a feature."*
- A paid creative tool for mixed audiences: *"Expressive trust — the aesthetic must feel inspiring enough to justify the cost, accessible enough for newcomers, and restrained enough not to compete with what the user creates."*
- A free wellness app used in emotionally sensitive contexts: *"Calm safety — warmth and visual quiet are functional requirements. Nothing jarring, nothing demanding, nothing that competes with the user's emotional state."*

**Step 4**: Use the Aesthetic Goal Profile as the filter for every finding in §E, §F, and §L. A finding that contradicts the profile is wrong regardless of how valid it would be for a different app.

---

#### THE FIVE-AXIS LENS REFERENCE

Throughout §E, §F, and §L, findings are marked with the axis they primarily serve:

- `[A1]` Commercial intent axis — applies when revenue or institutional credibility is a goal
- `[A2]` Use context axis — applies when emotional state or cognitive load shapes the requirement  
- `[A3]` Audience axis — applies when expertise level shapes vocabulary or density
- `[A4]` Subject identity axis — applies when the subject has visual conventions to honor
- `[A5]` Aesthetic role axis — applies when aesthetic investment level or restraint is the question

When a finding is tagged `[A1]` — **skip or substantially reframe it if the app is non-revenue.** When a finding is tagged `[A4]` — **skip or substantially reframe it if the subject has no established visual identity.** And so on. The tags make the conditionality explicit rather than requiring the auditor to remember it.

---

### §I.5. Domain Rule Extraction

When the user doesn't provide domain rules, extract them from the code — but apply strict source discipline. Every extracted value is `[CODE]` until the user confirms it, at which point it becomes `[§0-CONFIRMED]`. Never assume a code value is correct just because it is present.

```javascript
// Named constants → immediate §0 candidates
const TAX_RATE = 0.21           // → [CODE: line N] — verify with user → becomes [§0-CONFIRMED] only after user confirms
const MAX_ITEMS = 50            // → [CODE: line N] — is this a spec value or an implementation guess?
const MAX_SESSIONS = 5          // → [CODE: line N] — verify against authoritative spec

// Hardcoded numbers in formulas → red flags requiring verification
if (score > 100) { ... }                   // → [CODE: line N] — why 100? Spec rule or implementation assumption?
const rate = 0.08 + (i - 50) * ramp       // → [CODE: line N] — why 0.08? Why 50? Need source. DO NOT assume from memory.
const total = items * 1.21                 // → [CODE: line N] — hardcoded tax rate? Which jurisdiction? DO NOT recall from training.
const dose = weight * 0.5                  // → [CODE: line N] — CRITICAL: DO NOT guess the correct coefficient. Ask.
```

Present extracted rules as:
> ✓ `MAX_SESSIONS = 5` [CODE: line 342] — §0 confirms `[§0-CONFIRMED]`. Verified.
> ⚠ `ramp_divisor = 15` [CODE: line 989] — not in §0. Flagging for user confirmation before any findings use this value.
> 🚨 `dose = weight * 0.5` [CODE: line 1204] — coefficient in code but unconfirmed. CRITICAL until confirmed. Not recalling a "correct" value from training — asking user to confirm the intended coefficient and its source reference.
> 🔲 `BASE_RATE` — needed for §A1 assessment but absent from code and §0. Cannot assess correctness. Deferring finding and flagging as audit gap.

**Domain-specific escalation triggers** — when these patterns appear in code, escalate automatically:

| Code Pattern | Auto-Escalation |
|-------------|-----------------|
| `dose`, `dosage`, `medication`, `mg`, `mcg` | All §A1 findings → CRITICAL minimum |
| `payment`, `charge`, `billing`, `stripe` | All §C1, §C2 findings → CRITICAL minimum |
| `balance`, `transaction`, `transfer` | All §A1, §B3 findings → CRITICAL minimum |
| `float` used for monetary values | Automatic CRITICAL finding — money is never float |
| `age`, `minor`, `children` | All §C6 compliance findings → HIGH minimum |
| `password`, `token`, `secret` in localStorage | Automatic CRITICAL finding |
| Probability displayed without worst-case | §A2, §L5 — HIGH if real money involved |

---

### §I.6. Knowledge & Source Integrity

> Domain data fabrication is the most damaging audit error — worse than a missed bug, because fabricated facts produce false findings. Every domain fact must be sourced, evaluated, and cited.

#### Source Classification

Every domain fact must carry one of four source tags:

| Tier | Tag | Meaning | Action |
|------|-----|---------|--------|
| Code-verified | `[CODE: line N]` | Value read directly from the provided source file | Ground truth for code *behavior* — still needs verification against §0 to confirm it is *correct* |
| User-confirmed | `[§0-CONFIRMED]` | Value provided or confirmed by the user in §0 | The specification — code is tested against this |
| Web-sourced | `[WEB: source, version, date]` | Value found via live web search | Quality tier determines whether it supports a finding — see Web Source Quality below |
| Training-recalled | `[UNVERIFIED]` | Value recalled from training data only | Never use as a finding basis — present as a question to the user |

**Source hierarchy for correctness claims** (strongest → weakest):
`[§0-CONFIRMED]` → `[WEB: official-docs]` → `[WEB: patch-notes]` → `[WEB: official-wiki]` → `[CODE]` alone → `[WEB: community-wiki]` → `[WEB: forum]` → `[UNVERIFIED]`

Only `[§0-CONFIRMED]` and `[WEB: official-docs/patch-notes]` are strong enough to assert "the code is wrong." Everything below supports a question or a flag — not a finding.

#### Web Source Quality

Web search has its own failure modes: outdated pages, version-mismatched docs, community wikis with unverified edits, and multiple sources contradicting each other on the same value.

| Quality | Tag | Examples | Supports correctness finding? |
|---------|-----|----------|-------------------------------|
| Official documentation | `[WEB: official]` | Developer's own API docs, official site, medical publisher, standards body | Yes — after version check |
| Official patch / release notes | `[WEB: patch-notes, vX.Y]` | Dated changelogs from the developer | Yes — if version matches the app under audit |
| Developer-maintained wiki | `[WEB: official-wiki]` | Wiki explicitly maintained by the studio or org | Yes — with date check |
| Community wiki | `[WEB: community-wiki, date]` | Player-maintained wikis, fan wikis | Conditional — only if recently verified and internally sourced |
| Forum / community post | `[WEB: forum, date]` | Reddit, Discord, community guides | No — lead only; requires corroboration from a higher-quality source |
| Aggregator / secondary | `[WEB: aggregator]` | Sites summarizing other sources | No — locate and cite the original source directly |

**Seven rules for web-sourced domain facts:**

1. **Prefer official sources always.** Do not cite a wiki when official docs exist.
2. **Always record version and date.** Tag as `[WEB: official-docs, v1.8, 2024-03]`. A correct value from last year may be wrong today.
3. **When sources disagree — surface the conflict, never pick a winner silently.**
   > "⚠ SOURCE CONFLICT: Official API docs [WEB: official, 2025-01] state `MAX_TOKENS = 4096`. Community wiki [WEB: community-wiki, 2024-06] states `8192`. Please confirm which applies to your integration."
4. **When community wiki contradicts official docs — defer to official docs and flag the discrepancy.**
5. **When only community/forum sources exist — do not assert correctness. Ask.**
   > "🔲 `BASE_RATE = 0.006` [CODE: line 412]. Only a community guide found — no official reference. Flagged as audit gap."
6. **Never silently prefer a web value over the code value.** A discrepancy must be surfaced, not auto-corrected.
7. **Never use training memory as a tiebreaker when web sources conflict.** The user arbitrates — not the auditor.

**Domain categories where web sources are especially unreliable:**
- Game mechanics and live-service constants: frequently updated per-patch; wiki edits lag announcements; community speculation presented as fact is endemic
- Third-party API limits: frequently tier-dependent and silently changed; documented limit ≠ enforced limit
- Medical reference ranges: must cite peer-reviewed publication or official clinical guideline — no wikis, no forums
- Community-derived formulas (datamining, reverse-engineering): approximations, not specifications — tag `[WEB: community-derived]` and flag uncertainty explicitly

**Domain categories where training data is especially unreliable — always treat as `[UNVERIFIED]` unless sourced:**
- Game mechanics, live-service constants, per-patch values (rates, thresholds, drop tables, cooldowns)
- Third-party API rate limits, pricing tiers, model context windows, service SLAs
- Medical reference ranges, drug interaction rules, clinical formula coefficients
- Financial regulation specifics (tax rates, reporting thresholds, jurisdiction rules)

#### Scenario Reference

| Situation | Correct output |
|-----------|----------------|
| Code matches §0 or official web source | ✓ `RATE = 0.006` [CODE: line 412] matches [§0-CONFIRMED] / [WEB: official, v1.8, 2024-11]. Verified. |
| Code value, no §0, official web source found | ⚠ Official docs confirm this value. Flagging for user confirmation that this is the correct reference version. |
| Code value, web sources conflict | ⚠ SOURCE CONFLICT: Official docs: X. Community wiki: Y. Cannot assert correctness — please confirm which source applies. |
| Code value, only community sources | 🔲 Only community sources found. Flagged as audit gap; cannot confirm correctness. |
| Value needed, no source anywhere | 🔲 Value needed for §A1 assessment. No §0, no reliable web source found. Deferring correctness finding — audit gap. |

#### Cross-Session Consistency

When a second or subsequent audit occurs on the same app or a different version:
1. Surface the Prior Audit Continuity block from §0 at the top of Part 1
2. Prior `[§0-CONFIRMED]` rules carry forward as confirmed until explicitly contradicted by new §0 input
3. Any conflict between this session's findings and a prior confirmed rule surfaces immediately as `[CONFLICT]` — never silently resolved in either direction
4. Do not re-derive previously confirmed domain rules from training data — re-derive only from code + new user confirmation
5. Version differences are real: a constant correct in v1.2 may have changed in v1.3. Always check whether the app version has changed and flag which confirmed rules may be version-specific

---

### §I.7. Adaptive Analysis Protocols

#### Mid-Audit Reclassification Triggers
During the audit, if any of the following are discovered, **STOP and reclassify before continuing**. The initial §0 classification was based on incomplete information; these discoveries change the audit's severity baseline:

| Discovery | Reclassification Action |
|-----------|------------------------|
| Undisclosed financial transaction code | Escalate Stakes → HIGH; activate §K1, §C1 |
| Undisclosed health / dosage calculations | Escalate Stakes → CRITICAL immediately |
| State that persists PII to localStorage | Activate full §C5, §C6 GDPR review |
| CDN scripts without SRI in a payment/auth context | Immediate CRITICAL — supply chain attack surface |
| Code quality varies dramatically by section | Flag multiple-author / rush-commit sections; elevate confidence threshold for those sections |
| Dead code > 20% of total codebase | Adjust P10 scope — dead code analysis becomes primary; active/dead boundary must be mapped before other dimensions |
| Hardcoded credentials found | Surface as CRITICAL immediately; do not proceed without developer acknowledgment |
| Imports or calls to modules not present in provided files | Note as audit gap — affected findings are [THEORETICAL] until missing code is provided |

#### Partial Codebase Protocol
When only part of the codebase is provided:
1. Explicitly list what is **not** provided — `backend/`, auth module, worker file, etc.
2. Flag findings that depend on missing code as `[THEORETICAL]` — cannot confirm without full context
3. Do not assume missing code is correct — record its absence as a named audit gap
4. State which dimensions are affected by each gap and what would be needed to close it
5. Ask the user to provide the missing files before proceeding with deeply affected dimensions

#### Novel Pattern Protocol
When encountering a pattern not covered by the audit taxonomy:
1. Describe the pattern precisely — what it does, what it appears to intend, what it resembles
2. Classify by analogy: "behaves like a state machine implemented via X instead of Y"
3. Apply the nearest applicable dimension's criteria and note the approximation explicitly
4. Flag: `[NON-STANDARD PATTERN — audit criteria approximated via §X analogy]`
5. Note what cannot be assessed without understanding the original intent

#### Code Quality Variance Signal
If code quality varies significantly between sections — professional in some areas, rushed or inconsistent in others:
- This is a strong signal of **multiple authors**, **time-pressure commits**, or **copy-paste from external sources**
- Identify which sections have lower quality and apply elevated scrutiny to those sections
- Critical risk flag: if lower-quality sections are also the ones handling higher-stakes logic (payments, medical, auth), this is the **highest-risk combination** in the codebase — escalate all findings in those sections by one severity level

#### Signal Correlation — Connecting Distant Patterns
Some bugs are only visible when two distant code locations are read together:
- A constant defined early, used incorrectly hundreds of lines later
- A validation rule in one component that contradicts business logic in another
- State initialized correctly but reset incorrectly in a cleanup function far away
- A security assumption in one layer silently violated by a different layer

**Protocol**: For every validation rule, find every place that validates the same concept — do they all agree? For every security assumption, trace whether anything downstream silently violates it. Cross-reference findings across sections before finalizing severity.

---

## II. AUDIT PHILOSOPHY — THE IRON LAWS

### Law 1 — Specificity Is Non-Negotiable
Every finding names the exact function, variable, line number (or `near functionName`), CSS class, or data value. "Improve error handling" is not a finding. "`handleImport()` near line 847 calls `JSON.parse()` without a try/catch — any non-JSON clipboard paste throws an uncaught TypeError that crashes the React tree" is a finding.

### Law 2 — Bugs Before Refactors, Always
If a function has a bug AND is poorly structured: fix the bug first, with a minimal targeted change. The structural improvement is a separate, lower-priority recommendation. Never bundle them — it multiplies regression risk and obscures what actually changed.

### Law 3 — Honesty Over Completeness
`[THEORETICAL]` with clear reasoning is infinitely more valuable than `[CONFIRMED]` that is fabricated. Never invent line numbers, function names, or behavior. When uncertain, say so explicitly and explain why you suspect the issue.

Every domain fact must carry a source tag. Any fact tagged `[UNVERIFIED]` is a question to the user, not a finding. The full source classification system, web source quality tiers, and scenario reference are in **§I.6**.

### Law 4 — The Feature Preservation Contract
Every working feature is innocent until proven broken. The Feature Preservation Ledger (built in Part 1) is a binding contract: **no recommendation may break, remove, or diminish a working feature.** This applies to optimization, polishing, standardization, and refactoring recommendations equally. If a simplification would remove a working feature — it is rejected, full stop.

### Law 5 — The Identity Preservation Contract
The app's intentional design character (from §0 Design Identity) must not be erased by the audit. Polishing means making the existing vision more refined and consistent — not replacing it with generic conventions. A dark cyberpunk aesthetic with glowing accents gets polished *as* a dark cyberpunk aesthetic, not converted into a neutral gray corporate dashboard.

### Law 6 — Stakes Multiply Severity
Wrong numbers in a hobby tracker → MEDIUM. The same wrong numbers in a medical dosing app → CRITICAL. Use the Stakes field from §0 as a severity multiplier across all categories.

### Law 7 — The Golden Question
Before every modification recommendation:
> *"If the developer applies this change at 2 AM without carefully reviewing it, what is the worst realistic outcome?"*
> If the answer is anything other than "nothing bad" — add explicit warnings, reduce scope, or split into safer atomic steps.

### Law 8 — Minimum Footprint
Every fix recommendation must use the smallest safe change that resolves the problem. A 3-line targeted fix is strictly preferred over a refactor that happens to fix the problem as a side effect. Scope creep in a fix is itself a risk: it expands the surface area that can introduce regressions, conflicts with the Feature Preservation Contract, and makes the change harder to review. If a larger structural improvement is also needed, it is a separate, explicitly lower-priority recommendation.

### Law 9 — Project Forward
Every audit finding exists in time. Before closing each section, ask:
> *"If this issue is not fixed, what does it cost in 6 months when the codebase is 2× larger?"*
> A small validation gap that costs 5 minutes to fix today may require a data migration affecting thousands of records in 6 months. A naming inconsistency that is mildly confusing now becomes a maintenance trap when the team grows. An unabstracted localStorage call that takes 2 minutes to add now becomes a 2-week refactor before user accounts can be added.
> Time-amplified findings are marked with **⏱ COMPOUNDS** — these should be prioritized above their individual severity suggests, because the cost of inaction is not fixed.

### Law 10 — Knowledge Integrity

Domain data that cannot be sourced from code, §0, or an official web search **must not be used as a finding basis**. See **§I.6** for the full source classification system, web source quality tiers, and scenario reference. The three valid paths to a correctness finding: (1) user confirms in §0 → `[§0-CONFIRMED]`, (2) official docs found via web search → `[WEB: official, vX.Y, date]`, (3) code value present, no external confirmation → flag as unverified, ask user.

**Never:** state a value from memory then issue findings based on it. **Never:** use a community source as sole basis for "the code is wrong." **Always:** source every domain fact explicitly.

---

### Compound Finding Chains
Some bugs are individually minor but form chains of escalating harm when combined. Always look for:
- Validation gap [LOW] → invalid value in engine [MEDIUM] → wrong output displayed [HIGH] → user makes bad real-world decision [CRITICAL given stakes]
- Missing cache invalidation [LOW] → stale data served [MEDIUM] → user acts on outdated info [HIGH] → financial/health consequence [CRITICAL]

When a chain exists: document it, escalate the combined severity, and number the chain.

---

## III. EXECUTION PLAN

### Pre-Flight Checklist (Mandatory — Before Any Finding)

> **Claude Code**: Use `Agent` (subagent_type: Explore) to read the entire codebase in parallel. For large apps, launch multiple agents targeting different directories. Use `TodoWrite` to create the audit progress tracker. Use `AskUserQuestion` for any §0 fields you can't extract from code.

```
[ ] Read the entire source file(s) top-to-bottom without skipping
    → Claude Code: Use Agent(Explore) for large codebases, Glob + Read for small ones

[ ] Classify: domain type, architecture pattern, app size → determine part count

[ ] Extract all domain rules from code → verify against §0 → flag discrepancies
    → Claude Code: Use Grep with these patterns:
      Constants:      Grep(pattern: "(val|const|let|var|static|final)\\s+[A-Z_]{2,}\\s*=", type: "kotlin")
      Magic numbers:  Grep(pattern: "[^0-9][0-9]{2,}[^0-9dpsp]", glob: "*.kt")  — then filter non-obvious
      Hardcoded URLs: Grep(pattern: "https?://", glob: "*.{kt,java,xml}")
      Formulas:       Grep(pattern: "(Math\\.|ceil|floor|round|sqrt|pow|abs|max|min)", type: "kotlin")

[ ] Identify all architectural constraints → acknowledge them explicitly
    → Claude Code: Read build config files first:
      Android: Glob(pattern: "**/build.gradle*") + Read AndroidManifest.xml
      iOS:     Glob(pattern: "**/Podfile") or Glob(pattern: "**/Package.swift")
      Web:     Read package.json, vite.config.*, webpack.config.*

[ ] Extract Design Identity from code if not provided → confirm with user
    → Claude Code: Read theme/style files:
      Android: Glob(pattern: "**/res/values/colors.xml") + Glob(pattern: "**/res/values/themes.xml")
              + Glob(pattern: "**/res/values/styles.xml") + Glob(pattern: "**/res/values-night/**")
      Web:     Grep(pattern: "--[a-z]", glob: "*.css") for CSS variables
      iOS:     Glob(pattern: "**/*.xcassets/**")

[ ] Build Feature Preservation Ledger (every named feature: status + safety flags)
    → Claude Code: Use Grep(pattern: "class.*Fragment|class.*Activity|class.*ViewModel", type: "kotlin")
      to inventory all screens/features

[ ] Map each critical workflow from §0 through the actual code

[ ] Identify top 5 risk areas based on domain classification

[ ] Announce: domain class, architecture class, planned part count, top-risk areas

[ ] For apps > 3,000 lines: wait for user acknowledgment before Part 2
    → Claude Code: Use AskUserQuestion to confirm before proceeding

[ ] Create progress tracker with TodoWrite listing all planned parts
```

### Part Structure

| Part | Focus | Non-Negotiable Deliverables |
|------|-------|-----------------------------|
| **P1** | Pre-Flight · Inventory · Architecture | Feature Preservation Ledger, Constraint Map, Design Identity Confirmation, Domain Rule Verification Table, Workflow Map, Audit Plan |
| **P2** | Domain Logic & Business Rules | Rule-by-Rule Verification, Formula Test Vectors, Data Accuracy Report, Temporal/Timezone Audit |
| **P3** | Security · Privacy · Compliance | Threat Model, Sensitive Data Inventory, Attack Surface Map, Compliance Gap List |
| **P4** | State · Data Integrity · Persistence | State Schema Audit, Validation Gap Report, Data Flow Diagram, Corruption Paths |
| **P5** | Performance · Memory · Loading | Web Vitals Estimate, Resource Budget Table, Memory Leak Inventory, Computation Bottlenecks |
| **P6** | Visual Design · Polish · Design System | Design Token Audit, Visual Rhythm Analysis, Component Quality Scorecard, Polish Gap Inventory |
| **P7** | UX · Information Architecture · Copy | Flow Analysis, IA Audit, Copy Quality Inventory, Interaction Pattern Audit |
| **P8** | Accessibility | Full WCAG 2.1 AA Checklist, Screen Reader Trace, Keyboard Nav Map, ARIA Correctness |
| **P9** | Browser · Platform · Compatibility | Cross-Browser Matrix, PWA Audit, Mobile/Touch Audit, Network Resilience Matrix |
| **P10** | Code Quality · Architecture · Optimization | Dead Code Inventory, Duplication Map, Naming Audit, Structural Analysis, Optimization Opportunities |
| **P11** | AI / LLM Integration *(activated when External APIs or AI/LLM field in §0 references any AI provider)* | Prompt Injection Surface, Output Sanitization Audit, Streaming Error Handling, Token/Cost Risk, Hallucination Exposure |
| **P12** | Internationalization & Localization | Hardcoded String Inventory, Locale-Sensitive Format Audit, RTL Audit, i18n Completeness Report |
| **P13** | Development Scenario Projection *(§O — see Growth Context in §0)* | Scale Cliff Analysis, Feature Addition Risk Map, Technical Debt Compounding Map, Dependency Decay Forecast, Constraint Evolution Analysis, Maintenance Trap Inventory |
| **P14+** | Domain Deep Dives | App-specific: probability math, financial precision, medical logic, AI integration, API contracts, etc. |
| **P-R&D** | Research, Development & Improvement *(§X)* | Existing Feature Health Audit, Feature Gap Matrix, Improvement Prioritization, R&D Roadmap, Experimentation Protocol |
| **P-POL** | Polishing & Restructuration *(§XI)* | App Comprehension Record, Coherence Fracture Map, Polish Passes (0–6), Code Restructuring, Architecture Evolution, Quality Gates |
| **Final** | Summary Dashboard | Findings table, Root Cause Analysis, Compound Chains, Quick Wins, Optimization Roadmap, Polish Roadmap |

---

## IV. AUDIT DIMENSIONS

> 120+ dimensions across 15 categories. Every dimension applies to every app.
> Domain Classification (§I.1) determines depth and severity multipliers.

> **Claude:** This is the largest section (~1,400 lines). Do NOT read it all at once. Use this mini-index to jump to the category you need:
>
> | Category | Line Anchor | Sections |
> |----------|------------|----------|
> | **A** Domain Logic | `### CATEGORY A` | §A1–§A7 |
> | **B** State & Data | `### CATEGORY B` | §B1–§B6 |
> | **C** Security | `### CATEGORY C` | §C1–§C7 |
> | **D** Performance | `### CATEGORY D` | §D1–§D5 |
> | **E** Visual Design | `### CATEGORY E` | §E1–§E11 |
> | **F** UX & IA | `### CATEGORY F` | §F1–§F6 |
> | **G** Accessibility | `### CATEGORY G` | §G1–§G5 |
> | **H** Compatibility | `### CATEGORY H` | §H1–§H4 |
> | **I** Code Quality | `### CATEGORY I` | §I1–§I6 |
> | **J** Data Presentation | `### CATEGORY J` | §J1–§J4 |
> | **K** Domain Depths | `### CATEGORY K` | §K1–§K5 |
> | **L** Polish & Standard. | `### CATEGORY L` | §L1–§L7 |
> | **M** Deployment | `### CATEGORY M` | §M1–§M3 |
> | **N** i18n | `### CATEGORY N` | §N1–§N4 |
> | **O** Projections | `### CATEGORY O` | §O1–§O7 |
>
> Use `Grep` with the category header (e.g., `### CATEGORY E`) to jump directly.

---

### CATEGORY A — Domain Logic & Correctness

The most consequential category. An app that looks polished but produces wrong output is harmful. Every point here verifies against §0 Domain Rules.

#### §A1. BUSINESS RULE & FORMULA CORRECTNESS
- **Constants verification**: Every named constant vs §0 expected value. Flag every discrepancy immediately, regardless of size.
- **Formula reproduction**: For every non-trivial formula — reproduce it by hand with known inputs and compare to actual code output.
- **Operator precision**: `>` vs `>=`, `&&` vs `||`, `Math.floor` vs `Math.round` vs `Math.ceil` — each one changes behavior at boundaries.
- **Order of operations**: Integer vs float division? Parenthesization correct? Associativity assumptions?
- **Precision strategy**: Does rounding happen at computation, at display, or both? Are rounding errors accumulating across a multi-step pipeline?
- **Units consistency**: Values that look similar but differ (percentages as 0–1 vs 0–100, ms vs s, cents vs dollars) — always handled correctly?
- **Domain invariants**: Properties that must always hold true (probabilities sum to 1.0, totals match line items, age ≥ 0) — are they enforced or just hoped for?
- **Boundary values**: Test the exact edges: `0`, `1`, `max_valid`, `max_valid + 1`, `-1`, `null`, `undefined`, `NaN`, empty string.

#### §A2. PROBABILITY & STATISTICAL CORRECTNESS *(deepened for gambling/gacha/actuarial/analytics)*
- **Model validity**: Is the mathematical model appropriate for the actual stochastic process (Markov, independence, memoryless)?
- **CDF integrity**: Does the cumulative distribution reach ≥0.9999 within the supported domain? Residual probability accounted for?
- **Expected value verification**: Computed EV matches closed-form solution where one exists?
- **Monte Carlo adequacy**: Sufficient trial count for the required precision? Standard error reported to user?
- **Numerical stability**: Underflow at very small probabilities? Overflow at very large inputs?
- **Known-good test vectors**: ≥3 manually-verified {input → expected output} pairs tested against the engine.
- **Uncertainty communication**: Results labeled as estimates? Confidence intervals disclosed?

#### §A3. TEMPORAL & TIMEZONE CORRECTNESS
- **UTC offset correctness**: Every region's offset verified. Named timezones vs hardcoded offsets (named are safer).
- **DST transitions**: For any region with DST (US, EU, AU, etc.) — spring-forward and fall-back handled? Countdowns crossing a DST boundary?
- **Epoch arithmetic**: Timestamps in ms vs s — mixed? Far-future overflow?
- **Relative time**: "X days until" — off-by-one at midnight? Timezone-aware?
- **Scheduled events**: Daily/weekly resets — correct simultaneously for all supported timezones?
- **Stale temporal data**: Hardcoded dates that were correct at write-time but have since passed?

#### §A4. STATE MACHINE CORRECTNESS
- **Reachable invalid states**: Can the app arrive at a combination of state values that has no defined meaning?
- **Transition completeness**: Every event from every state — is the transition defined, or does some combination produce undefined behavior?
- **Guard conditions**: Transitions that should only fire under certain conditions — are guards actually enforced?
- **Race conditions**: Rapid clicks, concurrent tabs, Worker messages arriving simultaneously — state consistency maintained?
- **Idempotency**: Actions safe to repeat (refresh, double-click, re-import) — produce the same result?

#### §A5. EMBEDDED DATA ACCURACY
- **Named entity correctness**: Every named item (character, product, rate, rule, material) verified against authoritative source in §0.
- **Staleness**: Data accurate as of which version/date? Is that version documented? What has changed since?
- **Cross-reference integrity**: Entity A references Entity B — B exists and has the expected attributes?
- **Completeness**: All expected entities present? Gaps in coverage?
- **Relationship correctness**: Parent-child, lookup, many-to-many — all bidirectionally consistent?

#### §A6. ASYNC & CONCURRENCY BUG PATTERNS

These bugs are invisible in single-path testing but surface reliably in real usage.

- **Stale closure captures (React)**: `useEffect` callbacks capturing state/props via closure — if the dependency array is missing or incomplete, the effect runs with stale values. Classic symptom: a `setInterval` inside `useEffect` reads state that never updates after the initial render.
- **`async` in `forEach`**: `array.forEach(async item => { ... })` — `forEach` does not await Promises. All async operations fire simultaneously and errors are silently swallowed. Use `for...of` with `await` or `Promise.all()` instead.
- **Promise swallowing**: `.catch(() => {})` or bare `.catch(console.error)` with no recovery path — the operation silently fails, the app continues in a broken state the user cannot detect or recover from.
- **Unhandled Promise rejections**: `async` functions called without `await` and without `.catch()` — the rejection is unhandled. Modern browsers fire an `unhandledrejection` event but give the user no feedback. Search for every `asyncFn()` call pattern (no `await`, no `.then/.catch`).
- **Race condition on sequential async calls**: Two rapid user actions each fire an async request — the second resolves first, then the first overwrites the newer result. Fix: use `AbortController` to cancel the previous request, or track a request sequence number and discard stale responses.
- **Missing `useEffect` cleanup**: Effect creates a subscription, event listener, timer, or WebSocket — but the cleanup function (`return () => { ... }`) is absent. Causes resource leaks and React's "state update on unmounted component" warning.
- **Concurrent state writes from multiple effects**: Multiple `useEffect` hooks each calling `setState` on the same state slice, triggered by the same event — one effect silently overwrites another's result. Execution order is deterministic but non-obvious to future maintainers.
- **`setTimeout`/`setInterval` drift**: Using `setInterval` for a countdown timer — each tick drifts slightly due to JS event loop variance. After minutes, a visible desynchronization appears. Fix: use absolute timestamp deltas (`targetTime - Date.now()`).
- **Async constructor / mount pattern**: Critical initialization logic placed in `useEffect` — the component renders once with empty/default state before the effect runs. If no loading state is shown, the user sees a flash of wrong data or empty UI.

#### §A7. JAVASCRIPT TYPE COERCION & IMPLICIT CONVERSION TRAPS

JS silently converts types in ways that produce wrong results without throwing errors — the code runs, the numbers look plausible, and the bug is invisible until edge cases hit.

- **`==` vs `===`**: `"0" == false` → `true`. `null == undefined` → `true`. `[] == false` → `true`. `"" == 0` → `true`. Any `==` comparison with non-identical types is a potential silent misclassification. Search the entire codebase for `==` (not `===`) and assess each one.
- **`+` operator with mixed types**: `"5" + 3` → `"53"`. Any `+` operation touching user input or API response data (which arrives as strings) silently concatenates instead of adding. Always explicitly convert: `Number(input) + 3` or `parseInt(input, 10) + 3`.
- **`parseInt` without radix**: Always `parseInt(str, 10)`. Also: `parseInt("3.5px")` → `3` — stops at the first non-numeric character. Is that the intended behavior for inputs like "3.5rem", "10%", "N/A"?
- **`parseFloat` on formatted numbers**: `parseFloat("1,234.56")` → `1`. Any user-formatted or locale-formatted number string must be normalized (strip commas, currency symbols) before parsing.
- **Falsy value cascade**: `0`, `""`, `null`, `undefined`, `NaN`, and `false` are all falsy. `if (count)` is `false` when `count === 0` — a common off-by-one source for zero-item states. `if (name)` is `false` when `name === ""`. Use explicit comparisons: `count !== null && count !== undefined` or `count != null` (intentional `!=`).
- **NaN propagation**: `NaN !== NaN` — the only value not equal to itself. `isNaN("hello")` returns `true`; so does `isNaN(undefined)` — they mean different things. Use `Number.isNaN()` for strict detection. Any arithmetic involving NaN silently produces NaN, which propagates through the entire calculation pipeline, ultimately displaying as `NaN` or `0` (after `|| 0` guards) with no error.
- **Array/object truth inconsistency**: `[]` and `{}` are truthy. `Boolean([])` → `true`, but `[] == false` → `true` via type coercion. Conditionals that expect to distinguish "no data" from "empty array" must use `.length` checks, not truthiness.
- **Numeric string comparisons**: `"10" > "9"` → `false` (string comparison: `"1" < "9"`). If sort comparators or range checks operate on uncoerced string inputs, ordering silently fails for numbers ≥ 10.
- **`typeof null === "object"`**: Historical JS bug, unfixable. `if (typeof x === "object")` is true for `null`. Always add `&& x !== null` for any object type check.
- **Implicit global variable creation**: A variable assigned without `let`/`const`/`var` inside a function silently becomes a property on `window`. Is `"use strict"` enabled globally to catch this class at runtime?

---

### CATEGORY B — State Management & Data Integrity

#### §B1. STATE ARCHITECTURE
- **Schema completeness**: Every field — type, valid range, default value, null/undefined behavior, documented purpose.
- **Normalization**: Any piece of data represented in two places that can diverge? Single source of truth for everything?
- **Derived state staleness**: Computed values re-derived on demand vs cached — if cached, what invalidates the cache?
- **Initialization correctness**: Default state valid for both fresh install and state-restored-from-storage?
- **Reset completeness**: State reset/clear — leaves orphaned storage keys? Misses any field?

#### §B2. PERSISTENCE & STORAGE
- **Completeness**: Every user-meaningful state field persisted? Any transient UI state accidentally persisted?
- **Schema versioning**: Version identifier in stored data? Migration logic for schema evolution across app versions?
- **Quota management**: localStorage size monitored? User warned approaching 5MB? `QuotaExceededError` caught gracefully?
- **Concurrent write safety**: Multiple tabs writing simultaneously — race condition? Data loss?
- **Cold start validation**: Persisted state parsed and validated against current schema before use? Handles corrupted state from a previous bug?
- **Sensitive data in storage**: Tokens, passwords, PII stored unencrypted in localStorage?

#### §B3. INPUT VALIDATION & SANITIZATION
- **Coverage**: Every user-facing input validated — none bypassed?
- **Type enforcement**: Silent type coercion (`"0" == 0`, `parseInt(undefined)`) producing wrong values?
- **Range enforcement**: Min/max limits — enforced at input layer, computation layer, or display layer (or none)?
- **Boundary testing**: For each input: test `0`, `max`, `max+1`, `-1`, `""`, `null`, `NaN` — what happens?
- **NaN/Infinity propagation**: Division by zero? `parseInt("")` returning NaN silently becoming 0 in downstream math?
- **Validation UX**: Error messages tell the user what went wrong and what they should enter instead.

#### §B4. IMPORT & EXPORT INTEGRITY
- **Import safety**: `JSON.parse` in try/catch everywhere? Prototype pollution via `__proto__`/`constructor`/`prototype` keys?
- **Size enforcement**: Maximum import size enforced before parsing begins?
- **Schema validation**: Imported data validated against expected schema — not blindly spread into state?
- **Preview before commit**: User sees what will change before confirming?
- **Rollback capability**: Pre-import state snapshot saved? Import undoable?
- **Round-trip fidelity**: `export → import → export` — both exports identical?
- **Partial import**: Can user import a subset without overwriting unrelated state?
- **Export completeness**: 100% of user state in export? Anything missing?
- **Self-describing schema**: Version field and field descriptions in export JSON, so external tools can parse it?

#### §B5. DATA FLOW MAP
Produce a text diagram: `User Input → Validation → State → Computation → Display`
At every arrow: What can go wrong? What protection exists? What is the gap?

#### §B6. MUTATION & REFERENCE INTEGRITY

Mutation bugs are among the hardest to find — the code looks correct but silently operates on shared references, causing distant, non-reproducible state corruption.

- **Direct state mutation (React/Vue)**: `state.items.push(item)` or `state.count++` mutates the existing reference — the framework's reconciler sees the same reference and may not re-render, or renders with partially updated state. Always produce new references: `setState(prev => ({ ...prev, items: [...prev.items, item] }))`.
- **`Object.assign` shallow copy trap**: `Object.assign({}, state)` creates a shallow copy — nested objects and arrays are still shared references. Mutating a nested property mutates both the copy and the original. Use structured clone, spread recursively, or an immutability library for nested state.
- **`Array.sort()` and `Array.reverse()` mutate in place**: Calling `items.sort(compareFn)` in a render path or derived value mutates the source array. Use `[...items].sort(compareFn)` to sort a copy.
- **Shared default parameter objects**: `function createItem(options = DEFAULT_OPTIONS)` where `DEFAULT_OPTIONS` is a module-level object — if any caller mutates `options`, subsequent callers receive the already-mutated object as their "default". Always spread defaults: `{ ...DEFAULT_OPTIONS, ...options }`.
- **Closure accumulation across calls**: A function closes over an array or object and mutates it on every call — each invocation accumulates state from all previous calls, not starting fresh. Particularly subtle in callbacks registered during module initialization.
- **Props mutation (React)**: Directly mutating a prop value (e.g. `props.items.push(...)`) instead of triggering a parent state update — violates unidirectional data flow and causes stale state across renders in ways that are very difficult to trace.
- **Synthetic event object pooling (React < 17)**: Accessing `event.target.value` inside a `setTimeout` or async callback — React's synthetic event pool reuses the event object, so accessing it after the handler returns returns `null`. React 17+ removed pooling, but if React version is unknown: check all async event accesses.
- **Immer `produce` misuse**: Mutations outside the Immer draft context, returning both a mutation and a value from the same producer, or forgetting to return from a non-mutating producer — all cause silent state corruption that is extremely difficult to trace.

---

### CATEGORY C — Security & Trust

#### §C1. AUTHENTICATION & AUTHORIZATION
- **Credential storage**: Passwords, tokens, API keys — never in localStorage unencrypted, never in source code.
- **Hash comparison**: Client-side hash comparisons — constant-time? Hash visible in source (extractable for offline brute-force)?
- **Lockout bypass**: Attempt-rate limiting stored in localStorage — clearable by user to reset counter?
- **Session management**: Token expiry handled? Idle logout? Session fixation?
- **Privilege escalation**: Can a user manipulate localStorage/state to access features beyond their authorization?

#### §C2. INJECTION & XSS
- **innerHTML / dangerouslySetInnerHTML**: Any use? Is the content user-supplied or from an external source?
- **DOM-based XSS**: User strings inserted into `className`, `href`, `src`, `style`, `data-*` attributes?
- **Dynamic code execution**: `eval()`, `Function()`, `setTimeout(string)`?
- **URL injection**: User-controlled values concatenated into URLs? Open redirect?
- **CSS injection**: User values in inline `style` strings?

#### §C3. PROTOTYPE POLLUTION & IMPORT SAFETY
- **JSON.parse safety**: Every parse call in try/catch — including ones that "can't fail"?
- **Prototype pollution**: Imported objects merged/spread without filtering `__proto__`, `constructor`, `prototype`?
- **Property collision**: Imported data keys capable of shadowing expected application properties?

#### §C4. NETWORK & DEPENDENCIES
- **All HTTPS**: Mixed-content risk from any HTTP resource?
- **SRI (Subresource Integrity)**: `integrity` attributes on CDN `<script>` and `<link>` tags? Without SRI, a CDN compromise serves malicious code.
- **External data tracking**: Third-party image hosts, CDNs — user IP/referrer logged without disclosure?
- **CORS**: External API CORS handling correct? Credentials in cross-origin requests?
- **CSP**: Content Security Policy present? `unsafe-inline`/`unsafe-eval` requirements that undermine it?

#### §C5. PRIVACY & DATA MINIMIZATION
- **PII inventory**: What personal data is collected, stored, or transmitted? Is each piece necessary?
- **URL leakage**: State in hash/query params leaks via browser history, referrer headers, server logs?
- **Third-party fingerprinting**: CDNs, analytics, presence systems — disclosed to user?
- **Export sensitivity**: Export JSON contains data the user didn't know was being recorded?

#### §C6. COMPLIANCE & LEGAL
- **GDPR/CCPA**: Personal data processed? Right to deletion? Privacy policy linked?
- **Age restrictions**: Gambling-adjacent, adult, or violence content — age gating present?
- **IP/Copyright**: Third-party copyrighted assets used? Attribution and disclaimer present?
- **Financial regulations**: App gives financial advice? Regulatory disclaimer?
- **Medical regulations**: App gives health guidance? "Not medical advice" disclaimer prominent?
- **Accessibility law**: ADA/EN 301 549 obligations relevant to this app?

#### §C7. MOBILE-SPECIFIC SECURITY *(activated for Android/iOS apps)*
- **Permission audit**: Are all declared permissions actually used? Over-requesting permissions signals privacy issues and can trigger store rejection.
  - Android: Check `AndroidManifest.xml` `<uses-permission>` vs actual usage in code
  - iOS: Check `Info.plist` usage descriptions vs actual API calls
- **Exported components**: Android — are Activities/Services/BroadcastReceivers unnecessarily exported? (`android:exported="true"` without intent filters = attack surface)
- **Data storage security**: Sensitive data in SharedPreferences/UserDefaults without encryption? Use EncryptedSharedPreferences / Keychain.
- **WebView security**: `setJavaScriptEnabled(true)` + `addJavascriptInterface()` = injection surface. `setAllowFileAccess(true)` = local file read risk.
- **Network security config**: Android — is `android:networkSecurityConfig` present? Does it allow cleartext traffic unnecessarily?
- **ProGuard/R8 rules**: Are security-critical classes excluded from obfuscation appropriately? Are reflection-dependent classes kept?
- **Content Provider exposure**: Android — `android:exported` on ContentProviders without proper permission checks = data leak.
- **Deep link validation**: Are deep links/app links validated against expected patterns, or can arbitrary URIs trigger sensitive actions?
- **Clipboard security**: Sensitive data (passwords, tokens) copied to clipboard without timeout/clearing?

---

### CATEGORY D — Performance & Resources

#### §D1. RUNTIME PERFORMANCE
- **Main thread blocking**: Computations >50ms on the main thread — UI freeze during execution?
- **Worker offloading**: Expensive algorithms in a Worker? Message passing correct? Fallback if Worker unavailable?
- **Unnecessary re-renders** (React): Every component that re-renders when it shouldn't. `memo()` comparators correct? Missing `useCallback`/`useMemo` deps?
- **List virtualization**: Grids/lists with 100+ items — virtualization needed? Jank with current approach?
- **Layout thrashing**: Reading `offsetWidth`/`scrollHeight` inside a write loop? Forces repeated reflows.
- **Debounce/throttle**: High-frequency events (input, scroll, resize) handled without overwhelming the main thread?
- **Cold start computations**: Expensive work triggered on mount instead of lazily on demand?

#### §D2. WEB VITALS & LOADING
- **LCP**: Largest element on first load — blocked by scripts? Image without preload?
- **FID/INP**: Long tasks during load — time to interactive?
- **CLS**: Images without `width`/`height`? Dynamic content injected above existing content? Font reflow?
- **Render-blocking scripts**: CDN scripts without `defer`/`async` — which ones block first paint?
- **FOUC**: CSS loaded after content renders?
- **Parse time**: Large single-file apps — JS parse/compile on low-end mobile (4× CPU throttle)?
- **Resource hints**: `preconnect`/`dns-prefetch` for CDN origins? `preload` for hero images?

#### §D3. RESOURCE BUDGET
Produce this table for the app:

| Resource | Source | Est. Size | Load Strategy | Critical Path? | Optimization? |
|----------|--------|-----------|--------------|----------------|--------------|
| App code | inline/CDN | ? kb | blocking | yes | ? |
| Framework | CDN | ~130kb gz | blocking | yes | lighter alt? |
| … | … | … | … | … | … |
| **Total** | | **? kb** | | | |

- 3G first-load estimate (total / ~1.5 Mbps)?
- What % of app code executes in a typical session (unused code ratio)?

#### §D4. MEMORY MANAGEMENT
- **Closure leaks**: Closures holding references to large objects that should be GC'd?
- **Event listener leaks**: Every `addEventListener` has a corresponding `removeEventListener` in cleanup?
- **Timer leaks**: Every `setInterval`/`setTimeout` cleared on unmount?
- **Worker lifecycle**: Terminated when no longer needed? Multiple instances accidentally spawned?
- **Blob URL revocation**: `URL.createObjectURL` — matching `URL.revokeObjectURL` called?
- **Computation array retention**: Heavy tables (DP, MC) released after use or held in closure?
- **Canvas/WebGL cleanup**: Contexts and canvases disposed on unmount?

#### §D5. MOBILE-SPECIFIC PERFORMANCE *(activated for Android/iOS apps)*
- **Coroutine/async lifecycle**: Are coroutines properly scoped to ViewModel/Fragment lifecycle? Orphaned coroutines running after fragment destruction?
- **RecyclerView / LazyColumn optimization**: ViewHolder pattern correct? DiffUtil used? No nested scrolling conflicts? ViewType reuse?
- **Image loading**: Thumbnails appropriately sized? Image caching configured? Large bitmaps loaded on main thread?
- **Database queries on main thread**: Room/CoreData queries dispatched to background? No `runBlocking` on main thread?
- **Fragment transaction overhead**: Excessive fragment replacements causing layout thrashing? Proper use of `replace` vs `add`?
- **APK/IPA size**: ProGuard/R8 shrinking enabled? Unused resources stripped? Large assets that could be on-demand?
- **Process death recovery**: Is state saved via `onSaveInstanceState` / ViewModel SavedState for critical user data? Process death = complete state loss without this.
- **ANR risk**: Any operation > 5s on main thread triggers ANR dialog (Android). Check file I/O, network calls, heavy computation.
- **Battery impact**: Unnecessary background work? Wake locks held too long? Location updates too frequent?

---

### CATEGORY E — Visual Design Quality & Polish

> This category treats visual design as a professional discipline, not an afterthought.
> The goal is to make the app's existing design vision more **refined, consistent, and polished** — not to replace it with generic conventions.
> §0 Design Identity is protected throughout. All findings improve toward the app's own aesthetic, not away from it.

> **Deep visual work:** When this audit's §E findings reveal systemic visual design issues — or when the user specifically requests a design audit, asks to "make it feel like [X]", or references a named aesthetic — the `design-aesthetic-audit-SKILL.md` skill should be invoked as a companion. It covers 95 sections of visual-design-specific analysis (component character, copy alignment, illustration, data viz, token architecture, state design, responsive character, source material intelligence) that go well beyond what §E covers here. Route to it via §COMPANION in that skill, which maps directly to §E/P6 in this audit.

#### §E1. DESIGN TOKEN SYSTEM
- **Spacing scale**: Is every padding and margin value from a coherent mathematical scale (4/8/12/16/24/32/48/64)? List every one-off value like `p-[13px]` or `margin: 7px`. Each one is a token debt.
- **Color palette architecture**: Is the color system built on a small set of intentional tokens, or are there dozens of slightly-different hardcoded values? List near-duplicate colors and consolidate candidates.
- **Typography scale**: List every unique `font-size` in the codebase. Do they form an intentional modular scale (e.g., 12/14/16/20/24/32px), or are there arbitrary in-between values?
- **Font weight semantics**: Is each weight (`normal`, `medium`, `semibold`, `bold`) used for a consistent semantic purpose? Mixing `font-bold` and `font-semibold` for "emphasis" is token inconsistency.
- **Border radius system**: Are `rounded-*` values consistent by component type? Cards all use the same radius? Buttons the same? Inconsistency in radius reads as unprofessional at a subconscious level.
- **Shadow hierarchy**: Is there a shadow scale (e.g., `sm` for cards, `md` for modals, `lg` for popovers)? Or arbitrary per-component shadows?
- **Z-index governance**: Is stacking order explicitly managed? List every z-index value used. Collisions between layers (modals, toasts, dropdowns, sticky headers)?
- **Animation token set**: Are duration values from a consistent set (e.g., 100/200/300/500ms)? Are easing curves consistent for the same type of motion?
- **Token naming as documentation**: Are token names semantic (what they *mean*) rather than presentational (what they *look like*)? `--color-action-primary` scales to theming and dark mode; `--color-blue-500` does not. A well-named token system is itself product documentation — and for paid/multi-tenant products, also scales to whitelabeling and multi-brand use. For any product nature, naming tokens semantically reduces the cost of every future visual change.
- **Android theme attribute coverage**: Are colors referenced via theme attributes (`?attr/colorPrimary`, `?attr/colorOnSurface`) or hardcoded hex values? Every hardcoded color is a dark mode bug waiting to happen. Check `themes.xml`, `colors.xml`, and all layout files for `#RRGGBB` literals vs `?attr/` or `@color/` references. Material 3 dynamic color support — is it leveraged?
- **Dimension resource consistency**: Are dp/sp values defined in `dimens.xml` as named resources, or scattered as literals across layouts? Audit `android:padding="16dp"` vs `@dimen/spacing_md`. Literal values are token debt on Android just as `px` literals are in CSS.
- **Style inheritance chain**: Is there a clean style hierarchy (`Theme.App` → `Widget.App.Button` → specific overrides)? Or are styles flat-copied with minor variations, creating maintenance debt? Count the number of `<style>` definitions and check for near-duplicate styles that should be consolidated via parent inheritance.
- **Night mode token completeness**: Every color resource has a `-night` variant? Every drawable has a night-appropriate version? Missing night resources cause jarring fallbacks to light-mode values in dark theme.

#### §E2. VISUAL RHYTHM & SPATIAL COMPOSITION
- **Vertical rhythm**: Is there consistent spacing between sections, between cards, between form groups? Inconsistent vertical spacing destroys the feeling of order even when individual components look fine.
- **Density consistency**: Similar components (cards, list items, table rows) have similar internal density. One card with 24px padding and another with 12px padding on the same screen reads as broken.
- **Alignment grid**: Do elements align to a consistent invisible grid? Are there elements that appear to "float" without visual anchoring?
- **Whitespace intention**: Is whitespace used actively to group related items and separate unrelated ones? Or is it applied without rhythm (some areas cramped, others sparse)?
- **Proportion**: Do related elements (label + value, icon + text, header + content) feel proportionally balanced?
- **Focal point clarity**: On every key screen — is there one clear visual focal point that draws the eye first? If the answer is "everything has equal visual weight," the design has no hierarchy and users don't know where to look. Identify the intended focal point on each primary view, then assess whether the current visual treatment actually draws the eye there.
- **Visual weight distribution**: Is visual mass (size, color saturation, contrast, bold weight) distributed intentionally across the screen? Heavy visual elements clustering in one corner makes the layout feel unbalanced. Scan each primary view for unintentional visual weight accumulation.
- **Mobile screen real estate discipline**: On mobile, every pixel is expensive. Is vertical space used efficiently? Are there screens where excessive padding, oversized headers, or decorative spacing pushes primary content below the fold? Count how many primary-content items are visible without scrolling on a standard phone (360×640dp). If fewer than 3 items are visible, the density is likely too low.
- **Edge-to-edge content**: Modern Android (API 30+) and iOS support edge-to-edge layouts behind system bars. Is the app using this for immersive content (image galleries, maps, media)? Are system bar insets handled correctly via `WindowInsetsCompat` so content doesn't hide under status/navigation bars?
- **Landscape layout quality**: If landscape is supported — is there a dedicated layout, or does the portrait layout just stretch? Wide screens should use master-detail, side-by-side, or multi-column layouts. A single-column layout stretched to landscape wastes half the screen.
- **Responsive grid breakpoints**: For tablets and foldables — does the layout adapt? `ConstraintLayout` with guidelines, or responsive grid layouts? A phone layout pixel-doubled on a tablet is a craft failure.

#### §E3. COLOR CRAFT & CONTRAST
- **Color harmony**: Does the accent color work harmoniously with the background and surface colors? Is there a clear hierarchy: background → surface → elevated surface → accent?
- **Dark mode craft**: For dark themes — are dark surfaces using near-black with slight hue (e.g., `#0f1117` with a hint of blue) rather than pure black (except intentional OLED)? Pure neutral dark often reads as less refined than dark with character.
- **Accent consistency**: Is the accent color used consistently as an emphasis signal? Or does it appear so frequently that it loses meaning?
- **Color temperature coherence**: Does the palette stay within a consistent temperature range? A warm orange accent on a cool blue-gray dark surface creates subconscious tension unless intentional.
- **WCAG contrast compliance**: Every text/background combination meets 4.5:1 (normal text) or 3:1 (large/bold). Pay special attention to: muted grays on dark, colored text on colored backgrounds, placeholder text on inputs.
- **Non-text contrast**: UI components (input borders, icon buttons, focus rings) meet 3:1 (WCAG 1.4.11).
- **State colors**: Hover, active, disabled, error, success, warning — distinct, consistent, and on-brand?
- **Color psychology alignment**: Does the palette's psychological character match the app's emotional target (§0)? Blues and cool grays signal reliability and precision — appropriate for financial and medical tools. Warm oranges and greens signal energy and growth — appropriate for gamified or wellness tools. Misalignment between color psychology and domain creates subconscious friction.
- **Material 3 color system adherence**: If using Material 3 — are colors generated from a proper tonal palette (primary, secondary, tertiary, error, surface, outline)? Are `colorOnPrimary`, `colorOnSecondary`, etc. properly set for text/icon contrast on colored surfaces? Are surface tones (surface1–5) used for elevation rather than shadow on dark theme? Check `themes.xml` for complete Material 3 color attribute coverage.
- **Dark mode color quality**: Not just inverted light mode — dark surfaces need intentional tonal elevation. Material 3 uses surface tint (primary color overlay at low opacity) for elevation rather than shadows. Pure `#000000` backgrounds are acceptable for OLED power savings but need intentional tonal surfaces for cards and elevated elements. Check every screen in dark mode for: text contrast, icon visibility, image background blending, divider visibility.
- **Color saturation calibration**: Oversaturated colors (`#FF0000`, `#00FF00`) signal low craft regardless of product nature — a pure green is less refined than a calibrated `#14b8a6`. Assess the saturation and lightness of the palette: does it feel purposeful, or do any values feel like the first pick from a color wheel? *For paid/professional tools*: this directly affects trust and willingness to pay. *For fan/creative tools*: this affects whether the palette feels artistically considered or placeholder-level. The standard changes; the question doesn't.

#### §E4. TYPOGRAPHY CRAFT
- **Heading hierarchy**: Is there a clear visual hierarchy between h1/h2/h3/body/caption levels? Can a user scan the page and immediately identify the most important information?
- **Line length**: Body text lines ideally 45–75 characters. Very short or very long lines hurt readability.
- **Line height**: Body text typically 1.4–1.6× for readability. Tight line height on dense text reads as cramped.
- **Font pairing**: If using multiple typefaces — do they complement or conflict? Consistent use of primary/secondary/monospace roles?
- **Letter spacing**: Display/heading text often benefits from slightly negative tracking (`-0.01em` to `-0.03em`) for refinement. Is this applied consistently to large text?
- **Text rendering**: `-webkit-font-smoothing: antialiased` applied for crispness on dark backgrounds?
- **Label quality**: Form labels, column headers, section titles — concise, sentence-case consistently applied, unambiguous?
- **Typography as character signal** `[A2][A3][A4]`: The typeface choice communicates personality before a single word is read. Assess whether the typeface matches the personality in §0, using the axis profile to determine what "correct" means for this specific app:
  - *High commercial intent (A1)*: Typeface credibility matters — a humanist sans (Inter, Plus Jakarta Sans) signals approachability; a geometric sans (DM Sans, Geist) signals precision; a transitional serif signals authority. Wrong tier here is a trust problem.
  - *Strong subject visual identity (A4)*: Does the typeface feel tonally coherent with the subject? A gritty crime drama tool using a soft rounded font, a classical music app using a harsh display face, a hiking companion using a cold corporate sans — all represent tonal mismatches between typeface and subject.
  - *Expert/practitioner audience (A3)*: Type density and precision are signals of domain competence. A clinical tool with oversized, rounded type feels like it's talking down to experts. A financial terminal with a decorative display font feels wrong.
  - *Emotionally sensitive / high-stakes context (A2)*: Typeface warmth, weight, and size directly affect emotional register. Sharp, compressed, or overly stylized typefaces increase anxiety in sensitive contexts.
  - *Aesthetic IS the product (A5)*: Typeface is part of the output's visual experience — the bar is highest here.
  Whatever the context — if the typeface contradicts the intended personality, name a specific alternative that would serve it better within the app's constraints.
- **Type craft signals** `[A1][A3][A5]`: The relevant refinements depend on the app's axis profile:
  - *High commercial / professional audiences*: tabular nums for aligned number columns (`font-variant-numeric: tabular-nums`), optical size adjustments for display text, consistent lining vs oldstyle figures, proper typographic quotes.
  - *Expert/dense-data contexts*: monospaced or tabular numerals for scannable data columns, appropriate weight for scanability under time pressure.
  - *Aesthetic-primary / creative tools*: OpenType features as expressive tools — ligatures, alternates, stylistic sets — used intentionally.
  - *Any product*: Is there any typographic personality (weight contrast, tracked caps, a purposeful accent) that makes the app feel designed rather than defaulted? Intentionality — not prestige — is the goal.

#### §E5. COMPONENT VISUAL QUALITY

> Every UI component must be audited for visual consistency, state completeness, and craft. This section covers every element type that can appear in a modern app. On Android: check both XML layout definitions AND runtime-applied styles. On iOS: check both Storyboard/XIB and programmatic styling.

**Core Interactive Components:**
- **Button states completeness**: Every button variant has all five states: default, hover, active/pressed, focus (keyboard-visible), disabled. Missing states feel broken during interaction. On Android: check `StateListDrawable`, `ColorStateList`, ripple effects, Material Button styles. Every button type (text, outlined, contained, elevated, tonal) must have complete state coverage.
- **FAB (Floating Action Button) quality**: Correct elevation and shadow? Consistent size (regular 56dp, mini 40dp, extended)? Proper color contrast against all backgrounds it floats over? Hide/show animation on scroll smooth? Position consistent across screens? Extended FAB with icon+text properly sized? FAB does not overlap critical content or navigation elements.
- **Input field states**: Default, focus, filled, error, disabled. The focus ring must be clearly visible. On Android: `TextInputLayout` with proper hint animation, error text below field, helper text, character counter, prefix/suffix icons, end icon (clear, password toggle, dropdown). Check: does the field expand/contract smoothly? Is the label animation smooth? Does error state include both color change AND error text?
- **Checkbox and radio button quality**: Visual size consistent (minimum 48dp touch target on mobile). Custom styled or default? If custom — are they visually consistent with the app's design language? Animation between checked/unchecked smooth? Indeterminate state designed (for checkboxes)? Group alignment consistent?
- **Switch/toggle quality**: Track and thumb proportions feel balanced? On/off state visually unambiguous (not just color — also position, icon, or text)? Animation between states smooth? Disabled state clearly distinguishable? Label positioned consistently (before or after, never mixed)?
- **Slider quality**: Track, thumb, and value label styled consistently? Active vs inactive track colors distinct? Discrete steps clearly marked if applicable? Range slider (two thumbs) handles overlap gracefully? Touch target large enough? Value tooltip positioned without clipping at edges?
- **Dropdown/spinner quality**: Consistent trigger appearance across all instances? Dropdown menu elevation and shadow match the app's shadow hierarchy? Selected item clearly indicated? Menu positioned to avoid clipping at screen edges? Animation for open/close smooth?
- **Search bar quality**: Consistent styling across all screens? Clear/cancel button appears when text is entered? Search icon properly positioned? Voice search icon if applicable? Suggestion dropdown styled consistently? Transition between collapsed and expanded states smooth?

**Container Components:**
- **Card design quality**: Internal padding consistent. Border or shadow — not both unless intentional. Corner radius consistent. Content alignment consistent across all instances. On Android: card elevation consistent across similar card types? Material CardView used with consistent `cardCornerRadius`, `cardElevation`, `strokeWidth`? Clickable cards have proper ripple and elevation change on press?
- **Bottom sheet quality**: Handle/drag indicator consistent? Peek height appropriate for content preview? Expansion animation smooth? Backdrop dimming consistent? Half-expanded state properly designed? Does the bottom sheet conflict with system gesture navigation? Rounded top corners consistent radius?
- **Modal/dialog quality**: Consistent backdrop opacity, border/shadow, corner radius, header/body/footer structure. Close button always in same position and same size. On Android: `MaterialAlertDialogBuilder` styled consistently? Title, message, and buttons properly spaced? Scrollable content within dialog handled? Full-screen dialog for complex forms?
- **Tab bar/TabLayout quality**: Active and inactive tab states visually distinct (color, weight, indicator)? Tab indicator animation smooth? Scroll behavior for many tabs? Tab text not truncated? Icon+text tabs properly aligned? Tab indicator width (full-width vs content-width) consistent?
- **Bottom navigation quality**: Active/inactive icon and label states distinct? Badge/notification dot positioned consistently? Animation between states smooth? Icons optically consistent in weight and size? Label text never truncated? Correct number of items (3-5, never more)?
- **Toolbar/AppBar quality**: Title alignment consistent (centered vs left-aligned)? Overflow menu icon positioned correctly? Navigation icon (back, hamburger) consistent size and position? Collapsing toolbar parallax and fade effects smooth? Status bar color coordinated? Elevation changes on scroll correct?
- **Navigation drawer quality**: Header section designed (not default)? Item height and padding consistent? Active item clearly highlighted? Dividers between groups consistent? Drawer width correct (standard: 256dp)? Edge-to-edge content or properly inset? Scrim overlay opacity consistent?

**Informational Components:**
- **Badge/chip/tag design**: Consistent padding, radius, typography across all instances. On Android: input chips, filter chips, choice chips, action chips, assist chips — each type has consistent styling? Chip close/remove icon consistent? Chip groups wrap properly or scroll horizontally?
- **Snackbar/toast quality**: Consistent position (typically bottom), elevation, corner radius? Action button styled differently from message text? Text never truncated? Duration appropriate (short: 4s, long: 10s, indefinite for critical)? Multiple snackbars queued, not stacked? Does not overlap FAB or bottom nav?
- **Progress indicator quality**: Determinate and indeterminate variants styled consistently? Linear progress bar height and color consistent? Circular progress size appropriate for context? Progress color matches app accent? Buffer state designed for streaming/download? Percentage text positioned clearly?
- **Tooltip quality**: Consistent appearance (background color, text color, corner radius, padding)? Arrow/caret positioned correctly? Appears on hover/long-press without delay? Disappears when no longer relevant? Never clips at screen edges? Text concise (one line preferred)?
- **Banner/alert quality**: Distinct visual treatment for info, warning, error, success? Icon used alongside color to differentiate? Dismissible with consistent close button? Action buttons styled consistently? Does not push content in a jarring way (smooth height animation)?

**Content Display Components:**
- **List item quality**: Consistent height for single-line, two-line, three-line variants? Leading element (icon, avatar, thumbnail) consistently sized and aligned? Trailing element (text, icon, switch) consistently positioned? Dividers between items consistent (full-bleed vs inset)? Long text properly truncated with ellipsis?
- **Icon quality**: All icons from the same family at the same base size. Mixed icon families are visually noisy. Icons sized to optical weight, not just pixel dimensions. On Android: consistent use of outlined vs filled style? Icon tinting using `colorControlNormal` and theme attributes, not hardcoded colors? Vector drawables preferred over rasterized assets?
- **Avatar/thumbnail quality**: Consistent size across same-context usage? Circular vs rounded-square applied consistently? Placeholder/loading state designed? Fallback for missing images (initials, generic icon)? Image scaling (center-crop, fit) consistent?
- **Divider usage**: Lines/dividers used consistently — not as decoration but as structural separators. Too many dividers fragment the layout. On Android: `MaterialDivider` with consistent `dividerInsetStart` and `dividerInsetEnd`?
- **Image presentation**: Images consistently cropped (same aspect ratios for same context), with consistent corner radius treatment. Loading placeholder (solid color, shimmer, blur-up)? Error state for failed loads? Transition animation on load?
- **Empty state design quality**: Every empty state designed (not default system text). Illustration or icon consistent in style? Message text helpful and action-oriented? Primary action button prominent? Visual weight appropriate — not so heavy it feels like an error, not so light it feels like a bug.
- **Date/time picker quality**: Styled consistently with app theme? Calendar view properly designed? Time picker format consistent (12h/24h matching device settings)? Range selection visual treatment clear? Today/selected state visually distinct?

**Structural/Layout Components:**
- **Status bar integration**: Color coordinated with toolbar/app bar? Light/dark status bar icons matching background? Transparent/translucent for edge-to-edge content? Consistent across all screens?
- **System navigation bar integration**: Color or transparency consistent? Handles edge-to-edge content properly? Button bar vs gesture bar visual treatment consistent?
- **Skeleton/shimmer loading quality**: Shimmer shapes match the actual content layout? Animation smooth and not distracting? Color and brightness appropriate (not too flashy, not invisible)? Consistent across all loading states in the app?
- **RecyclerView/list visual quality**: Scroll performance smooth (no jank)? Item animations consistent (add, remove, move)? Overscroll effect styled or disabled intentionally? Grid vs linear layout spacing consistent?

#### §E6. INTERACTION DESIGN QUALITY
- **Hover feedback**: Every interactive element has a perceptible hover state that communicates interactivity. Elements that look interactive but have no hover state confuse users.
- **Active/pressed feedback**: Pressing a button should feel physically responsive — typically a slight scale-down or color deepening.
- **Transition quality**: Transitions should feel deliberate and smooth. Abrupt appearance/disappearance, or overly long/bouncy transitions, break the professional feel.
- **Loading state quality**: Spinners vs skeleton screens — skeleton screens preserve layout and feel more polished for content-loading. Spinners are appropriate for actions.
- **Animation narrative**: Every motion should tell a story about the relationship between UI states. An element sliding in from the left implies it came from somewhere left. Fade-in from nothing implies it was created. Are animations telling the right story?
- **Empty state design**: Empty states are a design opportunity — they should be designed, not blank. Clear visual, helpful message, a clear call to action.
- **Error state design**: Inline errors positioned immediately adjacent to the field that caused them. Not just color — includes icon and descriptive text.
- **Animation as character signal** `[A2][A4][A5]`: The right motion vocabulary is derived from the axis profile — not from a product category:
  - *Focus-critical / high-stakes / high-frequency use (A2)*: Motion is a cognitive tax. Every animation must justify itself — does it serve the user's task, or serve visual interest? Lean toward 100–150ms, ease-out, nothing bouncy or attention-seeking.
  - *Emotionally sensitive contexts (A2)*: Abrupt or jarring transitions increase anxiety. Slow, smooth, and predictable motion is a safety requirement here, not a style preference.
  - *Creative / exploratory / leisure contexts (A2)*: Expressive motion is appropriate — spring physics, slight overshoot, personality without chaos.
  - *Strong subject visual identity (A4)*: The motion character can honor the subject's tonal register — urgency, calm, playfulness, weight — whatever the subject carries. This is a fidelity opportunity, not a decoration question.
  - *Aesthetic IS the product (A5)*: Animation may be the primary value — assess it as output quality, not UI polish.
  - *Any context*: Simple and consistent beats complex and inconsistent. One well-chosen transition applied throughout outperforms five different ones.
  Name the 1–2 specific timing or easing changes that would bring the motion vocabulary into alignment with this app's axis profile.
- **Delight moments** `[A1][A2][A4]`: The highest-impact moments for craft investment are derived from the use context and subject identity — not from a product tier:
  - *High-frequency tools (A2)*: The small moment that makes a daily tool feel good to use — a snappy response, a clean success state, an efficient transition at the right place.
  - *Emotionally sensitive tools (A2)*: Warmth and gentleness at key moments — a kind empty state, a calm confirmation, nothing abrupt when the user is vulnerable.
  - *Creative / expressive tools (A2)*: Moments that feel generative and alive — the tool responding as a collaborator, not just executing commands.
  - *Strong subject identity (A4)*: Moments that feel authentic to the subject and community — a result displayed in a way that resonates with how the audience experiences this subject.
  - *Any app*: The moment the app delivers its primary value — is it presented with any intentionality, or does the result just appear? Even a free utility benefits from treating its output moment with care.
  For each high-impact moment — is there any brief, purposeful visual acknowledgment? If not, it is a craft gap regardless of axes.
- **Physical responsiveness**: The best interfaces feel physical. Buttons compress, drawers slide, modals lift. Assess whether the interaction model feels flat and digital or has a quality of physical responsiveness — and whether that matches the product's intended personality.

#### §E7. OVERALL VISUAL PROFESSIONALISM
- **Design coherence**: Does the app feel like it was designed as a whole, or like different sections were designed independently? Incoherence is visible even when users can't articulate it.
- **Attention to detail**: Pixel-perfect alignment? No 1-pixel misalignments on borders? No slight gaps where elements should touch? Details matter at the professional level. On Android: check for inconsistent `layout_margin` values between similar components, padding differences between fragments using the same layout patterns.
- **Brand consistency**: Is the app's visual identity consistent from section to section? Would a user recognize a new section as part of the same app?
- **First-impression test**: Show the app to someone for 7 seconds, then take it away. Ask them: "What kind of app is this? Does it feel professional?" Their snap judgment reveals the design's non-verbal communication. If they say "it looks like a default/template app" — the design is generic. If they say "it looks polished" — the design is working.
- **Screenshot quality test**: Take a single static screenshot of each primary screen. Would each screenshot look good in an App Store listing, a social media post, or a presentation slide? A screenshot that looks unprofessional out of context signals weak visual design — because App Store screenshots ARE the first impression for most users.
- **Visual noise inventory**: List every element on each primary screen that could be removed, reduced in visual weight, or hidden behind progressive disclosure without losing core functionality. Each unnecessary visual element competes for attention and reduces the signal-to-noise ratio. Count decorative dividers, redundant labels, low-information badges, and visual elements that exist "because they were in the template."
- **Cross-device visual consistency**: Does the app look equally intentional on different screen densities (hdpi vs xxxhdpi), different device sizes (compact vs large phone), and different system themes (light/dark)? Test on at least two device sizes — inconsistencies between devices reveal design decisions that are pixel-tuned for one screen rather than systematically designed.
- **Competitive credibility check**: Name the 2–3 most polished apps in the same category. Compare specific craft elements side-by-side: spacing consistency, color calibration, transition quality, empty state design, typography hierarchy. For each dimension: is this app at parity, close behind, or visibly less polished? The user's mental benchmark is always the best app they use regularly — not the average.
- **Polish delta**: For each section — list specific changes that would move it from "functional" to "intentional" within the existing design language and axis profile.
- **Polish level assessment** `[A1][A2][A5]`: The right polish standard depends on the axes — not on a tier label:
  - *High commercial intent (A1)*: Verify these credibility signals — consistent 4/8-based spacing — subtle shadows with intentional offset and blur — smooth 200–300ms transitions — letter-spacing on headings — antialiased type — hover states that feel physical — skeleton loaders that mirror content shape — contextual empty states — confirmation animations on success.
  - *Focus-critical / invisible-aesthetic contexts (A2)*: The polish goal inverts — the absence of distraction IS the polish. Assess how little the interface demands of the user's attention while still feeling finished and trustworthy.
  - *Emotionally sensitive contexts (A2)*: Polish means warmth and safety — gentle corners, calm palette, generous spacing, transitions that feel unhurried. Clinical sharpness is a polish failure here.
  - *Aesthetic-primary contexts (A5)*: Polish means the UI chrome recedes so the output shines. Evaluate how well the interface disappears in favor of what it produces.
  - *Any app*: The universal baseline — is there one detail that clearly took extra effort? Does the app look *intentional* rather than defaulted? Is spacing consistent enough that nothing feels accidental?

#### §E8. PRODUCT AESTHETICS — DERIVED FROM AXIS PROFILE

> **This section is driven entirely by the Five-Axis profile from §I.4.** There are no fixed branches for "paid" vs "free" — instead, each question activates based on which axes are present. Run every tagged item whose axis is active in the profile; skip or substantially reframe items whose axis is inactive.

---

**`[A1]` COMMERCIAL INTENT ACTIVE** *(revenue-generating or institutional)*:
- **The first-impression credibility test**: Before the user reads a single word — does the composition signal "trusted tool" or "rough prototype"? List the 3 visual elements most undermining this credibility and the specific change that would fix each.
- **Visual trust hierarchy**: Does the palette feel stable and intentional? Does the typography feel appropriate for the domain? Does spacing feel designed or accidental? Trust is communicated visually before it is read.
- **Competitive visual benchmark**: Name the 2–3 most credible tools in this category. Compared to them — what does this app do better, at parity, or worse, in craft specifics?
- **Conversion or commitment blockers**: In any paid, sign-up, or institutional commitment flow — identify visual elements that undermine the user's confidence: unclear primary action, visual hierarchy that buries the CTA, absence of legitimacy signals.
- **Distribution channel fit** `[A1]`: What first-impression surface matters most for this app — App Store screenshot, sales demo, marketing page, Product Hunt listing? Is the visual design compelling in that specific context?

---

**`[A2]` USE CONTEXT: FOCUS-CRITICAL OR HIGH-STAKES**:
- **Cognitive load audit**: Identify every visual element that demands attention beyond what the user's task requires. Decorative elements, animations, color variety, complex backgrounds — each one is a cost. List everything that should be eliminated or minimized.
- **Information scannability**: Under time pressure or stress, can the user find the critical number, status, or action within 2 seconds? Is the most important information visually dominant?
- **Visual noise inventory**: List every element that could be removed, reduced, or quieted without losing functional information. In high-stakes contexts, visual noise is not a minor polish issue — it is a functional failure.

**`[A2]` USE CONTEXT: EMOTIONALLY SENSITIVE**:
- **Safety signals**: Does the visual design feel safe? Assess: corner radius (sharp corners feel clinical), color temperature (cold blues feel institutional), spacing (cramped layouts feel anxious), animation speed (fast transitions feel jarring). Identify the 2–3 specific changes that most increase felt safety.
- **Warmth calibration**: Is the palette warm enough for this emotional context without feeling saccharine? Is the typography gentle without being unreadable? Does the empty state feel welcoming or clinical?
- **Tone-design coherence**: Does the visual language match the emotional register the copy is attempting? A warm, reassuring message delivered inside a harsh, clinical layout creates dissonance.

**`[A2]` USE CONTEXT: CREATIVE OR EXPRESSIVE**:
- **Inspiration quality**: Does the interface itself feel inspiring, or purely functional? In a creative tool, the environment shapes the output — a beautiful, expressive interface puts users in a creative mindset.
- **Expressive range**: Is there room in the visual design for personality and surprise? Or is everything so controlled that the app feels sterile?
- **Chrome vs canvas**: How much visual space does the interface take from the user's creative work? Is the UI chrome earning its space?

**`[A2]` USE CONTEXT: LEISURE OR CASUAL**:
- **Delight calibration**: For a leisure tool, friction that would be acceptable in a professional context is not acceptable here. Is the experience genuinely pleasurable? Is there any moment of unexpected delight?
- **Low-stakes visual permission**: Leisure contexts allow more visual personality, playfulness, and even imperfection — provided it is intentional. Assess whether the current design uses this freedom, or applies professional-tool austerity where it isn't needed.

---

**`[A3]` AUDIENCE: EXPERT / PRACTITIONER**:
- **Density as respect**: Information density is a feature for expert users, not a flaw. Assess whether the current density level respects the expertise of the audience or talks down to them with excessive whitespace and simplified presentation.
- **Vocabulary accuracy**: Every label, stat name, unit, and domain term is a trust signal. One wrong term signals that the maker doesn't understand the domain. Audit every piece of domain vocabulary for precision.
- **Power-user surface area**: Are advanced capabilities accessible without being buried? Expert users should be able to do in 2 clicks what a novice does in 5 steps.

**`[A3]` AUDIENCE: MIXED OR BRIDGING**:
- **Progressive disclosure integrity**: The design must serve both expert and novice simultaneously. Is the complexity ladder clearly implemented — default view for novices, accessible depth for experts — without condescending to one or overwhelming the other?
- **Dual-register visual design**: Assess whether the visual design has a successful strategy for serving two different expertise levels. If it doesn't — this is a structural design problem that visual polish cannot fix.

---

**`[A4]` SUBJECT HAS STRONG VISUAL IDENTITY**:
- **Palette coherence**: Identify the dominant visual tones associated with the subject and assess whether the app's palette is *inspired by*, neutral to, or in conflict with them. Give a specific, actionable palette direction — not just "make it darker" but the specific character shift that would increase coherence.
- **Typographic tone**: Does the typeface feel tonally coherent with the subject? Identify a tonal mismatch if it exists and name a specific alternative.
- **Motion character**: Does the animation vocabulary honor the subject's energy, weight, and atmosphere? Name the specific adjustment that would increase alignment.
- **Iconography and visual register**: Do any custom icons or decorative elements feel consistent with the subject's visual language? Generic stock illustrations feel detached from a subject with a strong identity.

**`[A4]` COMMUNITY AESTHETIC NORMS EXIST**:
- **Insider signal audit**: What visual choices communicate that the maker is genuinely part of this community — familiar with its vocabulary, conventions, and tastes? What choices inadvertently signal an outsider? List both.
- **Anti-corporate check**: Does the visual design feel like it belongs to the community, or like it's trying to productize the community? Flag any design choices that feel like a startup trying to monetize a subculture — regardless of whether the product is actually paid.

**`[A4]` SUBJECT IS NEUTRAL / NO ESTABLISHED IDENTITY**:
- **Invented coherence**: With no subject identity to reference, the visual language must be invented entirely from within. Is there a coherent internal logic — a design concept or metaphor running through the product? If not, identify the strongest available candidate.

---

**`[A5]` AESTHETIC IS THE VALUE**:
- **Chrome restraint**: The UI interface around the product's output must recede as much as possible. Every pixel of interface competes with the product's own aesthetic output. Identify every non-essential UI element and recommend minimum-footprint alternatives.
- **Output quality assessment**: The visual quality of what the product *produces* — not just the container — must be assessed as a design output. Is it beautiful? Is it surprising? Does it feel like the tool is a creative collaborator?
- **Signature output quality**: Can a user immediately tell this output came from this tool? Is the output aesthetically distinctive?

**`[A5]` AESTHETIC MUST STAY INVISIBLE**:
- **Distraction inventory**: Every element that draws attention to itself is a failure. List every visual element that is "nice" but competes with the user's task — and recommend eliminating or reducing each.
- **Trust-through-clarity**: In invisible-aesthetic contexts, trust comes entirely from clarity and reliability, not from polish. Is every element present because it is functionally necessary? Are there any decorative elements that should be removed entirely?

---

**UNIVERSAL** *(always apply)*:
- **The "made with intent" test**: Does the app look like every visual decision was made deliberately — or like some things were shipped at their default? Identify the 3 visual elements most clearly signaling unintentional defaulting, and the specific changes that would make them look chosen.
- **App icon / favicon quality**: Legible at 16×16 and all required sizes? Visually coherent with the app's design language? Distinct enough to be identified in a browser tab or home screen?
- **Visual coherence across sections**: Does the app feel designed as a whole? Would a user recognize a new screen as part of the same product?

#### §E9. VISUAL IDENTITY & RECOGNIZABILITY

> Identity means different things depending on the axis profile. For commercial products it is competitive differentiation. For community products it is subject fidelity. For creative products it is the distinctiveness of the output itself. Apply the questions whose axis is active.

- **Visual signature** `[A1][A4][A5]`: Can a user identify this app from a partial screenshot — a fragment of color, a component shape, a motion pattern, or the visual character of its output? Identify what could become a distinctive visual signature, or what already is one.
  - *Commercial (A1)*: Is the signature distinctive within the product category, or generic among competitors?
  - *Subject identity (A4)*: Does the signature feel like it belongs to the subject — or does it feel imported from a different visual world?
  - *Aesthetic-primary (A5)*: Is the output itself visually distinctive? Could the user recognize output from this tool versus a competing one?
- **Visual metaphor coherence** *(all)*: Is there a consistent design concept or visual logic running through the product — a coherent internal language? If one exists, is it consistent throughout? If none exists, what is the strongest candidate based on the subject, audience, and use context?
- **Accent color intentionality** `[A1][A4]`: Is the accent color purposeful — a calibrated hue with intentional saturation, not the first pick from a wheel? *Commercial*: Is it distinctive within the competitive landscape? *Subject identity*: Does it feel tonally connected to the subject?
- **Emotional arc design** *(all)*: Does the visual language guide users through the right emotional journey for this specific app and audience? Map the intended emotional arc (e.g., focus → confidence → satisfaction for a work tool; curiosity → discovery → delight for an exploratory tool; calm → trust → relief for a sensitive-context tool), then assess whether the visual transitions, state changes, and feedback moments support it.
- **Anti-genericness audit** *(all)*: Identify visual elements that make the app look interchangeable with a dozen others — same default palette, same component style, same layout conventions with no adaptation to the subject or audience. For each: what is the specific, minimal change that would make this element more distinctly *this* app?
- **App icon / launcher icon quality**: The app icon is the single most-seen brand element — appearing on the home screen, recent apps, app stores, and notifications. Is it legible at every size (16×16 notification, 48×48 launcher, 512×512 store listing)? Visually coherent with the app's internal design language? Distinct enough to pick out on a crowded home screen? On Android: does the adaptive icon have properly designed foreground and background layers? Does the icon work on all launcher shapes (circle, squircle, rounded square)?
- **Motion identity**: Does the app have a recognizable animation vocabulary — a consistent timing, easing, and direction language that feels like *this* app? A spring-physics app has a different identity than a snappy-instant app. If all animations use the same generic 300ms ease-in-out, there is no motion identity. Identify the motion character and whether it's consistent or accidental.
- **Iconography as identity signal**: Beyond technical consistency (§E5) — do the icons communicate the brand's personality? A clinical precision tool with rounded bubbly icons has a split personality. The icon family's weight, corner treatment, and fill style are identity decisions, not just aesthetic ones.
- **Color system as memory**: Is the palette memorable? After using the app for 10 minutes and closing it, could a user describe its color character? A palette that is merely "correct" (good contrast, harmonious) but unmemorable is a missed identity opportunity. The accent color, the surface temperature, the dark mode character — each is a memory anchor.
- **Brand scalability**: Does the visual identity work at all scales — icon size, watch complication, widget, tablet, splash screen, notification, share card? An identity that only works at phone-screen scale is fragile. Test: does the brand's key visual element (color, shape, motif) survive at 32×32px?

#### §E10. DATA STORYTELLING & VISUAL COMMUNICATION

> Numbers and data are not just displayed — they are communicated. This section evaluates whether the app's visual language helps users understand, not just see.

- **Numbers as visual elements**: Are the most important metrics in the app displayed with visual weight proportional to their importance? A key output number in the same size and weight as a label fails visual information design. Identify every key number and whether its typographic treatment matches its significance.
- **Hierarchy of insight**: For data-forward apps — is there a visual path from "raw input" → "computed result" → "actionable insight"? Or does the user have to parse a flat grid of equal-weight numbers to find the answer to their question?
- **Chart design quality**: Every chart should answer a specific question. For each chart: state the question it is designed to answer — then assess whether the visual encoding (chart type, scale, color, label placement) answers that question as directly as possible. Common failures: pie charts for comparing more than 4 values, line charts for categorical data, bar charts where a table would communicate more precisely.
- **Progressive complexity revelation**: Does the design guide users from simple overview → detailed drill-down → power-user controls? Or does it present full complexity immediately? The visual design should embody progressive disclosure — not just the UX architecture.
- **Data density calibration**: Assess whether the information density is calibrated for the target audience. A tool for analysts can be dense; a tool for casual users must be generous with whitespace and explanation. Is the current density right? What is the cost to the app's usability of the current density choice?
- **Empty → populated visual storytelling**: The transition from empty state to populated state is one of the most important visual moments in the product. Does populating data feel like the app coming alive, or does it feel like a spreadsheet being filled in? Identify the specific visual improvements — animation, color, layout shift — that would make this transition feel more meaningful.
- **Error as communication**: Error states should communicate clearly, not just signal failure. Does the visual design of error states match their urgency? A critical error and a mild warning should look visually distinct. Are error states designed with the same craft as the default states?
- **Colorblind-safe data encoding**: If data is differentiated by color (chart series, status indicators, category badges), is the encoding also distinguishable without color? Use shape, pattern, label, position, or icon as secondary encoding. Test: view every data visualization in grayscale — can each category still be distinguished?
- **Data table design quality**: Tables are data visualization too. Assess: header row visually distinct (background, weight, sticky position)? Rows alternating or separated clearly? Number columns right-aligned with `tabular-nums`? Sort indicators designed (not default arrows)? Long text truncated with tooltip? Row hover state designed? Empty cells handled (dash, "—", or explicitly styled "N/A")?
- **Responsive data display**: When charts, tables, or data-dense views are compressed to mobile — do they remain useful? Common failures: chart labels overlapping, axis labels rotated to illegibility, table columns truncated beyond recognition, data cards that collapse into a wall of text. Every data view needs a mobile-specific layout strategy — not just the same view squeezed smaller.
- **Number formatting as visual design**: Beyond accuracy (§J1) — is number formatting serving the visual hierarchy? Key metrics should use the app's display typography. File sizes, dates, and counts should use the app's secondary typography. Consistent thousands separators, decimal places, and unit labels across all screens. On Android: use `NumberFormat` with appropriate locale, and ensure numbers use `tabular-nums` equivalent (`android:fontFeatureSettings="tnum"`) for alignment.
- **Real-time data visual treatment**: For data that updates live (download progress, file scan counts, storage usage) — is the update animation smooth or does it jump/flash? Counters should animate between values, not snap. Progress bars should have smooth transitions. Timestamps like "3 minutes ago" should update without the entire view rebinding.

#### §E11. MOBILE-SPECIFIC VISUAL QUALITY

> Mobile platforms have unique visual concerns that web-centric audits miss entirely. This section covers Android and iOS-specific visual quality checks.

**System Integration:**
- **Material You / Dynamic Color**: On Android 12+, does the app support Material You dynamic color theming? If yes — do all custom colors harmonize with the user's wallpaper-derived palette? If no — is the static palette still high quality? Dynamic color is a free polish upgrade on Android.
- **Dark mode completeness**: Switch to dark mode and audit EVERY screen. Common failures: hardcoded white backgrounds on individual views, hardcoded text colors that become invisible, images with white backgrounds that don't adapt, splash screen still light, WebView content still light, third-party views not themed.
- **System font scaling**: Increase system font size to maximum. Does the layout survive? Text overflow, truncation, overlapping elements, buttons that can't fit their label — all are failures. Use `sp` for text sizes, and ensure layouts use `wrap_content` or constrain properly. This is not just accessibility — many users run slightly larger text.
- **Display cutout handling**: Does the app handle notches, camera holes, and display cutouts correctly? Content should not be hidden behind cutouts. Use `WindowInsetsCompat` for proper safe area handling.
- **Splash screen quality**: Android 12+ uses the Splash Screen API. Is the splash screen designed (themed icon, correct background color, proper branding moment) or default? The splash screen is the app's first visual impression — a white flash or mismatched color is a craft failure.

**Visual Fidelity:**
- **Screen density handling**: Are all raster assets provided at appropriate densities (mdpi, hdpi, xhdpi, xxhdpi, xxxhdpi)? Missing density variants cause blurry upscaling or wasteful downscaling. Prefer vector drawables (`VectorDrawable`) for icons — they scale perfectly at all densities.
- **Ripple effect consistency**: Material Design ripple effects on all touchable surfaces? Ripple bounded correctly (not spilling outside card edges, not clipped incorrectly)? Ripple color appropriate for the surface (light ripple on dark surfaces, dark ripple on light surfaces)?
- **Elevation and shadow consistency**: On Android, elevation creates shadows. Are shadows consistent across similar component types? Do elevated surfaces in dark mode use surface tint instead of visible shadows? Are there any z-fighting issues where overlapping elevated surfaces produce visual artifacts?
- **Animation performance**: Do animations run at 60fps (or 120fps on high-refresh devices)? Jank during fragment transitions, RecyclerView item animations, or bottom sheet expansion is a visual quality failure. Check for: layout passes during animation, overdraw, alpha animation on complex views.
- **Overscroll effect**: The edge glow (pre-Android 12) or stretch overscroll (Android 12+) — does it feel natural? Is the color appropriate? On iOS, the bounce effect should feel physics-based and smooth.
- **Font rendering quality**: On Android, check that custom fonts are loaded correctly via `ResourcesCompat.getFont()` or XML font resources. Font weight interpolation working? No fallback to system default causing visual inconsistency? Variable font axes properly configured?
- **RTL (Right-to-Left) visual quality**: If supporting RTL languages — are all layouts mirrored correctly? Icons that have directional meaning (arrows, back buttons) flipped? Padding and margins mirrored? Text alignment correct? This is not just a layout check — it's a visual quality check for half the world's languages.

**Platform Convention Fidelity:**
- **Navigation pattern correctness**: Android uses bottom navigation + back button. iOS uses tab bar + swipe-back. Are the platform-correct patterns used, or is an iOS pattern forced onto Android (or vice versa)? Platform convention violations feel foreign to users.
- **System dialog integration**: When the app invokes system dialogs (file picker, permission dialog, share sheet) — does the visual transition feel smooth? Is the app's theme compatible with the system dialog's appearance?
- **Keyboard interaction visual quality**: When the keyboard appears — does the layout resize smoothly or jump? Is the focused field scrolled into view? Does the keyboard's color scheme (light/dark) match the app? Are input fields not hidden behind the keyboard?

---

### CATEGORY F — UX, Information Architecture & Copy

#### §F1. INFORMATION ARCHITECTURE
- **Navigation model**: Do tab/menu labels and icons match users' mental model of the content? Would a new user find what they're looking for?
- **Content hierarchy**: Most important information visually prominent? Clear visual path from "input" to "output" to "action"?
- **Progressive disclosure**: Advanced/infrequently-used options hidden behind expandable sections? Or are all options shown at once overwhelming the user?
- **Categorization logic**: Is content grouped in ways that feel natural to the target audience? Groups should reflect user mental models, not implementation structure.
- **Section depth**: Is the navigation hierarchy the right depth — not so flat that everything is at the same level, not so deep that users lose track of where they are?
- **Location awareness**: Does the user always know where they are? Breadcrumbs, highlighted nav items, screen titles, back button context — every screen must answer "where am I?" instantly. On Android: is the toolbar title updating per fragment? Is the bottom nav item highlighted correctly?
- **Search UX**: If the app has search — is it discoverable (visible on primary screens, not buried)? Does it provide recent searches, suggestions, or filtered results? Does it handle empty results gracefully with actionable guidance? Is search scope clear (searching this screen vs entire app)?
- **Cross-linking between related content**: When the user is looking at item A, can they easily navigate to related items B and C? Missing cross-links force users to go "back, scroll, find, tap" when a direct link would save 4 steps.
- **Tab bar / bottom nav vs drawer**: Is the right navigation pattern chosen for the content volume and access frequency? Bottom nav for 3–5 high-frequency destinations. Drawer for 6+ low-frequency destinations. Tabs for same-level parallel content. Mixing patterns without clear hierarchy confuses users.
- **Navigation affordances**: Are interactive elements visually distinguishable from static content? Clickable items must signal clickability — via chevrons, underlines, color, elevation, or cursor changes. "Flat" clickable items that look identical to text are discoverability failures.
- **Cognitive load per screen**: How many distinct decisions or information items are on each screen? Screens with > 7 distinct items need grouping, progressive disclosure, or simplification. Count the decisions a user must make on each primary screen.
- **Dead zones**: Are there screens or sections a user can navigate to but cannot navigate out of easily? Every screen must have a clear path back AND a clear path forward.
- **Feature discoverability over time**: Are there powerful features that a new user wouldn't find for weeks? Long-press actions, swipe gestures, hidden menus, advanced filters — each one needs a discovery mechanism (contextual hints, onboarding tips, "did you know" prompts after N sessions).

#### §F2. USER FLOW QUALITY
- **Friction audit**: For each workflow in §0 — count the steps. Are any steps unnecessary, confusable, or surprising? Every unnecessary step is a design failure.
- **Default value quality**: Are default values the most common/sensible choice? Good defaults dramatically reduce user effort.
- **Action reversibility**: Can users undo or go back from every action? Irreversible actions are acceptable if the user is clearly warned with enough context to make an informed decision.
- **Confirmation dialog quality**: Destructive confirmations tell the user specifically what will be destroyed and whether it is recoverable — not just "Are you sure?". The confirmation must name the item ("Delete 'vacation_photos.zip'?"), state the consequence ("This cannot be undone"), and offer an alternative when possible ("Move to trash instead?").
- **Feedback immediacy**: Does every action produce immediate visual feedback? Clicks that feel unresponsive damage trust. Even a 100ms delay without any visual change makes users doubt the tap registered.
- **Perceived performance**: During recomputation — does the UI show stale data, blank space, or a skeleton? Which is chosen, and is it the right choice?
- **Keyboard shortcuts**: For power users — are common actions keyboard-accessible? Are shortcuts discoverable (tooltip mentions it)?
- **Multi-step workflow state preservation**: If a user is mid-way through a multi-step flow (e.g., batch rename, file conversion, cloud setup) and the app is backgrounded, rotated, or interrupted by a phone call — is their progress preserved? On Android: does `onSaveInstanceState` cover the flow state? Does the ViewModel survive config changes?
- **Error recovery flows**: When something fails mid-flow — can the user retry from the failure point, or must they restart from scratch? A file transfer that fails at 80% should offer "retry remaining" not "start over." Every error state needs a recovery path that preserves user work.
- **Interruption handling**: What happens when the user leaves mid-action and returns? Draft state for forms? Pause state for operations? Or silent data loss? Map every interruptible flow and verify the resume behavior.
- **Deep link entry points**: Can users enter the app at any screen (via notification, share intent, shortcut)? Does each entry point provide enough context, or does the user arrive disoriented? Every deep-linked screen must work standalone — not just as part of a navigation sequence.
- **Gesture navigation conflicts**: On Android 10+, system gesture navigation (back swipe from edge) conflicts with app drawer swipes and edge-based gestures. On iOS, swipe-back conflicts with horizontal content. Map every conflict and verify the resolution.
- **Batch operation UX**: When performing actions on multiple items — is the selection model clear (checkboxes, long-press-to-select, select all)? Is the count of selected items visible? Can the user preview what will happen before confirming? Is there progress feedback during batch operations?
- **Contextual actions**: Are the right actions available at the right time? A file that's selected should show file-relevant actions. A folder should show folder-relevant actions. Actions that don't apply to the current context should be hidden or disabled with explanation — never shown and silently failing.
- **Back navigation predictability**: Does the back button always do what the user expects? After a deep navigation chain, does back retrace the path or jump to an unexpected screen? After completing a flow (e.g., file conversion), does back go to the result or the starting screen? Back behavior must be predictable and consistent.

#### §F3. ONBOARDING & FIRST USE
- **First impression**: On the very first visit, does the user understand what the app does and what to do first? Without tooltips or documentation?
- **Onboarding quality**: Does the onboarding teach by doing (interactive) or just describe (passive)? Interactive is more effective.
- **Onboarding re-entry**: Can users replay the onboarding? Can they access help at any time?
- **Empty state → filled state**: The transition from "no data" to "data present" — is it visually satisfying? Does it feel like the app is gaining value?
- **Progressive complexity**: Does the app reveal complexity incrementally, or does it present everything at once?
- **Activation path clarity** `[A1][A2][A3]`: Is the visual hierarchy guiding the user toward their first meaningful interaction? What "meaningful" means depends on the axes:
  - *High commercial intent (A1)*: The path to first value must be visually direct — identify any elements that distract from or delay the activation moment.
  - *Expert audience (A3)*: Experts should reach their first productive action faster than novices, not be forced through the same beginner scaffolding.
  - *Casual / emotionally sensitive audiences (A3/A2)*: Is the function obvious without reading anything? Is the first step gentle enough not to intimidate?
- **First success moment design** `[A2][A4]`: The moment the user first achieves something meaningful is the highest-value moment in the product. Is it visually acknowledged? The right acknowledgment depends on context:
  - *Focus-critical tools*: A quiet, efficient confirmation — not celebration, just closure.
  - *Creative / leisure tools*: A moment of genuine visual satisfaction — the result feels like an output worth having.
  - *Community / subject tools*: The result presented in a way that resonates with how the community experiences the subject — using the right vocabulary, the right visual weight.
  - *Emotional / sensitive tools*: A warm, gentle affirmation — not enthusiasm, just reassurance.
- **Time-to-function legibility** *(all)*: Can a new user tell within 10 seconds what they will be able to do? This is a visual clarity question — the app's core function should be visually legible, not just textually stated.
- **Permission request UX**: Permission dialogs are trust gates. Are permissions requested in context (when the user tries to use the feature that needs it) or upfront in a wall of permission dialogs? Context-triggered permission requests have dramatically higher acceptance rates. Is the reason explained BEFORE the system dialog? ("We need storage access to scan your files" → then system dialog). Denied permissions — does the app degrade gracefully with a clear explanation of what's lost, or does it break silently?
- **Tutorial skippability**: Can the user skip the onboarding entirely and figure things out by doing? Forced tutorials that block the primary experience are a friction source. The best onboarding is no onboarding — the UI is self-explanatory.
- **Re-engagement after absence**: A user who returns after 2 weeks — do they land on a useful screen or a stale empty state? Is there a "welcome back" moment that orients them? Is their last context preserved (last folder browsed, last tab open)?
- **Contextual help and tooltips**: Beyond onboarding — are there in-context hints for complex features? Tooltips on icons, "?" buttons near advanced settings, info icons that explain jargon? Help should be available where the question arises, not in a separate FAQ screen.
- **Feature discovery over time**: New features should surface gradually, not all at once. First session: core features only. After 3 sessions: introduce power features. After 10 sessions: surface advanced/hidden capabilities. Drip-feed discovery prevents overwhelm and creates ongoing delight.
- **Settings discoverability**: Can users find how to change their preferences? Is the settings screen organized logically (grouped by function, not by implementation)? Are the most-changed settings near the top? Do settings explain their effect before the user changes them?

#### §F4. COPY QUALITY
- **Tone consistency**: Does every piece of UI copy feel like it came from the same voice? List any copy that sounds notably different from the rest.
- **Clarity**: Every label, tooltip, placeholder, error message, and heading — is it unambiguous? Could a user unfamiliar with the domain understand it?
- **Conciseness**: UI copy should be as short as possible while remaining clear. List every piece of copy that could be tightened.
- **Terminology consistency**: The same concept always called the same thing. List every synonym pair or inconsistency.
- **Capitalization convention**: Title Case for navigation and headings, Sentence case for body text and labels — applied consistently?
- **Action verb quality**: Buttons should use strong, specific verbs: "Save draft" not "Submit", "Delete account" not "Confirm", "Import history" not "OK".
- **Empty state copy**: Empty states have a clear, helpful, action-oriented message — not blank or just "No data found."
- **Error message copy**: Human-readable, no jargon, explains the cause, explains what to do next.
- **Copy as commitment asset** `[A1]`: *Activate only for revenue-generating or institutional products.* In any paid, sign-up, or commitment flow — copy is conversion infrastructure. Does the CTA communicate value ("Start building") or just request action ("Sign up")? Does the copy build confidence or just inform? For each commitment-adjacent CTA, suggest a more compelling alternative.
- **Copy as domain fluency signal** `[A3][A4]`: *Activate when the audience has domain expertise or the subject has community vocabulary.* Does the copy use the community's or domain's natural vocabulary accurately — the terms, shorthand, and framing that practitioners and enthusiasts actually use? Copy that describes the subject the way a press release would, or uses domain terms loosely, signals distance from the audience. List any copy that feels written by an outsider and suggest alternatives that feel more native.
- **Copy as emotional register** `[A2]`: *Activate for emotionally sensitive, creative, or high-stakes contexts.* Does the copy's tone match the emotional context of use? Clinical language in a wellness tool, playful language in a high-stakes professional tool, bureaucratic language in a creative tool — all represent tone-design mismatches. Identify any copy that is tonally wrong for the use context.
- **Brand voice extraction** *(all)*: Based on the copy that exists, extract a 3-adjective voice descriptor. Then identify every piece of copy that violates this voice — too formal, too casual, too generic, or out of register for this app's axis profile.

#### §F5. MICRO-INTERACTION QUALITY
- **Hover states communicate intent**: Every interactive element has a hover state that feels intentional (cursor change, color shift, underline, elevation change). On mobile: the hover equivalent is ripple/highlight on touch.
- **Loading states**: Async operations have immediate feedback — even a short 200ms delay without feedback feels broken. A scan that takes 10 seconds needs a progress indicator. A file delete that takes 500ms needs an optimistic removal with undo.
- **Success confirmation**: Successful actions are confirmed visually — save, copy, export, submit all acknowledge completion. The confirmation must match the weight of the action: a settings toggle gets a subtle checkmark; a file deletion gets a snackbar with undo; a major operation gets a dedicated success state.
- **Scroll behavior**: Scroll-to-content after navigation? Scroll position preserved on back navigation? Smooth scrolling where appropriate? RecyclerView scroll position restored after returning from detail screen?
- **Focus indicator quality**: Visible and styled to match the app's design language — not just the browser default blue rectangle (unless the design is minimal).
- **Pull-to-refresh**: If implemented — does it have a threshold that feels natural (not triggering on casual scrolls)? Does the refresh indicator match the app's design? Does it show meaningful feedback ("Scanning..." not just a generic spinner)? Is it available on every list screen where the user expects it?
- **Swipe gesture feedback**: Swipe-to-delete, swipe-to-archive, swipe-to-reveal — does the gesture preview the action before committing? (Show the delete icon/color as the user drags.) Is the threshold for committing the action clear? Does the animation complete satisfyingly? Is there an undo path?
- **Long-press interactions**: If long-press enters selection mode — is the transition clear (visual mode change, selection count, contextual toolbar)? Does the first long-press provide haptic feedback? Can the user exit selection mode easily (clear button, back press)?
- **Drag-and-drop UX**: If supported — does the dragged item have a distinct visual state (elevated, semi-transparent)? Is the drop target highlighted? Does the placeholder show where the item will land? What happens on invalid drops (smooth return to origin, not a jarring snap)?
- **Haptic feedback**: On Android/iOS — are haptic responses used for meaningful moments (selection, toggle, delete, completion)? Haptics that match the visual action reinforce the interaction. Over-use of haptics creates noise. Under-use misses a free quality signal. Map every interaction that should have haptic feedback and verify it exists.
- **Selection feedback**: In multi-select modes — is the selection state of each item unambiguous? Checkmarks, color change, elevation change — the selected state must be instantly distinguishable from the unselected state. Is the selection count visible? Is "select all" available?
- **Animation interruption**: If the user taps a button while an animation is playing — does the animation complete, cancel, or get interrupted gracefully? Animations that block input feel broken. Animations that get cut mid-way feel janky. The right behavior: new input cancels the current animation and immediately responds.
- **Gesture cancellation**: If the user starts a swipe/drag but changes their mind — does the gesture cancel cleanly when they lift their finger in the original position? Or does it commit the action accidentally?
- **Empty state interaction**: Empty states are not static dead ends — they are interaction opportunities. Every empty state should have a primary action (create, import, scan) that is tappable and prominent, not just decorative text.
- **Error state interaction**: Error states must have actionable recovery — retry buttons, alternative paths, contact options. An error with no action is a dead end. The error state should be as interactive as the success state.
- **Toast/snackbar interaction**: Toasts with actions (Undo, Retry, View) — is the action tap target large enough? Does the toast stay long enough for the user to read and decide? Does it dismiss on the right edge (action completed OR timeout, not both simultaneously)?

#### §F6. ENGAGEMENT, DELIGHT & EMOTIONAL DESIGN

> The goal of this section is derived entirely from the axis profile. "Engagement" means radically different things depending on whether the app is a high-frequency work tool, an emotionally sensitive companion, a community gift, or a creative instrument. Apply the questions whose axis is active.

**UNIVERSAL** *(all apps)*:
- **Reward moments**: When the user achieves something meaningful — does the UI visually acknowledge it? Even a brief, quiet confirmation transforms a functional interaction into a satisfying one. List every "achievement moment" in the core workflow and assess whether it has any visual acknowledgment. The *form* of that acknowledgment should match the axis profile — not all success moments should be celebrations.
- **Personality moments**: Are there interactions that reveal the app's character — an empty state with genuine voice, a micro-animation that feels considered, a transition that feels right? These are what users remember and describe to others. Identify 2–3 places where a personality moment would feel authentic to this app's axis profile.
- **Notification quality**: Any notification, badge, or alert indicator — designed with the same craft as the rest of the product? Unstyled browser alerts break the contract regardless of product nature.

**`[A1]` COMMERCIAL INTENT**:
- **Progress and investment visibility**: Can users see how far they've come, how much they've built? Progress signals create retention pull. Does the app leverage this without resorting to manipulative patterns?
- **Shareable outcomes**: Would a user want to share something they produced or achieved in this app? What is the most naturally shareable moment, and is it visually compelling enough to share?

**`[A2]` EMOTIONAL SENSITIVITY**:
- **Emotional safety in transitions**: Every state change — loading, error, empty, success — should feel emotionally appropriate to someone in a vulnerable state. Is there any moment that feels jarring, cold, or clinical where warmth was needed?
- **Absence of pressure patterns**: Are there any visual elements that create urgency, scarcity, or anxiety — even unintentionally? Countdown timers, red badges, aggressive empty states — all create pressure that is inappropriate in emotionally sensitive contexts.
- **"Feels like support" quality**: Does the app feel like it is on the user's side? What specific visual or copy choices most contribute to — or detract from — this feeling?

**`[A2]` CREATIVE / EXPLORATORY CONTEXTS**:
- **Discovery encouragement**: Does the interface visually invite exploration — or does it present a flat list of functions? Are there visual cues that suggest "there is more here to discover"?
- **Creative momentum**: Does the visual design maintain creative flow — or does it interrupt it with friction, confirmations, or loading states that break the user's concentration?

**`[A3][A4]` COMMUNITY / SUBJECT CONTEXTS**:
- **Community shareable moments**: Is there a moment in the app compelling enough that a user would screenshot it and share it in their community's space — a forum, a Discord, a social feed? Identify that moment and assess its visual quality for sharing.
- **Authentic delight**: Are there details that reward genuine familiarity with the subject — a label using community shorthand, a display that reflects how insiders think about the subject, a detail in an empty state that speaks directly to this audience's experience? These signals are disproportionately valuable for establishing insider credibility.
- **Integrity over manipulation**: Retention mechanics (streaks, FOMO, aggressive notifications) are tonally wrong when the app is a community gift or free tool — and often wrong even for paid tools. Flag any patterns that prioritize the product's engagement metrics over the user's actual experience.

---

### CATEGORY G — Accessibility

> This section covers accessibility for all platforms. Web uses WCAG/ARIA. Android uses TalkBack/contentDescription. iOS uses VoiceOver/accessibilityLabel. The principles are universal — the implementation differs.

#### §G1. ACCESSIBILITY COMPLIANCE

> **Platform mapping**: Web WCAG → Android Accessibility Scanner → iOS Accessibility Inspector. All three platforms share the same goal: perceivable, operable, understandable, robust. The implementation APIs differ.

**Perceivable:**
- **Images and icons**: Every meaningful image has a description. Decorative images are hidden from assistive technology.
  - *Web*: `alt` text on `<img>`; decorative = `alt=""` or `role="presentation"`.
  - *Android*: `android:contentDescription` on `ImageView`; decorative = `android:importantForAccessibility="no"`.
  - *iOS*: `accessibilityLabel` on `UIImageView`; decorative = `isAccessibilityElement = false`.
- **Semantic structure**: UI elements use semantically correct components, not generic containers styled to look interactive.
  - *Web*: `<button>`, `<nav>`, `<main>`, `<header>`, `<h1>–<h6>`, `<label>` — not `<div>` for everything.
  - *Android*: `Button`, `Toolbar`, `NavigationView` — not `TextView` with `onClickListener` (no accessibility role). Custom views must set `AccessibilityNodeInfoCompat` roles.
  - *iOS*: `UIButton`, `UINavigationBar` — not `UILabel` with `tapGestureRecognizer`. Custom views must set `accessibilityTraits`.
- **Reading order**: Assistive technology traversal order matches visual layout order.
  - *Web*: DOM order matches visual order.
  - *Android*: `android:accessibilityTraversalBefore`/`After` for custom ordering; default is layout tree order. `ConstraintLayout` children may need explicit traversal ordering if visual order differs from XML order.
  - *iOS*: `accessibilityElements` array for custom ordering.
- **Sensory-only instructions**: No instruction relies solely on color, shape, or position ("tap the red button", "the icon on the left"). Always pair with text.
- **Color independence**: Status, error, success conveyed by icon + text + color — never color alone. Test: would a colorblind user understand every state?
- **Text contrast**: All text meets 4.5:1 contrast (normal) or 3:1 (large/bold ≥18sp or ≥14sp bold). Pay special attention to: muted text on dark surfaces, colored text on colored backgrounds, placeholder text in inputs, disabled state text.
- **Text scaling**: App survives system font size at maximum (200% on web, largest setting on Android/iOS). No text truncation that hides critical information, no overlapping elements, no buttons that can't fit their labels. On Android: use `sp` for text sizes, test with Settings > Display > Font Size at maximum.
- **Non-text contrast**: UI components (input borders, icon buttons, switches, sliders) meet 3:1 against adjacent backgrounds.
- **Touch target size**: Every tappable element ≥ 48×48dp (Android) or 44×44pt (iOS) or 44×44px (web). This includes: icon-only buttons, close buttons, overflow menu icons, checkbox/radio buttons, list item trailing actions. Measure the actual touch area, not just the visible element.

**Operable:**
- **Full navigability**: Every interactive element reachable via assistive technology (TalkBack swipe, VoiceOver swipe, Tab key). No orphaned interactive elements that can't receive focus.
- **No focus traps**: Focus never gets stuck in a component. Exceptions: modal dialogs intentionally trap focus and provide a dismiss action. On Android: TalkBack users can always navigate back via the system back gesture.
- **Logical focus order**: Focus traversal follows the visual reading order (left-to-right, top-to-bottom for LTR languages). Custom focus ordering only when the default order is genuinely wrong.
- **Visible focus indicator**: When navigating via keyboard or switch access, the focused element is clearly highlighted. Not just the platform default — styled to match the app's design language while meeting 3:1 contrast.
- **Focus not obscured**: Focused element not fully hidden by sticky headers, floating toolbars, or bottom sheets (WCAG 2.4.11). On Android: `CoordinatorLayout` with `AppBarLayout` can obscure focused content during TalkBack navigation — verify scroll-to-focus behavior.
- **Action labels**: Every button and interactive element has an accessible name that describes its action. Icon-only buttons must have labels.
  - *Web*: `aria-label` on icon buttons.
  - *Android*: `android:contentDescription` on `ImageButton`, or `app:tooltipText` on Material buttons.
  - *iOS*: `accessibilityLabel` on `UIButton`.
- **Custom gestures documented**: Swipe-to-delete, long-press, drag-and-drop — all have alternative accessible actions. On Android: custom actions via `AccessibilityNodeInfoCompat.addAction()`. On iOS: `accessibilityCustomActions`.

**Understandable:**
- **Language declared**: App language declared for screen readers. Web: `lang` on `<html>`. Android: locale set in `Configuration`. iOS: `accessibilityLanguage`.
- **Predictable behavior**: No unexpected context changes on focus or input. Navigation is predictable. Back button does what the user expects.
- **Error identification**: Input errors described in text near the field, not just color change. On Android: `TextInputLayout.setError()` properly announces the error via TalkBack. Error text must state what went wrong AND how to fix it.
- **Input labels**: Every input has a visible label or clear accessible name. On Android: `TextInputLayout` with `hint` provides both visual and accessible label. Standalone `EditText` must have `labelFor` association.
- **Form instructions**: Complex inputs (date formats, password requirements, character limits) provide instructions before the user attempts input, not just as error messages after.

**Robust:**
- **Custom component accessibility**: Non-standard interactive elements have correct roles and states.
  - *Web*: ARIA roles (`role="tab"`, `aria-selected`), states (`aria-expanded`), live regions (`aria-live`).
  - *Android*: `AccessibilityDelegateCompat` on custom views. `ViewCompat.setAccessibilityDelegate()` for role, state, and action overrides. `RecyclerView` items must announce position ("Item 3 of 15").
  - *iOS*: `accessibilityTraits`, `accessibilityValue`, `UIAccessibilityNotification` for dynamic updates.
- **Dynamic content announcements**: When content changes without user action (toasts, live counters, async results), the change is announced to screen readers.
  - *Web*: `aria-live="polite"` for informational, `aria-live="assertive"` for urgent.
  - *Android*: `announceForAccessibility()` for transient messages; `AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED` for structural changes. Snackbar announcements: verify TalkBack reads the snackbar content AND action.
  - *iOS*: `UIAccessibility.post(notification: .announcement, argument:)`.
- **State changes communicated**: Toggle states (on/off), expanded/collapsed states, selected states — all communicated to assistive technology, not just visual changes. On Android: `setChecked()`, `setSelected()`, `setExpanded()` on appropriate widgets, or custom `AccessibilityNodeInfo` state reporting.

#### §G2. SCREEN READER TRACE

> Simulate the primary user workflow using ONLY the screen reader — no visual reference. This is the real-world accessibility test.

**Android TalkBack trace:**
- Enable TalkBack on device/emulator. Navigate the primary workflow by swiping right through all elements.
- Does every screen announce its title when entered? (Check `Toolbar` title, or custom `announceForAccessibility` on fragment transitions.)
- Can the user complete the primary workflow end-to-end? Count the steps via TalkBack vs visual — more than 2× steps via TalkBack is a navigation efficiency problem.
- Dialog open: does TalkBack focus move to the dialog? Dialog close: does focus return to the trigger? Bottom sheet open: does focus move inside the sheet? Sheet dismiss: focus returns correctly?
- RecyclerView items: does each item announce meaningful content (not "FrameLayout, double-tap to activate")? Does it announce position ("Item 3 of 47")?
- Icon-only buttons: do they announce their action ("Navigate up", "More options", "Delete")? Not just "button" with no label.
- Custom components (seekbar, custom toggles, swipe-to-reveal): accessible? Do they announce their type, current value, and available actions?
- Tab navigation: does swiping through tabs announce "Tab 1 of 4, selected" or just "Tab"?

**iOS VoiceOver trace** *(if building for iOS)*:
- Same workflow trace as above, using VoiceOver swipe navigation.
- Does Rotor work correctly for headings, links, and form controls?
- Custom scroll views: does VoiceOver 3-finger scroll work?

**Web screen reader trace** *(if building for web)*:
- Test with NVDA (Windows) or VoiceOver (Mac) + Chrome/Safari.
- Heading hierarchy navigable via H key? Landmark regions navigable via D key?
- `aria-live` regions announcing at appropriate politeness levels?

#### §G3. KEYBOARD & SWITCH ACCESS

> This section covers keyboard navigation (web + desktop apps), switch access (Android/iOS), and external keyboard use on mobile.

- **Full keyboard operability**: Every interactive element reachable and operable via Tab/Enter/Space/Arrow keys. Test with an external Bluetooth keyboard connected to the mobile device — many Android users with motor impairments use this.
- **Focus traversal**: Tab order matches visual order. Custom `android:nextFocusDown`/`nextFocusRight` used only when the default order is wrong, not as a band-aid for layout issues.
- **Dialog focus management**: Modal dialog traps focus (Tab cycles within). Dialog close returns focus to trigger. On Android: `DialogFragment` handles this automatically if `setCancelable(true)`. Custom bottom sheets need explicit `importantForAccessibility` management.
- **Dismiss actions**: Back button/gesture closes dialogs, bottom sheets, drawers, dropdown menus. Consistent everywhere — no "stuck" states where back does nothing.
- **Custom component keyboard support**: Date pickers, color pickers, sliders, custom dropdowns — all operable via arrow keys and Enter/Space when focused via keyboard. On Android: override `onKeyDown`/`onKeyUp` for custom views.
- **Switch Access compatibility**: On Android, Switch Access allows users to navigate via one or two physical switches. Test: can the primary workflow be completed using Switch Access scanning? Are focusable elements grouped logically so scanning isn't painfully slow?

#### §G4. REDUCED MOTION & SENSORY ACCOMMODATIONS

- **Reduced motion respected**: When the user requests reduced motion, all non-essential animations are disabled or minimized. Essential state-communicating animations (loading indicators, progress bars) may continue but should be simplified.
  - *Web*: `prefers-reduced-motion: reduce` media query for CSS transitions, `@keyframes`, and JS-driven animations. Canvas/WebGL animations need explicit JS check.
  - *Android*: Check `Settings.Global.ANIMATOR_DURATION_SCALE` — when set to 0 by the user, ALL animations should respect this. `ViewCompat.animate()` respects this automatically; custom `ObjectAnimator` instances should check this value. Also respect `Settings.Global.TRANSITION_ANIMATION_SCALE`.
  - *iOS*: `UIAccessibility.isReduceMotionEnabled` — check and disable spring animations, parallax, auto-playing content.
- **Reduced transparency**: Some users request reduced transparency for readability. Frosted glass, blurred backgrounds, semi-transparent overlays — provide solid fallbacks.
  - *Android*: No system-level setting, but consider providing an in-app option for users who struggle with transparency effects.
  - *iOS*: `UIAccessibility.isReduceTransparencyEnabled`.
- **Bold text support**: On iOS, users can request bold text system-wide. Does the app's typography survive this without breaking layout?
- **Color inversion compatibility**: When the user enables color inversion (Android: Settings > Accessibility > Color inversion), does the app remain usable? Images and videos should be exempt from inversion. On Android: set `android:forceDarkAllowed="false"` on views that should not be inverted.
- **Haptic feedback as information**: If the app uses haptic feedback to communicate state (success vibration, error vibration), there must be a visual/audio equivalent for users who cannot perceive haptics.

#### §G5. ANDROID-SPECIFIC ACCESSIBILITY DEEP DIVE

> Android has platform-specific accessibility concerns that don't map to web WCAG. This section covers them.

- **ContentDescription audit**: Use `Grep` to find all `ImageView`, `ImageButton`, and `FloatingActionButton` elements. Every one must have `android:contentDescription` or `app:contentDescription` — or be explicitly marked `importantForAccessibility="no"` if decorative. Missing contentDescription is the #1 Android accessibility failure.
- **TalkBack navigation grouping**: Related elements should be grouped so TalkBack reads them as a single item, not as 5 separate swipes. On Android: set `android:focusable="true"` on the parent container and provide a merged `contentDescription`, or use `android:screenReaderFocusable="true"` (API 28+). Example: a list item with icon + title + subtitle should be ONE TalkBack stop, not three.
- **Live region announcements**: Views that update asynchronously (download progress, file count, scan status) must use `android:accessibilityLiveRegion="polite"` or `"assertive"` so TalkBack announces changes. Without this, TalkBack users don't know something changed unless they manually navigate to it.
- **Touch exploration**: With TalkBack enabled, the first tap selects/reads an element, the second tap activates it. Does every interaction work correctly with this double-tap model? Long-press actions need alternative accessible paths (accessibility custom actions or context menu).
- **Heading structure**: Use `android:accessibilityHeading="true"` (API 28+) on section titles so TalkBack users can navigate between headings, just as screen reader users navigate by H key on web. Without headings, TalkBack users must swipe through every element linearly.
- **Scrolling accessibility**: Can TalkBack users scroll through long lists? `RecyclerView` handles this, but custom scroll implementations may not announce "scrolled to item X" or provide scroll actions to TalkBack.
- **Permission dialog accessibility**: When runtime permissions are requested, is the rationale dialog accessible? Does TalkBack correctly announce the purpose of the permission before the system dialog appears?

---

### CATEGORY H — Platform Compatibility & Resilience

> This category covers platform-specific compatibility. For native Android/iOS apps, §H1 and §H2 are replaced by platform-specific equivalents. For web apps, the original web-focused checks apply. §H3 (Touch) and §H4 (Network) apply to all platforms.

#### §H1. PLATFORM COMPATIBILITY

**For web apps — Cross-Browser Matrix:**

Build this table for the specific APIs and features the app uses:

| Feature Used | Chrome | Safari/iOS | Firefox | Samsung | Edge | Fallback? |
|-------------|--------|------------|---------|---------|------|-----------|
| Blob Worker/SW | ✓ | ✗ | ✗ | ? | ✓ | Required |
| `crypto.randomUUID` | ✓ | 15.4+ | 92+ | ? | ✓ | Math.random fallback |
| `backdrop-filter` | ✓ | ✓`-webkit-` | 70+ | ? | ✓ | Graceful skip |
| `navigator.vibrate` | ✓ | ✗ | ✓ | ✓ | ✓ | No-op |
| View Transitions API | ✓ | ✗ | ✗ | ? | ✓ | Graceful skip |
| `dialog` element | ✓ | 15.4+ | 98+ | ? | ✓ | Polyfill |
| CSS container queries | ✓ | 16+ | 110+ | ? | ✓ | Media query fallback |
| CSS logical properties | ✓ | 15+ | 66+ | ? | ✓ | Physical property fallback |
| *[App-specific APIs]* | | | | | | |

For every ✗ or uncertain cell: does the app crash or degrade gracefully?

**Discovery strategy**: Use `Grep` to find API-specific calls (`navigator.`, `window.`, `CSS.`, `new IntersectionObserver`, `new ResizeObserver`, etc.) and check each against browser support tables.

**For Android apps — API Level Compatibility:**

| Feature / API | Min API | App's minSdk | Needs compat? | Current handling |
|--------------|---------|-------------|--------------|-----------------|
| Material 3 Dynamic Color | 31 (Android 12) | | `DynamicColors.applyIfAvailable()` guards? |
| Splash Screen API | 31 | | `core-splashscreen` compat library used? |
| Predictive back gesture | 33 (Android 13) | | `android:enableOnBackInvokedCallback`? |
| Photo picker | 33 | | Falls back to `ACTION_OPEN_DOCUMENT`? |
| Edge-to-edge | 30 (Android 11) | | `WindowCompat.setDecorFitsSystemWindows(false)`? |
| `WindowInsetsCompat` | All via AndroidX | | Used for safe area handling? |
| `BlurMaskFilter` / RenderEffect | 31 | | Blur effects degrade gracefully on older devices? |
| Scoped storage | 29 (Android 10) | | `MediaStore` / SAF used instead of direct file paths? |
| *[App-specific APIs]* | | | | |

For every feature above the app's `minSdkVersion`: is there a version check (`if (Build.VERSION.SDK_INT >= X)`) with a graceful fallback? Missing version guards cause crashes on older devices.

**For iOS apps — OS Version Compatibility:**
- Check deployment target vs API availability
- Use `@available` checks for newer APIs
- Verify backward-compatible alternatives exist

#### §H2. APP DISTRIBUTION & UPDATE

**For web apps — PWA & Service Worker:**
- **Cache strategy correctness**: Cache-first for static assets, network-first for data, stale-while-revalidate for semi-static?
- **Version cleanup**: Old caches purged on app update? User ever stuck on stale JS?
- **Update notification**: User notified when a new version is deployed? Can they act on it?
- **Offline completeness**: Core functionality works offline? Network-dependent features fail gracefully?
- **Manifest completeness**: Required icon sizes (192×192, 512×512, 180×180 iOS, maskable for Android), `display`, `theme_color`, `background_color`, `start_url`, `scope`.

**For Android apps — Distribution & Update:**
- **Play Store metadata**: App icon at all required sizes (adaptive icon with foreground/background layers)? Feature graphic designed? Screenshot quality matching the UI polish?
- **In-app update**: For critical fixes, is Google Play In-App Update API used (`AppUpdateManager`)? Flexible vs immediate update flow chosen correctly?
- **Version code management**: `versionCode` always incrementing? `versionName` human-readable and meaningful?
- **ProGuard/R8 rules**: Obfuscation rules correct? No runtime crashes from missing keep rules on reflection-dependent classes (Room entities, Gson models, Navigation args)?
- **App bundle vs APK**: Using Android App Bundle for optimal download size? Split APKs for density/ABI/language?
- **Baseline profiles**: Startup and critical path code pre-compiled via baseline profiles for faster cold start?

#### §H3. MOBILE & TOUCH

**Web-specific:**
- **iOS Safari quirks**: `position: fixed` + virtual keyboard? `100vh` including address bar (use `dvh`)?
- **Android**: Back gesture in PWA — navigates back or exits app?
- **Touch vs hover**: Hover-only interactions blocked by `@media (hover: hover)`?
- **Safe area insets**: `env(safe-area-inset-*)` respected in fixed/absolute elements on notched devices?
- **Pinch-to-zoom**: `user-scalable=no` present? (Accessibility violation — WCAG 1.4.4)
- **Swipe gestures**: Conflict with native scroll? Threshold too sensitive for intentional scroll?

**Touch interaction quality (all platforms):**
- **Touch target sizing**: Every tappable element ≥ 48×48dp (Android Material guideline) or 44×44pt (iOS HIG). This includes: list items, icons, toggle switches, close buttons, action buttons in toolbars. Measure the actual hit area, not just the visible element — a 24dp icon with no padding is a miss target. Use `Grep` to find `layout_height` and `layout_width` values < 48dp on interactive elements.
- **Touch target spacing**: Adjacent touch targets must have sufficient spacing (≥ 8dp gap) to prevent accidental taps. Toolbar icons packed tightly together cause misfire. Bottom navigation items too close together cause wrong-tab taps.
- **Touch feedback**: Every tappable element must provide immediate visual feedback on press. On Android: ripple effect (`?attr/selectableItemBackground` or `?attr/selectableItemBackgroundBorderless`). On iOS: highlight state. On web: `:active` state. Missing touch feedback makes the UI feel broken — the user doesn't know if their tap registered.
- **Thumb zone ergonomics**: Primary actions should be in the natural thumb reach zone (bottom half of screen on mobile). Critical actions placed at the top of the screen require stretching — move them to bottom sheets, FABs, or bottom action bars. Map the most-used actions and verify they're in comfortable reach.
- **Scroll vs tap ambiguity**: In scrollable lists with tappable items — is there enough distinction between a scroll gesture and a tap? Quick taps on list items while the list is still settling (momentum scroll) can trigger accidental selections. Minimum scroll distance threshold should prevent this.
- **Edge gesture conflicts**: Android 10+ gesture navigation reserves the left and right edges for back-swipe. Apps with drawer menus, edge-based swipe actions, or horizontal scroll views near edges must handle this conflict. iOS swipe-from-left-edge is system back — app horizontal swipes near the left edge will conflict.
- **Orientation handling**: Does the app support landscape? If so — does the layout adapt meaningfully (not just stretch)? Are dialogs, bottom sheets, and keyboards handled in landscape? If portrait-only — is `android:screenOrientation="portrait"` set, or does the app rotate and break?
- **Keyboard interaction**: When a text input is focused — does the content scroll to keep the input visible above the keyboard? On Android: `android:windowSoftInputMode="adjustResize"` or `adjustPan`? Are action buttons (Submit, Next) still accessible when the keyboard is up, or do they disappear behind it?
- **Multi-touch handling**: If the app supports multi-select via tap, or zoom via pinch — are these gestures correctly scoped? A pinch-to-zoom on an image viewer shouldn't accidentally trigger a list scroll. A two-finger gesture shouldn't register as two single taps.
- **One-handed usability audit**: For phone-primary apps — can all primary workflows be completed one-handed? Map the full primary flow and mark every point where the user must reach to the top of the screen or use two hands. Each one is a friction point worth optimizing.

#### §H4. NETWORK RESILIENCE

> This section applies to all platforms. Network is unreliable — every network-dependent feature must handle failure gracefully.

- **Offline detection reliability**: How does the app detect network availability? `ConnectivityManager` (Android) / `NWPathMonitor` (iOS) / `navigator.onLine` (web, unreliable). The correct signal is actual request failure, not just connectivity state — the device can be "connected" to WiFi with no internet. Does the app distinguish between "no connection" and "connection too slow"?
- **Offline mode**: What happens when the network is unavailable? Can the user still browse cached/local content? Are network-dependent features clearly marked as unavailable (grayed out, disabled with explanation)? Or does the entire app become a blank loading screen?
- **Retry strategy**: Failed network requests — does the app retry with exponential backoff and jitter (e.g., 1s, 2s, 4s, 8s ± random)? Or does it fail silently, retry infinitely, or show a generic error? Each retry must have a maximum attempt count and a final failure state with user action (retry button, contact support).
- **Timeout handling**: Every network request must have a timeout. On Android: OkHttp `connectTimeout`, `readTimeout`, `writeTimeout` set to appropriate values (not infinite). Operations that can hang (file uploads, cloud sync, API calls) — does the UI show a timeout error after a reasonable duration, or does it spinner forever?
- **Request cancellation**: When the user navigates away from a screen, are pending network requests cancelled? On Android: coroutine scope tied to ViewModel lifecycle? Orphaned requests waste battery, data, and can cause crashes if they complete after the fragment is destroyed.
- **Third-party service failure**: Image host down, cloud storage API error, analytics SDK timeout — does the app degrade gracefully? Placeholder shown for failed images? Layout preserved (no collapsed/invisible elements)? Core functionality still works when non-essential services fail?
- **Reconnection behavior**: When connectivity returns after an offline period — does the app detect this and refresh stale content? Does it show a "back online" indicator? Does pending work (queued uploads, deferred syncs) resume automatically? Is there a risk of duplicate operations (upload sent twice because the first attempt's response was lost)?
- **Low-bandwidth resilience**: On slow connections (2G, spotty WiFi) — does the app remain usable? Are images loaded at appropriate quality/resolution? Are large operations (file download, backup) resumable if interrupted? Does the UI show meaningful progress for slow operations rather than appearing frozen?
- **Data saver mode**: On Android, users can enable Data Saver (`ConnectivityManager.isActiveNetworkMetered()` + `RESTRICT_BACKGROUND_DATA`). Does the app respect this by reducing image quality, deferring non-critical syncs, and avoiding background data usage?

---

### CATEGORY I — Code Quality & Architecture

#### §I1. DEAD CODE & WASTE

> **Claude Code** — grep patterns for dead code detection:
> - Dev artifacts: `Grep(pattern: "console\\.log|debugger|TODO|FIXME|HACK|XXX|TEMP", glob: "*.{kt,java,js,ts,swift}")`
> - Commented code: `Grep(pattern: "^\\s*//.*\\(|^\\s*//.*=|^\\s*//.*fun |^\\s*//.*class ", glob: "*.{kt,java}")`
> - Unused imports (Android): `Grep(pattern: "^import ", type: "kotlin")` — then cross-reference usage
> - Unused string resources: `Grep(pattern: "<string name=\"", glob: "**/strings.xml")` — cross-ref with layout XMLs and Kotlin

- **Unused functions**: Defined but never called?
- **Unused constants**: Defined but never referenced?
- **Unreachable branches**: `if (CONSTANT === false)`, conditions that can never be true given state machine?
- **Commented-out code**: Old implementation left as dead comments — delete or document why it's kept.
- **Unused CDN libraries**: Loaded but never used?
- **Development artifacts**: `console.log`, `debugger`, `TODO`, `FIXME`, `HACK` — inventory and prioritize.

#### §I2. NAMING QUALITY
- **Casing conventions**: `camelCase` (variables/functions), `PascalCase` (components/classes), `SCREAMING_SNAKE` (constants) — consistent?
- **Semantic accuracy**: Functions that do more than their name says? Names that imply something different from actual behavior?
- **Boolean naming**: `is`/`has`/`can`/`should` prefix for boolean variables and props?
- **Event handler naming**: `on{Event}` for callbacks, `handle{Event}` for internal handlers — consistent?
- **Magic numbers**: Every unexplained numeric literal that should be a named constant. List all.
- **Unclear abbreviations**: What is `wsv`? `ctr`? `tmp2`? Either expand or document.

#### §I3. ERROR HANDLING COVERAGE
For every `try/catch` and every async operation:
- **Caught**: Is the exception caught, or does it bubble up to crash the app?
- **Logged**: Is there a trace for debugging (even in development)?
- **Surfaced**: Does the user see a meaningful message, or does the error disappear silently?
- **Recovered**: Does the app return to a valid, operable state?
- **Error boundaries**: React Error Boundaries at the right granularity — not just one global boundary that blanks the whole app?

#### §I4. CODE DUPLICATION
- **Logic duplication**: Same calculation in multiple places — which copy gets the bug fix?
- **UI pattern duplication**: Same component structure copied 3+ times — should be parameterized.
- **Constant duplication**: Same value hardcoded in multiple places — one change misses the others.
- **Copy-paste divergence**: Duplicated code where one copy was updated and the other wasn't — this is where bugs hide.

#### §I5. COMPONENT & MODULE ARCHITECTURE
- **Single responsibility**: Each component does one clearly-defined thing.
- **God components**: Components >300 lines doing multiple unrelated things — natural split points?
- **Prop drilling**: Props passed through 4+ intermediate components — context or composition?
- **Reusability**: Near-duplicate components that could be unified with a well-designed prop API.
- **Dependency direction**: Lower-level components not importing from higher-level state/context.

#### §I6. DOCUMENTATION & MAINTAINABILITY
- **Algorithm comments**: Non-trivial algorithms (probability engines, optimizers, state machines) have comments explaining the math, assumptions, inputs, outputs, and edge cases.
- **Lying comments**: Comments that describe what the code *used to* do before a refactor.
- **Architecture decisions**: Key choices documented with rationale (why single-file? why no server? why this state model?).
- **Section organization**: For large files — section index? Navigable by grep?
- **Changelog**: Version history maintained?

---

### CATEGORY J — Data Presentation & Portability

#### §J1. NUMBER & DATA FORMATTING
- **Numeric display consistency**: Same number formatted the same way everywhere (1,234 vs 1234)?
- **Percentage precision**: Contextual — 2dp for small values (<10%), 1dp for medium, 0dp for 100%? Consistent?
- **Date/time formatting**: Single format across all views? ISO 8601 for data, human-readable for display?
- **Currency formatting**: Locale-correct? Correct decimal places for the currency?
- **Null/zero/empty representation**: Consistent — `0`, `—`, `N/A`, hidden — same treatment for same meaning throughout?
- **Unit labels**: "45 items" not just "45". No ambiguous bare numbers.
- **Significant figures**: Contextual precision — casual context shows `~2.4 hrs`, not `2.41739012...`.

#### §J2. DATA VISUALIZATION QUALITY
- **Data correctness**: Chart data points map to correct domain values? Off-by-one errors?
- **Axis honesty**: Y-axis starting at 0 (unless explicitly justified)? No misleading truncation?
- **Scale choice**: Logarithmic vs linear — appropriate for the data range and user question?
- **Small value visibility**: Values near zero visible at default scale, or crushed to invisibility?
- **Tooltip accuracy**: Tooltip values match underlying computed values (not re-approximated)?
- **Visual vs computed agreement**: For every displayed number — the value shown equals the value computed.
- **Responsive correctness**: Labels overlap at narrow widths? Chart reflows on resize?
- **Colorblind safety**: Colors distinguishable without hue? (Use shape, pattern, or label as secondary encoding)

#### §J3. ASSET MANAGEMENT
- **Third-party image hosts**: Reliability? Rate limiting? GDPR implications?
- **Format modernity**: WebP/AVIF vs legacy PNG/JPEG?
- **Lazy loading**: `loading="lazy"` for below-fold images?
- **Error handling**: `onError` fallback image? No broken-image glyphs in the UI?
- **Alt text quality**: Meaningful descriptions (not filenames, not "image").
- **PWA icons**: All required sizes (192, 512, 180 for iOS)?

#### §J4. REAL-TIME DATA FRESHNESS
- **Staleness indicators**: Data that changes frequently (prices, statuses, counts) — does the UI communicate age? "Last updated 3m ago" vs silently stale?
- **Poll / push strategy**: Polling interval appropriate for data volatility? WebSocket reconnect on disconnect?
- **Optimistic updates**: Local state updated immediately, then confirmed or rolled back on server response — rollback path implemented?
- **Cache invalidation**: When does a cached response get considered stale? Can the user force-refresh?
- **Timestamp handling**: Server timestamps compared to client clock — timezone mismatch? Clock skew?
- **Race condition on rapid refresh**: Two in-flight requests, older response arrives after newer — does old data overwrite new?
- **Loading vs stale distinction**: Is there a visual difference between "this data is loading" and "this data might be outdated"?

---

### CATEGORY K — Specialized Domain Depths

Activate at maximum depth based on §0 Stakes and §I.1 Domain Classification.

#### §K1. FINANCIAL PRECISION
- **Integer cents rule**: All monetary values stored as integer cents/pence? Never float. `0.1 + 0.2 ≠ 0.3` in IEEE 754.
- **Rounding discipline**: Explicit rounding at defined points — not relying on floating-point truncation.
- **Tax application order**: Before or after discount? Correct for jurisdiction?
- **Rounding rule**: Banker's rounding (round-half-to-even) vs standard rounding — which is legally required?
- **Multi-currency**: FX rate freshness? Which rate used for conversion?
- **Atomicity**: Can a partial operation (interrupted payment, network failure mid-transaction) leave state inconsistent?
- **Audit trail**: Financial actions logged immutably?

#### §K2. MEDICAL / HEALTH PRECISION
- **Formula source**: Every clinical formula cited against a published medical reference.
- **Unit safety**: Imperial/metric mixing? `mg` vs `mcg` confusion? `kg` vs `lbs`?
- **Dangerous value flagging**: Clinically dangerous values flagged prominently, not just displayed.
- **Disclaimer visibility**: "Not medical advice" prominent and impossible to miss.
- **HIPAA/equivalent**: Health data stored locally or transmitted? Regulatory requirements?
- **Uncertainty communication**: Model limitations stated? Estimates vs exact values labeled?

#### §K3. PROBABILITY & GAMBLING-ADJACENT
- **Model appropriateness**: Mathematical model valid for the actual stochastic process?
- **Worst-case disclosure**: Expected value shown alongside worst-case. Not just the average.
- **Spending escalation UX**: Does the UI design (with or without intent) guide users toward spending more?
- **Age verification**: Gambling-adjacent mechanics — is age gating present or required?
- **Jurisdiction**: Gambling regulations vary by country — is this app subject to any?

#### §K4. REAL-TIME & COLLABORATIVE
- **Conflict resolution strategy**: Two users editing simultaneously — last-write-wins, merge, or lock?
- **Presence accuracy**: Online/offline status stale? Reconnect latency?
- **Message ordering**: Out-of-order messages handled correctly?
- **Optimistic update rollback**: If a server operation fails — does the UI correctly roll back?

#### §K5. AI / LLM INTEGRATION
*(Activate when External APIs or AI/LLM field in §0 references any AI provider)*
- **Prompt injection via user input**: User-controlled text concatenated into a prompt — can a user inject instructions that change model behavior? Sanitize or clearly separate user content from system instructions.
- **Output sanitization**: AI-generated text inserted into the DOM via `innerHTML` or `dangerouslySetInnerHTML`? AI output can contain adversarial HTML/JS. Always treat LLM output as untrusted user input — escape or sanitize before rendering.
- **Markdown rendering XSS**: AI output rendered via a markdown library — is the library configured to sanitize HTML? (e.g., `marked` with `sanitize: true`, or `DOMPurify` post-process)
- **Token cost runaway**: Is there a `max_tokens` limit on every request? Can a user trigger unbounded completion chains (recursive calls, loops, tool use without depth limit)?
- **API key exposure**: API key in frontend source code, localStorage, or URL params → extractable by any user. Keys must go through a backend proxy.
- **Model fallback**: If the primary model is unavailable or returns an error — graceful fallback or error message? No silent empty state?
- **Latency handling**: LLM calls are slow (1–30s). Is there a visible streaming indicator or progress state? Can the user cancel? Does the UI remain interactive?
- **Hallucination disclosure**: App presents AI-generated content as fact? Caveat required.
- **PII in prompts**: Does the prompt include user PII (name, health data, financial data)? Data processor obligations under GDPR/CCPA?
- **Rate limiting / retry**: 429 responses from the API — exponential backoff with jitter? User-visible message vs silent hang?
- **Streaming edge cases**: Partial chunk handling — what happens if stream cuts mid-token? Partial JSON in structured outputs?

---

### CATEGORY L — Optimization, Standardization & Polish Roadmap

> This category does not find bugs. It identifies opportunities to improve the app beyond "working" to "exceptional" — without introducing bugs, without removing features, without denaturing the design identity.

#### §L1. CODE OPTIMIZATION OPPORTUNITIES
- **Algorithm efficiency**: Are there O(n²) operations where O(n log n) or O(n) is achievable without architectural change?
- **Memoization gaps**: Expensive pure computations called repeatedly with the same inputs — should be memoized.
- **Redundant computation**: Multiple places computing the same derived value — unify to a single derivation.
- **Bundle size reduction**: Dead imports? Lighter library alternatives that fit within the architectural constraints?
- **CSS optimization**: Unused CSS classes? Specificity conflicts? Long selector chains?
- **Render optimization**: Components that render on every global state change despite depending on only a small slice of state?

#### §L2. CODE STANDARDIZATION
- **Consistent patterns**: For similar problems (data fetching, error handling, form validation) — is one pattern used throughout, or multiple ad-hoc approaches?
- **Utility consolidation**: Repeated utility functions (date formatting, number formatting, string manipulation) that should be in a shared module.
- **Constant registry**: All domain constants in one place? Or scattered throughout the file?
- **Component API consistency**: Similar components with inconsistent prop naming (`onClose` vs `handleClose` vs `dismiss`)? Standardize.
- **Import/dependency order**: Consistent grouping and ordering of imports/CDN dependencies?
- **Error handling pattern**: Consistent try/catch shape and error reporting throughout — not a different approach in every async call.

#### §L3. DESIGN SYSTEM STANDARDIZATION
> The goal: move from "many components that each look fine individually" to "one coherent design system."
- **Token consolidation plan**: For every one-off spacing/color/radius value found in §E1 — provide the standardized token it should use and what currently uses that token.
- **Component variant audit**: For every component type (button, card, badge, input, modal) — list all existing variants, identify variants that should be merged or unified, identify missing variants the app needs but lacks.
- **Pattern library gap**: For components used ≥3 times without a shared implementation — recommend extraction to a shared component.
- **Theme variable completeness**: Every value that changes with theme (light/dark/OLED/brand) should be a CSS variable or theme token, never hardcoded. List every hardcoded value that bypasses the theme system.
- **Design system as product asset**: A coherent design system enables faster iteration, safer changes, and visual coherence as the product grows — for any product nature. Assess: does the current system have enough structure to support adding 5 new components without breaking the visual language? If not — what minimal token/component foundations would make it robust?
- **Theming readiness** *(paid/multi-tenant products only)*: If the monetization tier or distribution model involves multiple brands, clients, or customization — are brand-identity values (primary color, radius personality, font) isolated in a small set of root tokens that could be swapped per tenant? Flag this only when relevant — this is not a goal for a single-user or community tool.
- **Design system documentation**: Is the design system documented anywhere — even minimally? A `colors.xml` with organized groups and comments? A `styles.xml` with a clear hierarchy? A design spec document? Without documentation, every new component is a guess that may or may not match the system. Even a single README listing "these are our tokens" prevents divergence.
- **Accessibility baked into the system**: Do design tokens include focus ring styles, minimum touch target sizes, and contrast-safe color pairings? Are component patterns accessible by default (buttons with ripple feedback, inputs with proper label association, lists with proper contentDescription)? Accessibility that must be remembered per-component will be forgotten — it must be built into the system.
- **Android theme architecture**: Is there a clean theme hierarchy? `Theme.App` → `Theme.App.NoActionBar` → activity-specific overrides? Are styles organized by component type (`Widget.App.Button`, `Widget.App.Card`)? Does the theme properly extend Material Components (`Theme.Material3.DayNight`)? Flat, unstructured `styles.xml` with dozens of near-duplicate styles is design system debt.

#### §L4. COPY & CONTENT STANDARDIZATION
- **Voice guide**: Describe the app's copy voice in 3 adjectives, then list any copy that violates this voice.
- **Terminology dictionary**: For every key concept in the app, the canonical name. List synonyms used inconsistently.
- **Capitalization audit**: List every label, button, and heading — flag inconsistent capitalization.
- **Punctuation consistency**: Trailing periods in labels? Em-dashes vs hyphens? Consistent quotation marks?
- **Number/unit style**: Spelled-out numbers vs digits ("three" vs "3")? Consistent in same context?
- **CTA optimization**: Are calls-to-action specific enough? "Get started" → "Create invoice" → "Create your first invoice" — each more specific and more effective.
- **Brand voice guide deliverable** *(all)*: Based on the copy audit, produce a minimal voice guide for this specific app — derived from its axis profile, not a generic template. The guide should include:
  ```
  Voice: [adjective 1 / adjective 2 / adjective 3]
  Derived from: [Axis 1: commercial/non-revenue] × [Axis 2: use context] × [Axis 3: audience]
  
  This app sounds like: "[example]"  not  "[anti-example]"
  This app sounds like: "[example]"  not  "[anti-example]"
  
  Always: [rule 1], [rule 2], [rule 3]
  Never:  [rule 1], [rule 2], [rule 3]
  
  [A3/A4 if active] Domain/community vocabulary:
    Use: [terms the audience actually uses]
    Avoid: [generic substitutes that signal distance]
  
  [A2 if emotional/sensitive context] Tone floor:
    Never use language that: [specific tone restrictions for this context]
  ```
- **Copy quality as context-appropriate signal** *(all)*: Generic, utilitarian copy signals low craft in any context — but what "low craft" means varies by axis. For commercial tools it signals low ambition; for community tools it signals unfamiliarity with the subject; for sensitive-context tools it signals emotional tone-deafness; for expert-audience tools it signals domain ignorance. Identify the highest-priority rewrites based on the most active axes in this app's profile.

#### §L5. INTERACTION & EXPERIENCE POLISH
- **Transition coherence**: Every transition tells the correct spatial/relational story. Elements that appear from nowhere should instead grow, slide, or fade from a logical direction.
- **Delight opportunities**: Are there interactions that are currently functional but could be made memorable without adding visual noise? (Examples: subtle success animations, satisfying completion states, smooth drag interactions)
- **State change communication**: When something important changes (new calculation result, data saved, error cleared) — is the change communicated as an event, not just a static update?
- **Scroll experience**: Is scroll behavior intentional? Smooth scroll where appropriate? Scroll position preserved and restored correctly?
- **Loading sequence**: For multi-stage loading — does the sequence feel progressive (each stage appears in order) or jarring (everything appears at once)?
- **The craft implementation checklist** `[A1][A2][A5]` — derived from the axis profile:
  - *High commercial intent (A1)*: `transform: scale(0.97)` on button press — `transition: all 0.2s ease-out` on interactive surfaces — skeleton loaders that mirror actual content layout — `font-variant-numeric: tabular-nums` on number columns — focus rings styled to match the design language — hover states with appropriate cursor changes — contextual empty states — integrated notification system — success confirmation that closes the interaction loop.
  - *Focus-critical contexts (A2)*: Every transition under 150ms — zero decorative animation — information-forward layout with no competing visual elements — instant feedback on every interaction — nothing moves that doesn't need to move.
  - *Emotionally sensitive contexts (A2)*: All transitions 200–400ms minimum — ease-in-out curves only — no abrupt appearance/disappearance — warm confirmation states — gentle empty states — no red for anything non-critical.
  - *Aesthetic-primary contexts (A5)*: UI chrome transitions under 100ms so attention stays on the output — output presentation given full visual investment — no interface element competes with what the tool produces.
  - *Any app — universal baseline*: Is there at least one detail that clearly took extra effort? Does the app look intentional rather than defaulted? Is spacing consistent enough that nothing feels accidental? Do transitions feel considered rather than left at browser defaults?
- **Motion budget**: Every animation in the app consumes attention. Total the number of simultaneous animated elements a user might see at once. More than 2–3 simultaneous animations competes for attention and degrades perceived quality. Identify any views where the motion budget is exceeded and recommend which animations to reduce or remove.
- **Enter/exit animation asymmetry**: Elements should typically enter slower than they exit — the entrance draws attention, the exit clears space. A common refinement: enter at 250ms, exit at 150ms (60% of enter). Assess whether enter and exit animations are differentiated, or whether they use the same duration bidirectionally (which feels less polished).
- **Stagger sequencing**: When multiple elements appear simultaneously (list items, grid cards, dashboard widgets) — do they stagger their entrance (30–50ms delay per element, capped at 150ms total)? Staggered entrances create a feeling of orchestration; simultaneous appearance feels like a jump cut. On Android: use `RecyclerView.ItemAnimator` with stagger, or `LayoutAnimation` with delay multiplier.
- **Fragment/screen transition quality**: On Android, screen transitions are a primary polish surface. Default fragment transitions are functional but generic. Assess: are shared element transitions used where appropriate (tapping a list item that expands into a detail screen)? Are enter/exit/popEnter/popExit animations defined in `nav_graph.xml` or set programmatically? Do transitions match the app's motion character (Material motion: container transform, shared axis, fade through)?
- **Haptic feedback polish**: On Android, haptic feedback at the right moments transforms "functional" into "premium." Key moments: toggle switch state change, selection mode entry (long-press), destructive action confirmation, pull-to-refresh threshold, slider value change. Use `HapticFeedbackConstants.CONFIRM`, `REJECT`, `GESTURE_START`, `GESTURE_END` (API 30+). Over-use of haptics degrades the signal — limit to 5–8 key moments maximum.

#### §L6. PERFORMANCE POLISH

> Performance polish is about perceived speed — making the app feel fast regardless of actual computation time. §D covers raw performance bugs; this section covers the perception layer.

- **Render jank identification**: Identify specific interactions where frame drops are likely and suggest targeted fixes within architecture constraints. On Android: use `GPU profiling` bars or Systrace to identify frames exceeding 16ms. Common jank sources: RecyclerView item inflation, fragment transition with complex layouts, bitmap decoding on main thread, alpha animation on complex view hierarchies.
- **Perceived performance improvements**: Even without changing actual speed — optimistic UI (show the result before server confirms), instant visual feedback (button press acknowledged in <50ms), skeleton screens that match real content shape (not generic gray rectangles), progressive disclosure of complex results (show the summary immediately, load details on demand).
- **Startup sequence optimization**: What is the minimum viable first render? What can be deferred? Can the critical path be reduced without changing functionality? On Android: use `Baseline Profiles` to pre-compile critical startup code. Defer heavy initialization (`Room` database open, analytics SDK init, non-essential feature modules) to background threads after first frame.
- **Memory footprint reduction**: Identify data structures that could be more memory-efficient without changing behavior. On Android: large bitmap handling (`inSampleSize` for downscaling), RecyclerView view pool sizing, fragment lifecycle management (fragments in back stack holding large views), LiveData observers not cleaned up.
- **Image loading optimization**: Images are the most common perceived-performance bottleneck. Are images loaded at the correct resolution for their display size (not loading a 4000px photo for a 48dp thumbnail)? Is there a blur-up or dominant-color placeholder while full images load? Do image transitions feel smooth (crossfade from placeholder to loaded image)? On Android: `Glide` or `Coil` with proper `override()` sizing, `placeholder()`, `transition(DrawableTransitionOptions.withCrossFade())`.
- **Animation performance**: CSS `transform` and `opacity` are GPU-composited (fast). `width`, `height`, `top`, `left`, `background-color` trigger layout/paint (slow). On Android: `ObjectAnimator` on `translationX`/`translationY`/`alpha`/`scaleX`/`scaleY` is hardware-accelerated. Animating `layout_width`, `layout_height`, or calling `requestLayout()` during animation causes jank. For complex animations: use `MotionLayout` instead of programmatic layout changes.
- **Scroll performance**: RecyclerView/ListView scrolling should be butter-smooth at 60fps. Common causes of scroll jank: inflating complex layouts in `onBindViewHolder`, loading images synchronously, calculating layouts during scroll (`wrap_content` heights that change), nested scrollable views. On Android: use `RecyclerView.setHasFixedSize(true)` when item sizes are constant, use `DiffUtil` for efficient list updates, prefetch items with `LinearLayoutManager.setInitialPrefetchItemCount()`.
- **Cold start time**: On Android, cold start > 1 second is noticeable, > 2 seconds feels slow. Use the Splash Screen API to provide branded visual during initialization. Audit `Application.onCreate()` for heavy synchronous work. Consider lazy initialization for non-critical services. Measure with `adb shell am start -W` and `Perfetto`.

#### §L7. ACCESSIBILITY POLISH *(beyond compliance — toward excellence)*

> §G covers compliance (does it work for assistive technology users?). This section covers excellence (is it a *good* experience for assistive technology users?).

- **Screen reader navigation efficiency**: Can a TalkBack/VoiceOver user complete the primary workflow in roughly the same number of steps as a sighted user? If the screen reader path requires 3× more swipes because of ungrouped elements, redundant labels, or poor heading structure — the experience is compliant but poor. Count the steps and optimize.
- **Heading hierarchy excellence**: Not just technically correct — does the heading structure help a screen reader user understand the page structure and navigate efficiently? On Android: `accessibilityHeading="true"` on section titles enables TalkBack's heading navigation (swipe up/down with reading controls set to Headings). Every screen should have at least one heading so TalkBack users can orient quickly.
- **Content description quality**: Beyond "present or absent" — are content descriptions genuinely useful? "Image" is worse than no description. "Profile photo of the current user" is useful. "Button" is worse than "Delete selected files." Audit every `contentDescription` for informational value, not just existence. Descriptions should convey *purpose*, not *appearance* ("Navigate back" not "Left arrow icon").
- **Announcement verbosity calibration**: TalkBack reads every `contentDescription`, role, and state. Overly verbose descriptions slow down experienced screen reader users (who often run at 3× speed). "File item, vacation_photos.zip, 24 megabytes, modified January 3rd, double-tap to open, long-press for more options" is comprehensive but slow. Group information logically: "vacation_photos.zip, 24 MB, January 3. Double-tap to open."
- **Focus choreography**: For complex interactions (dialogs, bottom sheets, multi-step flows, selection mode) — does focus movement tell a coherent spatial story? When a dialog opens, focus moves inside. When it closes, focus returns to the trigger. When selection mode activates, focus moves to the contextual action bar. When a fragment transition occurs, focus moves to the new screen's first meaningful element. Each focus movement should be intentional, not accidental.
- **Live region tuning**: Dynamic content updates (scan progress, file counts, download status) announced at the right frequency. `accessibilityLiveRegion="polite"` for informational updates (announces when TalkBack is idle). `"assertive"` only for genuinely urgent updates (errors, critical alerts). A progress counter that announces every 1% is too verbose — announce at 25% intervals or meaningful milestones.
- **Color-independent comprehension**: Can every piece of meaning in the app be understood in grayscale? Test: take a screenshot, convert to grayscale. Can you still distinguish: selected vs unselected items? Error vs success states? Active vs inactive tabs? Primary vs secondary buttons? If any distinction relies solely on color — add shape, icon, weight, or position as a secondary signal.
- **High contrast mode support**: On Android, some users enable high contrast text (Settings > Accessibility > High contrast text). Does the app remain well-designed in this mode, or do elements become visually broken? Test and verify that critical UI elements survive high contrast mode without visual artifacts.
- **Cognitive accessibility**: Beyond screen reader and motor support — is the interface kind to users with cognitive disabilities? Plain language in labels and instructions. Consistent navigation patterns (same actions in the same place on every screen). Error prevention (confirmation before destructive actions). Time limits that can be extended. No auto-playing content that demands immediate attention.

---

### CATEGORY M — Deployment & Operations

#### §M1. VERSION & UPDATE MANAGEMENT
- **Version single source of truth**: App version in one place in the codebase?
- **Schema migration**: State schema changes across versions — migration from old to new handled?
- **Rollback strategy**: Bad deploy — how do users get back to a working state?
- **Cache busting**: Static assets get new URLs when content changes?

#### §M2. OBSERVABILITY
- **Error reporting**: Uncaught exceptions — sent to error monitoring? At minimum, logged to console in a structured way?
- **Debug mode**: Development-only logging gated behind a flag (not `console.log` left in production)?
- **State inspection**: Can a developer inspect current application state without browser devtools?
- **Admin action logging**: Privileged actions logged? Immutable audit trail?

#### §M3. FEATURE FLAGS & GRADUAL ROLLOUT
- **Flag inventory**: List every `if (FEATURE_FLAG)` or `if (process.env.FEATURE_X)` in the codebase. Are the flags documented?
- **Dead flags**: Flags that are always true or always false in production — dead code that should be cleaned up?
- **Flag coupling**: Feature flags that must be toggled together — is this documented? Toggleing one without the other creates a broken state?
- **Emergency kill switch**: For risky or AI-powered features — is there a runtime flag to disable without a deploy?
- **A/B test cleanup**: Concluded experiments with flag code still in place — when is it scheduled for cleanup?

---

### CATEGORY N — Internationalization & Localization

> Activate at full depth whenever §0 `Locale / i18n` is not "English only" or is omitted.
> Even English-only apps should pass the hardcoded-strings check — future i18n cost compounds with every unchecked string.

#### §N1. HARDCODED STRING INVENTORY

> **Platform mapping**: Web uses JS/HTML strings → locale JSON/YAML. Android uses `strings.xml` in `res/values/`. iOS uses `Localizable.strings` or `String Catalogs`. The principle is the same: no user-visible text hardcoded in source code.

- **User-visible strings in source**: Every string rendered in the UI that is hardcoded in source code rather than in a locale resource — list all.
  - *Web*: Strings in JS/JSX/HTML/TSX files instead of locale JSON/i18n library.
  - *Android*: Strings in Kotlin/Java code (`"Delete"`, `"Error"`) or in XML layouts (`android:text="Submit"`) instead of `@string/` references. Use `Grep(pattern: 'android:text="[^@]', glob: "*.xml")` to find hardcoded strings in layouts. Use `Grep(pattern: '"[A-Z][a-z].*"', glob: "*.kt")` to find potential UI strings in Kotlin code.
  - *iOS*: Strings in Swift/ObjC code instead of `NSLocalizedString` / `String(localized:)`.
- **Pluralization logic**: `"1 item" / "2 items"` — handled with proper pluralization API? Not `count === 1 ? "item" : "items"` (fails in many languages — Russian has 3 plural forms, Arabic has 6).
  - *Web*: Use `Intl.PluralRules` or i18n library's plural system (i18next, FormatJS).
  - *Android*: Use `<plurals>` resource in `strings.xml` with `getQuantityString()`. Never use `if (count == 1)` in Kotlin.
  - *iOS*: Use `Localizable.stringsdict` with `NSStringPluralRuleType`.
- **Concatenated UI strings**: `"You have " + count + " messages"` — word order varies by language; must use a template/message format, not string concatenation.
  - *Android*: Use `getString(R.string.message_count, count)` with positional arguments (`%1$d items in %2$s`), not string concatenation in Kotlin.
- **Hardcoded error messages**: Error strings in catch blocks, validation messages, toast content, snackbar text — all extractable to string resources?
- **Accessibility text**: `contentDescription` (Android) / `accessibilityLabel` (iOS) / `aria-label` (web) — hardcoded or localizable? These are user-facing strings that screen reader users hear — they must be localized.
- **Android-specific string resource audit**: Check for: strings in `menu.xml` items (should use `@string/`), strings in `AndroidManifest.xml` (`android:label`), strings in Navigation graph arguments, strings in notification builders, strings in preference XML.

#### §N2. LOCALE-SENSITIVE FORMATTING
- **Number formatting**: Uses `Intl.NumberFormat` (or equivalent) for display? Decimal separator differs: `.` (EN) vs `,` (DE, FR). Thousands separator differs. Hardcoded `toFixed(2)` is not locale-safe for display.
- **Date/time formatting**: Uses `Intl.DateTimeFormat` (or equivalent) for display? Month/day order, 24h vs 12h, calendar system all vary by locale.
- **Currency display**: `$1,234.56` is US-only. `Intl.NumberFormat` with `style: 'currency'` handles locale-correct formatting.
- **Collation/sorting**: String `sort()` uses byte order — not correct for non-ASCII text. `Intl.Collator` for locale-aware alphabetical sort.
- **Relative time**: `Intl.RelativeTimeFormat` for "3 days ago" style strings?
- **List formatting**: `"A, B, and C"` (Oxford comma, EN) vs `"A, B et C"` (FR). `Intl.ListFormat` handles this.

#### §N3. RTL (Right-to-Left) LAYOUT
*(Activate only if §0 Locale includes Arabic, Hebrew, Persian, Urdu, or other RTL languages)*
- **`dir="rtl"` on `<html>`**: Set dynamically per locale?
- **CSS logical properties**: `margin-inline-start` / `padding-inline-end` instead of `margin-left` / `padding-right` — the latter don't flip in RTL.
- **Flexbox direction**: `flex-direction: row` items reverse in RTL — intentional?
- **Icon mirroring**: Directional icons (arrows, chevrons, progress indicators) — should they flip in RTL? (Checkmarks and warning icons should not.)
- **Text alignment**: `text-align: left` should be `text-align: start` for RTL safety.
- **Canvas/SVG**: Custom rendering code — does it have RTL awareness?
- **Third-party components**: Date pickers, dropdowns, data grids — do they respect `dir="rtl"`?

#### §N4. LOCALE LOADING & PERFORMANCE
- **Bundle size**: All locale data bundled upfront vs loaded on demand? Loading all locales adds significant weight.
- **Fallback chain**: Missing key in current locale → falls back to default locale → falls back to key name? No blank UI?
- **Locale detection**: Browser `navigator.language` used for detection? User override persisted to storage?
- **Dynamic locale switch**: App re-renders fully in new locale without page reload? State preserved across switch?

---

### CATEGORY O — Development Scenario Projection

> This category looks **forward**, not backward. Every other category diagnoses what is wrong today.
> This category answers: what will this codebase become under normal development pressure, growth, and time?
> The output is not a list of bugs — it is a map of the future the developer is currently building toward,
> with specific forks where a small choice now prevents an expensive problem later.

#### §O1. SCALE CLIFF ANALYSIS

For every data-intensive, storage-bound, or computation-bound operation, identify the data volume at which it transitions from "works fine" → "noticeably slow" → "crashes or becomes unusable". Express as concrete thresholds, not vague warnings.

For each identified cliff:
```
Operation:       {e.g. "Filtering items list", "localStorage write on save", "O(n²) sort"}
Location:        {specific function / component}
Current safe range:  {works acceptably up to N items / N KB / N concurrent actions}
Warning zone:    {degrades noticeably between N and M — user perceives lag}
Cliff edge:      {fails, freezes, or loses data above M}
Trigger:         {the specific user action or growth event that crosses this threshold}
Current trajectory: {estimated time to reach cliff at normal usage pace}
Fix window:      {how long the developer has before this becomes urgent}
```

Common cliff locations to analyze:
- **localStorage quota** (5MB hard cap): current payload size × growth rate per user action
- **O(n²) operations**: any sort + filter combination, nested loops over the same list, or `find()` inside a `map()`
- **Unvirtualized DOM lists**: lists rendered without virtualization — beyond 200–500 items, scroll jank becomes severe; beyond 2,000, the browser may freeze on initial render
- **Bundle parse time on mobile**: single-file apps growing past 500KB uncompressed are measurably slow to parse on mid-range Android (simulate with 4× CPU throttle)
- **Re-render cascade**: a global state change that re-renders the entire tree — harmless at small scale, increasingly expensive as component count grows
- **Regex performance on large inputs**: pathological backtracking on user-provided strings

#### §O2. FEATURE ADDITION RISK MAP

Based on §0 `Likeliest Next Features` and reasonable inference from the app's domain and trajectory, identify the top 5 features most likely to be added — then analyze exactly what in the current codebase will break, resist, or require expensive redesign when each is added.

For each anticipated feature:
```
Feature:               {name}
Probability:           HIGH / MEDIUM (based on domain norms, code signals, §0 roadmap)

Current code that conflicts or must change:
  - {specific function/pattern} at {location} — {why it conflicts with this feature}
  - {specific assumption} baked into {component} — {why it breaks under this feature}
  - {data structure choice} — {why it requires redesign for this feature}

Pre-adaptation cost (fix now, before feature exists):  Trivial / Small / Medium / Large
Post-addition cost (fix after feature is already built): {estimated 3–10× higher — why}

Pre-adaptation recommendation:
  {The minimal abstraction, interface, or structural change that opens the door for this feature
   without breaking any current behavior. This is not the feature itself — it is the preparation.}
```

Example conflicts to look for:
- **User accounts**: state stored flat (no `userId` scope) → all data must be re-keyed; no concept of "current user" in state schema → every component that reads state must be updated
- **Undo/redo**: state mutations applied directly → no command history; immutable state + command pattern required
- **Multi-device sync**: localStorage as sole persistence → no sync surface; no conflict resolution strategy
- **Theming / white-label**: hardcoded brand colors throughout → cannot swap theme without touching hundreds of values
- **Server-side rendering**: `window`/`document` accessed at module level → crashes during SSR; `localStorage` calls not guarded → crashes on server

#### §O3. TECHNICAL DEBT COMPOUNDING MAP

Not all technical debt is equal. Some debt is inert — it stays roughly the same cost to fix forever. **Compounding debt grows in cost with every new line of code built on top of it.** Identify which current issues are compounding — these must be prioritized above their individual severity suggests.

Compounding debt markers:
- **Foundation coupling**: Logic that other features are being built directly on top of, without an abstraction layer. Every new feature deepens the coupling, making the foundation progressively harder to change.
- **Terminology divergence**: The same concept named differently in different sections — as the codebase grows and more developers touch it, the confusion multiplies with every new file that references both names.
- **Schema without migration infrastructure**: A stored data schema with no version field and no migration logic — every schema change risks silently breaking all existing users' stored state. The cost to add migration infrastructure compounds with every release that ships without it.
- **Test debt on changing code**: Frequently-modified logic with no test coverage. Every untested change increases the probability of an undetected regression. This compounds — the longer it goes without tests, the more likely existing behavior is already wrong, and the harder it is to add tests without first understanding what "correct" means.
- **Copy-paste architecture that has already diverged**: Duplicated logic where the copies are now subtly different. Each new feature must be applied to every copy; each copy is an independent bug surface. The longer this persists, the more the copies diverge.
- **Magic constants without a registry**: Domain-critical numbers scattered through the code without centralization. Every new formula that uses one of these values may use a different hardcoded version — silent inconsistency that compounds with every new formula.

For each identified compounding debt item:
```
Debt:                     {description}
Location:                 {where it lives in the codebase}
Current cost to fix:      Trivial / Small / Medium / Large
Cost multiplier (6 months): {estimated — e.g. "3× harder after user accounts are added"}
Compounding trigger:      {the specific event or feature that causes the cost to jump}
Pre-emption recommendation: {the specific, minimal change that breaks the compounding cycle}
⏱ COMPOUNDS
```

#### §O4. DEPENDENCY DECAY FORECAST

For every external dependency (CDN script, npm package, third-party API), assess its forward risk profile.

| Dependency | Version | Maintenance Status | Risk Level | Specific Risk | Recommended Action |
|-----------|---------|-------------------|-----------|--------------|-------------------|
| {name} | {ver} | Active / Slow / Abandoned / Security history | LOW / MED / HIGH | {specific concern} | {action} |

Risk factors to assess for each:
- **Abandonment indicators**: No releases in 18+ months; single maintainer with reduced activity; issue response time > weeks; no responses to CVEs
- **Breaking change trajectory**: Frequent major versions; poor deprecation communication; current version many majors behind latest
- **Security history**: Prior CVEs — how quickly were they patched? Are there open unpatched vulnerabilities?
- **CDN single-point-of-failure**: Loaded from CDN without `integrity` attribute and with no fallback — a CDN compromise or outage causes catastrophic failure. Single CDN dependency for the entire app framework is a HIGH risk for any app with uptime expectations.
- **API version sunset**: External API endpoints with announced deprecation dates; versioned endpoints where the used version is no longer current
- **Framework compatibility drift**: Library last tested with framework version N; app now runs N+2; breaking changes in between are silent

#### §O5. CONSTRAINT EVOLUTION ANALYSIS

Based on §0 `Planned Constraint Changes` and natural growth pressure, analyze the migration complexity when the app outgrows each current constraint. The goal is to identify pre-adaptations — small, low-cost changes that make the eventual migration from 2 weeks of work to 2 days.

For each constraint likely to evolve:
```
Current Constraint:     {e.g. "localStorage-only persistence"}
Evolution Trigger:      {the growth or feature requirement that forces this change}
Migration Complexity:   LOW / MEDIUM / HIGH / PROHIBITIVE (if attempted without pre-adaptation)

Migration obstacles (specific — function/pattern names):
  - {what in the current code assumes this constraint and must be refactored}
  - {what data transformation is required for existing users' stored data}

Pre-adaptation opportunity:
  {The abstraction, interface, or structural change that can be added now at low cost
   that converts the eventual migration from a rewrite into a substitution.
   Cost now: {Trivial/Small}. Avoided cost later: {Medium/Large}.}
```

Key constraint evolutions to analyze by architecture type:
- **localStorage → backend API**: Are all storage read/writes behind a service/repository abstraction? Or called directly from components? Direct calls mean every component must be updated during migration.
- **Single-user → multi-user**: Is data stored with user scope (`userId` prefix) or flat? Flat storage requires a data migration affecting every existing user.
- **CDN imports → build pipeline**: Do imports use bare specifiers (`import React from 'react'`) compatible with bundlers? Any `eval()`, `new Function()`, or string-based dynamic imports that break tree-shaking?
- **Hardcoded locale → multi-locale**: What is the cost of string extraction? Are date/number formats centralized or scattered?
- **Monolith → modular**: Circular dependency chains? Implicit shared global state between would-be modules? Which features are genuinely isolated vs. deeply entangled?

#### §O6. MAINTENANCE TRAP INVENTORY

Identify every location in the codebase that is disproportionately risky to modify — where a developer making what appears to be a simple change is at high risk of introducing a non-obvious regression.

For each maintenance trap:
```
Trap name:          {short descriptive name}
Location:           {specific function, component, or section}
Why it's a trap:    {the specific coupling, hidden dependency, or non-obvious behavior
                     that makes this section dangerous to touch}
Symptom signature:  {the error or failure mode a developer would see after accidentally breaking it}
Safe modification protocol: {the specific step-by-step check a developer must do before
                              and after any change to this section}
Defusion recommendation:    {the refactor that eliminates the trap — labeled separately from bug fixes}
```

Common maintenance trap patterns to scan for:
- **Functions with hidden side effects**: appear to compute a value but secretly mutate shared state, write to storage, or trigger network calls as a side effect — callers assume they are pure
- **Order-dependent initialization**: works only if functions or modules are called in a specific sequence, but nothing in the code enforces or documents this order
- **Load-bearing "magic" values**: constants whose specific values are non-obvious but critical — changing them "slightly" breaks unrelated functionality
- **Deep prop chains**: a prop value that flows through 5+ component layers — renaming or reshaping it requires updating every intermediate component
- **CSS specificity landmines**: a rule that overrides another rule via specificity, not structure — changing either rule breaks the other without any connection being visible in the source
- **Global state assumed by multiple components**: state that two or more components both read and write, with no coordination mechanism — changes to the write pattern silently break the read pattern

#### §O7. BUS FACTOR & KNOWLEDGE CONCENTRATION

Identify code sections that are effectively a **black box** — where the implementation is only safely modifiable by whoever wrote it, or where the only documentation is "don't touch this."

For each high-risk knowledge concentration:
```
Location:           {specific function/section}
Knowledge gap:      {what a new developer cannot understand from reading the code alone}
Bus factor risk:    {what breaks or becomes unmaintainable if the author is unavailable}
Minimum documentation:  {the specific comment or documentation that would make this safe
                          for a developer unfamiliar with the section to modify}
```

---

**§O — Required Output: Scenario Projection Summary**

This table must appear at the end of the Projection Analysis part:

| Scenario | Likelihood | Time Horizon | Current Readiness | Pre-adaptation Cost | Without Pre-adaptation |
|----------|-----------|-------------|-------------------|--------------------|-----------------------|
| {e.g. "User reaches 500+ items"} | HIGH | 3 months | NOT READY — cliff at 200 items | Small | Large refactor under pressure |
| {e.g. "Adding user accounts"} | HIGH | 6 months | PARTIAL — no user scoping in schema | Medium | Data migration + full state redesign |
| {e.g. "Moving to a backend"} | MEDIUM | 12 months | NOT READY — no storage abstraction | Small | Every component must be updated |
| {e.g. "React CDN major version bump"} | MEDIUM | 18 months | READY — no deprecated API usage | Trivial | Small |
| {e.g. "Second developer on the team"} | HIGH | Now | NOT READY — 3 maintenance traps, 2 knowledge concentration zones | Small docs effort | High onboarding risk |

---

## V. FINDING FORMAT

Every finding follows this exact format. No exceptions. No vague findings.

```
[SEVERITY] [CONFIDENCE] — {Short descriptive title}

Category:         {§Letter.Number} — {Category and Dimension Name}
Location:         Line {N} in {function/component name} / "near `functionName()`" if line unknown
Domain Impact:    {Connects finding to the app's actual purpose and user stakes}

Description:
  {What is wrong or suboptimal. References the exact variable, function, value, or CSS class.
   Never generic. "The `handleImport` function" not "the import function".}

Evidence:
  {For [CONFIRMED]: direct code reference or execution trace.
   For [LIKELY]: strong circumstantial code evidence.
   For [THEORETICAL]: the reasoning chain explaining why this is a risk.}

User Impact:
  {Concrete scenario: what actually happens to a real user because of this issue.}

Recommendation:
  {Specific, actionable. Before/after code snippet where feasible.
   If multiple approaches exist, rank them by safety and effort.
   Tag recommendations that change behavior with ⚠ BEHAVIOR CHANGE.
   Tag recommendations that touch design identity with ✦ IDENTITY-SENSITIVE.
   Tag findings whose cost grows over time with ⏱ COMPOUNDS.}

Effort:        Trivial (<5 min) | Small (<1 hr) | Medium (<1 day) | Large (>1 day)
Risk:          {What could break if this recommendation is applied incorrectly}
Cross-refs:    {Other finding IDs that should be fixed together or in sequence}
Automated Test: {What test type (unit/integration/E2E/snapshot) and what assertion would catch a regression of this finding — e.g., "Unit test: `expect(calculateTax(100, 'FR')).toBe(20)` — currently returns 21 due to wrong rate"}
```

### Severity Scale

| Level | Meaning | When to Apply |
|-------|---------|---------------|
| **CRITICAL** | App-breaking crash, data loss, security breach, wrong output causing real harm | XSS vector, import corrupts all state, wrong financial/medical calculation, prototype pollution |
| **HIGH** | Major feature broken, significant user harm, serious accessibility failure, important data wrong | Broken workflow, missing critical validation, modal not keyboard-accessible, wrong domain data |
| **MEDIUM** | Feature partially wrong, degraded UX, moderate accessibility issue, noticeable inconsistency | Missing loading state, color contrast below 4.5:1, inconsistent number formatting, NaN not handled |
| **LOW** | Minor inconsistency, cosmetic issue, non-critical opportunity | Spacing inconsistency, slightly wrong animation timing, minor dead code |
| **NIT** | Style, naming, purely aesthetic with no UX impact | Typo in comment, inconsistent quote style, unused `console.log` in dev path |

**Stakes multiplier**: For CRITICAL-stakes apps, apply one level upward to findings in §A, §B, §C, §K.

**Optimization/Polish findings**: Use severity to express the improvement value, not a defect level. A HIGH polish finding means a high-value design improvement opportunity.

### Confidence Scale

| Level | Meaning |
|-------|---------|
| **[CONFIRMED]** | Verified by reading code and tracing execution path |
| **[LIKELY]** | Strong code evidence; near-certain but not runtime-verified |
| **[THEORETICAL]** | Architectural risk that cannot be confirmed without runtime testing |

---

## VI. REQUIRED DELIVERABLES

### Tier 1 — Must Complete (Parts 1–4)

| Deliverable | Format | Contents |
|-------------|--------|---------|
| **Feature Preservation Ledger** | Table | Feature · Status (Working/Broken/Partial/Unknown) · Dependencies · Safe to Modify · Safe to Remove · Notes |
| **Design Identity Record** | Summary | Confirmed design character, protected signature elements, any ambiguities resolved with user |
| **Architecture Constraint Map** | Table | Constraint · Why it exists · What breaks if violated · How recommendations respect it |
| **Domain Rule Verification Table** | Table | Rule from §0 · Code value/implementation · Match (✓/✗/⚠) · Finding ID if mismatch |
| **Workflow Trace Report** | Per-workflow | Each step · Code location · Bugs found at step · Pass/Fail |
| **Data Integrity Report** | Table | Input · Validation gap · Invalid values possible · Downstream corruption |
| **Priority Action Items** | Two-tier | Tier A: Quick Wins (CRITICAL/HIGH + Trivial/Small) · Tier B: Strategic (remaining CRITICAL/HIGH by user impact) |
| **Scenario Projection Summary** | Table | Scenario · Likelihood · Time Horizon · Current Readiness · Pre-adaptation Cost · Cost Without Pre-adaptation |

### Tier 2 — Should Complete (Parts 5–10)

| Deliverable | Contents |
|-------------|---------|
| **Sensitive Data Inventory** | Every stored/transmitted datum: classification, protection, risk |
| **Data Flow Diagram** | `Input → Validation → State → Computation → Display` with gap annotations at every arrow |
| **Graceful Degradation Matrix** | Dependency · Failure mode · User impact · Current fallback · Quality (Good/Partial/None/Crash) |
| **Resource Budget Table** | Resource · Size · Load strategy · Critical path? · Optimization opportunity |
| **Web Vitals Estimate** | LCP, FID/INP, CLS — each with bottleneck and fix |
| **WCAG 2.1 AA Scorecard** | Criterion · Pass/Fail/N/A · Evidence · Fix |
| **Cross-Browser Matrix** | Feature × Browser: Pass/Fail/Partial/Unknown |
| **Design Token Inventory** | Every unique spacing, color, radius, shadow, z-index, transition — with consolidation plan |
| **Component Quality Scorecard** | Every component type: variant completeness, state completeness, visual consistency grade |
| **Copy Quality Inventory** | Every piece of UI copy: voice consistency, clarity, conciseness, suggested rewrites |
| **i18n Readiness Report** | Hardcoded string count, locale-unsafe format calls, RTL gaps, estimated i18n migration effort |

### Tier 3 — Complete if Time Allows (Parts 11+)

| Deliverable | Contents | Applies To |
|-------------|---------|------------|
| **Optimization Roadmap** | Code efficiency, render performance, bundle size — ranked by effort vs impact | All |
| **Design System Standardization Plan** | Token consolidation, component unification, pattern library gaps | All |
| **Polish Delta Report** | Per section: specific changes that move from "functional" to "intentional/professional" (framing adapted to product nature) | All |
| **Brand Voice Guide** | Voice adjectives, always/never rules, copy rewrites — adapted to product nature (community authenticity vs conversion copy) | All |
| **Commercial Readiness Assessment** | First-impression audit, competitive benchmark, monetization-tier alignment gap | Paid/Freemium/B2B only |
| **Thematic Fidelity Assessment** | Source-material color/type/tone alignment, community authenticity audit, fan credibility signals | Fan/Community tools only |
| **Visual Identity Report** | Brand signature, color/type/motion alignment to personality, differentiation or fidelity opportunities | All (framing varies) |
| **Missing Tests Matrix** | Critical code paths → test type (unit/integration/E2E) → priority | All |
| **Architecture Evolution Roadmap** | (1) Safe incremental improvements · (2) Medium-term refactors · (3) Long-term goals | All |
| **Domain-Specific Deep Dive** | Per §K dimensions activated by domain classification | All |

---

## VII. SUMMARY DASHBOARD (Final Part)

### Findings Table

| Category | Total | CRIT | HIGH | MED | LOW | NIT | Quick Wins |
|----------|-------|------|------|-----|-----|-----|------------|
| A — Logic | | | | | | | |
| B — State | | | | | | | |
| C — Security | | | | | | | |
| D — Performance | | | | | | | |
| E — Visual Design | | | | | | | |
| E8 — Product Aesthetics | | | | | | | |
| E9 — Brand Identity | | | | | | | |
| E10 — Data Storytelling | | | | | | | |
| F — UX/Copy | | | | | | | |
| F6 — Engagement/Delight | | | | | | | |
| G — Accessibility | | | | | | | |
| H — Compatibility | | | | | | | |
| I — Code Quality | | | | | | | |
| J — Data/Viz | | | | | | | |
| K — Domain | | | | | | | |
| L — Optimization | | | | | | | |
| M — Ops | | | | | | | |
| N — i18n/L10n | | | | | | | |
| O — Projection | | | | | | | |
| **Total** | | | | | | | |

### Root Cause Analysis

```
RC-{N}: {Root Cause Name}
Findings affected: F-001, F-007, F-012 (list all)
Description: The upstream condition that, if fixed, resolves multiple downstream findings
Fix leverage: Fixing this one root cause replaces {N} individual fixes
```

### Compound Finding Chains

```
Chain-{N}: {Name}
Combined Severity: {Escalated beyond individual findings}
  Step 1: [F-003] [LOW]  — {description}
  Step 2: [F-011] [MED]  — {description}
  Step 3: [F-019] [HIGH] — {description}
  Combined: {User harm scenario} → {Severity at stakes level}
```

### Positive Verifications

{N} critical paths confirmed working correctly:
- `{feature}` — verified via {method} — no issues found

### Top 10 Quick Wins

Highest (severity × user impact) with lowest effort — fix these first:

| # | ID | Title | Severity | Effort | Impact |
|---|----|----|---------|--------|--------|
| 1 | | | | | |

### Remediation Roadmap

```
IMMEDIATE — before next release:
  [ ] F-{id} {title} — Effort: {X} — Risk: {Y}

SHORT-TERM — next sprint:
  [ ] F-{id} ...

POLISH SPRINT — standalone improvement sprint:
  [ ] Design token consolidation — Effort: Medium
  [ ] Copy standardization — Effort: Small
  [ ] Component variant completion — Effort: Medium

MEDIUM-TERM — next 1–3 months:
  [ ] ...

ARCHITECTURAL — 6+ months:
  [ ] ...
```

---

## VIII. CROSS-CUTTING CONCERN MAP

> Patterns that span multiple categories. Check each one after completing all audit dimensions.

| Concern | Sections | Failure Chain |
|---------|----------|---------------|
| **Data Integrity** | | |
| Floating-point precision | §A1, §A2, §J1 | Calculation drift → wrong display → user decisions |
| Validation gap chain | §B3, §A1, §F4 | Missing validation → wrong logic → wrong display → user harm |
| Input boundary cascade | §A1, §B3, §D1 | Out-of-range value → engine crash or wrong silent result |
| Type coercion in validation | §A7, §B3 | String input → `+` concatenates → invalid value passes → corrupts downstream |
| NaN/Infinity propagation | §A7, §B3, §J1 | Silent NaN → propagates through pipeline → wrong display |
| **State & Persistence** | | |
| Storage limits | §B2, §I1 | Quota exceeded → silent data loss → corrupted reload |
| Concurrent state modification | §A4, §B2 | Multiple tabs / rapid actions → race condition → data corruption |
| Stale closure cascade | §A6, §B1 | Missing useEffect deps → stale state → wrong computation → wrong display |
| Mutation through abstractions | §B6, §B1 | Shallow copy → child mutates nested object → parent state silently corrupted |
| Import/export chain | §B4, §C3, §C5 | Malformed import → prototype pollution → state corruption |
| **Security** | | |
| AI output injection | §K5, §C2 | LLM output via innerHTML → XSS from adversarial model output |
| Domain data fabrication | §A1, §K1–K5, §I.5 | Unverified domain fact used as finding basis → developer acts on false info |
| **Visual & Design** | | |
| Theme completeness | §E1, §E3, §L3 | Hardcoded color bypassing theme → inconsistency + a11y failure |
| Design token fragmentation | §E1, §L3 | One-off values throughout → visual inconsistency + maintenance burden |
| Design-nature mismatch | §E8, §E9, §L3 | Polish misaligned with product nature → blocks conversion / feels inauthentic |
| Color psychology conflict | §E3, §E9 | Palette emotion mismatched to domain → subconscious friction |
| Brand identity absence | §E9, §E7 | No visual signature → indistinguishable from competitors |
| Copy inconsistency | §F4, §L4 | Same concept named differently → user confusion |
| Copy-tier mismatch | §L4, §F4 | Generic copy undermines trust built by visual design |
| Delight debt | §F6, §L5 | No personality or reward moments → product feels transactional |
| **Accessibility & Compatibility** | | |
| Semantic HTML gap | §G1, §G2, §G3 | `<div>` buttons → no keyboard, no screen reader, no WCAG |
| Reduced motion gap | §E6, §G4 | CSS respects prefers-reduced-motion but canvas/JS doesn't |
| **Infrastructure** | | |
| Worker reliability | §D1, §H2, §H4 | Blob Worker incompatibility → missing fallback → wrong results |
| External dependency failure | §H2, §H4, §J3 | CDN/image host down → crash vs graceful degrade |
| Stale cache on deploy | §H2, §M1 | SW serves old JS with new schema → silent corruption |
| Timezone/DST | §A3, §A5 | Wrong DST offset → wrong dates/countdowns |
| Locale assumption | §N1, §N2, §J1 | Hardcoded formats → wrong display in non-English locales |
| Feature flag coupling | §M3, §A4 | Flags toggled independently when they must be together → broken state |
| **Growth & Evolution** | | |
| Compounding constraint | §O5, §B2 | Direct localStorage calls everywhere → migration requires touching every component |
| Scale cliff invisibility | §O1, §D1 | O(n²) works at dev volume → cliff invisible until production |
| Cross-audit contradiction | §0, §I.5 | Second audit silently produces different value for same rule |
| **§X/§XI Specific** | | |
| R&D-audit disconnect | §X.2, §VII | Improvement plan ignores audit findings → new features on broken foundation |
| Existing feature blindness | §X.0, §X.2 | New feature excitement → existing improvements perpetually defer |
| Polish regression cascade | §XI.2, §XI.5 | Polish one dimension, degrade another → caught only by quality gates |
| Restructuring-during-polish | §XI.3, §XI.2 | Code + visual changes mixed → regression source ambiguous |
| Feature preservation gap | §XI.1, §XI.5 | Ledger incomplete → polish breaks unlisted feature |
| Coherence fracture cascade | §XI.0, §X.0 | Healing one fracture reveals deeper one → re-analyze after each heal |
| Vision drift | §XI.0, §XI.5 | Vision forgotten by step 15 → later steps optimize for code, not product |

---

## IX. FINAL MANDATE

**Every finding must be specific enough that the developer can implement it without asking a follow-up question.**

"Improve error handling" fails. "`handleImport()` near line 847 calls `JSON.parse(pastedText)` without try/catch — any non-JSON clipboard content crashes the React tree. Wrap in try/catch, display toast: 'Clipboard content is not valid JSON.'" passes.

**Every domain fact carries a source tag:** `[CODE]`, `[§0-CONFIRMED]`, or `[UNVERIFIED]`. A finding based on `[UNVERIFIED]` data is a question, not a finding.

**Cross-session continuity:** Prior `[§0-CONFIRMED]` rules carry forward. Conflicts are surfaced as `[CONFLICT]` — the developer arbitrates.

**The audit serves the app's vision, not a generic standard.** The Five-Axis framework (§I.4) determines what "good" looks like for *this specific app*. A nurse's calculator, a meditation app, a gacha companion, and a B2B dashboard all require different aesthetic reasoning.

**Time dimension:** ⏱ COMPOUNDS findings are highest-leverage because their cost grows with delay. §O Scenario Projection is a first-class deliverable.

**Audit connects to action.** §X determines *what* to improve. §XI determines *how* to make it coherent. Both protect the Feature Preservation Ledger and Design Identity.

**Execution:** Follow §III. Begin with Part 1. For apps > 3,000 lines, confirm with user after Part 1. Part 1 = read entire codebase → classify → extract domain rules → confirm design identity → build Feature Preservation Ledger → announce plan → wait.

### Audit Completion Criteria

> **Claude:** Use this checklist to determine when the audit is complete. An audit is "done" when all applicable criteria are met.

```
COMPLETION GATES — check each before delivering the Summary Dashboard (§VII):

[ ] Every Part from §III Part Structure has been executed (or explicitly skipped with reason)
[ ] §0 is fully populated — no placeholder values remain
[ ] Every CRITICAL and HIGH finding includes a specific fix (not "improve X")
[ ] Every finding has a confidence tag: [CODE], [§0-CONFIRMED], or [UNVERIFIED]
[ ] Summary Dashboard (§VII) is populated: findings table, root cause analysis, quick wins
[ ] Top 10 Quick Wins are ranked by (severity × impact) / effort
[ ] Remediation Roadmap has at least IMMEDIATE and SHORT-TERM sections filled
[ ] Cross-Cutting Concern Map (§VIII) checked — compound chains documented
[ ] Feature Preservation Ledger confirms no existing feature was accidentally invalidated
[ ] TodoWrite shows all parts as "completed"

EARLY EXIT — acceptable to stop before full completion when:
  - User says "that's enough" or "stop here" → deliver §VII with what you have
  - Context window is running low → deliver §VII, note which parts were not completed
  - User redirects to "fix mode" → switch from auditing to implementing fixes
  - Only LOW/NIT findings remain in uncovered parts → note and stop

NOT DONE — do not stop if:
  - Any CRITICAL finding exists without a documented fix
  - §VII Summary Dashboard has not been produced
  - §0 Domain Rules have [UNVERIFIED] items that could affect CRITICAL findings
```

---

## X. RESEARCH, DEVELOPMENT & IMPROVEMENT PROTOCOL

> **Evaluates existing features** (which may have drifted, stagnated, or been half-finished) **and new feature opportunities** — then produces a unified, prioritized development plan. The most impactful improvement is often not a new feature — it is an existing feature made twice as good.
>
> **Prerequisite**: §0 + §I classification (lightweight if standalone, full if post-audit).
>
> **Execution order**: §X.0 (look inward) → §X.1 (look outward) → §X.2 (prioritize) → §X.3 (deliverable).

---

### §X.0. EXISTING FEATURE DEEP EVALUATION

> Before looking outward (competitors, new features), look inward. Apps grow feature by feature, each built at a specific point in time. Over months, features drift: UX evolves but old features don't update, two features overlap, a feature shipped at MVP was never revisited.

#### X.0.1 — Feature Health Audit

For every feature in the app, evaluate across six dimensions:

```yaml
Feature: {name}
  # ── FUNCTIONAL ──
  Correctness:     SOLID / FRAGILE / BROKEN
    # Does it produce the right output? Always, or only on the happy path?
  
  # ── UX ──
  Usability:       INTUITIVE / ADEQUATE / CONFUSING / HOSTILE
    # Can a user accomplish the task without guessing? Has the UX evolved with the app?
  Discoverability: OBVIOUS / FINDABLE / HIDDEN / ORPHANED
    # Can users find this feature? Or has navigation growth buried it?
  
  # ── DESIGN ──
  Visual coherence: INTEGRATED / DATED / INCONSISTENT / ALIEN
    # Does it visually belong to the current version of the app, or an older era?
  
  # ── STRATEGIC ──
  User value:      CORE / IMPORTANT / MINOR / VESTIGIAL
    # If removed, would users notice? Would they leave?
  Completion:      COMPLETE / 80% DONE / HALF-BAKED / STUB
    # Was this feature fully realized, or shipped at MVP and never revisited?
  
  # ── DRIFT (most important dimension) ──
  Drift from current standard: NONE / MILD / SIGNIFICANT
    # Compared to the app's best features — how far has this one fallen behind?
    # This inconsistency is invisible to the developer but obvious to the user.
```

#### X.0.2 — Feature Relationship Map

Features don't exist in isolation. Map how they depend on, overlap with, and sometimes contradict each other:

```yaml
Dependencies:     # Feature A requires Feature B to function
  - "{A} → {B}: {what breaks if B changes — e.g. 'Export depends on Import's data model'}"

Overlaps:         # Feature A and Feature B do similar things
  - "{A} ↔ {B}: {how they overlap — e.g. 'Quick Add and Full Editor both create entries 
     with different validation rules, built in different sprints, never reconciled'}"

Contradictions:   # Feature A and Feature B imply different mental models
  - "{A} ✕ {B}: {the conflict — e.g. 'Settings has auto-save toggle but Editor always 
     shows a manual Save button — user can't tell which is happening'}"

Orphans:          # Features disconnected from the rest of the app
  - "{feature}: {why it's disconnected — e.g. 'Analytics page exists but nothing links to it'}"

Missing Bridges:  # Features that should connect but don't
  - "{A} ⇥ {B}: {the missing link — e.g. 'Items created in A can't be referenced from B'}"
```

#### X.0.3 — Feature Evolution Assessment

For every feature rated below SOLID + INTUITIVE + INTEGRATED + CORE + COMPLETE:

| Action | When | Meaning |
|--------|------|---------|
| **ELEVATE** | Valuable but below current quality standard | Bring to the standard of the app's best features. No scope change — quality uplift only. |
| **EVOLVE** | Works but users need more than it offers | Add depth/options. The feature's *scope* expands. |
| **CONSOLIDATE** | Two+ features overlap significantly | Merge into one coherent feature that does both jobs better. |
| **REIMAGINE** | Fundamental UX approach is wrong | Redesign from the user's perspective. Same goal, different interaction model. Higher risk/reward. |
| **DEPRECATE** | Vestigial — low usage, no strategic value | Plan graceful removal. Migrate data/expectations first. |
| **LEAVE** | Healthy, coherent, well-integrated | No action. Confirm explicitly so the developer knows it was evaluated, not skipped. |

```yaml
Feature: {name}
  Action:      {ELEVATE / EVOLVE / CONSOLIDATE / REIMAGINE / DEPRECATE / LEAVE}
  Rationale:   {why — tied to health audit findings}
  Current:     {1–2 sentences: what the feature does now and how it feels}
  Target:      {1–2 sentences: what the feature should become}
  Changes:
    - {concrete change — e.g. "Replace 3-step modal with inline editor matching Feature Y's pattern"}
    - {concrete change — e.g. "Add validation feedback that was missing"}
    - {concrete change — e.g. "Update visual style to current design tokens"}
  Effort:      LOW / MEDIUM / HIGH
  User impact: {specific experience improvement — not abstract quality}
  Risk:        {what could break or regress}
```

#### X.0.4 — Feature Coherence Score

Rate the app's feature coherence as a whole:

```yaml
Feature Coherence:
  Total features:                {N}
  At current standard:           {N} ({%})
  With significant drift:        {N} ({%})
  Overlapping pairs:             {N}
  Contradicting pairs:           {N}
  Orphaned features:             {N}
  Missing bridges:               {N}
  
  Rating: HIGH / MEDIUM / LOW / CRITICAL
    # HIGH:     ≥80% at standard, no contradictions, no orphans
    # MEDIUM:   ≥60% at standard, ≤1 contradiction, ≤1 orphan
    # LOW:      <60% at standard, or ≥2 contradictions/orphans
    # CRITICAL: The app feels like multiple apps stitched together
  
  Narrative: {2–3 sentences — does the app feel like one product or a patchwork? Where do seams show?}
```

**LOW or CRITICAL coherence → §XI must include a holistic coherence pass** (not just code restructuring).

---

### §X.1. COMPETITIVE & LANDSCAPE RESEARCH

> Internal state understood — now look outward. Understand what exists, what users expect, and where the gaps are.
>
> **Claude execution note**: Use `WebSearch` for competitor discovery — launch parallel searches for different competitor aspects. Use `WebFetch` to analyze competitor websites/app store listings. If web search is unavailable, use `AskUserQuestion` to ask the user to list 2–3 competitors and describe their strengths/weaknesses. Skip §X.1.1–X.1.3 if the developer explicitly says they don't care about competitors — go straight to §X.2 with only §X.0 findings as input.
>
> **For mobile apps**: Search app stores (Google Play, App Store) for competitor analysis. Use `WebFetch` on store listing URLs to extract feature lists, ratings, and user reviews.

#### X.1.1 — Direct Competitor Inventory

For the app's domain (from §0), identify the closest alternatives — tools solving the same problem for the same audience.

```
Competitor-{N}: {Name}
  URL / Platform:       {where it lives}
  Overlap:              {which of this app's features it also covers}
  Differentiation:      {what it does that this app does NOT}
  Weakness:             {where this app is already stronger}
  UX Model:             {key interaction patterns — how does it structure the user's workflow?}
  Monetization:         {how it sustains itself}
  Visual Tier:          {Rough visual quality: Prototype / Functional / Polished / Premium}
  User Sentiment:       {From reviews, forums, app stores — what do users love? What do they complain about?}
```

**Minimum**: 3 competitors for any app with commercial intent. 2 for community/free tools. 0 only for genuinely novel concepts — and even then, identify adjacent-domain tools.

#### X.1.2 — Feature Gap Matrix

Map features across this app and its competitors to identify gaps and opportunities — for both *missing* features and *existing features that competitors do better*:

```
| Feature / Capability          | This App       | Competitor A | Competitor B | Competitor C | Opportunity |
|-------------------------------|----------------|-------------|-------------|-------------|-------------|
| {e.g. "Offline mode"}        | ✗ Missing      | ✓           | ✗           | ✓           | HIGH — two competitors offer it, users expect it |
| {e.g. "Export to PDF"}       | ✓ Basic        | ✓ Advanced  | ✓ Basic     | ✓ Advanced  | UPGRADE — feature exists but competitors' versions are significantly better |
| {e.g. "AI-assisted input"}   | ✗ Missing      | ✗           | ✗           | ✗           | DIFFERENTIATOR — nobody offers it yet |
| {e.g. "Search"}              | ✓ Broken UX    | ✓ Excellent | ✓ Good      | ✓ Good      | CRITICAL UPGRADE — feature exists but is embarrassingly behind competitors |
| {e.g. "Custom themes"}       | ✓ Unique       | ✗           | ✗           | ✗           | STRENGTH — exclusive feature, no competition |
```

**Classify each row**:
- `PARITY` — table-stakes, must-have, this app already has it at competitive quality
- `UPGRADE` — feature exists but competitors do it noticeably better
- `CRITICAL UPGRADE` — feature exists but is so far behind competitors that it actively hurts the app's credibility
- `OPPORTUNITY` — users want it, some competitors have it, this app doesn't
- `DIFFERENTIATOR` — nobody offers it yet — blue ocean
- `STRENGTH` — this app does it and competitors don't — protect and promote this
- `OVER-SERVED` — this app has it, nobody else does, but it's unclear if users actually value it

**The distinction between UPGRADE and new OPPORTUNITY is crucial.** Upgrading an existing feature that users already rely on is almost always higher-leverage than adding a new feature — because the user base, the UX patterns, and the data model already exist. The developer just needs to make them better.

#### X.1.3 — User Signal Synthesis

Collect and structure all available signals about what users actually want, need, and struggle with. These signals outrank the developer's intuition and the auditor's analysis.

| Signal Source | What to Extract |
|---------------|-----------------|
| **User feedback** (direct messages, emails, form submissions) | Explicit requests, complaints, praise — verbatim where possible |
| **App store / review site reviews** | Recurring themes in positive and negative reviews — not individual outliers |
| **Community discussions** (Reddit, Discord, forums about the domain) | What problems do people describe? What workarounds do they use? What do they wish existed? |
| **Support tickets / bug reports** | Patterns — which features generate the most confusion or the most requests? |
| **Usage analytics** (if available) | Most-used features, abandoned flows, bounce points, session duration |
| **Competitor reviews** | What users praise and criticize about alternatives — these are proxy signals for this app's roadmap |

**Output**: A ranked list of **User-Validated Needs** — needs that appear in ≥2 independent signal sources. Single-source requests are listed separately as **Unvalidated Signals**.

#### X.1.4 — Technology & Approach Research

For each high-priority improvement or new feature, research the best available approaches before committing to an implementation:

```
Improvement: {e.g. "Add real-time collaboration"}
  Approaches Considered:
    1. {e.g. "CRDTs via Yjs"} — Pros: {X} / Cons: {Y} / Effort: {Z}
    2. {e.g. "Operational Transform via ShareDB"} — Pros: {X} / Cons: {Y} / Effort: {Z}
    3. {e.g. "Simple last-write-wins with polling"} — Pros: {X} / Cons: {Y} / Effort: {Z}
  Recommended Approach: {N} — Rationale: {why this approach wins for this specific app's constraints}
  Architecture Impact: {what existing code must change to support this}
  Risk Assessment: {what could go wrong — integration risk, performance risk, UX risk}
```

**Critical constraint**: All recommendations must respect §0 Architectural Constraints. An approach that requires abandoning a constraint is an *architectural proposal*, clearly marked as such — not a standard recommendation.

---

### §X.2. IMPROVEMENT PRIORITIZATION

> Existing feature improvements compete with new features on equal terms. Correct for "new feature bias" — an existing feature used daily has a larger impact surface than a new feature used weekly by some.
>
> **Claude execution note**: Present the Impact × Effort matrix to the user as a table. Let them react before producing the sequenced roadmap — they may disagree with impact/effort assessments.

#### X.2.1 — Unified Improvement Inventory

Before prioritizing, build a single list that contains *every* potential improvement from *every* source — existing features and new features side by side, evaluated on equal terms:

| Source | Type | Examples |
|--------|------|----------|
| §X.0 Feature Health Audit | Existing feature improvement | ELEVATE, EVOLVE, CONSOLIDATE, REIMAGINE actions |
| §X.1 Competitive Research | Existing feature upgrade OR new feature | UPGRADE, CRITICAL UPGRADE, OPPORTUNITY, DIFFERENTIATOR |
| §X.1.3 User Signal Synthesis | Either | Validated user needs — may point to existing feature frustrations or new feature requests |
| Audit findings (§IV) | Existing feature fix | Outstanding MEDIUM/LOW findings not yet resolved |
| Developer's own roadmap | Usually new feature | Ideas the developer has been planning |
| §O Scenario Projection | Architectural pre-adaptation | Infrastructure work that enables future features |

**Every item in this inventory is classified**:
```
Item: {description}
  Type:         EXISTING-ELEVATE / EXISTING-EVOLVE / EXISTING-CONSOLIDATE / EXISTING-REIMAGINE / NEW-FEATURE / INFRASTRUCTURE / BUG-FIX
  Source:       {which analysis identified this — §X.0 / §X.1 / user signal / audit / developer roadmap}
  Feature(s):   {which existing feature(s) this affects, or "NEW: {feature name}"}
```

#### X.2.2 — Impact × Effort Matrix

Place every item from the inventory on the matrix:

| | LOW EFFORT | HIGH EFFORT |
|---|---|---|
| **HIGH IMPACT** | **DO FIRST** — quick wins, ship this week | **PLAN CAREFULLY** — strategic investments requiring planning |
| **LOW IMPACT** | **FILL GAPS** — low-hanging fruit for downtime | **DEFER OR DROP** — revisit when effort drops or impact rises |

```yaml
Item: {description}
  Type:        {EXISTING-ELEVATE / EXISTING-EVOLVE / EXISTING-CONSOLIDATE / NEW-FEATURE / INFRASTRUCTURE / BUG-FIX}
  Source:      {§X.0 / audit F-XXX / user signal / competitive gap / developer roadmap}
  Impact:      HIGH / MEDIUM / LOW — {specific reason tied to user value}
  Effort:      HIGH / MEDIUM / LOW — {specific scope assessment}
  Quadrant:    {DO FIRST / PLAN CAREFULLY / FILL GAPS / DEFER}
  Dependencies: {what must be done first}
  # For existing feature improvements:
  Current pain:    {what users experience today}
  Risk of inaction: {what happens if this stays as-is for 6 months}
```

#### X.2.3 — Strategic Sequencing

Order matters. Some improvements unlock others. Some become impossible after others ship. Build the optimal sequence:

```
Phase 1 — Foundation (Sprint 1–2):
  Goal: {what user-facing improvement this phase delivers}
  Items: {list with IDs and types — EXISTING-ELEVATE, NEW-FEATURE, etc.}
  Unlocks: {what becomes possible after this phase}
  
Phase 2 — Core Value (Sprint 3–5):
  Goal: {what user-facing improvement this phase delivers}
  Items: {list with IDs and types}
  Depends on: Phase 1 items {specific IDs}
  
Phase 3 — Differentiation (Sprint 6–8):
  Goal: {what user-facing improvement this phase delivers}
  Items: {list with IDs and types}
  Depends on: Phase 2 items {specific IDs}
  
Phase 4+ — Growth & Polish:
  {Continue as needed}
```

**Sequencing rules**:
1. Bug fixes before everything — always. A new feature on a broken foundation compounds technical debt.
2. Existing feature CRITICAL UPGRADEs before new features — a feature that embarrasses the app every time a user touches it is a higher priority than any feature that doesn't exist yet.
3. CONSOLIDATE actions early — merging overlapping features simplifies the codebase and the user's mental model, which makes everything that follows cheaper and cleaner.
4. Infrastructure before features that depend on it — always. Don't build collaboration before auth.
5. ELEVATE actions alongside new features — bringing old features to the current standard can often be batched efficiently when the developer is already in that area of the code.
6. User-validated needs before developer-intuited features — unless the developer has strong domain expertise that users cannot articulate.
7. Differentiators before parity features — when possible. Parity features attract users who are already comparison-shopping; differentiators attract users who weren't looking.
8. REIMAGINE actions are planned like new features — because they effectively are. They need the same research, design, and testing rigor.

#### X.2.4 — Experimentation Protocol

For improvements where the right approach is genuinely uncertain, define experiments instead of committing to full implementation:

```
Experiment: {e.g. "Does AI-assisted input actually improve user completion rates?"}
  Hypothesis: {specific, falsifiable — e.g. "Adding AI suggestions to the input form will reduce average completion time by 30%"}
  Minimum Viable Test: {the smallest possible implementation that tests the hypothesis}
  Success Metric: {what you will measure and what threshold constitutes success}
  Time-Box: {maximum time to invest before evaluating — e.g. "3 days of development, 2 weeks of observation"}
  Kill Criteria: {what result means you stop and try a different approach}
  Rollback Plan: {how to cleanly remove the experiment if it fails}
```

**When to experiment vs. commit**: Experiment when the improvement is high-effort AND the impact is uncertain. Commit when the improvement is well-understood (bug fix, parity feature, user-validated need with clear solution).

---

### §X.3. R&D ROADMAP DELIVERABLE

```yaml
R&D ROADMAP — {App Name}

EXISTING FEATURE HEALTH:
  At current standard:       {N}/{total} ({%})
  Significant drift:         {N}
  Coherence rating:          {HIGH/MED/LOW/CRITICAL}
  Actions: ELEVATE({N}), EVOLVE({N}), CONSOLIDATE({N}), REIMAGINE({N}), DEPRECATE({N})

COMPETITIVE POSITION:
  Strengths:                 {top 3}
  Existing features behind:  {N UPGRADE items}
  Missing features:          {N OPPORTUNITY + DIFFERENTIATOR items}
  Unique strengths:          {N STRENGTH items}

USER-VALIDATED PRIORITIES:
  1. {need} — Sources: {signals}
  2. {need} — Sources: {signals}
  3. {need} — Sources: {signals}

UNIFIED INVENTORY:
  Total items: {N} — Existing({N}, {%}) / New({N}, {%}) / Infrastructure({N}, {%})

PHASES:
  Phase 1: {goal} — {N items} — {effort estimate}
  Phase 2: {goal} — {N items} — {effort estimate}
  Phase 3: {goal} — {N items} — {effort estimate}

EXPERIMENTS: {N} defined — {total time-box}

DEFERRED: {items not being pursued, with rationale}
```

---

## XI. APP POLISHING & RESTRUCTURATION PROTOCOL

> **Transforms an app that has become messy, incoherent, or fragmented** through organic growth back into a unified, intentional product — then polishes it to the quality it deserves. Not just code cleanup — this restructures the whole app: logic, navigation flow, design language, mental model, and codebase.
>
> **The problem**: You build a solid v1, then add features, fix bugs, add more features, refactor one part, add another feature. After enough iterations, the app has more features and fewer bugs — but it no longer feels like *one thing*. Different eras of development coexist. The user's mental model of "how this app works" no longer matches the actual structure.
>
> **Core principle**: You cannot restructure what you do not understand. §XI.0 (comprehension) is mandatory.
>
> **Prerequisite**: §0 + §I classification. Prior audit strongly recommended. If none, do Parts 1–3 first.
>
> **Execution order**: §XI.0 (understand) → §XI.1 (inventory) → §XI.2 (polish passes) → §XI.3 (code restructure) → §XI.4 (architecture) → §XI.5 (quality gates) → §XI.6 (deliverable).

> **Claude:** This section is ~500 lines. Work through one subsection at a time. Here's the map:
>
> | Subsection | What It Does | Skip When |
> |------------|-------------|-----------|
> | **§XI.0** Comprehension | Reads and internalizes the app as a product | NEVER — mandatory |
> | **§XI.1** Pre-Polish Inventory | Maps all coherence fractures and rough edges | Never |
> | **§XI.2** Polish Passes (0–6) | 7 passes from structural to fine-grained | Passes 3–6 if time-limited |
> | **§XI.3** Code Restructure | File structure, modules, naming, architecture | If user only wants visual polish |
> | **§XI.4** Architecture Evolution | Incremental arch improvements without rewrites | If app is small (<500 lines) |
> | **§XI.5** Quality Gates | Verifies polish preserved behavior | NEVER — mandatory after changes |
> | **§XI.6** Deliverable | Structured output of all changes | Never |
>
> **Minimum viable path**: §XI.0 + §XI.1 + §XI.2 (Passes 0, 1, 1.5) + §XI.5

---

### §XI.0. DEEP COMPREHENSION PHASE — MANDATORY BEFORE ANY CHANGE

> **The failure mode of restructuration is not "broke something"** — quality gates catch that. It is "restructured the app into something clean but soulless." This phase prevents it by forcing the auditor to internalize the app as a *product*, not just code.

#### XI.0.1 — Purpose & Identity Internalization

> **Claude execution note**: Fill this by reading the entire codebase first. Do NOT copy §0 — derive these answers from understanding the whole app. Output the completed record to the user and ask: "Does this accurately capture what your app is trying to be?" Adjust based on their response before proceeding.

Answer these before any restructuration:

```yaml
APP COMPREHENSION RECORD:

  Core Purpose:
    # One sentence. Not "it's a React app that..." but "it helps [who] do [what] when [context]"
    # e.g. "It helps Wuthering Waves players decide when to pull by showing their pity status"

  User Mental Model:
    # How does the user think about this app? What's its "shape" to them?
    # e.g. "A personal tracker — 'my pull history, my pity count, my chances'"

  Core Loop:
    # The user's primary repeated interaction — the heartbeat
    # e.g. "Log a pull → see updated pity → check probability → decide to pull again"

  Emotional Contract:
    # What the user feels when the app works well
    # e.g. "In control. Informed. Like they have an edge."

  Design Personality:
    # If this app were a person, how would it talk?
    # e.g. "Confident and precise, like a trusted advisor who knows the game inside-out"

  Best-in-App Standard:
    # Which part of this app is the best? This is the target — everything else rises to this level.
    # e.g. "The main dashboard is excellent. The settings page feels like a different app."

  Growth Archaeology:
    # Reconstruct the probable development timeline from code evidence:
    # - Which features were built first? (simpler patterns, older conventions)
    # - Which were added later? (newer patterns, sometimes better, sometimes hastier)
    # - Where did the developer change their mind mid-implementation?
    # - Where did a quick fix become permanent?
    # This is not judgment — it's understanding. Every "messy" part has a history.
```

**This record is the North Star.** Every §XI decision is tested against it: "Does this change make the app more like what's described here, or less?"

#### XI.0.2 — Coherence Fracture Analysis

> **Claude execution note**: This is the diagnostic that drives all of §XI.2 Pass 1.5. Be thorough here — every fracture you miss will survive restructuration. Present fractures to the user grouped by type and ask for confirmation before proceeding to fixes.

Identify *exactly* where and how coherence broke down. Each fracture becomes a restructuration task in Pass 1.5.

**Five fracture types to map:**

```yaml
LOGIC FRACTURES — the app's internal logic contradicts itself
  L-{N}:
    Where:     {features/flows involved}
    History:   {how it probably happened}
    Impact:    {what the user experiences — confusion, distrust, workaround}
    Example:   {specific instance — e.g. "Settings has 'metric units' toggle but workout
               log uses hardcoded imperial. Built before settings existed."}

FLOW FRACTURES — the user's journey hits seams
  F-{N}:
    Where:     {navigation paths/transitions}
    History:   {e.g. "feature added as new page instead of integrated into existing flow"}
    Impact:    {disorientation, dead ends, unexpected jumps}

DESIGN FRACTURES — different visual eras coexist
  D-{N}:
    Era A:     {visual conventions of the older part}
    Era B:     {visual conventions of the newer part}
    Boundary:  {where the user crosses from one era to the other}

CONVENTION FRACTURES — same problem solved differently in different places
  C-{N}:
    Pattern A: {how it's done here} — used in: {list}
    Pattern B: {how it's done there} — used in: {list}
    Canonical: {which one should win, and why}

MENTAL MODEL FRACTURES — the app's conceptual model is inconsistent
  M-{N}:
    Model A:   {what this part implies about how things work}
    Model B:   {what that part implies — contradicting Model A}
    Example:   {e.g. "Inventory treats items as flat list with tags. Crafting treats
               them as tree with categories. Two mental models of the same data."}
```

#### XI.0.3 — Unified Vision Statement

> **Claude execution note**: Write this and present it to the user. This becomes the North Star for every change in §XI. Reference it explicitly when making major decisions. If the user revises it, update all downstream work.

Write a single paragraph describing what this app should feel like when restructuration is complete. Not a feature list — a product description.

```yaml
UNIFIED VISION — {App Name}:
  # A paragraph describing the app as it should be. What it feels like to use.
  # How it flows. What its personality is. What makes it coherent.
  #
  # e.g. "Whispering Wishes feels like a single, confident tool built by someone
  # who plays daily. Every screen speaks the same visual language. The user never
  # thinks about where things are — navigation mirrors how players think about
  # gacha (banners → history → pity → probability). Every interaction gives
  # immediate feedback. The import flow matches the dashboard's quality. A new
  # user understands it in 30 seconds. A power user never hits a wall."
```

**Reference this vision explicitly in every major restructuration decision.** When in doubt: "Does this change bring us closer?"

---

### §XI.1. PRE-POLISH INVENTORY

#### XI.1.1 — Current State & Quality Target

```yaml
App State at Polish Start:
  Version:              {from §0}
  Outstanding CRITICAL: {count — must fix BEFORE polish begins}
  Outstanding HIGH:     {count — fixed in Pass 1}
  Debt Zones:           {3–5 worst areas: technical, design, and code}

Quality Baseline → Target (rate 1–5):
  Correctness:    {__}/5 → {__}/5  # e.g. "all CRITICAL/HIGH fixed, test vectors passing"
  Robustness:     {__}/5 → {__}/5  # e.g. "every async op has error handling"
  Performance:    {__}/5 → {__}/5  # e.g. "LCP < 2s, no jank on core interactions"
  Visual Polish:  {__}/5 → {__}/5  # e.g. "consistent tokens throughout, smooth transitions"
  Code Quality:   {__}/5 → {__}/5  # e.g. "zero dead code, consistent naming"
  UX Clarity:     {__}/5 → {__}/5  # e.g. "zero-doc onboarding, clear empty states"
  Accessibility:  {__}/5 → {__}/5  # e.g. "full WCAG 2.1 AA, keyboard-navigable"
```

#### XI.1.2 — Feature Preservation Ledger (Refresh)

Refresh from prior audit or build now. **Every named feature** gets an entry:

```yaml
Feature: {name}
  Status:           WORKING / PARTIALLY WORKING / BROKEN
  Tested:           YES / NO
  Polish priority:  HIGH / MEDIUM / LOW / SKIP
  Restructure:      YES ({reason}) / NO
  Risk during work: HIGH (shared state) / MEDIUM (complex) / LOW (isolated)
```

**The ledger is the contract**: No WORKING feature may degrade during polish. Every pass ends with ledger verification.

---

### §XI.2. SYSTEMATIC POLISH PASSES

> Passes are ordered foundational → cosmetic. Surface polish on a broken foundation is waste. Each pass has a single focus and a verification step.
>
> **Claude execution note**: Do NOT attempt all passes in one response. Work through one pass at a time, verify, then proceed. **Pass 1.5 is the most important** — it's where coherence is restored. Passes 2–6 are standard polish. If the user is impatient, Passes 0 + 1 + 1.5 are the minimum viable restructuration.

#### Pass 0 — Critical Fix Pass *(mandatory if outstanding CRITICAL findings exist)*

**Scope**: Fix every CRITICAL-severity finding from the audit. Nothing else.
**Why first**: A CRITICAL finding means the app produces wrong results, loses data, or has an exploitable security hole. Polishing an app that is fundamentally broken is performative.

```
For each CRITICAL finding:
  Finding ID:     F-{XXX}
  Fix:            {specific code change}
  Verification:   {how to confirm the fix works — test vector, manual check, or automated test}
  Regression check: {which features in the ledger could be affected — verify each one}
```

**Exit criteria**: Zero CRITICAL findings. Feature Preservation Ledger re-verified.

#### Pass 1 — Structural Integrity Pass

**Scope**: Fix HIGH-severity findings. Resolve data integrity issues. Ensure every feature works correctly under normal conditions.

**Checklist**:
- [ ] All HIGH findings from audit resolved
- [ ] All domain rules verified against §0 — every formula produces the correct output
- [ ] All state transitions are clean — no orphaned state, no zombie listeners, no stale closures
- [ ] All persistence operations are safe — write-read round-trip verified, quota handling confirmed
- [ ] All error paths are handled — no uncaught exceptions in any user-reachable path
- [ ] Feature Preservation Ledger re-verified — every WORKING feature still works

**Exit criteria**: App is *correct and robust* under normal usage. Not yet polished, not yet restructured — but trustworthy.

#### Pass 1.5 — Holistic Coherence Restructuration *(the core of §XI — driven by the Fracture Map)*

> **Claude execution note**: This is where the real restructuration happens. Work through fractures one at a time, verifying after each. When modifying app flow or mental model, explain your reasoning to the user first — these are high-impact changes.

**Scope**: Heal every fracture from §XI.0.2. Operates at the *app level* — changes how features relate, how the user moves through the app, how the conceptual model works.

**Why before visual polish**: Visual coherence is impossible on a fragmented foundation.

**1.5a — Logic Fracture Healing**: For each L-{N} — determine canonical logic, make specific changes, verify both halves now agree.

**1.5b — Flow Restructuration**: Map current navigation vs. ideal navigation (based on user mental model from §XI.0.1). For each seam where the user feels like they "left the app":
```yaml
Change F-{N}:
  Current:    {what happens now}
  Target:     {what should happen — tied to user mental model}
  Rationale:  {from Unified Vision}
  Complexity: LOW / MEDIUM / HIGH
```

**1.5c — Convention Unification**: For each C-{N} — choose canonical pattern, list every instance to update, assess migration risk.

**1.5d — Mental Model Alignment**: For each M-{N} — choose single canonical model, identify which features change their conceptual approach, assess user disorientation risk.

**1.5e — Design Era Unification**: For each D-{N} — identify target era (current/best), list every element to update.

**Exit criteria**: A user can navigate the entire app without hitting a seam. Test by narrating a user journey aloud — if you say "and here the pattern changes," the pass is not complete.

#### Pass 2 — Visual Coherence Pass

**Scope**: Make the design system coherent — not "prettier," but *consistent*.

- **2a — Design Token Consolidation**: For each one-off value → map to nearest token or document as intentional exception.
- **2b — Component Variant Unification**: List all instances of each component type → unify variants that should match.
- **2c — Color System**: Every color mapped to a token or flagged as rogue. Theme completeness. Contrast ratios verified.
- **2d — Typography**: Sizes, weights, line-heights mapped to a type scale. One style per heading level.
- **2e — Spacing Rhythm**: Vertical rhythm verified. Horizontal alignment verified.

**Exit criteria**: Every visual decision traceable to a token or an intentional exception. The app looks designed as a system.

#### Pass 3 — Interaction Polish Pass

**Scope**: Make every interaction feel responsive, intentional, and complete.

- **3a — State Change Communication**: Every action → visible feedback within 100ms. Loading/success/failure states for all async ops. Smooth transitions between states.
- **3b — Transition & Motion**: Consistent durations and easing curves. Logical spatial origins. `prefers-reduced-motion` respected. Motion budget ≤2–3 simultaneous animations per view.
- **3c — Empty & Edge States**: Designed empty states (not blank). Helpful error messages (not generic). Skeleton loaders matching content shape. Intentional overflow behavior.
- **3d — Micro-Interactions**: Button press feedback. Styled focus rings. Hover states with cursor changes. Distinct selection states.

**Exit criteria**: Zero moments of "that felt unfinished" when moving through the app.

#### Pass 4 — Copy & Content Polish Pass

**Scope**: Every word is clear, consistent, and matches the app's voice.

- **4a — Terminology Unification**: One word per concept — no synonyms for the same thing.
- **4b — Voice Alignment**: Every label, tooltip, error, and empty state matches the brand voice guide.
- **4c — Microcopy Optimization**: Specific CTAs ("Save your changes" not "Save"). Error messages that tell users what to do. Tooltips that add information. Confirmation dialogs that explain consequences.

**Exit criteria**: The interface reads as if written by someone who understood the user.

#### Pass 5 — Performance Polish Pass

**Scope**: Make the app feel fast.

- Render jank eliminated on core interactions
- Expensive computations memoized or debounced
- Assets optimized (format, dimensions, lazy loading)
- Startup optimized — critical path minimized, non-critical deferred
- Perceived performance: optimistic UI, skeleton screens, progressive loading

**Exit criteria**: Common operations feel instant. Complex operations feel responsive. No interaction takes >100ms to acknowledge.

#### Pass 6 — Accessibility Polish Pass

**Scope**: Beyond compliance — genuinely usable by everyone.

- Keyboard navigation intuitive — tab order follows visual order, focus trapping in modals
- Screen reader coherent — landmarks, headings, ARIA labels tell a complete story
- Color never the only information carrier
- Touch targets ≥44×44px on mobile
- Reduced motion fully respected

**Exit criteria**: Keyboard-only and screen reader users can accomplish every task without confusion.

---

### §XI.3. CODEBASE RESTRUCTURATION

> Pass 1.5 restructured the app's logic, flow, and mental model. This section ensures the *code* reflects that coherence. Code organized differently from the product's conceptual model creates a maintenance trap.
>
> **Claude execution note**: This is where you actually move, rename, extract, and reorganize code. Work in small, verifiable steps. Never restructure more than one module between verifications. If the app is a single file, extract outward from safest (constants) to riskiest (state).

#### XI.3.1 — Principles

1. **Code structure mirrors app structure.** If the user thinks of three main areas, the code has three main modules. Module names match feature names.
2. **Never restructure and add features simultaneously.** Regressions become undetectable.
3. **Every step independently verifiable.** Step 3 can be reverted without losing steps 1–2.
4. **Restructuring preserves behavior exactly.** Behavioral changes belong in §X.
5. **Clarity over cleverness.** A new developer reading the file tree should guess what each module does.

#### XI.3.2 — Dead Code Elimination

For each dead code block: location, type (UNREACHABLE / COMMENTED-OUT / UNUSED EXPORT / VESTIGIAL), confidence (CERTAIN / HIGH / MEDIUM), and removal verification.

**Rule**: Remove CERTAIN-confidence first. HIGH after developer confirmation. MEDIUM flagged and left.

#### XI.3.3 — Module & Component Restructuring

```yaml
Current Structure: {e.g. "Single App.jsx, 4200 lines, all components inline"}
Code-Concept Alignment: {how well current structure matches the product's conceptual model}

Target Structure:
  # Organized by product domain, not technical concern:
  # /features/banner-tracker/  — everything for tracking banners
  # /features/pull-history/    — everything for pull logging
  # /shared/                   — tokens, common components, utilities
  # /app/                      — root layout, navigation, global state
  Rationale: {why this structure — tied to user mental model from §XI.0.1}

Extraction Sequence:
  # Safest first: constants → pure utils → hooks → leaf components → 
  # composite components (bottom-up) → state management (last)
  Step 1: {what} — Risk: {L/M/H} — Verification: {check}
  Step 2: {what} — Risk: {L/M/H} — Depends on: {step 1}
```

#### XI.3.4 — State Architecture Restructuring

```yaml
Current: {e.g. "47 useState calls, 12 levels of prop-drilling, 3 context providers"}
Target:  {e.g. "Domain state in useReducer + context, UI state local, derived via useMemo"}

Migration Rules:
  - Never change state shape and consumers simultaneously
  - Introduce new system alongside old, migrate consumers one at a time, remove old
  - Every intermediate state (old + new coexisting) must be fully functional

Steps:
  Step 1: {migration} — Affected: {components} — Verify: {output unchanged}
  Step 2: ...
```

#### XI.3.5 — Dependency & Import Restructuring

Resolution order (safest first): Remove unused imports → Standardize import order → Break circular imports (verify each) → Replace heavy dependencies (behavioral verification required).

#### XI.3.6 — API & Interface Normalization

For inconsistent internal APIs (component props, function signatures, hook interfaces):
```yaml
Inconsistency: {e.g. "onClose vs handleClose vs dismiss — all mean the same thing"}
  Standard:    {canonical name — e.g. "onClose"}
  Instances:   {locations to update}
```

**Order**: Rename → Reshape → Remove. Each step independently verified.

---

### §XI.4. ARCHITECTURE EVOLUTION

> For apps that need to grow beyond their current architecture. This is not restructuring (which preserves behavior) — this is *evolving* the architecture to support new capabilities.

```yaml
Architecture Evolution Plan:
  Current:     {from §0 — e.g. "Single-file PWA, localStorage, CDN React, no auth"}
  Target:      {e.g. "Multi-file, Supabase backend, Vite build, auth + multi-user"}
  Horizon:     {timeline}

  Phase A — {name}:
    Prerequisite: {what must be true first}
    Deliverable:  {what the app can do after this that it couldn't before}
    Risk:         {data migration, feature regression, user disruption}
    Rollback:     {how to revert if it fails}
  Phase B — ...

Data Migration (when storage changes):
  Current schema:    {data shape in current system}
  Target schema:     {data shape in new system}
  Strategy:          {step-by-step migration path}
  Edge cases:        {corrupted data, declined migration, multi-device reconciliation}
  Fallback:          {old system continues working if migration fails}
```

---

### §XI.5. QUALITY GATES

#### Per-Step (after every individual change):
```
[ ] Feature Preservation Ledger: all WORKING features still work
[ ] No unintended visual changes outside this step's scope
[ ] Console: no new errors or warnings
[ ] Keyboard navigation still works
[ ] No new performance jank
```

#### Per-Pass (after completing an entire pass):
```
[ ] All per-step verifications passed
[ ] Target dimension improved (or held) — no other dimension degraded
[ ] Commit/checkpoint created — pass is independently revertible
```

#### Final Gate (§XI complete):

```
[ ] Every Quality Target from §XI.1.1 is met or exceeded
[ ] Feature Preservation Ledger: 100% of WORKING features still working
[ ] Zero CRITICAL or HIGH findings remain

COHERENCE VERIFICATION:
[ ] Every Logic Fracture from §XI.0.2 is healed — unified logic throughout
[ ] Every Flow Fracture is healed — user journey has no seams
[ ] Every Convention Fracture is healed — one pattern for each problem type
[ ] Every Mental Model Fracture is healed — one conceptual model throughout
[ ] Every Design Fracture is healed — one visual era throughout
[ ] The Unified Vision Statement (§XI.0.3) accurately describes the app as it now exists

POLISH VERIFICATION:
[ ] Design system is internally consistent — no rogue tokens, no orphaned styles
[ ] Copy is consistent — no terminology conflicts, no voice violations
[ ] Accessibility baseline met — WCAG 2.1 AA throughout
[ ] Performance baseline met — all core interactions within budget
[ ] Code quality baseline met — no dead code, consistent patterns, clear naming
[ ] Code structure mirrors product structure — a developer can navigate the code by thinking about features

HOLISTIC CHECK — the most important test:
[ ] A new user opening this app for the first time experiences ONE product, not a patchwork
[ ] A developer opening this codebase for the first time can understand its organization in 5 minutes
[ ] The app's best feature and its worst feature are now within one quality tier of each other
[ ] The developer looks at the result and says: "This is still my app — but the version I always wanted it to be"
```

---

### §XI.6. POLISH & RESTRUCTURATION DELIVERABLE

```yaml
POLISH & RESTRUCTURATION REPORT — {App Name}

COMPREHENSION:
  Purpose:    {one sentence from §XI.0.1}
  Vision:     {one sentence from §XI.0.3}
  Root cause: {key insight about how fragmentation happened}

COHERENCE HEALED:
  Logic({N}) | Flow({N}) | Design({N}) | Convention({N}) | Mental Model({N})
  Coherence: {before} → {after}

QUALITY: Baseline → Target → Achieved
  Correctness:   {__} → {__} → {__}
  Robustness:    {__} → {__} → {__}
  Performance:   {__} → {__} → {__}
  Visual Polish: {__} → {__} → {__}
  Code Quality:  {__} → {__} → {__}
  UX Clarity:    {__} → {__} → {__}
  Accessibility: {__} → {__} → {__}

PASSES:
  0-Critical({N} fixes) | 1-Structural({N}) | 1.5-Coherence({N} fractures)
  2-Visual({N}) | 3-Interaction({N}) | 4-Copy({N}) | 5-Perf({N}) | 6-A11y({N})

CODE RESTRUCTURING:
  Dead code removed: {N lines} | Modules extracted: {N}
  Code-concept alignment: {before → after}
  State simplified: {describe} | APIs normalized: {N}

ARCHITECTURE: {phases completed, data migrated, next phase}

FEATURES: {N}/{N} verified | {N} regressions caught (resolved) | {N} improved

VISION CHECK: Does the app match the Unified Vision? {YES / PARTIALLY — gaps: ...}
```
