---
name: design-aesthetic-audit
description: >
  Deep aesthetic and visual design audit for frontend apps. Trigger on: "improve the
  design", "make it beautiful", "visual critique", "aesthetic review", "art direction",
  "make it premium", "visual identity", "brand", "color palette", "typography review",
  "design language", "visual polish", "motion design", "design personality", "make it
  feel like [X]", "inspired by [source]", "look like [source]", "match the aesthetic
  of", "deepen the personality", "more distinctive", "more on-brand", "my app feels
  generic", "make it less template-y", "design tokens", "component character",
  "state design", "empty states look bad", "my dark mode sucks", or any named
  game / show / brand / IP (triggers mandatory 5-pass source research). Covers: style
  classification, color science, typography, motion, hierarchy, surface & atmosphere,
  iconography, component character, copy alignment, illustration, data visualization,
  token architecture, state design, responsive character, trend calibration, brand
  identity, competitive positioning, design character system, and source material
  intelligence with 5-layer Identity Thesis. Integrates with app-audit companion
  mode. Standalone or mid-audit.
---

# Design Aesthetic Audit — Deep Visual Analysis Framework

---

## QUICK START — How to Use This Skill

> **For Claude**: When this skill activates, follow these steps:
> 1. **Use `AskUserQuestion`** to present the §TRIAGE options (unless the user already specified design audit)
> 2. **Use `Agent` (subagent_type: Explore)** to read all style/theme/color/layout files in parallel
> 3. **Use `TodoWrite`** to create a progress tracker for the audit sections
> 4. **Fill §0** by extracting design identity from code (theme files, color definitions, styles)
> 5. **Work incrementally** — output findings per section, update progress after each
>
> **For the User**: Say any trigger phrase like "improve the design", "make it premium", "my dark mode sucks", "make it feel like [game/brand]", or "design audit" and Claude will guide you through. You can also jump directly: "run the brand identity audit", "audit my color palette", "deepen the design personality".
>
> **Platform-specific design resources to read first:**
> - **Android**: `res/values/colors.xml`, `res/values/themes.xml`, `res/values/styles.xml`, `res/values/dimens.xml`, `res/values-night/`, `res/drawable/`, `res/anim/`
> - **iOS**: Asset catalogs, `UIColor` extensions, SwiftUI theme files, `Appearance` proxies
> - **Web**: CSS files, Tailwind config, design token files, theme providers

---

## SCOPE CONVENTION — "audit/fix" vs "full deep audit/fix"

> **This is a binding instruction.** The words "full deep" in a user request are a scope multiplier.

### The Rule

| User Says | Scope | What Claude Does |
|-----------|-------|-----------------|
| **"audit/fix art design aesthetic"** | **Standard** | Run this SKILL only — the 21-step aesthetic audit path from §EXEC. Colors, typography, motion, icons, brand, tokens, states, components. |
| **"full deep audit/fix art design aesthetic"** | **Everything UI** | Run this SKILL's full 21-step path **PLUS every section from `app-audit-SKILL.md` that touches UI**. Nothing visual, interactive, or user-facing is skipped. |

### What "Full Deep" Adds (from `app-audit-SKILL.md`)

When the user says **"full deep"** for a design/aesthetic audit, expand to include:

| Added Section | Why — What It Touches |
|---------------|----------------------|
| §E (Visual Design Quality) | Structural/code side: tokens in code, spacing in layouts, contrast in implementation |
| §F1 (Information Architecture) | Navigation structure, grouping logic, discoverability |
| §F2 (User Flow Quality) | Dead ends, error recovery, back navigation |
| §F3 (Onboarding & First Use) | First-run experience, permission requests — first visual impression |
| §F4 (Copy Quality) | Labels, error messages, terminology — copy IS the visual surface |
| §F5 (Micro-Interactions) | Functional feedback loops — motion needs interaction triggers |
| §F6 (Engagement & Delight) | Personality moments, reward patterns — aesthetic at its most expressive |
| §G1-G4 (Accessibility) | Content descriptions, TalkBack, focus order, contrast, reduced motion |
| §H3 (Mobile & Touch) | Touch targets (48dp min), safe areas, gesture conflicts |
| §L3 (Design System Standard.) | Token consistency, component variants, spacing unification |
| §L4 (Copy & Content Standard.) | Voice consistency, terminology across the app |
| §L5 (Interaction & Experience Polish) | Micro-animation refinement, transition consistency |
| §D5 (Mobile Performance) | RecyclerView jank, animation frame drops — stuttering design is broken design |

### Execution Order for "Full Deep Art Design Aesthetic"

```text
PHASE 1 — Core aesthetic audit (this SKILL):
  §0 → §DS1–DS2 → §DP0–DP2 → §DBI1+DBI3 → §DC1–DC5 → §DT1–DT4
  → §DCO1–DCO6 → §DH1–DH4 → §DSA1–DSA5 → §DM1–DM5 → §DI1–DI4
  → §DST1–DST4 → §DCVW1–DCVW3 → §DIL1–DIL3 → §DDV1–DDV3
  → §DTA1–DTA2 → §DRC1–DRC3 → §DDT1–DDT2 → §DP3 → §DBI2 → §DCP1–DCP3

PHASE 2 — Expanded UI audit (from app-audit-SKILL.md):
  §E1–E10 → §F1–F6 → §G1–G4 → §H3 → §L3–L5 → §D5

PHASE 3 — Cross-reference:
  Review §VIII (Cross-Cutting Concern Map) for compound chains
  between aesthetic findings and UX/accessibility/performance findings
```

### The General Principle

**"Full deep"** applies to ANY subject, not just design. See `app-audit-SKILL.md` for the complete expansion map covering security, performance, UX, and code quality. The rule is always the same: if the subject touches it, audit it.

---

## SKILL MAP — Quick Reference

### Section Index

| Section | What It Does | Key Outputs |
|---------|-------------|-------------|
| **§TRIAGE** | Routes user to correct audit skill | Audit type selection |
| **§COMPANION** | Integrates with app-audit when both run | Context handshake, section mapping |
| **§0** | Captures design identity and axis profile | Five-Axis Quick Profile |
| **I. Style Classification** | Identifies the design school | Primary style + coherence score |
| **II. Color Science** | Audits palette architecture and craft | Perceptual color findings, palette role map |
| **III. Typography** | Assesses type personality and craft | Type matrix placement, scale audit |
| **IV. Motion** | Audits motion vocabulary and character | Motion vocabulary card, micro-interaction findings |
| **V. Hierarchy & Gestalt** | Evaluates visual weight and reading flow | Hierarchy map, contrast analysis |
| **VI. Surface & Atmosphere** | Assesses backgrounds, elevation, light | Material character, light source audit |
| **VII. Iconography** | Audits icon coherence and expressiveness | Icon system assessment, custom direction brief |
| **VIII. Trend Calibration** | Maps trend usage and strategy | Trend inventory, strategic posture |
| **IX. Brand Identity** | Engineers distinctive visual identity | Brand archetype, Design DNA, anti-genericness findings |
| **X. Competitive Positioning** | Benchmarks against visual competitors | Positioning matrix, whitespace opportunities |
| **XI. Design Character** | Extracts, analyzes, and deepens personality | Character Brief, deepening protocol |
| **XII. Source Material** | Researches named sources (games, brands, IPs) | 5-layer Source Style Brief, translation plan |
| **XIII. State Design** | Audits empty/loading/error/success states | Per-state character assessment |
| **XIV. Responsive Character** | Verifies character across viewports | Breakpoint character audit |
| **XV. Component Character** | Audits buttons, inputs, cards, nav, modals, toasts | Per-component character findings |
| **XVI. Copy × Visual** | Aligns written voice with visual character | Voice-character coherence score |
| **XVII. Illustration** | Audits graphic language and illustration | Illustration character spec |
| **XVIII. Data Visualization** | Audits chart styling and character | Chart-product alignment findings |
| **XIX. Design Tokens** | Audits token architecture layers | Token gap findings, migration path |
| **§FINDING FORMAT** | Standardized finding template | |
| **§EXEC** | Execution order for three audit paths | |

### Common Execution Paths

```text
"Audit my app's design" (general)
  → §TRIAGE → §0 → I (style) → XI.§DP0 (extract character)
  → XI.§DP1–DP2 (character brief) → IX (brand) → II (color)
  → III (type) → XV (components) → V (hierarchy)
  → VI (surface) → IV (motion) → remaining sections

"Make it feel like [source]" (source-referenced)
  → §TRIAGE → XII.§SR0 (5-pass research — IMMEDIATELY)
  → XII.§SR1 (source style brief) → XII.§SR2 (fidelity)
  → XI.§DP0–DP2 (character) → XII.§SR3 (translation)
  → II–III (color, type from source) → XV (components)
  → remaining sections → XII.§SR4–SR6 (accuracy + log)

"Deepen the personality" (character-focused)
  → §TRIAGE → XI.§DP0 (extract) → XI.§DP1 (dimensions)
  → XI.§DP2 (brief) → I (style) → IX.§DBI1 (archetype)
  → XI.§DP3 (deepening) → IV.§DM5 (motion signature)
  → XV (components) → XIII (states) → XVI (copy voice)

Companion mode (alongside app-audit)
  → Skip §TRIAGE, skip §0 → auto-populate from app-audit §0
  → I → XI.§DP0–DP2 → II.§DC1 → IX.§DBI3
  → expand as time allows
```

### Claude Execution Notes

- **§TRIAGE is shared with app-audit.** If user already selected in app-audit, skip it here.
- **For apps > 1,500 lines**: confirm with user after completing §0 + I + XI.§DP0–DP2. This gives them the character brief early.
- **Minimum companion execution**: I.§DS1 → XI.§DP0 → XI.§DP2 → II.§DC1 → IX.§DBI3 (5 sections, highest value).
- **XII (Source Material) activates ONLY when a named source is referenced.** Do not run §SR0–SR6 without a named source.
- **Always extract character (§DP0) before analyzing it (§DP1).** Never fill dimensions from imagination.
- **Do NOT attempt all 21 steps in one response.** Work through 2–4 sections per response, then pause for user feedback.

### Section Dependency Diagram

> **Claude:** Sections have dependencies. Follow the arrows — never skip an upstream dependency.

```text
§0 (Axis Profile)
  ↓
§DS1 (Style Classification) ─────────────────────────────┐
  ↓                                                       │
§DP0 (Character Extraction) → §DP1 (Dimensions) → §DP2 (Character Brief)
  ↑                                                  ↓         ↓
§SR0-SR1 (if source named)                      §DBI1    §DP3 (Deepening)
                                               (Archetype)
                                                  ↓
                                               §DBI3 (Anti-Genericness)

After §DP2 is confirmed, remaining sections can run in any order:
  §DC1-DC5 (Color)    §DT1-DT4 (Typography)    §DM1-DM5 (Motion)
  §DH1-DH4 (Hierarchy) §DSA1-DSA5 (Surface)    §DI1-DI4 (Icons)
  §DCO1-DCO6 (Components) §DST1-DST4 (States)  §DRC1-DRC3 (Responsive)
  §DCVW1-DCVW3 (Copy)  §DDV1-DDV3 (Data Viz)   §DTA1-DTA2 (Tokens)
  §DDT1-DDT2 (Trends)  §DCP1-DCP3 (Competitive)
  §DIL1-DIL3 (Illustration)
```

### Chunking Guide — Large Sections

> **Claude:** These sections are too large for a single response. Chunk them as shown:

| Section | Lines | Chunk Strategy |
|---------|-------|---------------|
| **II. Color** (~130 lines) | §DC1-DC5 | §DC1+DC2 together → §DC3 if dark mode → §DC4+DC5 |
| **III. Typography** (~120 lines) | §DT1-DT4 | §DT1+DT2 together → §DT3+DT4 |
| **IV. Motion** (~220 lines) | §DM1-DM5 | §DM1+DM2 → §DM3+DM4 → §DM5 |
| **IX. Brand Identity** (~180 lines) | §DBI1-DBI3 | §DBI1 → §DBI2 → §DBI3 (anti-genericness is one full response) |
| **XI. Character System** (~350 lines) | §DP0-DP3 | §DP0 → §DP1+DP2 → §DP3 (3 responses minimum) |
| **XII. Source Material** (~410 lines) | §SR0-SR6 | §SR0 → §SR1 → §SR2+SR3 → §SR4-SR6 |
| **XV. Components** (~200 lines) | §DCO1-DCO6 | §DCO1+DCO2 → §DCO3+DCO4 → §DCO5+DCO6 |

### Claude Code Tool Integration Protocol

> **These instructions are specific to Claude Code (CLI/web).** Use the right tool for each design audit task.

#### Tool Usage Map

| Design Audit Task | Tool to Use | Why |
|-------------------|-------------|-----|
| **Read theme/style files** | `Agent` (subagent_type: Explore) | Reads all color, style, layout files in parallel |
| **Search for color values** | `Grep` with pattern like `#[0-9a-fA-F]{6}` or `oklch` | Finds all color definitions across codebase |
| **Search for spacing/sizing** | `Grep` for `dp`, `px`, `rem`, `padding`, `margin` | Maps spatial vocabulary |
| **Search for font/typography** | `Grep` for `fontFamily`, `textSize`, `font-size`, `font-weight` | Maps type system |
| **Search for animation/motion** | `Grep` for `duration`, `anim`, `transition`, `MotionLayout` | Maps motion vocabulary |
| **Ask user for design intent** | `AskUserQuestion` | Captures desired aesthetic direction |
| **Track audit progress** | `TodoWrite` | Visible progress for multi-section audit |
| **Research sources/competitors** | `WebSearch` / `WebFetch` | Live research for §XII source material or §X competitors |
| **Implement design changes** | `Edit` / `Write` | Apply color, theme, style fixes |

#### Parallel Design Extraction Strategy

At the start of a design audit, launch parallel research agents to extract design decisions:

```
Agent(Explore, "Read all color/theme files: colors.xml, themes.xml, styles.xml, values-night/")
Agent(Explore, "Read all layout XML files and identify spacing, sizing, component patterns")
Agent(Explore, "Read all drawable/animation resources for motion and visual elements")
Agent(Explore, "Read all Kotlin/Swift files for programmatic style definitions")
```

This builds the §DP0 Character Extraction evidence base efficiently.

#### Platform-Specific Design Mapping

| CSS / Web Concept | Android XML/Kotlin | iOS / SwiftUI |
|-------------------|-------------------|---------------|
| `color: oklch(...)` / `#hex` | `<color name="...">` in `colors.xml` / `Color(0xFF...)` | `Color(.sRGB, ...)` / `UIColor` |
| `border-radius` | `app:cornerRadius` / `ShapeAppearanceModel` | `.cornerRadius()` / `.clipShape()` |
| `box-shadow` | `android:elevation` / `CardView.cardElevation` | `.shadow()` |
| `font-family` / `@font-face` | `android:fontFamily` / `res/font/` | `.font()` / `UIFont` |
| `transition` / `@keyframes` | `res/anim/`, `ObjectAnimator`, `MotionLayout` | `withAnimation()` / `UIView.animate` |
| CSS custom properties | `?attr/colorPrimary`, theme attributes | `@Environment` / appearance proxy |
| `gap` / `padding` / `margin` | `android:padding`, `android:layout_margin` | `.padding()` / `.spacing()` |
| `background-color` | `android:background` / `app:backgroundTint` | `.background()` |
| `opacity` | `android:alpha` | `.opacity()` |
| Dark mode (`prefers-color-scheme`) | `values-night/` resource qualifiers | `@Environment(\.colorScheme)` |
| Design tokens (CSS vars) | Theme overlay attributes, `MaterialTheme` | `Asset.xcassets` / theme extensions |

**When auditing Android/Material Design 3 apps:**
- Color system: Check `colorPrimary`, `colorSecondary`, `colorTertiary`, `colorSurface`, `colorSurfaceVariant` theme attributes
- Dynamic color: Is `DynamicColors.applyToActivitiesIfAvailable()` used? If so, the palette is user-device-dependent
- Shape system: Material 3 uses `ShapeAppearance` with `cornerFamily` and `cornerSize` — check for consistency
- Typography: Material 3 defines `displayLarge` through `labelSmall` — check `TextAppearance` definitions
- Elevation: Material 3 uses tonal elevation (surface color changes) not shadow elevation in dark mode
- Motion: Check `MotionUtil.kt` for custom animation helpers; check `res/anim/` for XML animations

---

## §TRIAGE — MANDATORY AUDIT ROUTING (execute BEFORE reading the rest of this skill)

**This gate fires whenever a user asks to "audit", "review", or "analyze" an app or file without specifying which audit type.**

Before loading any audit framework — **stop and ask the user which audit they want:**

| Option | Skill | What You Get | What Claude Does |
|--------|-------|-------------|-----------------|
| **Full App Audit** | `app-audit` | Code quality, security, performance, accessibility, UX, data integrity, architecture, domain correctness, i18n, AI/LLM risks, visual design (standard depth), forward-looking scenarios | Load `app-audit-SKILL.md`, stop reading this skill |
| **Design & Aesthetic Audit** | `design-aesthetic-audit` | Deep visual analysis — style classification, color science, typography craft, motion vocabulary, surface & atmosphere, brand identity, competitive positioning, design character system, source material research | Continue reading this skill from §ROLE onward |
| **Both (Companion Mode)** | `app-audit` + `design-aesthetic-audit` | Full app audit with expert-depth design analysis replacing standard §E/P6 visual sections. Longest and most thorough option | Load both skills, follow §COMPANION integration protocol |

**Use the `AskUserQuestion` tool to present these three choices.** Do not proceed until the user selects one.

```
AskUserQuestion({
  questions: [{
    question: "What kind of audit would you like?",
    header: "Audit type",
    options: [
      { label: "Full App Audit", description: "Code, security, performance, accessibility, UX, design, architecture — uses app-audit-SKILL.md" },
      { label: "Design & Aesthetic Audit", description: "Deep visual analysis — color science, typography, motion, brand identity, competitive positioning" },
      { label: "Both (Companion Mode)", description: "Full app audit + expert-depth design analysis. Longest and most thorough." }
    ],
    multiSelect: false
  }]
})
```

**Skip this triage ONLY when:**
- The user explicitly names which audit they want (e.g., "run the design audit", "do a visual critique")
- The user has already selected in a prior turn of this conversation
- The user says "continue" or "next part" during an in-progress audit
- This skill was invoked mid-audit from `app-audit` (companion mode trigger)

### Path Decision Tree

> **Claude:** After triage, determine which execution path to follow:

```text
User request
  ├─ Contains a named source (game/brand/IP)?
  │   YES → SOURCE MATERIAL PATH (§SR0 first, see §EXEC)
  │   NO ↓
  ├─ Contains "personality", "character", "distinctive", "generic"?
  │   YES → CHARACTER-FOCUSED PATH (§DP0 first, see §EXEC)
  │   NO ↓
  ├─ Is this a companion audit (app-audit already running)?
  │   YES → COMPANION PATH (skip §TRIAGE + §0, see §COMPANION)
  │   NO ↓
  └─ Default → GENERAL AUDIT PATH (§DS1 first, see §EXEC step 1–21)

If "full deep" is in the request → add PHASE 2 (app-audit §E–§L sections)
```

---

## ROLE OF THIS SKILL

This skill extends or replaces the design sections of app-audit (§E, §F6, §L3–L5) with expert-depth analysis. It operates under the same laws:
- **Identity Preservation** — polish the app's existing vision, never replace it with generic conventions
- **Axis-Governed** — every recommendation is filtered through the Five-Axis profile (§I.4 of app-audit, or §0 below if used standalone)
- **Specificity is non-negotiable** — "improve the colors" is not a finding. "The accent `#3b82f6` has 87% HSL saturation — perceptually oversaturated relative to the warm-dark surface it sits on; shifting to OKLCH `oklch(65% 0.15 255)` would retain the hue identity while reading as more calibrated" is a finding.

### Conditional Sections — Skip Guide

> **Claude:** Not every section applies to every app. Skip these sections when the condition is met:

| Section | Skip When |
|---------|-----------|
| §DC3 (Dark Mode) | App is light-mode only — no `values-night/` or dark theme |
| §DC5 (Color as Narrative) | User is pressed for time — lowest priority in Color |
| §DT4 (Variable Fonts) | No variable font detected in the codebase |
| §DM5 (Motion Signature) | App has no animations at all (flag the absence instead) |
| §DSA4-DSA5 (Light Physics, Focal) | App uses flat design with no elevation/atmosphere |
| §DBI2 (Design DNA) | Quick audit — §DBI3 (anti-genericness) is higher value |
| §DIL1-DIL3 (Illustration) | App has no illustrations, custom graphics, or spot illustrations |
| §DDV1-DDV3 (Data Visualization) | App has no charts, graphs, or data visualizations |
| §DRC1-DRC3 (Responsive) | App is single fixed-width layout (not responsive) |
| §DDT1-DDT2 (Trends) | Expert tools where trend relevance is zero |
| §DCP1-DCP3 (Competitive) | User says they don't care about competitors |
| §SR0-SR6 (Source Material) | No named source (game, show, brand, IP) referenced |

---

## §COMPANION. APP-AUDIT INTEGRATION

> **Claude execution note**: Check which mode you're in BEFORE starting. In companion mode, auto-populate §0 from app-audit's §0 — do NOT re-ask the user questions already answered. The minimum companion path (§DS1 → §DP0 → §DP2 → §DC1 → §DBI3) takes ~1 response and covers the highest-value findings.

This skill operates in two modes. **Know which mode you are in before starting.**

### Companion Mode (invoked during or after app-audit)

When this skill is called alongside `app-audit`, the two skills share state. Do not re-run what app-audit already ran.

**Context handshake — auto-populate from app-audit §0:**
```
From app-audit §0:            → Maps to this skill:
  App Name                    → §0 Design Identity / App name
  Audience                    → §0 Five-Axis A3
  Stakes                      → §0 Five-Axis A2 (HIGH stakes = focus-critical / emotional)
  Domain                      → §0 Five-Axis A1 + A4 (infer subject identity)
  Subject Identity (if A4)    → §SR0 trigger (named source = mandatory research)
  Design Intent (if stated)   → §0 Intended style + Personality
```

**Section mapping — findings from this skill feed app-audit at these exact locations:**

| This skill produces | Feeds into app-audit at |
|---|---|
| §DS1–DS2 (style classification) | P6 — Visual Design · §E findings |
| §DC1–DC4 (color architecture) | P6 — §E findings (color-specific) |
| §DT1–DT3 (typography) | P6 — §E findings (type-specific) |
| §DH1–DH3 (hierarchy) | P6 — §E findings (layout-specific) |
| §DM1–DM3 (motion) | P6 — §E findings (motion-specific) |
| §DSA1–DSA3 (surface) | P6 — §E findings (surface-specific) |
| §DBI1–DBI3 (brand identity) | P6 §F6 — Design differentiation |
| §DP1–DP3 (character system) | P6 §L3–L5 — Design personality |
| §SR1–SR6 (source material) | P6 §F6 + §L3–L5 — Source-derived recommendations |
| §DCP1–DCP3 (competitive) | P6 §F6 — Competitive positioning |

**Output format in companion mode:**
```
━━━ DESIGN AESTHETIC SUPPLEMENT ━━━━━━━━━━━━━━━━━━━━━━━━━━━━
Appended to app-audit Part P6 (Visual Design · Polish · Design System)
All findings prefixed §D to distinguish from app-audit's §E findings.
Severity scale matches app-audit: [CRITICAL] [HIGH] [MEDIUM] [LOW] [POLISH]
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
```

**When to invoke this skill mid-audit:**
- App-audit §0 A4 is `Strong aesthetic` or `Aesthetic IS subject` → invoke immediately after app-audit §0
- App-audit finds named source reference in comments, README, or design tokens → trigger §SR0 immediately
- App-audit's §E findings include ≥3 color findings → deeper color audit via §DC1–DC4 will be higher value
- User asks to "make it feel like [source]" while app-audit is in progress → pause, run §SR0 first

**Minimum companion execution** (when pressed for time — cover this, skip the rest):
```
§DS1 → §DP0 → §DP2 → §DC1 → §DBI3
(style ID) (character extract) (brief) (color arch) (genericness)
```
This covers the highest-value findings in the shortest path.

---

## §0. AESTHETIC CONTEXT (standalone use — skip if app-audit §0 already filled)

> **Claude execution note**: In standalone mode, fill this from the codebase. Ask the user to confirm the personality and protected elements — these are judgment calls they must own. In companion mode, auto-populate from app-audit §0 via the §COMPANION handshake and skip this entirely.

```yaml
Design Identity:
  Current style:    # Describe what you see — e.g. "dark minimal with cyan accent"
  Intended style:   # What the developer intended, if stated
  Personality:      # The emotional character it should project
  Protected elements: # Visual signatures that MUST be preserved

Five-Axis Quick Profile:
  A1 Commercial intent: # Revenue-generating / Institutional / Non-revenue
  A2 Use context:       # Focus-critical / High-stakes / Emotional / Creative / Leisure / Transactional
  A3 Audience:          # Expert / Enthusiast / General public / Mixed
  A4 Subject identity:  # Strong aesthetic / Community norms / Domain conventions / Neutral / Aesthetic IS subject
  A5 Aesthetic role:    # IS the value / Amplifies value / Communicates identity / Must stay invisible
```

---

## I. AESTHETIC STYLE CLASSIFICATION

> **Claude execution note**: Always complete §DS1 + §DS2 before any other section. The style classification is the lens for all subsequent findings — a color recommendation correct for minimal design may be wrong for neo-brutalism. Present the style classification to the user and confirm before proceeding.

**Identify the design school before writing any finding.** A recommendation correct for flat/minimal design may be actively wrong for neo-brutalist design.

### §DS1. Design Language Identification

Classify the app's current visual language against this taxonomy. One primary style + up to two secondary influences:

| Style School | Visual Signatures | Common Mistakes | Correct Reference |
|---|---|---|---|
| **Minimal / Flat** | Maximum whitespace, no shadows or decorations, type-forward, monochromatic or 2-color | Too much space without rhythm; no focal point | Linear, Vercel, Stripe |
| **Material / Elevation** | Layered surfaces, directional shadows, Z-axis depth, consistent light source | Inconsistent shadow angle; shadow over-use | Google Material 3, Notion |
| **Glassmorphism** | Frosted-glass blur, translucency, bright backdrop required, thin borders | Illegible text over texture; blur on dark = mud | Arc browser, iOS Control Center |
| **Neo-Brutalist** | Raw utility aesthetic, intentional visual tension, heavy borders, stark typography | Accidentally ugly vs intentionally raw | Gumroad (old), Figma community |
| **Skeuomorphic / Organic** | Texture, physical metaphors, depth through material, crafted surfaces | Over-literal; uncanny valley effect | Lasso, Bear Notes |
| **Cyberpunk / Terminal** | Dark OLED, glowing accents, monospace, scanlines, high-contrast edges | Oversaturated neon; illegible | Raycast, Warp terminal |
| **Editorial** | Typography-primary, strong grid, deliberate negative space, content-forward | Boring without type mastery | Are.na, Siteinspire |
| **Retro / Nostalgic** | Era-specific palette, period typography, intentional anachronism | Kitsch vs authentic; wrong decade signals | Poolside FM, Poolsuite |
| **Neumorphism** | Extruded surfaces, dual shadow, soft monochromatic | Very low contrast; accessibility failure | Dribbble shots (rarely ships) |
| **Data-Dense / Terminal** | Information maximalism, tabular rhythm, scannable density | Visual noise without hierarchy | Bloomberg Terminal, Linear |
| **Aurora / Liquid** | Gradient meshes, color-shift backdrops, living color | Distracting; hard to read text over | Arc, Framer |

**Output required:**
```
Primary style: [identified style]
Secondary influences: [0–2 styles]
Coherence score: COHERENT / MIXED-INTENTIONAL / ACCIDENTALLY MIXED
Style-appropriate execution: [2–3 sentences on whether the style's specific rules are followed]
```

### §DS2. Style Coherence Assessment

- **Consistent style vocabulary**: Are all components using the same visual language? A flat-minimal card next to a material-elevation modal on the same screen signals unintentional mixing.
- **Style inflection points**: Identify every component that breaks the established visual language. Document: what style it belongs to, what style the rest of the app uses, and the specific change that would bring it into alignment.
- **Intentional tension vs accidental mixing**: Neo-brutalism may intentionally mix conventions — but if the style is intended to be coherent, any mixing is a bug. Determine intent before flagging.
- **Style-appropriate detail level**: Minimal design requires extraordinarily precise spacing (every 1px off is visible). Material design requires precisely calibrated shadow elevation. Cyberpunk requires consistent glow calibration. Is the craft level appropriate for the style?
- **Material Design version mixing** *(Android)*: Are Material 2 and Material 3 components used on the same screen? `MaterialCardView` (M2) next to `MaterialToolbar` with M3 theming signals an incomplete migration. Grep for `com.google.android.material` imports and check which version each component targets. M2 components use `Widget.MaterialComponents.*` styles; M3 uses `Widget.Material3.*`.
- **XML View + Compose interop coherence** *(Android)*: If the app uses both XML layouts and Jetpack Compose, are the design tokens shared? Compose `MaterialTheme.colorScheme` and XML `?attr/colorPrimary` should resolve to the same values. Check for `MaterialTheme { }` wrapping `AndroidView` — without it, Compose and XML will drift visually.
- **Theme attribute vs hardcoded value audit** *(Android)*: Grep for hardcoded `#RRGGBB` in XML layouts and `Color(0xFF...)` in Kotlin. Every hardcoded color is a style coherence break — it won't respond to theme changes, dark mode, or Dynamic Color. Should reference `?attr/colorSurface`, `?attr/colorOnSurface`, etc.
- **Shape system consistency** *(Android)*: Material 3 defines shape families (`ShapeAppearance.Material3.Corner.Small`, `.Medium`, `.Large`, `.ExtraLarge`). Are components using consistent shape families for their size class, or are `cornerRadius` values scattered arbitrarily? Buttons/chips → Small, Cards → Medium, Sheets → Large.
- **Elevation vs tonal surface mixing**: In Material 3 dark mode, elevation is expressed through tonal surface color (lighter = higher), not shadow. If some components use `android:elevation` shadows while others use `?attr/colorSurfaceContainerHigh`, the depth language is incoherent.

---

## II. COLOR SCIENCE DEEP DIVE

> **Claude execution note**: §DC1 (perceptual architecture) and §DC2 (palette roles) are the highest-value color sections — always complete them. §DC3 (dark mode) only if the app has dark mode. §DC4 (brand distinctiveness) is high-value for products competing in crowded markets. §DC5 (color as narrative) is the deepest layer — skip if the user is pressed for time, but it produces the most differentiated findings.

### §DC1. Perceptual Color Architecture

**The fundamental problem with HSL:** HSL's "lightness" is not perceptually uniform — a yellow at HSL(60, 90%, 60%) looks far brighter than a blue at HSL(240, 90%, 60%) at the same values. Professional-grade palettes are built in OKLCH or LCH to achieve perceptual uniformity.

**Assessment questions:**
- Does the palette feel chromatically consistent across hues? (Blues, greens, and yellows at the "same" HSL saturation often feel drastically different in visual weight)
- Are the dark-mode surface colors using near-black with slight hue `oklch(12% 0.01 240)` rather than neutral gray `#1a1a1a`? Chromatic darks feel more refined.
- Is the accent color at peak chroma for its hue? Some hues (yellow, green-yellow) have much lower max chroma than others (blue, purple) — the palette should account for this.

**Color temperature coherence:**
- Map the palette temperatures: warm (orange/red family), neutral (gray family), cool (blue/green family)
- A mixed-temperature palette creates subconscious friction unless the temperature contrast is intentional (e.g., warm UI elements on cool surface = action pops)
- Identify the dominant temperature, then flag any element in strong conflict with it

### §DC2. Palette Architecture Audit

**Primary role inventory** — every color in the app should have exactly one semantic role:

| Role | Token Name | Current Value | Assessment |
|---|---|---|---|
| Background (deepest) | `--bg-base` | | |
| Surface layer 1 | `--bg-surface` | | |
| Surface layer 2 | `--bg-elevated` | | |
| Primary text | `--text-primary` | | |
| Secondary text | `--text-secondary` | | |
| Muted/disabled text | `--text-muted` | | |
| Accent primary | `--accent` | | |
| Accent hover | `--accent-hover` | | |
| Destructive | `--color-danger` | | |
| Success | `--color-success` | | |
| Warning | `--color-warning` | | |
| Border default | `--border` | | |
| Border focus | `--border-focus` | | |

For every role not covered by a token: **it is a design system gap.**

**Semantic color problems to find:**
- Multiple similar grays with no documented role distinction
- Accent color used in >3 semantic contexts (overloaded = loses meaning)
- No distinct hover/active/focus states for the accent
- Danger color that is too saturated or too similar to the warning color
- Success green that visually conflicts with the app's warm palette

### §DC3. Dark Mode Craft Assessment

*(Skip if app is light-mode only)*

The elevation-as-lightness system: in dark mode, elevation is communicated by lightness, not shadows. Each layer should be ~2-4% OKLCH lightness higher than the layer below it.

- **Surface 0** (page background): typically `oklch(10-14% chroma hue)` — darkest
- **Surface 1** (cards, panels): +3–4% lightness
- **Surface 2** (modals, popovers): +3–4% further
- **Surface 3** (tooltips, toasts): highest lightness among surfaces

**Common dark mode failures:**
- All surfaces the same value → no perceived depth
- Using pure black `#000000` as background (appropriate only for OLED/theater mode)
- Shadows on dark mode (they're nearly invisible and carry no visual information — use lightness instead)
- Hardcoded `color: white` text on dark surfaces (loses readability on lighter surface variants)
- Light mode asset assets (logos, illustrations) not inverted/adapted for dark context

### §DC4. Brand Color Distinctiveness

- **Hue ownership**: Does the primary accent hue feel distinctive in the competitive landscape? Or is it "another blue SaaS"? For Android file managers: is it "another teal/blue Material app"?
- **Calibration signature**: The specific saturation and lightness of the accent is the brand signature — not just the hue. `#3b82f6` (Tailwind blue-500) is generic. A carefully calibrated `oklch(62% 0.22 256)` can own the same family while feeling distinct.
- **Recommendation format**: When suggesting palette improvements, always provide specific values in both `oklch()` and hex. OKLCH reasoning + hex for implementation.
- **Dynamic Color interaction** *(Android)*: If the app uses `DynamicColors.applyToActivitiesIfAvailable()`, the brand color is device-dependent. Assess: does the app have a fallback identity when Dynamic Color is unavailable (API < 31)? Is the custom theme distinctive enough to justify NOT using Dynamic Color? Some apps are better served by owning their color than by adapting to the wallpaper.
- **Platform default detection** *(Android)*: Material 3's default `colorPrimary` is `#6750A4` (purple). If the app ships with this or any unmodified Material default, it has zero color identity. Grep `colors.xml` for the Material baseline values: `#6750A4`, `#625B71`, `#7D5260`, `#B3261E`.
- **Competitive hue mapping**: List the top 5 apps in the same category. Map each to its primary accent hue. If the current app's accent falls within 15° of any competitor's hue in OKLCH space, it risks visual confusion. The fix is recalibration — shift hue, adjust chroma, or move to a different hue region entirely.
- **Icon → accent coherence**: The launcher icon (adaptive icon foreground) establishes the first color association. Does the in-app accent color match the icon's dominant color? A mismatch between icon color and in-app accent creates brand fragmentation — the user associates one color in the app drawer and encounters a different one inside.

### §DC5. Color as Narrative

Color does more than identify — it tells a story at every moment of the user's experience. Most apps use color statically; strong products use it dynamically, directively, and expressively.

**Gradient design as a design decision** (not decoration):

A gradient is a color argument. It claims: *"this space has a direction."* Assess every gradient in the app:

| Gradient Type | What it says | Common mistake |
|---|---|---|
| **Linear top→bottom** | Above is light/air, below is ground/weight | Reversed (heavy top = oppressive) |
| **Linear left→right** | Movement, progress, forward direction | No clear narrative reason |
| **Radial from center** | Energy emanates from this point | Centered without focal hierarchy |
| **Radial from corner** | Off-screen light source, cinematic | Overpowered, distracts from content |
| **Mesh/aurora** | Living atmosphere, not a surface | Text placed over it without care |
| **Conic** | Rotation, process, selection rings | Rarely appropriate in UI |

**For each gradient found:** State what visual argument it makes. Does that argument serve the product's narrative at that moment?

**Tension colors** — the tool most designers skip:

A tension color is a secondary accent used specifically to create *dynamic contrast* with the primary accent. It is not a semantic color (not "danger" or "success") — it is a compositional color used to make certain moments feel electrically alive.

- The tension color should sit roughly 120°–150° away on the color wheel from the primary accent
- It appears rarely — 3–5 times maximum in an entire app — which is what gives it power
- Every appearance should mark a moment of genuine significance: a milestone, a key CTA, a featured item
- If the tension color appears on generic elements, it has been devalued

**Assess:** Does the app have a tension color? Is it used appropriately? Or is the accent doing all the work alone, producing flat energy?

**Color as state narrative** — how color tells the user where they are:

Map the color state of the app at each key moment. A strong narrative flows:
```
Onboarding  → [describe the color experience — what color energy does arrival have?]
Engagement  → [the "working" state — what does color say during active use?]
Achievement → [success — does color celebrate? what changes?]
Error       → [failure — does color communicate gravity without panic?]
Empty       → [nothing yet — does color feel hopeful or clinical?]
```

Each transition should feel intentional. If the color experience is flat throughout — the same palette at every emotional beat — it is a missed narrative opportunity.

**Color harmony system used:**

Identify which harmony structure governs the palette:
- **Monochromatic**: one hue, full lightness range — cohesive but risks monotony without strong tension color
- **Analogous**: 2–3 adjacent hues — warm and harmonious, less energetic; find which hues and whether they span the right arc
- **Split-complementary**: primary + two hues adjacent to its complement — dynamic without full tension; assess whether the split pair is calibrated or accidental
- **Triadic**: three equidistant hues — high energy, hard to manage; if present, assess whether any hue is being suppressed to maintain dominance hierarchy

If the palette doesn't conform to any structure: it is likely accidental. Propose the closest intentional structure.

---

## III. TYPOGRAPHY AS VISUAL EXPRESSION

### §DT1. Type Personality Matrix

Every typeface sits somewhere on these spectra. Assess the current typeface and whether it matches the app's §0 personality:

```
Sans-Serif spectrum:
  Geometric ←————————————→ Humanist
  (DM Sans, Geist)          (Inter, Plus Jakarta)
  [Precise, modern, cold]   [Approachable, warm, readable]

  Grotesque ←————————————→ Neo-Grotesque
  (Helvetica, Aktiv)         (GT Walsheim, Neue Haas)
  [Neutral authority]        [Character + neutrality]

Serif spectrum:
  Transitional ←—————————→ Old-style
  (Times, Georgia)            (Garamond, EB Garamond)
  [Authoritative, print-like] [Literary, historical warmth]

  Slab ←—————————————————→ Didone
  (Rockwell, Zilla Slab)      (Playfair, Bodoni)
  [Sturdy, editorial]         [High contrast, luxury]

Display:
  [Only appropriate at 32px+ — never use display faces for body text]
```

**For the current typeface:**
1. Place it on the matrix above
2. Assess whether its position matches the §0 personality
3. If misaligned: name a specific alternative at the correct position that would serve better *within the stack constraints*

### §DT2. Typographic Scale & Rhythm

**Scale coherence:** Extract every unique `font-size` used in the app. A coherent scale follows a ratio (typically 1.25 — Major Third, or 1.333 — Perfect Fourth):

```
Perfect Fourth (1.333):  12 / 16 / 21 / 28 / 37 / 50
Major Third (1.25):      12 / 15 / 19 / 24 / 30 / 38
```

- List every size used. Do they fall on a scale? Any in-between values with no clear reason?
- Sizes between scale steps feel accidental. Each must be intentional with a clear semantic role.

**Weight contrast:** The visual difference between the heaviest and lightest weight in the app is the "type contrast." Too narrow = everything looks the same weight = no hierarchy. Too wide = jarring. Ideal contrast uses a minimum of 2 weight steps between adjacent hierarchy levels.

**Tracking (letter-spacing) by size:**
- Body text: typically 0 to +0.01em (optical default)
- UI labels (10-12px): typically +0.03em to +0.06em (compressed letters need air)
- Headings (24px+): typically -0.01em to -0.03em (optical tightening)
- All-caps labels: +0.06em to +0.12em (mandatory — all-caps without tracking is painful)
- Assess every level against these norms

### §DT3. Advanced Type Craft Signals

**Tabular numerals:** Any column of numbers, pricing, timestamps, or statistics should use `font-variant-numeric: tabular-nums`. Without this, proportional numeral widths cause columns to misalign. This is one of the most visible craft gaps in SaaS products.

**OpenType feature usage** *(for variable/OTF fonts)*:
- `font-feature-settings: "kern" 1` — kerning (often default, verify)
- `font-variant-ligatures: common-ligatures` — fi/fl ligatures in display text
- `font-variant-numeric: oldstyle-nums` — for flowing text where numbers should feel like text

**Orphans and widows in multi-line display text:** A single word on the last line of a heading or large display text is an orphan — it reads as unfinished. Use `text-wrap: balance` (CSS, progressive enhancement) or manual `<br>` at known breakpoints.

**Type rendering quality:**
- `-webkit-font-smoothing: antialiased` — improves rendering on Retina/HiDPI, makes dark-mode type feel lighter and more refined
- `text-rendering: optimizeLegibility` — enables kerning and ligatures where not default
- Both should be applied at the body or root level for any polished product

### §DT4. Typographic Voice and Expressiveness

**Typography is not just readable — it speaks.** Beyond legibility craft, assess how the typographic decisions create a distinct voice and whether that voice matches the product's character.

**The measure (line length) as intimacy control:**

Measure — the number of characters per line — is one of the most powerful and most-ignored typographic tools:
- 45–75 characters: optimal for sustained reading; feels conversational
- 30–45 characters: tight, columnar; feels editorial, efficient, professional
- 75–100 characters: wide; feels open, reference-like, encyclopedic
- Under 30: fragmented; creates rhythm and punch (appropriate for taglines, not body)
- Over 100: straining; reader loses place; anti-pattern for reading contexts

**Assess every text block against its function**: Is the measure appropriate? A narrow 40-char measure on a 1440px screen wastes space and signals the layout wasn't designed — it was left at default.

**Line-height as breathing room:**

Line-height is the spatial character of text. It communicates how much the product respects the reader's time:
- `line-height: 1.4` — tight, dense, information-first; appropriate for data tables, code
- `line-height: 1.5–1.6` — standard; comfortable for most interface text
- `line-height: 1.7–1.8` — generous; warm, editorial, unhurried; appropriate for long-form reading
- `line-height: 2.0+` — very open; creates visual breathing room, almost poetic

**Typography as composition element:**

In aesthetic-primary products (A5: Aesthetic IS subject / amplifies value), type can be used expressively beyond its informational role:
- **Scale contrast as visual texture**: Pairing a very large display text with very small secondary text in the same viewport creates compositional energy without any additional graphics
- **Weight contrast as hierarchy drama**: Heavy/thin pairings (900 weight headline + 300 weight subtext) create visual tension that communicates confidence and editorial sophistication
- **Alignment breaks as emphasis**: A right-aligned element in a left-aligned context draws the eye with no additional styling needed — intentional alignment breaks are invisible emphasis tools
- **Typography as illustration**: Large, styled letterforms, numeral displays, or monogram treatments can function as the primary visual element of a hero or empty state, replacing the need for illustration

**Variable font utilization** *(if a variable font is detected in use)*:

Variable fonts have axes beyond weight. Assess whether the app exploits them:
- `wght` axis: weight variation (common — but is the full range used or just two stops?)
- `opsz` (optical size): designed for legibility at different sizes — dramatic improvement at small sizes
- `wdth` axis: width variation — can compress text without switching fonts
- Custom axes (some variable fonts have branded axes): check font documentation

**Typographic personality moments:**

Like motion personality moments (§DM3), identify where typography could be used expressively at key product moments:
- The empty state: does it use type as a design element or just "Nothing here yet."?
- The error state: does the error message typography feel appropriate (serious without being clinical)?
- The success state: can the typography celebrate in some way (size? weight? color?) without being gratuitous?
- The loading state: does placeholder/skeleton typography match the rhythm of what it's replacing?

For each key state: assess whether the typography is doing expressive work or just filling space.

---

## IV. MOTION ARCHITECTURE

> **Claude execution note**: §DM1 (vocabulary card) is the core deliverable — always produce it. §DM4 (micro-interactions) produces the most actionable findings for developers. §DM5 (motion signature) is highest-value when the user wants personality deepening. If the app has no animations at all, note the absence and recommend a starter vocabulary instead of auditing nothing.
>
> **Claude Code** — extract motion values before filling §DM1:
> - Android XML animations: `Grep(pattern: "android:duration|android:interpolator|android:fromAlpha|android:toAlpha", glob: "**/res/anim/**")`
> - Android Kotlin animators: `Grep(pattern: "setDuration|ObjectAnimator|ValueAnimator|SpringAnimation|interpolator|MotionLayout", type: "kotlin")`
> - Web CSS transitions: `Grep(pattern: "transition:|animation:|@keyframes|animation-duration", glob: "*.css")`
> - Motion helpers: `Grep(pattern: "MotionUtil|AnimationHelper|Anim", glob: "*.{kt,java}")`
> - Also: `Glob(pattern: "**/res/anim/*.xml")` to inventory all animation resource files

### §DM1. Motion Vocabulary Card

A professional motion vocabulary consists of 4–6 canonical transition definitions applied consistently throughout the app. Audit the current state and produce the card:

```yaml
Motion Vocabulary (Current Assessment)
────────────────────────────────────────
Micro-feedback (button press, toggle):
  Duration: [measured or inferred] | Easing: [measured or inferred]
  Appropriate?: YES / NO — [reason]

Entrance (appear, expand, slide-in):
  Duration: __ | Easing: __ | Direction: __
  Appropriate?: YES / NO

Exit (disappear, collapse, slide-out):
  Duration: __ | Easing: __ | Direction: __
  Appropriate?: YES / NO

Navigation (page/tab transition):
  Duration: __ | Easing: __ | Spatial story: __
  Appropriate?: YES / NO

State change (loading → loaded, empty → filled):
  Duration: __ | Easing: __ | Character: __
  Appropriate?: YES / NO

Recommendation for unified vocabulary:
  Micro-feedback:   100ms ease-out
  Entrance:         200ms ease-out (exits: 150ms ease-in — exits should be faster)
  Page transition:  250ms ease-in-out
  Emphasis/delight: 300–400ms spring(1, 0.6, 0.4) [if React Spring/Framer available]
```

*(Adjust recommendations based on §0 A2 axis — focus-critical = all timings at bottom of range; emotionally sensitive = all at top of range)*

### §DM2. Motion Character vs Axis Profile

The *character* of motion should match the product's personality:

| Motion Character | When Appropriate | Implementation |
|---|---|---|
| **Mechanical / instant** | Developer tools, terminals, expert tools | Minimal or no transitions; state appears immediately |
| **Brisk / confident** | Productivity SaaS, work tools | 100–200ms, ease-out, no spring |
| **Considered / graceful** | Premium consumer, professional tools | 200–300ms, ease-in-out, subtle scale |
| **Organic / warm** | Wellness, consumer, emotional contexts | 250–400ms, ease-in-out, slight spring |
| **Expressive / playful** | Creative tools, leisure, games | Spring physics, variable duration, personality moments |
| **Dramatic / cinematic** | Portfolio, creative showcase, aesthetic-primary | Long duration, staggered, orchestrated |

For the current app: does the motion character match the correct row above given its axis profile?

### §DM3. Motion Performance Audit

- **Compositor-only properties** *(Web)*: Smooth 60fps animation uses only `transform` and `opacity`. Animating `height`, `width`, `top`, `left`, `background-color`, `box-shadow` triggers layout/paint — jank risk.
- **Hardware-accelerated properties** *(Android)*: `ObjectAnimator` on `translationX`, `translationY`, `alpha`, `scaleX`, `scaleY`, `rotation` runs on the RenderThread — 60fps even if the main thread is busy. Animating `width`, `height`, `padding`, or calling `requestLayout()` during animation triggers expensive measure/layout passes. Use `ViewPropertyAnimator` (`view.animate().translationX().alpha()`) for the simplest hardware-accelerated path.
- **GPU layer promotion** *(Web)*: `will-change: transform` tells the browser to create a composite layer. Over-use wastes memory; under-use causes jank on complex animations. Use only where actually needed.
- **Hardware layer hint** *(Android)*: `view.setLayerType(View.LAYER_TYPE_HARDWARE, null)` during animation renders the view to an off-screen buffer — equivalent to GPU layer promotion. Set it before animation starts, clear it (`LAYER_TYPE_NONE`) when animation ends. `ViewPropertyAnimator` does this automatically with `.withLayer()`.
- **Stagger patterns**: Sequential element entrances (lists, grids) should stagger by 30–50ms per element. Less = no visible stagger. More = feels slow. Cap at 150ms total regardless of count. On Android, use `LayoutAnimationController` with `android:delay="0.15"` (15% of duration) or `RecyclerView.ItemAnimator` with staggered `startDelay`.
- **Reduced motion compliance**: Every animation must have an accessibility alternative.
  - *Web*: `prefers-reduced-motion` media query — not just CSS transitions; JS-driven animations need an explicit check.
  - *Android*: Check `Settings.Global.ANIMATOR_DURATION_SCALE`. When `0f`, all `ObjectAnimator`/`ValueAnimator` durations are automatically zeroed. But custom animations using `postDelayed()` or manual frame calculations must check this value explicitly: `val scale = Settings.Global.getFloat(contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f)`.
  - *iOS*: `UIAccessibility.isReduceMotionEnabled` / `accessibilityReduceMotion` environment value.
- **MotionLayout performance** *(Android)*: `MotionLayout` is powerful but expensive — it inflates a `MotionScene` and calculates constraint interpolation every frame. For simple translations/fades, `ViewPropertyAnimator` is significantly cheaper. Reserve `MotionLayout` for complex multi-property coordinated transitions (e.g., toolbar collapse with parallax + fade + scale).
- **RecyclerView animation jank** *(Android)*: `DefaultItemAnimator` runs change/add/remove animations. If the adapter calls `notifyDataSetChanged()` instead of granular `notifyItemInserted()`/`notifyItemRemoved()`, all item animations are lost and a full rebind occurs. Use `DiffUtil` or `ListAdapter` for smooth, animated list updates. Check for `setHasStableIds(true)` to enable cross-dataset animation.
- **Frame profiling** *(Android)*: Use `FrameMetrics` API (API 24+) or `adb shell dumpsys gfxinfo <package>` to identify frames exceeding 16ms. GPU rendering profile bars (`Settings > Developer Options > Profile GPU Rendering`) give real-time visual feedback during animation review.

### §DM4. Micro-interaction Design

Micro-interactions are the interactions that happen at the human scale — the 50–200ms moments that constitute the texture of using the app. They are the primary differentiator between apps that feel "polished" and apps that feel "functional." Most apps invest zero design thought here and leave them at browser/OS defaults.

**The five micro-interaction domains to audit:**

**1. Hover states** *(Web)* **/ Touch feedback states** *(Android/iOS)* — the most important and most neglected

*Web:* Every interactive element should have a hover state that:
- Communicates "I am clickable" through a change beyond cursor
- Expresses the product's motion character (instant = mechanical/expert; fade 150ms = considered)
- Is consistent: all buttons of the same type respond identically

Common hover failures:
- Background-only hover (`bg-gray-100`) with no other signal — only works if the element was already recognizable as interactive
- Hover that is too dramatic (full color shift, large scale) — distracts from what it should confirm
- No hover state on interactive-but-not-visually-obvious elements (list rows, card clicks, icon buttons)
- Hover state that breaks text contrast or border legibility

*Android (no hover — touch feedback replaces it):*
- **Ripple effect** is the primary touch feedback mechanism. Every interactive element must have a ripple: `android:foreground="?attr/selectableItemBackground"` (bounded) or `?attr/selectableItemBackgroundBorderless` (unbounded for icons). Assess: are all clickable views showing ripple on tap? Custom views that set `OnClickListener` without a ripple background feel broken.
- **StateListDrawable / ColorStateList**: For elements that need state-dependent appearance beyond ripple (pressed, focused, selected, disabled), use `<selector>` drawables or `ColorStateList` in `res/color/`. Assess: do buttons have distinct pressed/disabled/focused appearances?
- **Ripple color calibration**: Default Material ripple is `?attr/colorControlHighlight`. For character-expressive apps, override per-component: accent-tinted ripple on primary actions, neutral ripple on secondary elements. `RippleDrawable` accepts a `ColorStateList` for the ripple layer.

**Assess:** *Web*: Does every interactive element have a hover state? *Android*: Does every tappable element show ripple or equivalent touch feedback? Is the feedback motion consistent and appropriate to the product character?

**2. Focus rings** (accessibility-critical craft element)

The default browser focus ring is functional but often ugly. A custom focus ring is both an accessibility improvement and a design expression:

```css
/* Character-positive focus — for a "precision instrument" product */
:focus-visible {
  outline: 1.5px solid oklch(65% 0.22 256);
  outline-offset: 2px;
  border-radius: inherit; /* follows the element's shape */
}

/* Character-positive focus — for a "warm editorial" product */
:focus-visible {
  outline: 2px solid oklch(72% 0.12 60); /* warm amber */
  outline-offset: 3px;
  box-shadow: 0 0 0 4px oklch(72% 0.12 60 / 0.15);
}
```

*Android focus indication:*
- Focus rings matter on Android for keyboard navigation (Bluetooth keyboards, ChromeOS), Switch Access, and D-pad (Android TV). The default Material focus indicator is a color state change — often insufficient for visibility.
- Override `android:stateListAnimator` or use `ColorStateList` with `android:state_focused="true"` to provide a visible focus ring. Material 3 components use `?attr/colorSecondary` for focus indication.
- In Compose: `Modifier.focusable()` with `Modifier.border()` in focused state, or `indication = rememberRipple()` which shows a persistent highlight on focus.

**Assess:** Does the app have custom focus styles? Do they match the product's visual character? Do they meet WCAG 3:1 contrast ratio against adjacent background? *(Android)*: Is focus visible when navigating with a keyboard or Switch Access?

**3. Active/press states** (the "click/tap confirmation")

The moment between pressing and releasing — 50–100ms — is the most visceral feedback moment. If nothing visually changes on press, the UI feels unresponsive.

*Web* active state convention:
- Scale: `scale(0.97)` — subtle compression communicates "pressed into the surface"
- Brightness: `brightness(0.92)` — slight darkening confirms contact
- Both: produces the most convincing physical feedback

*Android* press feedback layers:
- **Ripple** provides the primary visual feedback (see domain 1 above)
- **Elevation change**: Material buttons reduce elevation on press (`stateListAnimator` drops from `2dp` to `0dp`). Custom elevated views should follow the same pattern — press = closer to surface.
- **Haptic feedback**: `view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)` adds tactile confirmation. Use sparingly — on primary actions, destructive confirmations, and toggle state changes. Overuse makes every tap feel the same.
- **Scale on long press**: For draggable items (file manager drag-to-move), a subtle `scaleX/scaleY` increase to `1.02–1.05` with `MotionLayout` or `ViewPropertyAnimator` signals "I am now being held."

**Assess:** Do interactive elements have active states beyond hover/ripple? Is the feedback intensity appropriate to the element's visual weight and action importance?

**4. Loading and skeleton states**

When content is loading, the placeholder must:
- Match the geometry of the content it's replacing (skeleton shape = actual content shape)
- Animate in a way that expresses the product character (shimmer direction, speed, color)
- Use the product's palette, not generic gray (#E5E7EB is recognizably "skeleton-from-a-template")

Character-expressive skeleton:
```css
/* "Cold precision" product */
.skeleton {
  background: oklch(18% 0.015 240);
  /* No shimmer — instant state is more in-character than loading animation */
}

/* "Warm editorial" product */
.skeleton {
  background: linear-gradient(90deg, oklch(88% 0.02 60), oklch(85% 0.03 60), oklch(88% 0.02 60));
  animation: shimmer 1.5s ease-in-out infinite; /* gentle, unhurried */
}
```

*Android loading patterns:*
- **ShimmerLayout** (Facebook Shimmer library or custom): Apply shimmer over placeholder shapes that match the final layout geometry. The shimmer gradient color should derive from the app's surface palette — not the library default gray.
- **Placeholder drawables**: Use `ShapeDrawable` or `GradientDrawable` in the product's surface color as placeholder backgrounds. `Glide.with(ctx).load(url).placeholder(R.drawable.placeholder_surface)`.
- **CircularProgressIndicator** (Material 3): For indeterminate loading, the indicator color should be `?attr/colorPrimary`. For determinate, show actual progress. Assess: is the progress indicator styled with the app theme or using the Material default?

**Assess:** Do skeleton states match content geometry? Are loading animations character-appropriate? Is the skeleton/placeholder color from the product palette or from a UI kit default?

**5. Scroll behavior**

Scroll behavior is motion design at the page level:
- **`scroll-behavior: smooth`** — appropriate for in-page anchor navigation; not appropriate for all scrolling
- **Scroll-triggered reveals**: content that enters the viewport with a transition. Appropriate for aesthetic-primary products; disruptive for productivity tools.
- **Scroll-linked effects** (parallax, progress indicators): only appropriate when the product's A5 axis is "Aesthetic IS the value" — otherwise they slow down task completion
- **Overflow scroll behavior**: `-webkit-overflow-scrolling: touch` for smooth iOS momentum scroll; `scrollbar-width: thin` (Firefox) + custom scrollbar styling for consistent cross-browser craft

*Android scroll behaviors:*
- **CoordinatorLayout + AppBarLayout**: Collapsing toolbar with `scroll|enterAlways|snap` flags is the primary scroll-linked motion on Android. Assess: is the collapse animation smooth? Does the toolbar title size transition feel intentional? Is `app:liftOnScroll` enabled for surface elevation changes?
- **RecyclerView overscroll**: The default edge glow (`EdgeEffect`) or stretch overscroll (API 31+) should use the app's accent color, not the system default. Set via `RecyclerView.setEdgeEffectFactory()` or theme attribute `android:colorEdgeEffect`.
- **NestedScrollView vs RecyclerView**: Nested scrollable containers (`RecyclerView` inside `NestedScrollView`) create scroll jank and broken fling physics. Each screen should have one primary scrollable container.
- **Pull-to-refresh**: `SwipeRefreshLayout` indicator color should be `?attr/colorPrimary`. The refresh threshold and indicator size are character moments.

**Assess:** Is scroll behavior declared at all? Does it match the product character? Are there scroll-triggered effects, and are they appropriate given the axis profile? *(Android)*: Is `CoordinatorLayout` scroll behavior configured? Does overscroll use the app's accent color?

### §DM5. Motion Signature

Every strong product should have one distinctive motion moment — a micro-animation so characteristic that someone who uses the product daily would notice if it changed.

**Examples of motion signatures:**
- Superhuman's instant-vanish on email archive (aggressive mechanical — "I process things, I don't celebrate them")
- Notion's block cursor blink (slower than standard — editorial pace)
- Linear's spring-loaded issue creation (slight overshoot — precision with a hint of personality)
- Framer's cursor tracking on hover (cursor becomes part of the UI — aesthetic-primary)

**Protocol:**
1. Does this app currently have a motion signature? (A moment that's distinctly its own?)
2. If yes: is it being used consistently and protected across updates?
3. If no: identify the highest-leverage interaction moment and design a motion signature appropriate to the product character:

```
MOTION SIGNATURE BRIEF
  Trigger: [the interaction that activates it]
  Character source: [what personality dimension it expresses — from §DP2]
  Duration: [ms]
  Easing: [specific cubic-bezier or spring parameters]
  What it says non-verbally: [the implicit message this motion communicates]
  Implementation:
    Web:     [specific CSS/JS code]
    Android: [ObjectAnimator / SpringAnimation / MotionLayout code]
    iOS:     [UIView.animate / SwiftUI withAnimation code]
  Where it appears: [exact components/moments]
  What it must NEVER do: [the anti-pattern that would break it]
```

**Android motion signature opportunities** *(file manager-specific)*:
- **File operation completion**: The moment a copy/move/delete finishes. A distinctive animation here (item shrink + fade, checkmark scale-in with spring overshoot) brands every file operation.
- **Navigation drawer reveal**: The drawer open animation — standard `DrawerLayout` slide, or a custom `MotionLayout` with parallax content shift and scrim fade. The drawer is opened hundreds of times; its motion IS the app's signature.
- **FAB transformation**: `FloatingActionButton` → `BottomSheetDialog` or extended FAB expansion. Material's `MaterialContainerTransform` makes this a smooth morph — the FAB becomes the container. If the app doesn't use this, it's missing its strongest motion moment.
- **List item swipe actions**: Swipe-to-delete/archive with `ItemTouchHelper` — the background reveal color, the icon animation, the snap-back spring all carry character.

---

## V. VISUAL HIERARCHY & GESTALT

### §DH1. Hierarchy Engineering

**The squint test:** Blur your vision or view at 10% opacity. The single most important element should still be clearly dominant. If multiple elements fight for dominance — the hierarchy has failed.

Assess each primary screen:
1. What does the eye land on first? (Should be the primary action or key output)
2. What draws the eye second?
3. Is this the intended reading order?

**Gestalt principles — violations to identify:**

| Principle | Violation Pattern |
|---|---|
| **Proximity** | Related items spaced equally to unrelated items — the eye can't distinguish groups |
| **Similarity** | Visually identical elements serving different functions — clicks land wrong |
| **Continuity** | Elements whose visual alignment implies a relationship that doesn't exist |
| **Closure** | Truncated elements (text, cards) that the eye doesn't naturally complete |
| **Figure/Ground** | Background elements competing with foreground — figure doesn't "pop" |
| **Common Fate** | Animated elements out of sync with elements they logically relate to |

### §DH2. Reading Pattern Compliance

**Z-pattern (low-text layouts):** Eye travels: top-left → top-right → bottom-left → bottom-right. In hero sections, marketing views, dashboards — are the four "anchor points" occupied by the most important elements?

**F-pattern (text-heavy layouts):** Users scan in an F: across the top, then down the left edge with occasional rightward sweeps. In list views, content feeds, settings — is the most important content left-aligned, with secondary content in the right 40%?

**Gaze path obstruction:** Identify any element whose visual weight draws the eye away from the intended reading path before the user has completed the primary task.

**Mobile thumb zone analysis** *(Android/iOS)*: On handheld devices, reading patterns are modified by reachability. The natural thumb arc creates three zones:
- **Easy zone** (bottom-center, bottom-right for right-handed): Primary actions and navigation belong here. On Android, this is why `BottomNavigationView` and FABs live at the bottom. Assess: are the most frequent actions in the easy zone?
- **Stretch zone** (top corners, far left): Acceptable for secondary actions. The app bar / toolbar falls here — appropriate for infrequent actions like search or settings.
- **Hard zone** (top-left corner on right hand, top-right on left): The worst position for frequent actions. If a critical action (like "back" or "create") lives here without also being reachable from the easy zone, it's a usability failure.

**List-dominant reading pattern** *(Android file managers, email, messaging)*: In list-heavy apps, the dominant reading pattern is a vertical scan of the left 60% of each row. The eye anchors on the leading icon/thumbnail, then sweeps right to the title, then optionally to metadata. Assess: is the most important information (file name, sender, subject) in the first 60% of the row? Is secondary metadata (date, size, count) right-aligned and visually subordinated?

**Safe area awareness** *(mobile)*: On modern Android (edge-to-edge with `WindowInsetsCompat`) and iOS (Dynamic Island, home indicator), content near screen edges may be obscured. Assess: do critical reading paths avoid the system gesture inset areas? Is `fitsSystemWindows` or `ViewCompat.setOnApplyWindowInsetsListener` used correctly?

### §DH3. Visual Weight Distribution

Map the visual weight (size × saturation × contrast) distribution across each primary screen:
- Is visual weight concentrated at the point of primary user action?
- Is there a "heavy corner" — weight accumulating in one area without intent?
- Are secondary elements subdued enough that they don't compete with primary elements?
- **The 80/20 test:** Can 80% of users accomplish the primary task by interacting with only 20% of the interface's visual surface?

### §DH4. Contrast as Composition Tool

Contrast is not only an accessibility metric — it is the primary tool of visual composition. Most designers think of contrast as "is this readable?" The real question is "where is contrast sending the eye, and is that the right place?"

**Four contrast dimensions — all operate simultaneously:**

| Dimension | What it controls | Design tool use |
|---|---|---|
| **Value contrast** (light/dark) | Depth, focus, readability | Lightest element in a dark layout = primary focus; darken everything secondary |
| **Scale contrast** (size difference) | Hierarchy, importance, relationship | Large/small pairing creates natural reading order without color |
| **Chroma contrast** (saturated/muted) | Energy, attention, meaning | The only saturated element in a desaturated field commands absolute attention |
| **Form contrast** (sharp/rounded) | Personality, interactivity, tension | Sharp = technical/precision; rounded = interactive/approachable |

**The isolation principle:** The most powerful way to create hierarchy is *not* to make the primary element more prominent — it is to make everything *around* it less prominent. An element surrounded by quiet elements at low contrast will read as primary even at moderate size.

**Deliberate tension in hierarchy:**

Some design styles (neo-brutalism, editorial, certain cyberpunk aesthetics) use intentional hierarchy violations — elements that *resist* the expected reading order — as an expressive tool. Assess whether any hierarchy violations in this app are:
- **Accidental** (multiple elements at equal weight competing for primacy — a bug)
- **Intentional** (a specific element is designed to create tension with the hierarchy — verify intent)

If intentional: is the tension producing the right effect, or is it just disruptive?

**Contrast ratio as a design choice** (beyond accessibility):

WCAG requires 4.5:1 for body text and 3:1 for large text. But contrast ratio is a design variable:
- 7:1+ ratio: high contrast — clinical, sharp, confident; appropriate for expert tools
- 4.5:1–7:1: normal range — comfortable for most audiences
- 3:1–4.5:1: low contrast — atmospheric, subtle; *only* appropriate for decorative/secondary text, never body

**Assess:** Is contrast ratio being used as a compositional tool — consciously varying across hierarchy levels — or is it just a compliance checkbox?

---

## VI. SURFACE & ATMOSPHERE DESIGN

> **Claude execution note**: §DSA1 (background as material) and §DSA2 (elevation) are foundational — always cover them. §DSA4 (light physics) produces the most sophisticated findings but requires careful observation. §DSA5 (focal vs ambient) is highest-value when the product has atmospheric treatments that feel indiscriminate.

### §DSA1. Background as Material

The background is the foundation that everything else sits on — it communicates the app's "physical world."

| Background Type | Effect | Appropriate For |
|---|---|---|
| **Flat neutral** | Maximum focus on content | Productivity, data tools, professional |
| **Chromatic near-black/near-white** | Warmth/cool without distraction | Most apps — a slight hue improves on pure neutral |
| **Subtle gradient** | Sense of direction, sky-like depth | Consumer, creative, premium SaaS |
| **Noise/grain overlay** | Analog warmth, reduces digital harshness | Creative tools, portfolio, editorial |
| **Gradient mesh** | Atmospheric, living, premium | Brand-heavy products, creative showcases |
| **Patterned/textured** | Strong character, high brand signal | Specific aesthetics only (retro, brutalist) |

**Assess:**
- Does the background type match the app's §0 personality and axis profile?
- Is the background serving as a neutral stage (most apps) or as an active design element (appropriate for aesthetic-heavy contexts)?
- Is there consistent light source implied? Gradients should originate from a consistent direction.

### §DSA2. Elevation System Audit

A coherent elevation system makes the Z-axis legible. Assess whether each layer in the app is visually distinguishable from its neighbors:

**Light mode elevation:** Achieved through shadows + slight background lightness increase
- Layer 0 (page): `bg-white` or lightest surface
- Layer 1 (cards): subtle shadow `0 1px 3px rgba(0,0,0,0.08)` + `bg-white` on light bg
- Layer 2 (popovers/sheets): medium shadow `0 4px 16px rgba(0,0,0,0.12)`
- Layer 3 (modals): heavy shadow `0 20px 60px rgba(0,0,0,0.2)` + backdrop

**Dark mode elevation:** Achieved through lightness alone (no shadows needed, shadows are invisible)
- Each layer: +2–4% OKLCH lightness from the layer below
- No shadows on dark mode (they're barely visible and add nothing)

**Common violations:**
- Cards with no elevation signal (no shadow, no border, no background shift = invisible structure)
- Shadows too large/dark for the component's function (a card with modal-weight shadow feels unstable)
- Inconsistent shadow angles across components (implies multiple light sources)
- Dark mode using light-mode shadow values (shadows look wrong on dark)

### §DSA3. Atmosphere Signals (Craft Layer)

These micro-decisions separate "functional" from "refined":

- **Grain/noise overlay** `(0–4% opacity)`: A subtle noise layer on solid backgrounds prevents the flat, sterile look. *Web*: `background-image: url("noise.svg")` or CSS filter. *Android*: Use a `BitmapShader` with `TileMode.REPEAT` on a custom `Drawable`, or overlay a tiled noise PNG in an `ImageView` with `android:alpha="0.03"`. Appropriate for most design styles except pure minimal.
- **Gradient directionality**: Linear gradients should use a consistent angle or metaphor (top = sky/light, bottom = ground/weight). Radial gradients should have a meaningful center. *Android*: `GradientDrawable` with `android:angle` (must be a multiple of 45). For radial: `android:gradientRadius` with `android:type="radial"`. For sweeping gradients in Compose: `Brush.linearGradient()` or `Brush.radialGradient()`.
- **Color temperature shift with depth**: Elements deeper in the stack (modals, drawers) can subtly shift temperature (slightly cooler = "deeper"). Adds spatial dimension without explicit depth cues. *Android M3*: The `colorSurfaceContainer` → `colorSurfaceContainerHigh` → `colorSurfaceContainerHighest` progression achieves this through tonal elevation. Check that bottom sheets use `?attr/colorSurfaceContainerHigh` and dialogs use `?attr/colorSurfaceContainerHighest`.
- **Border opacity treatment**: Semi-transparent borders on dark surfaces feel more refined than solid hardcoded borders — they adapt to the surface color underneath. *Web*: `rgba(255,255,255,0.08)`. *Android*: Use `<stroke>` in a `GradientDrawable` with a color like `#14FFFFFF` (8% white). Or use `MaterialShapeDrawable` which handles elevation overlay tinting automatically.
- **Scrim quality** *(Android)*: Material dialogs and bottom sheets use a scrim (semi-transparent overlay behind the foreground element). The default is `#52000000` (32% black). A refined scrim uses the app's dark surface color at reduced opacity instead of pure black — `#40` + the hex of `colorSurface`. This makes the scrim feel integrated rather than like a generic system overlay. Check `android:backgroundDimAmount` in theme and `BottomSheetDialog` scrim color.
- **Ripple color calibration** *(Android)*: The default Material ripple is `?attr/colorControlHighlight` — typically 12% of `colorOnSurface`. A refined approach tints the ripple to match the element's accent: `RippleDrawable` with `ColorStateList.valueOf(Color.argb(0x1A, r, g, b))` using a desaturated version of the element's primary color. This makes touch feedback feel intentional rather than system-default.
- **Status bar / navigation bar blending** *(Android)*: With edge-to-edge (`enableEdgeToEdge()`), the status bar and navigation bar overlay app content. The system bar colors should match or complement the surface beneath them — not be a jarring opaque block. Check `WindowInsetsControllerCompat.setAppearanceLightStatusBars()` and ensure the scrim (if any) is the surface color at appropriate opacity, not hardcoded black.
- **Elevation overlay in dark mode** *(Android M3)*: Material 3 dark mode applies a semi-transparent white overlay to elevated surfaces — the higher the elevation, the lighter the surface. This is automatic with Material components but custom views using `android:elevation` may not get the overlay. Check that custom elevated views in dark mode use `MaterialShapeDrawable` or manually apply `ElevationOverlayProvider.compositeOverlayWithThemeSurfaceColorIfNeeded()`.

### §DSA4. Light Physics

Strong surface design implies a consistent light source and follows light's physical behavior. Most apps violate this silently — every element is lit from a different direction, or lit from nowhere.

**Establishing a light source:**

Every visual design has an implicit or explicit light source. Identify it:
```
Light direction: [top-left / top / top-right / ambient (no direction) / from below]
Light quality:   [hard (sharp shadows) / soft (diffuse) / ambient (no shadows, consistent)]
Light warmth:    [cool-white / neutral / warm-gold]
Light intensity: [strong (high-contrast shadows) / moderate / low (atmospheric)]
```

**Light source consistency checks:**
- Do shadows on all elements originate from the same angle? (`box-shadow: 0 4px 12px` is down-left; `box-shadow: -4px 0 12px` is right-to-left — are they mixed?)
- Does elevation follow light physics? Higher elements cast longer/softer shadows than lower elements.
- Do highlights appear on the correct edge? (If light is from top-left, highlights should appear on top/left edges of elements, not bottom/right.)

**Light-surface interaction:**
- **Glossy/reflective surfaces** (glass, metal treatments): require both a shadow *below* and a highlight *above* — one without the other breaks the physics
- **Matte surfaces** (paper, concrete treatments): diffuse light, no specular highlight — only subtle shadow
- **Luminous surfaces** (glowing elements): emit light outward — `box-shadow: 0 0 20px oklch(60% 0.25 200 / 0.4)` creates the correct outward emission; the glow should be from the element's own color, not white

**Dark mode light physics:**

In dark interfaces, the light source reverses its meaning. Elevation in dark mode is *not* about shadows (which are invisible on dark) — it is about lightness as a luminosity proxy:
- Higher elevation = more luminous = lighter OKLCH value = appears closer to a (virtual) light source above
- This means dark mode UI should be conceived as looking *up* at a ceiling with a diffuse light source, not *down* at a lit table

### §DSA5. Focal vs Ambient Atmosphere

**Ambient atmosphere** is the consistent background mood — the surface, the background treatment, the air of the product. It should be present everywhere but never dominant.

**Focal atmosphere** is applied specifically to key moments — concentrated atmospheric effects that mark significant areas of the product:
- Hero sections: where the product's character is stated most boldly
- Primary CTA areas: where energy is concentrated to prompt action
- Feature highlights: where a specific capability is given its own atmospheric space
- Empty states: where atmosphere compensates for the absence of content

**The atmosphere hierarchy:**

```
Background atmosphere    → [lowest intensity — always present]
   ↓
Surface atmosphere       → [cards, panels — slight lift from background]
   ↓
Focal atmosphere         → [key moments — concentrated effect, maximum expressiveness]
   ↓
Accent atmosphere        → [the single most important element — unique, unrepeated]
```

**Common violation:** Focal atmosphere applied everywhere (every card has an aurora gradient, every button has a glow) — this eliminates the hierarchy. When everything glows, nothing glows.

**Assess:** Does the app have a clear ambient/focal atmosphere distinction? Are focal effects concentrated on the genuinely important moments, or distributed indiscriminately?

---

## VII. ICONOGRAPHY SYSTEM

### §DI1. Icon Language Assessment

**Icon family coherence:** All icons should be from the same visual family OR hand-crafted to a consistent specification. Mixing icon libraries is one of the most visible polish failures.

Audit the current icon usage:
- How many icon sources are used? (1 = correct; 2+ = problem unless deliberate)
- What is the icon style? Line / Filled / Duotone / Bold / Mixed
- Is there a consistent visual weight (stroke width for line icons, optical fill weight for filled icons)?
- Are corner treatments consistent? Fully rounded vs sharp-capped endpoints read as different personalities.
- *(Android)* Are icons using `VectorDrawable` (`res/drawable/*.xml`) or PNG assets? `VectorDrawable` scales perfectly across density buckets and is the correct choice for UI icons. PNGs require `mdpi`/`hdpi`/`xhdpi`/`xxhdpi`/`xxxhdpi` variants and still risk blurriness.
- *(Android)* Is the app using Material Symbols (variable font icons with adjustable weight/fill/grade/optical size) or static Material Icons? Material Symbols allow character-aligned icon weight matching — e.g., `weight=300` for a light/editorial feel, `weight=600` for bold/confident.

### §DI2. Icon Grid & Optical Sizing

**Icon grid compliance:** Professional icons are drawn on a consistent grid (most commonly 24×24 with 2px padding for a 20×20 content area). Icons off-grid have visible optical inconsistencies.

**Optical size adjustment:** A 16×16 icon and a 24×16 icon should not use the same artwork — smaller sizes need simpler, thicker strokes. Using a detailed 24px icon at 12px renders as mud.

**Icon-to-text alignment:** Icons alongside text must be optically centered — not mathematically centered. A vertically centered 16px icon next to 16px text will look low because the text's cap-height is shorter than its line-height. Correction: apply `margin-top: -1px` or `align-items: baseline`.

**Interactive icon states:** Icon-only buttons must have all five states. *Web*: The icon should change subtly on hover/active (not just the background). A color shift on the icon itself, not just the container, reads as more responsive. *Android*: Icon buttons (`MaterialButton` with `app:icon` or `IconButton` in Compose) should use `?attr/selectableItemBackgroundBorderless` for ripple. The icon tint should use a `ColorStateList` that shifts for pressed/focused/disabled states — not a static `android:tint` color. For `ImageButton` and `IconButton`, ensure the touch target is at least 48×48dp regardless of visual icon size (`android:minWidth="48dp"`, `android:minHeight="48dp"`).

### §DI3. Icon Expressiveness Spectrum

Icons exist on a spectrum from pure utility to pure expression. The appropriate position on this spectrum depends on the product's A5 axis:

| Expressiveness | Description | Appropriate for |
|---|---|---|
| **Utilitarian** | Standard library icons, zero customization, maximum familiarity | Expert tools, data dashboards, productivity |
| **Calibrated** | Standard library + weight/size customization to match character | Most SaaS — matches product voice without friction |
| **Signature** | Library base + distinctive modifications (corners, fills, motifs) | Products with strong visual identity (A4: Strong aesthetic) |
| **Illustrative** | Custom icons with expressive quality — they have personality beyond function | Creative tools, consumer apps, aesthetic-primary products |
| **Art-directed** | Fully custom icon system — often the product's most recognizable element | Flagship consumer products, strong IP-adjacent products |

**Assess:** Where does this product sit on the spectrum? Is that appropriate given its axis profile? Is it stuck at "Utilitarian" when its character demands "Signature"?

**Icon character alignment check:**

The icon style must be consistent with the product's design character (§DP2). Specific misalignments to flag:

| Character | Wrong icon choice | Right icon choice |
|---|---|---|
| Cold precision | Rounded Heroicons (too friendly) | Sharp-terminal Phosphor (thin weight, sharp caps) |
| Warm editorial | Sharp Material Icons (too corporate) | Rounded, slightly heavier icons with organic feel |
| Cyberpunk/terminal | Any mainstream library (too soft) | Custom angular icons, or Tabler with sharp modifications |
| Playful/consumer | Line icons only (too formal) | Filled icons with consistent corner rounding, slight irregularity |
| Minimal editorial | Any icon at all (often wrong) | Text labels or pure typographic indicators |

### §DI4. Custom Icon Direction

When the product warrants custom icons (Signature or above on the expressiveness spectrum), provide specific direction:

**Icon specification brief format:**
```
ICON SPECIFICATION BRIEF
  Grid:              [base size × base size, e.g. 24×24]
  Content area:      [inner area, e.g. 20×20 (2px padding each side)]
  Stroke weight:     [e.g. 1.5px — specific, not "medium"]
  Corner treatment:  [specific radius in px — e.g. "1.5px radius on all internal corners;
                      0px on terminals — sharp termination"]
  Cap style:         [round / square / butt]
  Internal angles:   [sharp / slightly rounded / fully rounded]
  Fill approach:     [none / partial fills / full fills / duotone]
  Signature motif:   [if the icon system should contain a recurring motif — e.g.
                      "all icons incorporate a small diagonal cut consistent with the
                      frame motif from §SR1 minimum authentic set"]
  Character the icons should project: [2–3 words from §DP2 character brief]
  Reference icons to design first: [the 5 most common icons in this product —
                                     these define the system, design them before any others]
```

**Icon test sequence** — before committing to a custom direction, produce these 5 icons and assess coherence:
1. Home / dashboard icon
2. Settings / gear icon (complex curves — reveals the corner treatment choices)
3. User / profile icon (organic form — reveals the humanist vs geometric balance)
4. Alert / warning icon (triangular — reveals the angular treatment)
5. Plus / add icon (simple — reveals the weight and cap style)

If all 5 feel like they belong to the same system: the specification is valid. If any feels like a visitor from a different library: the specification needs refinement.

---

## VIII. DESIGN TREND CALIBRATION

> **Claude execution note**: §DDT1 (trend inventory) is quick and high-value — flag any passed trends creating dated feel. §DDT2 (trend strategy) is strategic-layer work — most useful for products competing in trend-aware markets (consumer, creative). Skip entirely for expert tools where trends are irrelevant.

### §DDT1. Trend Inventory & Appropriateness

Identify which current or recent design trends the app uses, intentionally or accidentally:

| Trend | Peak Period | Currently | Appropriate For |
|---|---|---|---|
| **Glassmorphism** | 2021–2023 | Cooling | Products with vivid, high-contrast backdrops |
| **Neumorphism** | 2020 | Passed | Almost nowhere — severe accessibility issues |
| **Bento grid layouts** | 2023–present | Peak | Marketing pages, feature showcases |
| **Gradient mesh backgrounds** | 2023–present | Peak | Brand-heavy, creative, premium consumer |
| **Bold typography / type-as-hero** | 2022–present | Active | Editorial, creative, portfolio |
| **Neo-brutalism** | 2022–present | Active | Community, indie, counter-corporate |
| **Dot/grid texture overlays** | 2023–present | Active | Technical tools, developer products |
| **Aurora / chromatic gradients** | 2024–present | Peak | AI products, creative tools |
| **Minimal monochrome** | Perennial | Stable | Professional, B2B, data tools |
| **Skeuomorphic revival** | 2024–present | Emerging | Audio tools, craft-oriented consumer |

**For each trend identified:**
1. Is its use intentional or accidental?
2. Is it appropriate for the product's axis profile?
3. Is it executed at the quality level the trend requires? (Glassmorphism with illegible text is worse than no glassmorphism)
4. Trend debt check: Is a passed trend being maintained because it was built in, creating a dated feel?

### §DDT2. Trend Strategy

Trends are not just a quality check — they are a positioning signal. Using a trend intentionally communicates *"we are current, we know what's happening in design"*. Avoiding a trend intentionally communicates *"we are above trends, we have timeless discipline"*. Using a trend accidentally communicates nothing useful.

**Four strategic postures toward trends:**

| Posture | What it signals | When to adopt |
|---|---|---|
| **Trend leader** | "We set the direction — others will follow us" | Products with design as a core differentiator; only sustainable with exceptional execution |
| **Trend adopter** | "We are current, we are aware, we belong here" | Products competing in a trend-aware market (consumer, creative tools, B2C SaaS) |
| **Trend selective** | "We use trends where they serve us and ignore them where they don't" | Most products — the strategically correct default |
| **Trend agnostic** | "Our design language is timeless and doesn't participate in cycles" | Expert tools, institutional products, products where credibility > modernity |

**Assess:** Which posture is appropriate for this product (from §0 A1–A5)? Which posture does the current design actually project? Gap between intended and actual is a finding.

**Trend adoption quality bar:**

Every trend has a minimum quality threshold — below which using the trend is actively harmful to perceived quality. If a trend is present but executed below its quality bar, it should either be executed properly or removed.

| Trend | Minimum quality bar | Common below-bar execution |
|---|---|---|
| **Glassmorphism** | Backdrop filter with sufficient blur (≥12px); text must pass 4.5:1 contrast over the blurred content; requires a vivid backdrop to work at all | Glassmorphism on flat/dark surfaces where there's nothing to blur through; illegible text |
| **Gradient mesh** | Mesh must feel organic and living; colors must be harmonically related in OKLCH; no visible banding | 3-color mesh with unrelated hues; obvious banding from cheap CSS gradients |
| **Neo-brutalism** | Intentional visual tension that serves a purpose; heavy borders at consistent weight; the "raw" must be deliberate | Random weights, no system, just looks unfinished |
| **Aurora gradients** | Chromatic but controlled; must feel atmospheric not garish; text zones must be clean | Oversaturated rainbow that distracts from everything; poor legibility over moving gradient |
| **Bento grid** | Content parity across cells; layout must communicate relationships; cells must have deliberate size variation | All cells same size; content thrown in without hierarchy; feels like a generic template |

**Trend debt cleanup protocol:**

When a trend has passed but is still present in the codebase (neumorphism, flat 2.0, heavy card shadows from Material's peak):

```
TREND DEBT ITEM
  Trend:           [name]
  Status:          [passed / cooling]
  Where present:   [specific components and CSS classes]
  Age impact:      [how much it ages the product — HIGH / MEDIUM / LOW]
  Migration path:  [what it should become — specific replacement values]
  Effort:          [LOW / MEDIUM / HIGH]
  Priority:        [when this should be addressed relative to other debt]
```

---

## IX. BRAND IDENTITY ENGINEERING

> **Claude execution note**: §DBI3 (anti-genericness audit, all 12 signals) is the single highest-value section for most developers — it produces immediately actionable findings. §DBI1 (archetype) provides strategic framing. §DBI2 (design DNA) is the forward-looking investment. If time is limited, run §DBI3 first — it has the fastest payoff.

### §DBI1. Brand Personality Archetype

The 12 Jungian brand archetypes map onto visual design languages. Identify the current app's archetype and whether the design serves it:

| Archetype | Visual Language | Example Products |
|---|---|---|
| **Hero** | Bold, strong, confident; high contrast; action-oriented | Nike, Superhuman |
| **Sage** | Calm authority; clean information density; precise type | Notion, Stripe |
| **Creator** | Expressive, colorful, canvas-like; celebrates output | Figma, Canva |
| **Caregiver** | Warm, soft, rounded; calm palette; unhurried | Calm, Headspace |
| **Explorer** | Open, dynamic, spatial; invites discovery | Google Earth, Airbnb |
| **Rebel** | Counter-convention; raw; intentional tension | Gumroad (old), Lemon |
| **Magician** | Premium, transformative; shows the "before/after" | Adobe, Anthropic |
| **Innocent** | Clean, bright, optimistic; minimal conflict | Dropbox Paper, Linear |
| **Ruler** | Authoritative, structured, institutional | Bloomberg, Salesforce |
| **Jester** | Playful, unexpected, delight-first | Duolingo, Notion (marketing) |
| **Lover** | Sensory, intimate, warm; beautiful details | Spotify, VSCO |
| **Everyman** | Accessible, familiar, unpretentious | Basecamp, Mailchimp |

**Assessment:**
1. What archetype does the current design project?
2. What archetype does the product *intend* to project (from §0)?
3. Is there alignment or a gap?
4. What are the 2-3 specific visual changes that would close the archetype gap?

### §DBI2. Design DNA Extraction and Signature Building

Every strong product has 2–3 visual signatures — details so consistent and so characteristic that they become recognizable even when decontextualized. They are what makes an interface feel like *that product*, not any product.

**Signature examples and their anatomy:**
- Stripe's gradient mesh + large clean type: *atmosphere (mesh) + voice (scale) — two decisions, one identity*
- Linear's keyboard-shortcut density + muted accent: *density philosophy + color restraint — insiders read it as "we're serious"*
- Notion's block cursor: *a single motion moment at 1.1s blink — slower than default, editorial pace*
- Raycast's Cmd+K palette: *structural — the product's entire value proposition is a single interaction pattern*

**Step 1: Signature extraction (what already exists)**

Read the app's code and UI. Identify every element that could become a signature — elements that are:
- Already distinctive (not seen in most competitors)
- Highly visible (appears on multiple screens or in a primary location)
- Expressing the correct character (consistent with §DP2)

For each candidate:
```
SIGNATURE CANDIDATE: [element name]
  Distinctiveness: HIGH / MEDIUM / LOW (relative to competitive landscape)
  Visibility:      [where it appears — how often does a user encounter it?]
  Character alignment: [reinforces or contradicts the §DP2 character brief?]
  Investment to develop: [what would make this a true signature?]
```

**Step 2: Signature gap analysis**

If no strong candidates exist: the product needs one built from the raw material available. Assess which signature type is most buildable:

| Signature Type | What becomes recognizable | What it requires |
|---|---|---|
| **Color signature** | A specific calibrated value — not a hue, an exact OKLCH coordinate | A single accent, applied consistently and exclusively |
| **Spatial signature** | A distinctive spacing or layout rhythm | A non-standard base unit (7px vs 8px) or specific proportion |
| **Type signature** | A specific typographic move at every key moment | Fixed tracking + weight + size, always the same |
| **Motion signature** | One animation that's unmistakably this product | See §DM5 — the single highest-leverage motion moment |
| **Surface signature** | A distinctive surface treatment on all elevated elements | A specific border/shadow/texture combination |
| **Compositional signature** | A layout structure that recurs across screens | A specific panel proportion or element positioning rule |

**Step 3: Signature investment specification**

For the 1–2 highest-potential candidates, produce a full specification:

```
DESIGN SIGNATURE SPECIFICATION
  Type: [color / spatial / type / motion / surface / compositional]
  Current state: [what exists today — specific values]
  Target signature: [what the signature becomes — specific values]
  Implementation:
    [Exact CSS or design token definition]
  Where it appears: [every location in the product — exhaustive]
  Where it must NOT appear: [contexts where it would be inappropriate]
  Maintenance rule: ["Every new card component inherits this border treatment — no exceptions"]
  Recognition test: ["Screenshot this component, show it to someone who knows the product
                      without context — do they say [product name]?"]
```

### §DBI3. Anti-Genericness Audit

**The twelve most common visual genericness signals.** For each found: document it, explain the specific impact on perceived quality, and apply the fix protocol.

> **Platform note**: The twelve signals below use web/CSS examples. For Android, the equivalents are: Tailwind defaults → Material Design 3 default theme colors; CSS values → `colors.xml`/`themes.xml` values; `border-radius` → `cornerRadius` attributes; CSS transitions → `res/anim/` or `ObjectAnimator` durations. The *principles* are universal — a Material default `colorPrimary` used without customization is the same genericness signal as Tailwind blue-500. For iOS: default system blue (`#007AFF`), system fonts at default weights, default `cornerRadius: 10` everywhere.

---

**Fix Protocol (apply to each finding):**
```
GENERICNESS SIGNAL: [which of the 12]
  Exact value found:  [the specific CSS value — e.g. `color: #3b82f6`]
  Why it reads generic: [the mechanism — e.g. "this exact hex is the Tailwind default
                          shared by ~40% of CDN-built apps; it has no calibration identity"]
  Impact on character: [how it undermines the §DP2 character brief specifically]
  Replacement:
    Option A (minimal change): [the smallest edit that breaks the generic signal —
                                 e.g. shift hue 5°, adjust lightness 3%]
    Option B (full ownership):  [the edit that makes it unmistakably intentional —
                                  e.g. a full OKLCH recalibration with reasoning]
  Exact new value: [oklch() + hex]
  Apply everywhere: [every selector/token that carries this value]
```

---

**The twelve signals:**

**1. Default Tailwind blue** — `#3b82f6` / `blue-500` as primary accent
Shared by ~40% of all CDN-built apps. Not ugly, but invisible. The hue is generic; the calibration is non-existent.
Fix A: Shift to `oklch(62% 0.22 248)` — same family, 8° hue shift, full chroma recalibration. Feels owned.
Fix B: Move to a non-blue family entirely based on §DP2 character. Blue is a default, not a choice.

**2. Inter at default weight everywhere**
Inter is an excellent font. Inter at weight 400 for body and 600 for headings with no tracking adjustments is a template, not a design decision.
Fix A: Add `letter-spacing: -0.015em` on headings 24px+; add `letter-spacing: 0.01em` on body; add `font-weight: 700` contrast on primary headings.
Fix B: Switch display/heading to a different typeface from the §DT1 matrix — keep Inter for body only.

**3. `rounded-lg` on everything** — `border-radius: 0.5rem` applied globally
Radius communicates personality. One radius for every component says no personality was assigned. Cards, buttons, inputs, modals, avatars — each has a different appropriate radius based on its function and the character.
Fix: Define a radius scale with intent: `--radius-btn: 6px`, `--radius-card: 10px`, `--radius-input: 6px`, `--radius-badge: 100px`, `--radius-modal: 14px`. None of these should be the same value.

**4. 16px grid spacing only** — `p-4` and `p-8` everywhere
Mechanical spacing produced by using only the default 4-multiple grid. No rhythm, no contrast, no breathing.
Fix: Define an intentional spacing system with a base unit derived from the character. A "warm editorial" product might use base 6px (tighter, more printed-page feel). A "generous consumer" product base 5px (non-Tailwind unit creates distinctiveness). Apply `gap-5`, `p-6`, `p-10` — combinations that break the mechanical pattern.

**5. `shadow-sm` on cards** — `0 1px 3px rgba(0,0,0,0.1), 0 1px 2px rgba(0,0,0,0.06)`
Tailwind's shadow-sm is tonally unintegrated — it's a generic gray shadow regardless of the surface color it sits on. It reads as "card from a tutorial."
Fix: Replace with a tonal shadow using the product's background color: `0 2px 8px oklch(10% 0.02 240 / 0.15)`. Use the palette's dark tone at low opacity instead of black. On dark surfaces: use lightness for elevation (§DC3), remove shadows entirely.

**6. Default Heroicons / Lucide out of the box**
Both are good icon libraries. Both are immediately recognizable as "starter kit." They have no cultural specificity, no brand expression.
Fix A: Use Phosphor Icons (more weight/style options) with explicit `weight="light"` or `weight="bold"` matching the character instead of the default.
Fix B: Apply §DI3 expressiveness assessment — determine whether custom modifications are warranted.

**7. Gray-900 / Gray-500 / Gray-400 text stack**
The default Tailwind text hierarchy. Reads as "I used the colors that were there." No chromatic quality, no temperature, no identity.
Fix: Replace each with chromatic equivalents. Dark-mode example: `oklch(92% 0.008 240)` primary, `oklch(62% 0.01 240)` secondary, `oklch(42% 0.01 240)` muted — chromatic cool-gray that's visually almost identical but has identity. Light-mode: `oklch(18% 0.012 245)`, `oklch(45% 0.01 245)`, `oklch(62% 0.008 245)`.

**8. White `#ffffff` as background** (dark mode: `#0f1117` or `#111827`)
Pure white and these specific near-blacks are universal defaults with no chromatic identity. Pure white is harsh on the eye. Pure near-black is neutral to the point of emptiness.
Fix: Add slight chromatic quality: light mode `oklch(98% 0.005 90)` (very slightly warm), dark mode `oklch(12% 0.015 240)` (very slightly cool). Imperceptible individually, immediately noticeable as more refined compared to the default.

**9. `transition: all 0.2s ease-in-out` applied everywhere**
Animating `all` properties causes jank on complex components (triggers layout recalculation). `ease-in-out` at 200ms is the browser default motion feel — neither fast nor considered.
Fix: Replace with specific property transitions matching the motion vocabulary: `transition: background-color 150ms ease-out, border-color 150ms ease-out, color 100ms ease-out`. Explicit, compositor-safe, character-matched.

**10. Full-width buttons at `w-full`**
Buttons that stretch to fill their container feel cheap and untailored. Full-width is appropriate only for mobile contexts or form-submit scenarios.
Fix: Size buttons to their content plus explicit padding. Minimum width for primary CTAs: `min-width: 140px`. This creates visual proportion and reads as designed.

**11. `<hr>` or `border-t border-gray-200` as the only separator system**
One separator style for all separation needs creates flat hierarchy. Some divisions are section breaks; some are item separators; some are emphasis divisions.
Fix: Define three separator types: `--separator-heavy` (section break: 1px solid at 20% opacity), `--separator-light` (item separator: 1px solid at 8% opacity), `--separator-accent` (emphasis break: uses accent color at 30% opacity, used sparingly).

**12. Placeholder text `#9ca3af` / `placeholder:text-gray-400`**
Placeholder text at the default Tailwind gray is unintegrated into the color system. It reads as filler because it is.
Fix: Derive placeholder color from the input background with a specific ratio: if input surface is `oklch(18% 0.015 240)`, placeholder should be `oklch(40% 0.01 240)` — same hue family, matched temperature, clearly distinct from input value text.

---

**Dark-mode specific genericness signals** *(check in addition to the 12 above for dark-mode products):*

- `background: #000000` — absolute black is OLED/theater-mode only; everything else needs slight luminosity
- `border: 1px solid rgba(255,255,255,0.1)` everywhere — this exact value appears on hundreds of dark-mode dashboards; use the specific hue from the palette
- Text contrast 60% white everywhere — undifferentiated muted text reads as lazy; use OKLCH to create a genuine hierarchy
- `blue-400` / `#60a5fa` as the dark-mode accent — the most common dark-mode genericness signal; recalibrate to own it
- *(Android)* Unmodified `values-night/` colors — if `values-night/colors.xml` only overrides `colorPrimary` and `colorSurface` while leaving everything else at Material 3 defaults, the dark mode looks like every other Material 3 dark app. Check: `colorSurfaceContainer`, `colorSurfaceContainerHigh`, `colorOutline`, `colorOnSurfaceVariant` — all should be intentionally calibrated.
- *(Android)* `android:forceDarkAllowed="true"` reliance — Force Dark is a system-level inversion that produces generic, uncontrolled dark mode. An app with design identity should implement its own dark theme via `values-night/` and set `android:forceDarkAllowed="false"`.
- *(Android)* Default Material 3 dark surface `#1C1B1F` — this is the baseline `colorSurface` from the Material 3 default dark palette. If the app uses this without modification, the dark mode has zero identity.

---

## X. COMPETITIVE VISUAL POSITIONING

> **Claude execution note**: Use web search to find competitor screenshots for §DCP1. If web search is unavailable, ask the user to name 2–3 competitors and describe their visual qualities. Skip this section entirely if the user explicitly says they don't care about competitors — the findings depend on external research. Present the positioning matrix (§DCP2) to the user before producing differentiation recommendations.

### §DCP1. Benchmark Identification

For the app's domain, identify 3 directly comparable products and assess their visual positioning. For each: look at the actual UI (search for screenshots if needed — §SR0 protocol applies), not just a verbal description.

**What to look for in each benchmark:**
- Primary accent color family and calibration quality (generic blue vs owned hue)
- Typography character (which font, at which weights — does it feel designed?)
- Spatial density (tight / normal / airy — and whether it matches the product's use context)
- Signature element (the one visual detail most responsible for its distinctive feel)
- Genericness level (could this UI belong to a dozen other products, or is it unmistakably this one?)
- Design investment tier (starter-kit level / competent / polished / exceptional)

```
Product A: [name]
  Visual character:    [2 sentences — describe what design decisions define it]
  Accent / palette:    [hue family, calibration quality, distinctiveness]
  Typography:          [font, weight range, design-intent vs default]
  Spatial density:     [tight / normal / airy — appropriate for use context?]
  Signature element:   [the one detail that makes it recognizable]
  Genericness score:   [1–5 where 1 = could be any app, 5 = instantly identifiable]
  Strengths:           [visual qualities that command trust or preference]
  Weaknesses:          [visual gaps — what looks accidental or unowned?]
  Relation to this app: [ahead / at parity / behind — in which specific dimensions]

Product B: ...
Product C: ...
```

**Whitespace opportunity:**

After benchmarking all three, identify the visual positioning territory none of them occupies — and assess whether this app could credibly claim it:

```
WHITESPACE OPPORTUNITY
  Available position: [the visual character or quality no benchmark currently owns]
  Why it's available: [what the competitive landscape has failed to invest in]
  Fit for this app:   [does the whitespace align with this app's axis profile and §DP2 character?]
  Risk:               [why competitors might have avoided this position — is there a reason?]
  Claim strategy:     [the 2–3 specific visual decisions that would plant a flag in this territory]
```

### §DCP2. Positioning Matrix

Map the competitive landscape on two axes relevant to this specific domain (choose the most relevant pair):

```
Example axes for different domains:
  SaaS tools:     Minimal ←→ Feature-rich  ×  Technical ←→ Consumer
  Creative tools: Structured ←→ Expressive  ×  Pro ←→ Casual
  Wellness:       Clinical ←→ Warm  ×  Guided ←→ Self-directed
  Games/Fan:      Generic ←→ Authentic  ×  Polished ←→ Raw
```

Place this app and each benchmark on the matrix. Identify the whitespace. Is there a credible position the app could own that competitors don't currently occupy?

### §DCP3. Visual Differentiation Opportunities

Given the competitive map — identify the 2-3 most achievable visual differentiators available to this app:

**Format:**
```
Differentiator: [what visual quality would be distinctive]
Current state: [what the app does today in this area]
Target state: [specific visual direction]  
Effort: Low / Medium / High
Competitive value: [who it differentiates from and why it matters]
```

---

## XI. DESIGN CHARACTER SYSTEM

> Design personality is the aggregate visual character that emerges from an app's actual design decisions — its colors, spacing, motion, surfaces, typography, and component choices, taken together. This section names it precisely, assesses its coherence, and deepens it.

> **Claude execution note**: Always execute in order: §DP0 (EXTRACT from code) → §DP1 (ASSESS dimensions) → §DP2 (produce Character Brief) → §DP3 (DEEPEN). Never skip §DP0 — you cannot assess character you haven't read from the code. Present the §DP2 Character Brief to the user and confirm before deepening. §DP3 techniques 1–5 are highest-value; techniques 6–7 are for completeness.

**Three modes — all three always execute in order:**
1. **EXTRACT** — Read the actual design decisions in the code. Name the character that already exists.
2. **ASSESS** — Analyze that character along six dimensions. Find where the character is incoherent.
3. **DEEPEN** — Given the character (found or declared), produce a concrete plan to make it more concentrated, consistent, and unmistakably itself.

---

### §DP0. Character Extraction

**Before using any dimension framework, read the app's actual design decisions.** Extract the personality directly from the evidence.

> **Claude Code**: Use `Grep` to systematically extract design values:
> - Colors: `Grep(pattern: "#[0-9a-fA-F]{3,8}|colorPrimary|colorSurface|oklch", glob: "*.xml")`
> - Spacing: `Grep(pattern: "padding|margin|layout_margin|dimen", glob: "*.xml")`
> - Typography: `Grep(pattern: "textSize|fontFamily|textAppearance|font-size", glob: "*.{xml,css,kt}")`
> - Radius: `Grep(pattern: "cornerRadius|border-radius|corner", glob: "*.{xml,css,kt}")`
> - Animation: `Grep(pattern: "duration|anim|transition|interpolator", glob: "*.{xml,kt,swift}")`
> - For Android: also read `res/values/colors.xml`, `res/values/themes.xml`, `res/values/dimens.xml`

```
CHARACTER EXTRACTION — read from code, not from intent

Color character:
  Background values: [exact values — what temperature, lightness, chromatic quality]
  Surface values:    [exact values]
  Accent values:     [exact values — oversaturated? calibrated? muted?]
  Overall palette feeling: [describe in design terms: "cool-neutral with a single warm accent",
                            "high-contrast monochrome", "desaturated earth tones", etc.]

Spatial character:
  Dominant padding values: [list the most common — 8px? 24px? 48px?]
  Spacing rhythm: [tight / normal / generous — and is it consistent?]
  Gap between sections: [estimate from component structure]
  Overall density feeling: [information-dense / balanced / airy]

Typography character:
  Typeface(s) in use: [names]
  Weight range: [lightest weight used to heaviest]
  Size range: [smallest to largest]
  Overall type feeling: [precise/clinical / warm/humanist / editorial / technical]

Component character:
  Border radius: [values — what does this say about personality? sharp = technical, round = friendly]
  Shadow presence: [none / subtle / prominent — what elevation system exists?]
  Border style: [solid / dashed / none / subtle opacity]
  Button style: [filled / outlined / ghost — what personality does this project?]

Motion character (infer from CSS/JS):
  Transition durations: [values found]
  Easing values: [what's used]
  Overall motion feeling: [instant / snappy / considered / absent]

Icon character:
  Library in use: [Heroicons / Lucide / Phosphor / custom / mixed]
  Style: [line / filled / duotone / bold / inconsistent]
  Weight: [consistent stroke weight across icons? or mixed?]
  Overall icon feeling: [clinical-precise / friendly-rounded / expressive / generic-kit]

Copy / voice character:
  Formality register: [formal ("Authentication failed") / casual ("Wrong password")]
  Personality presence: [invisible ("No items") / voiced ("Nothing here yet — add one")]
  Domain fluency: [generic ("Submit") / specific ("Save draft") / expert ("Publish revision")]
  Overall voice feeling: [institutional / friendly / expert / generic / developer-terse]

Emergent personality statement:
  "Based on these decisions, this app reads as: [2–4 word character description]"
  "The strongest signals that produce this character: [top 3 specific design decisions]"
  "The weakest/most incoherent elements: [top 2 elements that contradict the character]"
```

**This extraction is the ground truth.** Everything in §DP1 uses it as input — not the other way around. If the user has declared an intended personality, compare the extraction to the intent. The gap between "what the design already says" and "what it's supposed to say" is the primary finding.

---

### §DP1. Character Dimensions Analysis

Using the extraction from §DP0, analyze the app's design character across six dimensions. These are **analytical tools for understanding the extracted character** — not abstract templates to fill in from scratch. Mark current position (from extraction) and target position (from §0 intent or axis profile). A significant gap is a finding.

#### Dimension 1 — Visual Voice

*What "tone of voice" do the actual design decisions speak in?*

```
Terse ←————————————————————————→ Expansive
(Information-forward, no decoration,   (Breathing room, white space, gestures,
 every pixel earns its place)            generous margins, the pause between words)

Cold ←—————————————————————————→ Warm
(Neutral grays, geometric precision,    (Chromatic surfaces, rounded forms,
 deliberate emotional distance)          soft gradients, human touch)

Formal ←————————————————————————→ Casual
(Structured, hierarchical, rules-driven, (Irregular, personal, rule-bending,
 institutional)                           feels handmade or personal)

Restrained ←————————————————————→ Expressive
(Discipline over personality,           (Personality over discipline,
 convention-respecting)                   convention-challenging)
```

**For each spectrum:** mark where the §DP0 extraction places the app (observed evidence), then mark where it should be (axis profile / intent). A gap of more than 2 units is a character misalignment finding — name the specific design decisions producing the gap.

#### Dimension 2 — Spatial Character

*What do the actual spacing values and layout structure say about how the product relates to space?*

```
Dense ←—————————————————————————→ Airy
(Information packed, small gutters,     (Large zones of breathing space,
 high data-per-pixel ratio)              few elements per screen)

Flat ←——————————————————————————→ Deep
(Single Z-plane, no layering,           (Multiple Z-planes, elevation,
 everything equally present)             depth communicates importance)

Rigid ←—————————————————————————→ Fluid
(Grid-locked, symmetric, predictable    (Organic, asymmetric, layout surprises
 alignment)                              without chaos)

Anchored ←——————————————————————→ Floating
(Elements clearly sit on surfaces       (Elements hover, elements bleed
 and relate to their context)            edges, gravity-free composition)
```

#### Dimension 3 — Material Character

*What do the surface treatments (radius, shadow, border, texture) say about what the UI feels like it's made of?*

| Material | Visual Signatures | Personality |
|---|---|---|
| **Glass** | Blur, translucency, thin borders, reflections | Refined, premium, slightly cold |
| **Metal** | Hard edges, specular highlights, anodized colors, precision | Technical, confident, performative |
| **Paper** | Flat, shadow-cast on surface, slight texture, organic | Familiar, comfortable, approachable |
| **Fabric / Felt** | Soft shadows, matte surfaces, tactile texture | Warm, crafted, analog |
| **Stone / Mineral** | Heavy visual weight, cool undertones, durable feeling | Authoritative, grounded, timeless |
| **Light** | Luminous, no material weight, color as the only surface | Ethereal, ephemeral, digital-native |
| **Wood / Organic** | Warm grain, slight imperfection, natural tones | Artisan, personal, alive |
| **Void / Spatial** | Near-black space, content floats, OLED-native | Cinematic, focused, dramatic |

**Identify** the current material personality (it may be accidental). **Assess** whether it matches the §0 axis profile. **Find** every component whose material treatment contradicts the dominant material personality.

#### Dimension 4 — Interaction Character

*What do the transition values and motion decisions say about what touching the product feels like?*

```
Mechanical ←————————————————————→ Physical
(State changes, no motion,             (Gravity, mass, spring, surfaces
 immediate, functional)                 push back, things have weight)

Snappy ←————————————————————————→ Considered
(100ms, instant response,              (200–400ms, eased curves, the
 everything is fast)                    product pauses before responding)

Passive ←———————————————————————→ Reactive
(UI elements don't respond             (UI responds to cursor proximity,
 until clicked)                         elements breathe, hover is generous)

Silent ←————————————————————————→ Expressive
(No micro-animations,                  (Every meaningful interaction has
 pure functional feedback)              a small visual personality moment)
```

#### Dimension 5 — State Character Consistency

*Design character is only as coherent as its weakest state.* Using §DP0 as baseline, assess whether the character holds across every key moment. States are where personality most often breaks — error states revert to generic red boxes, empty states become gray placeholders, loading states lose all visual identity:

| State | Character from §DP0 | Character here | Consistent? |
|---|---|---|---|
| First arrival (empty, onboarding) | | | |
| Active engagement (task in progress) | | | |
| Success / completion | | | |
| Error / failure | | | |
| Loading / waiting | | | |
| Edge/empty (no data) | | | |

For each inconsistency: a finding with the specific design decision that breaks character and the specific change that restores it.

#### Dimension 6 — Overall Character Coherence

After assessing all five dimensions against the §DP0 extraction: do they tell the same story?

A coherent character means all dimensions reinforce the same identity:
- `oklch(14% 0.01 240)` dark surface + void material + 80ms snappy transitions + dense spacing = coherent "precision instrument" character
- Warm palette + glass material + 300ms transitions + cramped spacing = incoherent — the materials tell contradictory stories

**Produce:**
```
Character Coherence: COHERENT / PARTIALLY COHERENT / INCOHERENT
Dominant character: [2–3 design-specific words — e.g. "clinical precision", "warm editorial",
                     "playful density", "cold utility", "atmospheric depth"]
Conflicting signals: [which specific design decisions break the dominant character]
Primary coherence fix: [the single highest-impact change — specific value or pattern]
```

---

### §DP2. Design Character Brief

Synthesize §DP0 + §DP1 into a single brief. This becomes the filter for all subsequent findings — every recommendation either reinforces or contradicts the character defined here.

```
━━━ DESIGN CHARACTER BRIEF ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
App: [name]
Axis Profile: [A1–A5 one-line summary]

EXISTING CHARACTER (extracted from §DP0)
  What the design already says:  [the character the current decisions produce,
                                   whether intentional or not — be specific]
  Strongest signals:             [the 3 design decisions most responsible for
                                   the current character — exact values]
  Weakest/incoherent signals:    [what breaks the character — exact values]

TARGET CHARACTER
  Voice:       [position on Voice spectra — anchored in specific design values]
  Space:       [position on Spatial spectra — anchored in specific spacing values]
  Material:    [dominant material — with the specific CSS properties that define it]
  Interaction: [character — with the specific timing/easing values that define it]
  States:      [character across all 5 states — consistent]

CHARACTER STATEMENT
  "[This app's design reads as ____. The strongest expression of this is ____.
   It should never feel ____ — which currently happens when ____.]"

CHARACTER TESTS (decision filters for new design work)
  ✓ ON CHARACTER: [specific rule derived from the extracted character]
  ✗ OFF CHARACTER: [specific anti-pattern to reject]
  ✓ ON CHARACTER: [second rule]
  ✗ OFF CHARACTER: [second anti-pattern]

PROTECT (existing design decisions that already express the character correctly)
  [list specific CSS values / component patterns / visual choices to preserve]

REJECT (patterns that belong to a different product's character)
  [list specific design choices that read as "wrong product" — not wrong aesthetics,
   wrong for *this specific character*]
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
```

---

### §DP3. Character Deepening Protocol

The character brief defines what the app already is and what it should become. This protocol provides the specific techniques to deepen it — to make the character more concentrated, more internally consistent, and more unmistakably itself.

**The deepening principle:** Character depth comes not from adding new elements, but from making every existing element express the character *more fully* and more precisely. An app with a clinical-precision character deepens by making its precision more exact — not by adding more clinical-looking elements, but by ensuring that every single decision, down to border opacity and cursor style, is precise in the specific way *this* app is precise.

**Seven deepening techniques:**

#### 1. Character Token Extraction

From §DP0 and §DP1, extract the specific CSS/design values that currently express the character *correctly* — and those that undermine it:

```
Example — "cold precision" character:
  EXPRESSES: border-radius: 2px, gap: 8px, color: oklch(45% 0.04 220) (desaturated cool)
  UNDERMINES: border-radius: 12px (projects warmth), color: #f59e0b (warm accent)

Example — "warm editorial" character:
  EXPRESSES: border-radius: 0px, gap: 32px, color: oklch(42% 0.08 60) (warm amber)
  UNDERMINES: font: Inter (too neutral), border: 1px solid #e5e7eb (too clinical)
```

Build a **character-consistent token set** — the specific CSS values that are correct for this character. Every finding that identifies a character violation should propose replacing it with a value from this set.

#### 2. Character Stress Testing

Apply the character across scenarios it wasn't designed for. The weakest moments reveal the gaps:

- **Error states:** Does the error message still look and feel like this specific app's design character? Or does it revert to a generic red box that could belong to any app?
- **Empty states:** Does the empty state have character, or is it a gray placeholder with no relationship to the product's visual identity?
- **Loading states:** Does the skeleton or spinner feel designed for this product, or is it a default?
- **Edge-case components:** Pagination, tooltips, date pickers, overflow menus — do these reflect the character or feel imported from a UI kit?
- **Mobile breakpoints:** Does the character survive layout compression, or does the mobile view feel like a different product?

For each stress test failure: a finding with the specific design decision that drops character, and the specific change that restores it.

#### 3. Sensory Vocabulary

Every strong design character has a sensory reference — a real-world material, texture, or experience that the visual language evokes. Name it. Then use it as a generative brief to find design decisions that don't yet match it:

```
Character: "precision instrument"
Sensory reference: "the tactile click of a machined watch crown"
Design implications not yet expressed:
  - Transitions exactly 100ms, ease-out — nothing longer or bouncier
  - 1px borders at full opacity — no semi-transparent softness
  - Grid alignment to within 1px everywhere — no floating elements
  - No border-radius beyond 2px on functional elements
  - Monospaced numerals (font-variant-numeric: tabular-nums) throughout

Character: "warm editorial"
Sensory reference: "opening a well-designed paperback book"
Design implications not yet expressed:
  - Slightly off-white surfaces (#faf8f5, not #ffffff) — paper, not screen
  - 1px borders at 25% opacity — rules, not walls
  - Generous leading (1.7) on body text — breathing room like typeset pages
  - Section dividers as thin horizontal rules, not heavy separators
  - Success state uses amber, not green — warm even in feedback
```

Produce a sensory vocabulary brief for this app's extracted character.

#### 4. Character Hierarchy

Not all elements carry the character equally. Establish which elements are **primary character carriers** (highest investment, most visible) vs **background elements** (consistent but quiet):

```
PRIMARY CARRIERS — character expressed at maximum intensity:
  [e.g. the app's central output moment, key CTAs, hero sections, success states]

SECONDARY CARRIERS — character present but not dominant:
  [e.g. navigation, form inputs, cards, list items]

BACKGROUND ELEMENTS — character-consistent but recede:
  [e.g. dividers, timestamps, pagination, scrollbars, tooltips]
```

Character deepening is most efficient when investment is concentrated on primary carriers first. A perfectly on-character success state matters more than a perfectly on-character scrollbar.

#### 5. The One Unavoidable Moment

Every app has one moment that is the product's central value delivery — the moment the user gets what they came for. This is the single highest-leverage moment for character expression.

Identify it. Assess whether it currently expresses the product's design character at maximum intensity. Provide a specific redesign of this moment that makes the character impossible to miss.

#### 6. Character-Neutral Audit

Review every component for elements that are "character-neutral" — they don't violate the character, but they don't express it either. A character-neutral divider is just `1px solid #e5e7eb`. The same divider on-character might be `1px solid oklch(30% 0.02 240 / 0.4)` — subtly different in a way that reinforces the specific character.

Character-neutral elements are wasted opportunities. For each: provide the specific minimal change that makes it character-positive without changing its function.

#### 7. Character Future-Proofing

Define the character rules that must be respected as the product grows:

```
Character Rules (non-negotiable as new features are added):
  1. [specific rule] — e.g. "Every new surface uses the chromatic dark token, never neutral gray"
  2. [specific rule] — e.g. "New interactive elements always use 100ms ease-out — nothing longer"
  3. [specific rule] — e.g. "Error states use desaturated amber — never generic red"

Character Risks (watch for these as the product scales):
  - [risk] — e.g. "Admin/settings sections typically revert to generic UI kit defaults — apply
    character explicitly to every new section, it won't happen automatically"
  - [risk] — e.g. "Third-party embeds (charts, forms, maps) break character — establish
    a wrapper/override pattern before adding them"
```

---

## XII. SOURCE MATERIAL INTELLIGENCE

> **The cardinal rule**: Never describe a named source's visual language from memory or keyword association. The goal is a living design specification that can generate correct answers to new design questions — "should this button have a glow?" — just by reasoning from the source's identity.

> **Claude execution note**: This section activates ONLY when the user references a specific named source (game, show, brand, IP). Do NOT run §SR0–SR6 without a named source. Execute §SR0 (5-pass research) IMMEDIATELY before any other section when a source is named — even before §DS1. If web search is unavailable, ask the user to provide screenshots directly. Never fabricate source visual descriptions from training data.

---

### §SR0. Source Research Mandate

> **Claude Code**: Execute each research pass using `WebSearch` for text searches and `WebFetch` for analyzing specific pages. Launch multiple `WebSearch` calls in parallel for efficiency. For image references, use `WebFetch` on image search result pages to extract descriptions and analysis. If the user provides screenshots directly, use the `Read` tool (which supports image files) to analyze them visually.

**BEFORE writing any recommendation that references a named source, execute this full multi-pass research protocol:**

```
PASS 1 — UI/INTERFACE EVIDENCE (what it actually looks like in use)
  Web search: "[source name] UI screenshots"
  Web search: "[source name] interface design"
  Web search: "[source name] HUD design" (for games)
  Web search: "[source name] menu screen"
  Image search: "[source name] UI" (3–4 queries, different angles)
  Image search: "[source name] main menu"
  Image search: "[source name] settings screen" or "[source name] inventory"
  → Goal: Real screenshots of the actual UI in context. Not fan art. Not promotional art.

PASS 2 — ART DIRECTION INTELLIGENCE (the design intent behind the choices)
  Web search: "[source name] art direction"
  Web search: "[source name] concept art visual development"
  Web search: "[source name] art book" or "The Art of [source name]"
  Web search: "design of [source name]" or "[source name] visual identity"
  Web search: "[source name] art director interview" or "[source name] visual design talk"
  → Goal: What the art directors intended. The *why* behind the *what*.

PASS 3 — CULTURAL AND THEMATIC ROOTS (what traditions it draws from)
  Web search: "[source name] inspired by [cultural tradition]" (infer from Pass 1 findings)
  Web search: "[source name] aesthetic influences"
  Web search: "[source name] art style analysis"
  → Goal: What real-world visual traditions, eras, movements, or cultures inform this source.

PASS 4 — COMMUNITY CODIFICATION (how insiders describe the aesthetic)
  Web search: "[source name] color palette" (fan-extracted palettes are often highly accurate)
  Web search: "[source name] design breakdown" or "[source name] UI analysis"
  Web search: "[source name] aesthetic wiki" or "[source name] lore visual guide"
  → Goal: How the community has already articulated the visual identity.
    Fans are often more precise about a source's aesthetic than official documentation.

PASS 5 — COMPETITIVE CONTEXT (what makes this source distinctive in its category)
  Web search: "[source name] vs [similar source] design"
  → Goal: What this source does that its peers do not — the differentiating visual signature.

NEVER:
  - Describe a named source's visual language from training memory alone
  - Say "inspired by [X]" without having actually looked at [X]
  - Use generic category descriptors as if they were source-specific
    ("fantasy game UI" is not "Wuthering Waves UI")
  - Produce a color palette "in the style of [X]" without having sampled
    actual colors from verified reference imagery
  - Treat the name as a mood word — treat it as a research target
  - Stop at Pass 1. Surface screenshots without understanding the art direction
    philosophy will produce shallow recommendations.
```

**If research fails** (no reliable visual reference found): state this explicitly. Do not fabricate. Say: *"I was unable to find reliable visual reference for [source]. To proceed accurately, please share screenshots of the source's UI/visual language directly in the conversation."*

**If user provides screenshots directly:** those are your primary evidence. Use them as Pass 1 input and continue from Pass 2 for context enrichment.

---

### §SR1. Source Style Brief

Once research is complete, populate this brief before writing a single design recommendation. Work through every layer — surface to philosophical. This brief is the specification; everything else derives from it.

```
━━━ SOURCE STYLE BRIEF ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
Source: [exact name and version/era — e.g. "Elden Ring (2022) UI" vs "Dark Souls III UI"]
Research method: [passes completed, what was found at each pass]
Reference quality: HIGH (official screenshots/art) / MEDIUM (fan-compiled) / LOW (indirect)
Era/version specificity: [which version or era is being referenced, if the source has evolved]

━━━ LAYER 1: SURFACE (what you can see and measure) ━━━━━━━━━

─── COLOR PALETTE (sampled from real reference, not imagined) ────
  Dominant backgrounds:   [specific values — oklch or hex + describe the quality:
                           "deep ink-black with faint indigo undertone, not neutral"]
  Primary surfaces:       [specific values + quality description]
  Accent / highlight:     [specific values + where it appears + what it signals]
  Typography colors:      [specific values — body, heading, secondary, disabled]
  Glow / atmosphere:      [specific values + how glow is applied — bloom, inner shadow, etc.]
  Palette character:      [overall temperature + saturation strategy + chroma range:
                           "cool-dominant with selective warm amber highlights; low average
                           chroma (~0.06) except accent at full chroma (~0.22)"]
  Colors that NEVER appear: [what palette tones are conspicuously absent — this defines
                              the aesthetic as much as what's present]

─── TYPOGRAPHY CHARACTER ─────────────────────────────────────
  Heading typeface feel:  [serif/sans/display/custom + specific qualities:
                           "slightly condensed gothic sans, feels institutional and heavy"]
  Body typeface feel:     [same specificity]
  Weight range used:      [lightest to heaviest actually observed in reference]
  Tracking character:     [tight / normal / loose + specific context:
                           "very tight on large headings, expanded on small caps labels"]
  Distinctive type treatments: [any unique uses: mixed case, color letterforms, outline text,
                                 gradient fills, mixed script, custom ligatures]
  Hierarchy strategy:     [how heading levels are differentiated — size? weight? case? color?]
  Typeface to use (mapped): [specific web font with reasoning for the match — not just
                             "something similar" but the specific font that captures
                             the exact quality: weight distribution, x-height, terminal style]

─── ICONOGRAPHY GRAMMAR ──────────────────────────────────────
  Icon style:             [line / filled / duotone / custom glyphs / mixed]
  Stroke weight:          [approximate visual weight at 24px — "1.5px, feels precise"]
  Corner treatment:       [fully rounded / slightly rounded / sharp / mixed]
  Complexity level:       [minimal/geometric / moderate / ornate / illustrative]
  Compositional approach: [symmetric / asymmetric / centered / offset]
  Distinctive motifs:     [recurring shapes, patterns, symbolic vocabulary — e.g.
                           "angular faceted gems", "circuit-trace lines", "calligraphic curves"]
  Cultural/thematic roots:[what iconographic traditions are being drawn from]
  What icons are NOT in use: [what would feel wrong — too cute, too corporate, too generic]

─── SPATIAL GRAMMAR ──────────────────────────────────────────
  Layout character:       [describe the grid logic, proportion, and rhythm in concrete terms:
                           "asymmetric panels with a dominant 2/3 left column; not a clean grid"]
  Density:                [how much information is packed per screen area]
  Padding rhythm:         [the spacing unit that defines the spatial feel — "multiples of 6px,
                           very tight compared to material conventions"]
  Border/frame treatment: [how elements are bounded — hard lines / soft glow / beveled /
                           no borders / ruled separator lines]
  Layering depth:         [flat / gently layered / dramatically layered / cinematic Z-depth]
  Signature spatial elements: [any distinctive structural patterns: "the angular corner-cut frame",
                                "the full-bleed hero panel bleeding to edge"]

─── ATMOSPHERIC QUALITIES ────────────────────────────────────
  Light source character: [direction, warmth, quality, and how it manifests:
                           "ambient upward light from below UI surface, cool-white, not warm"]
  Depth cues used:        [fog / glow halos / vignette / parallax / blur falloff / none]
  Texture presence:       [none / noise grain / surface texture / photographic texture]
  Contrast strategy:      [high-contrast / mid-tone dominant / low-contrast atmospheric]
  Atmospheric signature:  [what makes the atmosphere feel unmistakably like this source —
                           the specific quality that has no generic name]

─── MOTION CHARACTER (if observable from trailers, gameplay, or UI demos) ─
  Speed character:        [the dominant pace: "everything runs at 120ms or instant — no linger"]
  Signature transitions:  [how elements enter/exit — slide? fade? expand? iris? swipe?]
  Motion personality:     [mechanical / organic / dramatic / playful / invisible]
  What motion AVOIDS:     [bounce? spring? slowmo? — what would feel out of character]

━━━ LAYER 2: STRUCTURAL (the underlying logic and grammar) ━━━

─── VISUAL HIERARCHY STRATEGY ───────────────────────────────
  Primary attention target: [what the design consistently draws the eye to first]
  How contrast is used:   [contrast between primary/secondary — high gap / compressed range]
  Hierarchy tools in use: [size / weight / color / position / isolation / texture]
  What is consistently de-emphasized: [secondary info treatment — how it recedes]

─── MOTIF & SYMBOL LIBRARY ──────────────────────────────────
  Recurring geometric forms: [the shapes that appear again and again — diamonds, hexagons,
                               arcs, angular cuts, etc. — with their visual role]
  Ornamental patterns:    [any decorative elements — borders, dividers, watermarks, seals]
  Symbolic vocabulary:    [what symbols carry meaning in this world and how they look]
  Compositional motifs:   [structural patterns: "the three-panel status bar", "the radial menu
                           returning in multiple contexts", "diagonal cut as transition motif"]
  Color-role consistency: [which colors always signal which things — "amber = resource warning,
                           never used decoratively"; "blue = player identity consistently"]
  What appears at: [identify where motifs concentrate — loading screens, headers, transitions]

─── NEGATIVE SPACE DEFINITION ───────────────────────────────
  What this aesthetic deliberately excludes (this is as defining as what it includes):
  Excluded palette ranges:  [what color families this source never uses]
  Excluded form language:   [what shapes or radius values would feel wrong]
  Excluded typographic moves: [what type choices would break the identity — "no rounded
                               sans would survive here; no display serifs"]
  Excluded atmospheric qualities: [what moods would violate the aesthetic — "no warmth,
                                   no pastoral softness, no playfulness"]
  Excluded motion qualities: [what animation style would break character]
  → This negative space is essential for preventing "almost right" translations that
    include one element that breaks everything.

━━━ LAYER 3: CULTURAL & HISTORICAL (where it came from) ━━━━

─── VISUAL TRADITIONS DRAWN FROM ────────────────────────────
  Primary cultural references: [specific traditions with explicit evidence:
                                 "Ink wash (shuǐmò) painting — evident in the brush-stroke
                                 texture on environmental backgrounds and the monochrome
                                 ink wash atmospheric overlays"]
  Historical art movements:  [art deco / brutalism / constructivism / ukiyo-e / etc. —
                               with specific design elements that trace to each]
  Era influences:            [what decade or period aesthetic is evoked, and how]
  Regional character:        [the geographic/cultural origin of the aesthetic vocabulary
                               — this affects everything from spatial rhythm to color meaning]
  Secondary references:      [influences that appear but are not dominant]
  What it rejects culturally: [what traditions it explicitly moves away from]

─── ERA / VERSION SPECIFICITY ───────────────────────────────
  Which version/release to reference: [e.g. "pre-2.0 launch UI", "current live version",
                                        "original release — before the 2024 UI rework"]
  How the aesthetic has evolved:      [key changes between versions if applicable]
  Which era is most distinctive:      [the version that most purely expresses the identity]
  Recommendation:                     [which version to draw from and why]

━━━ LAYER 4: PHILOSOPHICAL (the intent and meaning) ━━━━━━━━

─── DESIGN PHILOSOPHY EXTRACTION ────────────────────────────
  What problem was this visual language solving?
    [What did the art director need to communicate / achieve? What failure mode were
    they designing against? e.g. "Solve: the UI must feel diegetic — like it exists
    in the world, not floating above it. Failure avoided: the 'videogame UI' meta-feeling
    that breaks immersion."]

  What emotional experience was being engineered?
    [Not just "epic" or "dark" — the specific feeling: "the solitary competence of an
    expert moving through a hostile world; mastery, not heroism"]

  What does the design say to the user non-verbally?
    [The implicit message the visual language communicates: "you are in a serious place
    that respects your intelligence and does not coddle you"]

  How do individual visual choices serve the narrative/product intent?
    [2–3 specific examples: "The angular frame with the corner-cut performs 'manufactured
    technology within a harsh world' — not organic, not generic corporate"]

  What would be lost if the aesthetic were stripped to generic?
    [What specific meaning would evaporate — this clarifies what must be preserved]

━━━ LAYER 5: IDENTITY THESIS (the generative essence) ━━━━━━

─── IDENTITY THESIS ─────────────────────────────────────────
  This is the single statement that can generate correct design decisions for new
  design problems. It captures not just what the source looks like, but why —
  and therefore can answer "would this design choice be in or out of character?"

  Thesis: "[One precise sentence that names the aesthetic strategy, the emotional
           target, and the design logic — e.g.: 'A cold, manufactured precision
           vocabulary built from angular geometry and desaturated cool tones that
           communicates dangerous competence and diegetic technology, never warmth,
           never organic, never generic game UI.'"]"

  Three decisions that, if right, make the source instantly recognizable:
    1. [The single most identifying visual choice — e.g. "The angular corner-cut frame
       on all panels — more than any color, this is the visual signature"]
    2. [Second most identifying — e.g. "The OKLCH(12% 0.03 240) near-black surface
       with precisely calibrated cool undertone"]
    3. [Third — e.g. "The tracked all-caps UI labels in a geometric sans at 0.08em"]

  Minimum authentic set (what you absolutely must have for the translation to read as
  this source, even if everything else is simplified):
    [List the 4–6 specific design decisions that are non-negotiable for recognition.
    Everything else is optional detail. These are the identity bones.]

  What would kill the identity:
    [The 2–3 specific things that, if introduced, would make the translation feel
    wrong regardless of how accurate everything else is]

─── TRANSLATION CONSTRAINTS ─────────────────────────────────
  What translates well to web UI:   [elements that adapt naturally with minimal loss]
  What requires interpretation:     [elements that need thoughtful translation —
                                     what is preserved, what is sacrificed, and how]
  What to avoid copying literally:  [elements that only work in their native medium
                                     and would fail or feel wrong in web UI]
  Scale-specific issues:            [elements that work at native resolution/distance
                                     but break at web/mobile sizes]
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
```

---

### §SR2. Fidelity Spectrum

Not every source-material reference request means "replicate exactly." Establish with the user where on this spectrum the translation should sit. **The identity thesis (§SR1 Layer 5) is what gets expressed at each level — shallower levels borrow less of it.**

| Level | Name | Description | What from §SR1 is used |
|---|---|---|---|
| **L1** | **Tone echo** | Same emotional register, no literal visual borrowing | Layer 4 only (emotional target, design philosophy) |
| **L2** | **Palette & atmosphere** | Same color temperature, surface character, atmospheric quality | Layers 4 + partial Layer 1 (color + atmosphere only) |
| **L3** | **Visual vocabulary** | Colors, typography character, iconography style, spatial grammar all aligned | Layers 1–2 fully, Layer 3 partially, Layer 4 informs decisions |
| **L4** | **Deep fidelity** | All of L3 + distinctive motifs, ornamental elements, cultural visual roots, negative space respected | All five layers fully applied |
| **L5** | **Immersive replica** | The app feels like it was made by the source's own design team; passes the "insider test" | All five layers + every motif, every texture, every motion signature; negative space enforced strictly |

**The insider test (L4/L5):** Would someone deeply familiar with the source immediately recognize the design as belonging to that world, before seeing any of the source's content? If yes: L4/L5 achieved. If they say "this is vaguely inspired by [source]": L3 at best.

**State the target fidelity level explicitly.** If user has not stated a preference, propose L3 as default for companion apps, L4 for fan-created tools, and ask for confirmation.

---

### §SR3. Source Translation Plan

After completing §SR1, use the identity thesis and minimum authentic set to build the translation plan. **Prioritize the minimum authentic set first** — these must be right before investing in secondary details.

**Format:**
```
━━━ IDENTITY THESIS (from §SR1) ━━━━━━━━━━━━━━━━━━━━━━━━━━━━
[Restate the thesis here — this is the filter for every decision below]
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

─── MINIMUM AUTHENTIC SET (non-negotiable — implement these first) ─────────

ELEMENT: [identity-critical element — e.g. "Panel framing — angular corner-cut geometry"]
  Source: [precise description from §SR1 Layer 1–2, with visual evidence reference]
  Identity role: [why this element is in the minimum set — what identity it carries]
  Translation: [what this becomes in web CSS — specific values and approach]
  Implementation:
    /* Angular corner-cut frame */
    clip-path: polygon(12px 0%, 100% 0%, 100% calc(100% - 12px), calc(100% - 12px) 100%, 0% 100%, 0% 12px);
    border: 1px solid oklch(40% 0.04 240 / 0.6);
  Fidelity level: L3
  Failure mode: [what goes wrong if this is simplified or omitted]

─── SECONDARY ELEMENTS (implement after minimum set) ──────────────────────

ELEMENT: [secondary element]
  Source: [from §SR1]
  Translation: [specific CSS/design values]
  Implementation: [code or specific values]
  Fidelity level: L2 / L3 / L4

─── NEGATIVE SPACE RULES (what must NOT appear) ─────────────────────────

PROHIBITED: [specific thing to avoid, from §SR1 negative space definition]
  Why: [which part of the identity it violates]
  Common mistake: [what well-meaning designers do that breaks this]
```

**One entry per major element from the Source Style Brief. Order: minimum authentic set first, secondary elements after.**

---

### §SR4. Authenticity vs Legibility Balance

Source material is rarely designed for web UI legibility. The job is to honor the source's visual language while maintaining functional readability.

**First: consult the identity thesis.** Some sources are designed to be *difficult* to read — this is intentional and part of their character. Before defaulting to "make it more legible," ask: does the source's own UI prioritize readability or atmosphere over readability?

**Authenticity risk — for every source element being translated:**
- Game HUD elements designed for 1080p at arm's length do not directly translate to 14px web body text
- Ornate decorative borders that read beautifully at 1440p become noise at 320px mobile
- Dark atmospheric overlays that work over gameplay video are illegible over web UI content
- Motion that works at 60fps native rendering may feel janky at browser frame rates

**Minimum authentic set protection:** Before modifying any element from the minimum authentic set for legibility, ask: is there a solution that preserves the identity element while solving the legibility problem? The minimum authentic set should only be modified as a last resort.

```
TENSION: [specific authenticity-legibility conflict]
  Source treatment: [what the source does, and why it works in context]
  Legibility problem: [what fails in web UI translation]
  Preferred resolution: [minimum modification that preserves authenticity]
  Identity cost: [what is sacrificed — be explicit, never silently]
  Alternative approach: [if modification is unavoidable, what else could preserve the identity element]
```

---

### §SR5. Source Material Accuracy Audit

After producing source-derived recommendations, validate against the evidence — not against the thesis alone:

```
For each recommendation that references [source]:
  □ Is the claimed color actually present in the source? (Not: "dark" — specific: oklch values
    sampled from reference imagery, or fan-extracted palettes with verifiable accuracy)
  □ Is the claimed typographic character accurate? (Not: "elegant" — specific: serif/sans,
    weight range actually used, tracking pattern actually observed)
  □ Is the iconography description accurate? (Not: "detailed" — specific: stroke weight
    measured, corner treatment observed, complexity level verified)
  □ Is the atmospheric quality accurate? (Not: "dramatic" — specific: light direction verified,
    depth technique identified, texture presence confirmed)
  □ Does the recommendation serve the identity thesis? (Every recommendation should
    be traceable back to the thesis — if it can't be, it's decoration, not translation)
  □ Does the recommendation respect the negative space rules? (Check each against
    the prohibited list in §SR3)
  □ Is there a real reference image or source that confirms this, or is this inferred?

For each unchecked box:
  - Revise the recommendation to remove the unverified claim, OR
  - Mark it [UNVERIFIED — needs visual reference confirmation], OR
  - Mark it [INFERRED FROM THESIS — directionally correct, verify against reference]

Identity thesis check:
  □ Does the minimum authentic set actually appear in the recommendations?
  □ Is the identity thesis honored even in secondary elements?
  □ Would an insider recognize this translation, or would they say "close but not quite"?
```

---

### §SR6. Source Research Log

Document the research process so the user can verify and extend it:

```
━━━ SOURCE RESEARCH LOG ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
Source: [name + era/version]

Research passes completed:
  Pass 1 (UI screenshots): [queries run → what was found, quality]
  Pass 2 (art direction):   [queries run → what was found, quality]
  Pass 3 (cultural roots):  [queries run → what was found, quality]
  Pass 4 (community codif): [queries run → what was found, quality]
  Pass 5 (competitive):     [queries run → what was found, quality]

Best reference sources:
  - [URL or description] — [what it contributes; which layer of §SR1 it informs]
  - [URL or description] — [contribution]
  ...

Identity thesis confidence: HIGH / MEDIUM / LOW
  [Reasoning: which passes produced strong evidence; where evidence was thin]

Gaps (what could not be verified):
  - [element] → marked [UNVERIFIED] in recommendations
  - [element] → marked [INFERRED FROM THESIS]

If user can provide: [what specific screenshots, art books, or reference material
  would close the remaining gaps and increase confidence to HIGH]
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
```

---

## XIII. STATE DESIGN SYSTEM

> States are where character most reliably collapses. Error states revert to generic red boxes. Empty states become gray placeholders. Designing all states with equal character investment is the single most visible quality separator between polished and functional products.

> **Claude execution note**: Audit every state that exists in the codebase. §DST1 (empty states) and §DST3 (error states) are highest-impact — these are where character collapse is most visible. §DST4 (success states) is highest-value for character deepening. If the app has no designed states at all, flag this as a single HIGH finding and provide a starter specification for each state type.

**The six states every screen has — and most designs only build one:**

| State | When it occurs | Most common failure |
|---|---|---|
| **Empty** | No data yet; first use; cleared state | Gray placeholder, no character, feels like a broken app |
| **Loading** | Waiting for data, processing, async | Default spinner, skeleton without character, frozen feeling |
| **Error** | Something went wrong | Generic red box, alarming, no character, no recovery path |
| **Partial** | Some data, not all; degraded state | Not designed at all — partial data renders as broken layout |
| **Success** | Task completed; data saved; milestone | Green check, sometimes no state at all — missed celebration |
| **Saturated** | Maximum capacity; too much data | Not designed — layout breaks or silently truncates |

---

### §DST1. Empty State Design

The empty state is the first experience new users see. It sets the product's character before the product has had a chance to prove its value.

**Empty state must contain, in order of importance:**
1. **Character-positive visual element** — not a generic illustration from an illustration library; something that expresses the product's specific visual identity
2. **Explanation** — what this space is for; why it's empty; what "full" looks like
3. **Primary action** — the single thing to do to fill this space
4. **Secondary tone signal** — the writing voice, the spacing, the color — all consistent with the product character

**Empty state visual strategies by character:**

| Character (from §DP2) | Empty state approach |
|---|---|
| **Cold precision** | No illustration; typographic statement; exact, confident wording; not apologetic |
| **Warm editorial** | Simple typographic display text as primary element; generous spacing; inviting |
| **Cyberpunk/terminal** | ASCII art or terminal-style prompt; monospaced; the emptiness is "no signal" |
| **Playful/consumer** | Custom character illustration; upbeat copy; color matches the celebration of eventually filling it |
| **Minimal** | Pure type, maximum whitespace; the emptiness is intentional — it's a promise |

*Android empty state specifics:*
- **File manager empty states**: Empty folder, empty recycle bin, no search results, no favorites — each is a distinct character moment. The empty folder state is seen frequently and should feel intentional, not broken.
- **Vector illustration vs icon**: A `VectorDrawable` illustration at ~120×120dp is the appropriate scale for an Android empty state. Avoid full-raster illustrations that blur across density buckets.
- **Empty state layout**: Center the illustration + text + action vertically in the available space (between toolbar and bottom nav). Use `ConstraintLayout` with vertical bias `0.4` (slightly above center) for optical balance.
- **Action button**: The primary CTA in an empty state should use `MaterialButton` with the app's accent color — not a text link. The action should be specific ("Create a folder", "Add files") not generic ("Get started").

**Assess:** For every empty state in the app:
```
State location: [which screen/component]
Current treatment: [describe exactly what appears]
Character consistency: [does it match §DP2? what specific elements break or maintain character?]
Recommendation: [specific visual change — including any copy changes]
Platform check (Android): [Is the illustration a VectorDrawable? Is the CTA a styled MaterialButton?
                           Is the layout vertically balanced?]
```

---

### §DST2. Loading State Design

Loading states are motion decisions more than visual decisions — they communicate how the product relates to time.

**Loading state character spectrum:**

| Loading character | What it says | When to use |
|---|---|---|
| **Instant / no loader** | "I am fast; I don't acknowledge slowness" | When actual load time is <200ms |
| **Skeleton** | "Here is the shape of what's coming" | When structure is known; most content-heavy screens |
| **Progress indicator** | "I am aware of the wait; here is where we are" | Long, knowable-duration processes |
| **Ambient pulse** | "Something is happening, don't worry" | Unknown duration, background processes |
| **Full-screen loader** | "This transition deserves attention" | Only for first load or major context shifts |

**Skeleton state specifics:**

The skeleton must:
- Match the geometry of the actual content — a skeleton card that is a different size from the real card is a design failure
- Use palette-appropriate colors (not generic `#E5E7EB` / `#F3F4F6`)
- Animate with character-appropriate timing (see §DM1 motion vocabulary)
- Maintain the same spatial rhythm as the loaded state — skeleton spacing must match actual content spacing

**Assess:** For each loading state in the app:
```
Location: [screen/component]
Type: [skeleton / spinner / progress / pulse / none]
Geometry match: [does the skeleton match the actual content size/structure?]
Palette match: [are the skeleton colors from the product palette?]
Animation character: [does the animation match the motion vocabulary?]
```

---

### §DST3. Error State Design

Error states are the product's character under stress. They reveal whether the design system was built with depth or just built for the happy path.

**Error state design principles:**

1. **The error must feel like this product's error** — not a generic red box that could belong to any product. The color, typography, and tone must be consistent with the design character.

2. **The error must communicate severity accurately** — three severity levels require distinct visual treatment:
   - **Informational/advisory**: the user should know something, but nothing is broken
   - **Recoverable error**: something went wrong, and the user can fix it
   - **Unrecoverable/system error**: something failed outside the user's control

3. **The error must contain a path forward** — every error state should have a primary action (retry, go back, contact support)

4. **Character-specific error design:**

| Character | Wrong error treatment | Right error treatment |
|---|---|---|
| **Cold precision** | Warm amber/orange warning box (wrong temperature) | Desaturated cool-red or amber; clinical; specific; actionable |
| **Warm editorial** | Harsh red alert box (feels like an attack) | Muted amber; gentle; apologetic tone; warm |
| **Playful/consumer** | Aggressive red with exclamation (too alarming for the context) | Characterful illustration; lighter color; friendly copy |
| **Minimal** | Any heavy error UI (violates the visual vocabulary) | Single underline on the field; inline text; no box |

**Assess:** For each error state:
```
Error type: [validation / system / network / empty-result / permission]
Current treatment: [describe — color values, typography, layout]
Character consistency: [on or off character?]
Severity calibration: [does the visual weight match the severity?]
Recommendation: [specific changes with values]
```

---

### §DST4. Success State Design

Success states are celebrations — but most apps design them as afterthoughts. A success state is the highest-impact moment for character expression: the user just completed something, they are emotionally open, and the product has their full attention.

**Success state intensity scale:**

| Moment type | Visual intensity | Example |
|---|---|---|
| **Micro-success** (field saved, setting toggled) | Subtle — color pulse, checkmark, brief confirmation | Auto-save indicator |
| **Task success** (form submitted, item created) | Moderate — toast, inline confirmation, state change | "Draft saved" toast |
| **Milestone success** (first item created, first transaction, goal reached) | Expressive — dedicated state, possible animation, genuine celebration | "You created your first project!" |
| **Major achievement** (paid, upgraded, completed onboarding) | Full moment — screen-level, branded, emotionally resonant | Post-checkout confirmation |

**Character-expressive success:**

The success moment should be the *most on-character* moment in the product — the character concentrated and expressed at maximum intensity, because the user earned it.

```
SUCCESS MOMENT BRIEF
  Moment: [which achievement triggers this state]
  Intensity level: [micro / task / milestone / major]
  Current treatment: [describe]
  Character opportunity: [what the §DP2 character brief suggests this moment should feel like]
  Recommendation: [specific visual + motion + copy direction]
  The one thing it must never feel like: [the generic anti-pattern to avoid]
```

---

## XIV. RESPONSIVE DESIGN CHARACTER

> Character that only works at one viewport is not a design system — it is a design scene. True design character survives and sometimes intensifies at every viewport.

> **Claude execution note**: Skip this section entirely if the app is not responsive or is a single fixed-width layout. For responsive apps, §DRC1 (breakpoint audit) is the core deliverable. §DRC2 (mobile intensification) is highest-value when the product is mobile-primary.

---

### §DRC1. Breakpoint Character Audit

For each major breakpoint: does the design character hold?

**Breakpoint definitions:**

*Web (CSS media queries):*
```
Desktop large:  1440px+ — full design expression; typically where character is strongest
Desktop normal: 1024–1440px — standard laptop; assess for character degradation
Tablet:         768–1024px — mixed; navigation and layout changes most affect character
Mobile:         375–768px — smallest canvas; most character compromises happen here
Mobile small:   320–375px — edge case; character elements must survive extreme compression
```

*Android (resource qualifiers + WindowSizeClass):*
```
Compact width:   < 600dp   — phone portrait; single-pane layout; BottomNavigationView
Medium width:    600–840dp — large phone landscape, small tablet; optional list-detail
Expanded width:  > 840dp   — tablet, foldable open, desktop; NavigationRail or permanent drawer
Compact height:  < 480dp   — phone landscape; collapse vertical chrome, hide app bar on scroll
Foldable states: FLAT / HALF_OPENED — use WindowInfoTracker for fold position + hinge bounds
```
Check `layout-sw600dp/`, `layout-w840dp/` resource qualifiers. Modern approach: `WindowSizeClass` from `androidx.compose.material3.windowsizeclass` (Compose) or `WindowMetrics` + `WindowSizeClass` from `androidx.window` (Views). Grep for `calculateWindowSizeClass` or `WindowMetricsCalculator`.

*iOS:*
```
iPhone SE:      375×667pt — smallest modern; character under maximum compression
iPhone std:     390×844pt — standard target; assess character here first
iPhone Pro Max: 430×932pt — largest phone; assess for excess whitespace / underleveraged space
iPad:           1024×1366pt — full expression; Split View / Slide Over modes add complexity
```

**For each breakpoint, assess:**
1. **Typography character** — does the type scale survive? Does the font size get so small that the typeface's character is lost? (Some typefaces lose their personality below 14px)
2. **Spacing character** — does the spatial rhythm compress to a generic `16px padding-x` or does it maintain the product's spacing vocabulary?
3. **Color character** — dark mode/light mode behavior; do background colors remain consistent or do they shift?
4. **Component character** — do components that express the design character (custom borders, distinctive radius, atmospheric treatments) survive layout changes?
5. **Motion character** — are touch-appropriate transitions in place? (Hover states don't exist on mobile — what replaces them?)

**Character compression failure patterns:**

| Failure | Description | Correction |
|---|---|---|
| **Spacing collapse** | All padding becomes `16px` on mobile regardless of component | Define mobile spacing tokens that preserve the spacing ratio |
| **Type reset** | All text becomes the same size on mobile | Maintain relative scale, just compress proportionally |
| **Shadow loss** | Elevation signals disappear on mobile | Use border or lightness instead — the Z-signal must survive |
| **Character element omission** | Distinctive borders/textures/atmospheres removed "to simplify" | Simplify where necessary, but keep 1 character-marker per screen |
| **Navigation character loss** | Mobile nav defaults to hamburger menu with zero design character | The navigation pattern is the most-seen interaction — it must carry character |
| **Density bucket mismatch** *(Android)* | Assets designed for one density look blurry or oversized on others | Provide drawables at `mdpi` through `xxxhdpi`, or use vector `VectorDrawable` which scales perfectly |
| **Foldable character collapse** *(Android)* | App ignores fold state, wastes half the screen when unfolded | Use `WindowInfoTracker` to adapt layout at fold boundaries; treat the expanded state as a character opportunity, not just "bigger phone" |

---

### §DRC2. Mobile Character Intensification

Mobile is not a constrained desktop — it is a different context with different opportunities. Some character elements that are subtle on desktop can be *intensified* on mobile because the viewing context is closer and more intimate.

**Mobile character opportunities:**
- **Touch feedback** can be more expressive than hover (tap ripple with character color). *Android*: Override `?attr/colorControlHighlight` per-component for character-tinted ripples.
- **Gesture transitions** (swipe, pull-to-refresh) can carry character through motion physics. *Android*: `ItemTouchHelper` swipe callbacks, `SwipeRefreshLayout` color scheme, predictive back animation (API 34+).
- **Bottom sheet presentations** replace modals — the bottom sheet's design is a character moment. *Android*: `BottomSheetDialogFragment` with `app:shapeAppearanceOverlay` for custom top corners, scrim color override, peek height calibration. The bottom sheet's corner radius and background color are high-visibility character decisions.
- **Safe area handling**: *(Android)* Edge-to-edge with `enableEdgeToEdge()` — the status bar and navigation bar become part of the app's canvas. The color that shows behind the transparent system bars is a character decision. *(iOS)* iPhone notch/island — some products use the safe area color as a design element.
- **Pull-to-refresh** animation is a personality moment — almost no products design it. *Android*: `SwipeRefreshLayout.setColorSchemeColors()` accepts up to 4 colors that cycle during the refresh animation — use the brand palette, not the Material default.
- **Navigation pattern as character** *(Android)*: `BottomNavigationView` with `app:itemIconTint` and `app:itemTextColor` as `ColorStateList` — the selected/unselected icon colors carry character. The label visibility mode (`labeled`/`unlabeled`/`selected`) is a character-appropriate choice: productivity apps often hide labels; consumer apps show them.
- **Haptic feedback moments** *(Android)*: Long press, drag threshold, toggle state changes, destructive action confirmation — each can have calibrated haptic feedback via `HapticFeedbackConstants.LONG_PRESS`, `.CONTEXT_CLICK`, `.CONFIRM`, `.REJECT` (API 30+). Most apps use none; the best apps use haptics as a design material.

**Assess:** Does the mobile experience feel like the same product at a different viewport, or a different product? List the 3 most significant character gaps. *(Android)*: Are Material components using themed `ColorStateList` values or system defaults? Is edge-to-edge implemented with intentional system bar treatment?

---

### §DRC3. Adaptive Character Specification

For any character elements that cannot survive compression intact, provide an adaptive specification:

```
ADAPTIVE ELEMENT SPECIFICATION
  Element: [what design element is being adapted]
  Desktop version: [specific values at desktop]
  Mobile adaptation: [what it becomes at mobile — different values, same character]
  Character preserved: [what aspect of the character is maintained in the adaptation]
  Character traded: [what is sacrificed — be explicit]
  Breakpoint trigger: [at what px does the adaptation kick in?]
```

**The character floor rule:** Even at the smallest viewport, every screen must contain at least one element that is unmistakably from this product's design system. If a screen strips all character elements for simplicity, it has violated the character floor — and needs at minimum one restoration.

---

## XV. COMPONENT DESIGN CHARACTER

> Every component has three design obligations: (1) function correctly, (2) be legible and accessible, (3) express the product's design character. Most apps satisfy 1 and 2. This section focuses entirely on 3.

> **Claude execution note**: §DCO1 (buttons) is highest-priority — buttons are the most-seen interactive element. §DCO3 (cards) and §DCO4 (navigation) are next. Complete at least these three. §DCO2 (inputs), §DCO5 (modals), §DCO6 (toasts) add depth but can be deferred if time is short.

---

### §DCO1. Button System Audit

Buttons are the most-seen interactive element in any product. They communicate: what you can do, how important each action is, and what personality is behind the product.

**The button hierarchy** — every product needs a system, not just styles:

```
PRIMARY (one per screen maximum — the most important action)
  → Should command the most visual weight
  → Color: accent at full saturation
  → Hover: accent shifted 5–8% lighter in OKLCH
  → Active: scale(0.97) + brightness(0.92) — physical confirmation

SECONDARY (supporting actions, alongside primary)
  → Less visual weight than primary — never competes
  → Style choices: outlined / ghost / muted fill
  → The relationship between primary and secondary weight gap is the hierarchy

TERTIARY / GHOST (low-stakes actions, destructive confirmations, navigation)
  → Nearly invisible at rest — visible on hover
  → Should feel like a suggestion, not an instruction

DESTRUCTIVE (delete, remove, irreversible actions)
  → Must be visually distinct from primary — different color family entirely
  → Never red as the default state — only on hover/confirm (red at rest = constant alarm)
```

**Character-specific button assessment:**

| Character (§DP2) | Wrong button treatment | Right button treatment |
|---|---|---|
| **Cold precision** | `rounded-lg`, soft shadow, `font-weight: 500` | `border-radius: 3px`, no shadow, `font-weight: 600`, tracked caps label |
| **Warm editorial** | Square corner, harsh fill color | `border-radius: 6px`, slightly warm accent fill, `font-weight: 450` if variable |
| **Cyberpunk/terminal** | Standard filled button | Outlined with glow on hover; `border: 1px solid accent`; text-only with terminal underline |
| **Playful/consumer** | Generic filled `rounded-md` | Fully rounded pill; slightly bouncy active state; playful hover color shift |
| **Minimal** | Any fill (violates vocabulary) | Underline-only; label-only; border appears on hover; no box |

**Button craft checklist:**
```
□ Primary button: does it have distinct hover, active, focus, disabled states?
□ Hover state: is it more than just a color-darken? Does it match motion vocabulary?
□ Active/press: is there physical feedback (scale, brightness)?
□ Focus ring: is it custom and character-appropriate? (See §DM4)
□ Disabled: reduced opacity only, or also changes cursor + prevents click?
□ Loading state: does the button have an in-progress state that maintains its size?
□ Icon + text: if icons appear in buttons, are they optically centered? (See §DI2)
□ Label typography: does it match the type character? (All-caps? Tracking? Weight?)
□ Size variants: are there deliberate small/medium/large sizes with proportional radius?
```

---

### §DCO2. Input & Form System Audit

Forms are where many products lose users. The design of inputs communicates: "this is easy" or "this is effort." The character of inputs must match the product — a clinical precision product with rounded, bouncy inputs has a split personality.

**Input anatomy and character:**

| Anatomy part | Generic treatment | Character-expressing treatment |
|---|---|---|
| **Border** | `1px solid #e5e7eb` | From the palette: chromatic, opacity-based, character-appropriate weight |
| **Focus border** | `2px solid #3b82f6` (Tailwind default) | Custom focus color from §DC2 `--border-focus` token, matching §DM4 focus ring |
| **Background** | `#ffffff` or `#1f2937` | Slight elevation above page background using OKLCH lightness system |
| **Label** | `text-gray-700 font-medium` | From the type hierarchy — size, weight, and color matching the label character |
| **Placeholder** | `text-gray-400` | Chromatic match to input background hue (See §DBI3 signal #12) |
| **Error state** | `border-red-500` + red text | Character-appropriate error (See §DST3) — never generic red |
| **Helper text** | `text-gray-500 text-sm` | Muted text from palette; same hue family as label |

**Input character consistency check:**

Inputs should feel like they belong to the same world as the buttons. Assess: if you removed all labels and context, would a designer looking at just the inputs and just the buttons place them in the same product? If not — the component vocabulary is split.

**Form layout character:**

The spatial arrangement of form fields carries as much character as the field styling:
- **Label position**: above (standard, readable), inside-floating (modern, saves space but complex), left-inline (technical/data-entry feel, document-like)
- **Field grouping**: how related fields are visually grouped — proximity, separator lines, section headers
- **Vertical rhythm**: the gap between fields; should use the product's spacing vocabulary (§DP0 spatial character), not a default margin

---

### §DCO3. Card & Surface System Audit

Cards are the primary content container in most products. They simultaneously define the elevation system, communicate the grouping logic, and carry the surface material character.

**Card character anatomy:**

```
CARD ANATOMY ASSESSMENT
  Background: [does the card lift above the page? what lightness difference?]
  Border:     [present / absent — if present: opacity, width, color, radius]
  Shadow:     [present / absent — if present: direction, blur, spread, color]
  Radius:     [is it character-appropriate? does it match the button radius family?]
  Padding:    [is the inner padding from the spacing vocabulary?]
  Divide:     [how are sections within the card separated?]
  Header:     [is there a visual header zone? how is it differentiated?]
  Hover:      [does the card respond to hover? how? — appropriate for cards that are links]
```

**Radius coherence across component types:**

The border radius of a card communicates the same personality dimensions as the button radius. They must form a coherent family — not identical, but related:

```
Example — "Cold precision" character:
  Buttons:  border-radius: 3px (sharp functional)
  Inputs:   border-radius: 4px (slightly less sharp — data entry, not action)
  Cards:    border-radius: 6px (containers are slightly softer than their interactive elements)
  Modals:   border-radius: 10px (largest container = most softening)
  Badges:   border-radius: 3px (same as buttons — same semantic weight)

Example — "Warm consumer" character:
  Buttons:  border-radius: 100px (full pill)
  Inputs:   border-radius: 10px (rounded but not pill)
  Cards:    border-radius: 16px (generous, welcoming)
  Modals:   border-radius: 20px (even more welcoming)
  Badges:   border-radius: 100px (pill — matches button)
```

**Assess:** Does the radius family follow a coherent logic, or are values scattered with no system?

---

### §DCO4. Navigation Design Character

Navigation is the highest-frequency interface — users encounter it on every screen. It communicates the product's structural philosophy and carries character through pure repetition.

**Navigation character dimensions:**

| Dimension | What it communicates | Character implications |
|---|---|---|
| **Position** (top / left / bottom) | Information architecture depth, spatial metaphor | Left sidebar = documents, depth, structure; Top bar = pages, breadth, modality; Bottom (mobile) = app-like, thumb-driven |
| **Active state treatment** | "Where you are" — how the product marks the current location | Active indicator color, weight, and style must be the most character-expressive element in the nav |
| **Hover treatment** | "Where you could go" — how the product invites exploration | Must be distinct from active but related — same color family, less saturation/weight |
| **Icon treatment** | Whether icons augment or replace labels | Icon-only nav signals expert use; labeled nav signals consumer/broad audience |
| **Density** | How many items, how much space | Dense nav (compact, many items) = power-user product; sparse nav = focused, simple product |

**Navigation active state deep assessment:**

The active indicator is the single most repeated design element in navigation-heavy products. It must:
1. Be unmistakably clear which item is active
2. Express the character — the shape, color, and weight of the indicator is a character signal
3. Be consistent: the same active pattern everywhere

Active state character patterns:
```
Filled pill/capsule:   Active = full background fill → approachable, consumer, modern
Left-side border:      Active = 2–4px border on left edge → editorial, document-like, structured
Underline:             Active = bottom border → minimal, tab-like, content-forward
Icon fill change:      Active = icon goes from line to filled → clean, icon-primary
Color shift only:      Active = text color change, no shape → most minimal, least clear
Dot indicator:         Active = small dot above/below icon → playful, mobile-app-like
```

**Assess:** What active state pattern is used? Is it character-appropriate? Is it visually clear enough that a new user can immediately identify where they are?

---

### §DCO5. Modal & Overlay System Audit

Modals interrupt — they take over the screen and demand the user's attention. How a product handles this interruption reveals its character under pressure.

**Modal character anatomy:**
- **Backdrop**: opacity and color of the dimmed background. Full black at 50% = generic. `oklch(10% 0.02 240 / 0.7)` = chromatic, character-positive.
- **Entry animation**: how the modal enters. Fade only = minimal. Scale from center = approachable. Slide from bottom = mobile-native. The entry must match the motion vocabulary.
- **Corner radius**: modals typically have the largest radius in the component set (softer than cards, softer than inputs) — calibrate to the character.
- **Padding**: the internal spacing of modals is often cramped. The modal's internal spacing should feel as generous as the product's spatial character.
- **Header zone**: how the title, subtitle, and close button are arranged — reveals typographic hierarchy decisions.
- **Action zone**: how the CTA buttons are arranged — stacked vs inline, and which side primary appears on.

**Assessment questions:**
```
□ Does the backdrop color use the product's chromatic dark or generic black?
□ Does the entry animation match the motion vocabulary?
□ Is the modal radius in the family with other component radii?
□ Can the modal be dismissed by clicking the backdrop? (UX + accessibility concern)
□ Is there a clear visual hierarchy between title, body, and actions?
□ Does the modal look like it was designed for this product or imported from a UI kit?
```

---

### §DCO6. Toast & Notification Design

Toasts and notifications are the product's voice. They speak directly to the user at moments of action completion, error, or information. Their typography, color, and motion carry character into every interaction.

**Toast character assessment:**

The toast must match the product's voice simultaneously in:
1. **Visual character** — color, radius, shadow, typography — all consistent with the component system
2. **Written voice** — the tone of the message must match the product's personality (see §DCVW1)
3. **Timing character** — how long it stays, how it leaves — should match the motion vocabulary

**Toast severity system:**

| Severity | Generic treatment | Character-expressive alternative |
|---|---|---|
| **Success** | Green fill, checkmark icon | Product accent + character-appropriate success marker; not necessarily green |
| **Error** | Red fill, X icon | Derived from character error palette (§DST3); severity-calibrated |
| **Warning** | Yellow/amber fill | Chromatic amber from the product palette; not Tailwind `yellow-500` |
| **Info** | Blue fill | Secondary palette color or muted accent; must not compete with primary CTA |
| **Neutral** | Gray fill | Surface elevation color — the product's elevated surface |

**Motion requirements for toasts:**
- Entry: from the correct edge. *Web*: top-right for desktop, top for mobile. *Android*: `Snackbar` enters from the **bottom** (anchored above `BottomNavigationView` if present via `snackbar.setAnchorView()`). `Toast` fades in-place at the bottom — no directional entry. *iOS*: system banners enter from top.
- Duration: 3–5 seconds for success; persistent for errors (require manual dismiss). *Android*: `Snackbar.LENGTH_SHORT` = ~1.5s, `LENGTH_LONG` = ~2.75s, `LENGTH_INDEFINITE` = persistent. For custom durations: `snackbar.duration = 4000`.
- Exit: faster than entry (150ms) — exits are less important than entrances
- Stacking: when multiple toasts queue, they should stack with consistent spacing, newest on top. *Android*: `Snackbar` does NOT stack by default — a new Snackbar dismisses the previous one. If stacking is needed, use a custom implementation or queue system.

**Android Toast vs Snackbar distinction** *(critical for Android audits)*:
- `Toast`: System-level, no action, no swipe-to-dismiss, positioned at bottom center, cannot be styled (pre-API 30). Use only for minor confirmations that need no user action. **After API 30, custom toast views are deprecated** — use `Snackbar` instead.
- `Snackbar`: App-level, supports one action button, swipe-to-dismiss, anchored to `CoordinatorLayout`. This is the correct component for almost all in-app feedback. Assess: is the app using `Toast` where `Snackbar` would be more appropriate? Is the `Snackbar` styled with the app's theme (`snackbarStyle` in `themes.xml`) or does it use the Material default appearance?
- **Snackbar character**: The default Material `Snackbar` uses `colorOnSurface` text on `colorSurfaceInverse` background. To add character: override `snackbarStyle`, `snackbarButtonStyle`, and `snackbarTextViewStyle` in `themes.xml`. The action button color should be the accent — not the default `colorPrimary`.

---

## XVI. COPY × VISUAL ALIGNMENT

> Design character without copy character is a mask without a face. Typography tells users *how* information is displayed — copy tells users *what the product sounds like thinking*. Both must speak the same language.

> **Claude execution note**: This section activates when copy is visible in the code being audited. §DCVW1 (voice-character alignment) is the core deliverable — always produce it. §DCVW2 (microcopy) produces the most actionable findings for developers. If no copy is visible, note the gap and move on.

---

### §DCVW1. Voice-Character Alignment

**Extract the written voice** from all visible copy in the app: navigation labels, button text, empty state messages, error messages, tooltips, onboarding text, success confirmations. Then assess whether the voice matches the §DP2 character brief.

**Voice dimensions — assess each:**

```
VOICE DIMENSION 1: Formality
  Formal ←————————————————————————→ Casual
  "Submit your application"          "Send it off"
  "Authentication failed"            "Wrong password"
  "Insufficient funds"               "Not enough money"

VOICE DIMENSION 2: Length
  Terse ←—————————————————————————→ Expansive
  "Delete"                           "Delete this item permanently"
  "Error"                            "Something went wrong — please try again"

VOICE DIMENSION 3: Personality presence
  Invisible ←—————————————————————→ Voiced
  "No items"                         "Nothing here yet — add your first item to get started"
  "Sign in"                          "Welcome back"

VOICE DIMENSION 4: User address
  Impersonal ←————————————————————→ Personal
  "The document was saved"           "Your document was saved"
  "User deleted"                     "Done — we've removed that person"

VOICE DIMENSION 5: Technical transparency
  Opaque ←————————————————————————→ Transparent
  "Error 403"                        "You don't have permission for this"
  "500: Internal server error"       "Something went wrong on our end — try again in a moment"
```

**For each dimension:** mark where the copy sits (observed) and where it should sit given the §DP2 character. A dimension gap of more than 2 positions is a voice misalignment finding.

---

### §DCVW2. Microcopy System Audit

**Microcopy** is the functional copy: button labels, navigation items, placeholder text, field labels, tooltip text, confirmation messages. It is the highest-frequency copy in the product and the most frequently generic.

**Audit each microcopy category:**

**Button labels:**
- Are they verbs? (Actions must be verb-led: "Save", "Create project", "Delete account")
- Are they specific? ("Submit" is generic; "Save changes" is clear; "Save and publish" is specific)
- Do they reflect the visual weight of the button? (A destructive action must sound as serious as it looks)
- Do they avoid the three most generic button labels: "OK", "Submit", "Click here"?

**Navigation labels:**
- Are they nouns (places) or verbs (actions)? Mixing them is confusing.
- Do they reflect the product's vocabulary or generic defaults? ("Home" vs the product-specific name for the dashboard)
- Are they scoped correctly? ("Settings" can mean anything; "Account settings", "Project settings", "System settings" are specific)

**Empty state copy:**
- Is there a message at all?
- Does it explain the value of filling this space?
- Does it have a specific call to action?
- Does it sound like this product? (Or does it sound like every other product?)

**Error message copy:**
- Is the error described in human terms (not technical codes)?
- Does it tell the user what happened?
- Does it tell the user what to do next?
- Does the severity of the language match the severity of the error?

**Placeholder text:**
- Is it actually helpful (showing an example value) or just generic ("Enter value...")?
- Does it disappear on focus, or does it persist awkwardly as the user types?

---

### §DCVW3. Voice-Visual Coherence Assessment

Produce a coherence score for each visible screen/component between the visual character and the copy voice:

```
VOICE-VISUAL COHERENCE
  Screen/component: [which area]
  Visual character from §DP2: [the character brief — brief summary]
  Copy voice observed:       [describe the voice of the actual copy]
  Coherence: ALIGNED / MINOR GAP / SIGNIFICANT GAP / CONTRADICTORY
  Primary conflict: [the specific copy that most contradicts the visual character]
  Recommendation: [rewritten copy + explanation of what changed and why]
```

**The coherence test:** A user who reads the app's copy without seeing the visuals should arrive at the same emotional character as a user who sees the visuals without reading the copy. If they arrive at different feelings — the product has a split personality.

---

## XVII. ILLUSTRATION & GRAPHIC LANGUAGE

> **Claude execution note**: Skip this section if the app has no illustrations, custom graphics, or spot illustrations. §DIL1 (current audit) is the core. §DIL2 (character specification) is only needed if the product needs new illustration direction. §DIL3 (spot graphics) is useful for products with abstract shape systems.

> **Illustration is where most products accidentally become generic.** The same five stock illustration libraries (Undraw, Storyset, DrawKit, Humaaans, Blush) appear across thousands of products with minimal customization. They carry no product identity — they carry the library's identity. A product with a carefully crafted design character that uses stock illustrations unchanged has inserted a foreign visual object into its identity. This section provides the framework to assess, direct, and specify illustration that carries character.

---

### §DIL1. Current Illustration Audit

Identify every illustration, spot graphic, and decorative graphic element in the product:

```
ILLUSTRATION INVENTORY
  Location: [screen/component where it appears]
  Type: [scene illustration / spot icon / abstract shape / character / data viz / decorative]
  Source: [library name if identifiable, or "custom"]
  Library recognizability: HIGH (clearly Undraw/Storyset/etc.) / MEDIUM / LOW (could be custom)
  Character alignment: ALIGNED / NEUTRAL / CONTRADICTORY
  Usage context: [empty state / onboarding / error / decorative / marketing]
```

**Library identification signals:**
- **Undraw**: flat, simple, bright, diverse characters with the signature "no-face" or simple oval face; blue dominant with accent color
- **Storyset**: more detailed, gradient fills, expressive poses; often 3D-influenced
- **Humaaans**: mix-and-match modular body parts; deliberately constructed look
- **DrawKit**: slightly more polished than Undraw; also flat but with more detail
- **Blush**: curated, often with cultural specificity; more editorial feel

If a library illustration is identified: flag it. Generic library illustrations at default color settings are genericness signal level similar to default Tailwind blue.

---

### §DIL2. Illustration Character Specification

Once the current state is audited, produce direction for how illustrations should be treated — whether customizing existing library assets or directing new custom work:

**The four levels of illustration customization:**

| Level | Description | Effort | Character impact |
|---|---|---|---|
| **L1: Color adaptation** | Recolor library illustrations to match the product palette | Low | Moderate — removes the library's default palette, creates palette coherence |
| **L2: Style selection** | Choose illustrations whose intrinsic style matches the character (choose Blush over Undraw for editorial; choose geometric abstract over character for clinical precision) | Low-medium | Significant — style selection is the primary driver |
| **L3: Directed customization** | Modify library illustrations — simplify, add motifs, change specific details to align with the product's iconographic vocabulary | Medium | High |
| **L4: Custom illustration** | Commission or produce original illustrations built to a character brief | High | Maximum — illustrations are product identity, not licensing |

**Illustration character brief format:**

For products needing custom illustration direction (L3 or L4):

```
━━━ ILLUSTRATION CHARACTER BRIEF ━━━━━━━━━━━━━━━━━━━━━━━━━━
Product character: [from §DP2 — brief summary]

Style direction:
  Figurative vs abstract: [does the illustration include people/characters? or geometric/abstract?]
  Rendering approach:     [flat / semi-flat / gradient / 3D-influenced / line / textured]
  Line weight:            [does illustration use lines? at what weight?]
  Color palette:          [must use: [list tokens] — no colors outside this palette]
  Mood:                   [what emotional quality the illustration should carry]

What the illustration must NEVER do:
  [3–5 specific prohibitions derived from the negative space of the character:
   e.g. "No smiling faces — clinical precision character doesn't perform positivity"
   e.g. "No rounded blob shapes — conflicts with the angular geometry vocabulary"
   e.g. "No white backgrounds — all illustrations must work on the product's dark surface"]

Motif integration:
  [Whether the product's geometric motifs or patterns (from §SR1 or §DP2) should
   appear within illustrations — and how]

Reference illustration style: [2–3 specific illustrations or illustrators whose
  character is closest to the target — not to copy, but to align direction]
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
```

---

### §DIL3. Spot Graphic & Abstract Shape System

Many products use abstract shapes, blobs, geometric forms, or decorative graphic elements — not illustrations, but graphic components. These are often more character-compatible than figurative illustration, and are underused.

**Abstract graphic character opportunities:**

| Shape vocabulary | Character alignment | How to use |
|---|---|---|
| **Angular faceted geometry** | Cold precision, cyberpunk, technical | Background accent, corner decorations, rule dividers |
| **Organic blobs** | Warm consumer, wellness, playful | Background atmosphere, image masks, section backgrounds |
| **Grid / dot patterns** | Technical, developer tools, data | Subtle texture overlay on surfaces |
| **Geometric fractals / tessellations** | Premium, intellectual, complex | Feature showcase backgrounds, hero atmosphere |
| **Calligraphic brushstrokes** | Editorial, cultural, artistic | Accent marks, section dividers, hero atmosphere |
| **Circuit traces / technical lines** | Technical precision, developer | Background texture, empty state decoration |
| **Concentric circles / waves** | Audio, sound, rhythm, signal | Visualizations, empty states |

**Assess:** Does the product use any abstract graphic elements? Are they from the character-appropriate vocabulary? Are they used at the correct atmospheric intensity (see §DSA5 focal vs ambient)?

---

## XVIII. DATA VISUALIZATION CHARACTER

> **Claude execution note**: Skip this section entirely if the app has no charts, graphs, or data visualizations. §DDV1 (chart color) is highest-value — chart colors from a default library are one of the most visible genericness signals.

> **Charts and data displays are the most-neglected design character carrier in data-heavy products.** Most products use the chart library's defaults — which carry the library's identity (Recharts blue, Chart.js gray grid, D3 defaults), not the product's. When a product has a carefully crafted dark surface, a specific accent color, and a distinctive typography system, and then shows a chart with gray grid lines, blue bars, and Helvetica axis labels — the character collapses entirely. Data visualization must be treated as a first-class design surface.

**This section activates whenever charts, graphs, metrics, or data displays appear in the product.**

---

### §DDV1. Chart Color System Audit

**The chart library default problem:** Every major chart library (Recharts, Chart.js, D3, Nivo, Victory) has default color schemes that are immediately recognizable. Using defaults without modification is as generic as using default Tailwind blue.

**Chart color character principles:**

1. **Series colors must come from the product palette** — not from the library's default series colors. Map every chart series to a specific token from §DC2.

2. **The dominant series color should be the product's primary accent** — the chart's primary data series should feel connected to the product's identity.

3. **Secondary series colors must be harmonically related to the accent** — not arbitrary rainbow colors from the library default.

4. **Grid lines and axes must use the product's muted color tokens** — not the library's gray.

5. **The chart background must be transparent or match the surface it sits on** — never a white or gray chart box dropped on a dark surface.

**Chart color palette derivation:**

```
From the product's §DC2 tokens, derive a chart color sequence:
  Series 1 (primary):     → product accent at full chroma
  Series 2:               → same hue, shifted 30° counterclockwise in OKLCH
  Series 3:               → same hue, shifted 60° counterclockwise
  Series 4:               → accent hue at 50% chroma (muted version)
  Series 5:               → complementary hue at 60% chroma
  Grid lines:             → `--text-muted` at 15–20% opacity
  Axis labels:            → `--text-secondary` — same size/weight as UI labels
  Axis lines:             → border color token at 40% opacity
```

---

### §DDV2. Chart Typography Alignment

Chart text — axis labels, tick values, legend text, tooltips, data labels — must be typographically consistent with the UI text system.

**Chart typography checklist:**
```
□ Axis labels: same font family as the product's UI text?
□ Tick values: using tabular numerals? (font-variant-numeric: tabular-nums — critical for alignment)
□ Legend text: same weight/size as the product's secondary text?
□ Tooltip: styled consistently with the product's tooltip/popover component?
□ Data labels (if used): not clashing with the product's text hierarchy
□ Chart title (if any): using the product's heading hierarchy?
```

**The font injection problem:** Some chart libraries render to canvas, bypassing web fonts entirely and defaulting to the system font stack. Assess whether chart text is rendering in the product font or a system fallback. Canvas-rendered text requires explicit `font` property injection:

```javascript
// Recharts example — custom tick rendering
const CustomAxisTick = ({ x, y, payload }) => (
  <text x={x} y={y} fontFamily="Inter" fontSize={12} fill={tokens.textSecondary}>
    {payload.value}
  </text>
);
```

---

### §DDV3. Chart Style × Product Character

The visual style of charts — beyond colors and typography — must match the product's design character.

**Chart character dimensions:**

| Visual decision | Character implication | Product-character mapping |
|---|---|---|
| **Grid lines: present vs absent** | Present = precision, data-forward; absent = clean, minimal | Clinical precision → light grid; minimal editorial → no grid |
| **Grid style: solid vs dashed** | Solid = authoritative; dashed = advisory, supportive | Expert tools = solid; consumer = dashed or absent |
| **Bar corner radius** | Rounded = approachable, consumer; sharp = technical, precise | Match component radius vocabulary |
| **Line chart: sharp vs curved** | Sharp (linear) = precise, data-exact; Curved (monotone) = flowing, trend-emphasis | Precision products = linear; consumer = monotone/bezier |
| **Area fill opacity** | Low (5–15%) = subtle, minimal; high (30–50%) = bold, expressive | Match surface opacity conventions |
| **Data point markers** | Present = precision, each point matters; absent = trend focus | Always present for high-stakes data; trend-only for consumer metrics |
| **Animation on load** | Present = polished, consumer; absent = expert, fast | Match motion character |

**Produce a chart style specification:**

```
CHART STYLE SPECIFICATION
  Character target: [from §DP2 — brief]
  Grid lines:       [present/absent; style; color token; opacity]
  Bar radius:       [px — matches component radius vocabulary]
  Line type:        [linear / monotone / step]
  Area fill:        [opacity value — or none]
  Animation:        [duration; easing — or disabled]
  Hover tooltip:    [component spec — matches §DCO5 modal/popover style]
  Color sequence:   [list of product tokens for series 1–5]
  Axis label style: [font; size; color token]
```

---

## XIX. DESIGN TOKEN ARCHITECTURE

> **Claude execution note**: §DTA1 (token layer audit) identifies which abstraction layers exist — this determines how much refactoring the character recommendations require. §DTA2 (character-carrying gaps) connects directly to the "find and replace" maintainability question. Skip this section for very small apps (<500 lines) where token architecture is premature.

> **A design system without token architecture is a collection of magic numbers.** §DC2 defines what tokens should exist (semantic roles). This section defines how to *structure* the token system itself — the difference between a list of CSS variables and an actual token architecture that carries character, scales with growth, and prevents accidental genericness as the product evolves.

---

### §DTA1. Token Layer Architecture

A professional token system has three layers. Most apps have only one — or none.

**Layer 1: Primitive Tokens (the raw material)**

Primitive tokens are the complete set of raw values. They have no semantic meaning — they are simply the full inventory of available values:

```css
/* Color primitives — named by value, not meaning */
--color-blue-60: oklch(60% 0.22 248);
--color-blue-50: oklch(50% 0.20 248);
--color-blue-40: oklch(40% 0.18 248);
--color-gray-95: oklch(95% 0.008 248);
--color-gray-20: oklch(20% 0.015 248);
/* etc. */

/* Space primitives */
--space-1: 4px;
--space-2: 8px;
--space-3: 12px;
--space-4: 16px;
/* etc. */
```

**Layer 2: Semantic Tokens (what values mean)**

Semantic tokens reference primitives and assign meaning. This is the layer that carries character:

```css
/* Semantic layer — meaning assigned to primitives */
--bg-base:        var(--color-gray-12);
--bg-surface:     var(--color-gray-16);
--bg-elevated:    var(--color-gray-20);
--text-primary:   var(--color-gray-92);
--text-secondary: var(--color-gray-60);
--accent:         var(--color-blue-60);
--accent-hover:   var(--color-blue-65);

/* Spacing semantic */
--spacing-component:  var(--space-4);   /* standard component padding */
--spacing-section:    var(--space-8);   /* between sections */
--spacing-inline:     var(--space-2);   /* between inline elements */
```

**Layer 3: Component Tokens (what values mean in context)**

Component tokens reference semantic tokens and make component-specific assignments:

```css
/* Component layer */
--btn-bg:           var(--accent);
--btn-bg-hover:     var(--accent-hover);
--btn-radius:       3px;                /* character decision — not from a primitive */
--card-bg:          var(--bg-surface);
--card-border:      oklch(100% 0 0 / 0.06);
--card-radius:      8px;
--input-bg:         var(--bg-elevated);
--input-border:     oklch(100% 0 0 / 0.12);
--input-radius:     var(--btn-radius);  /* inputs share button radius family */
```

**Assess:** Which layers exist in the codebase? An app with only raw hex values in component classes has no token system. An app with CSS variables that reference other CSS variables has reached Layer 2. An app with component-scoped variables that reference semantic variables has reached Layer 3.

**Android token layer equivalents:**

The same three-layer model applies to Android, using the resource system instead of CSS:

*Layer 1 — Primitives (`colors.xml`):*
```xml
<!-- Named by value, not meaning -->
<color name="blue_60">#3D6DB5</color>
<color name="blue_40">#2A4D80</color>
<color name="gray_95">#F2F2F2</color>
<color name="gray_20">#333333</color>
```

*Layer 2 — Semantic (`themes.xml` attributes):*
```xml
<!-- Meaning assigned via theme attributes -->
<style name="Theme.App" parent="Theme.Material3.DayNight">
    <item name="colorPrimary">@color/blue_60</item>
    <item name="colorOnPrimary">@color/gray_95</item>
    <item name="colorSurface">@color/gray_20</item>
    <item name="colorSurfaceVariant">@color/gray_25</item>
</style>
```
Layouts reference `?attr/colorPrimary` — never the primitive directly.

*Layer 3 — Component (`styles.xml`):*
```xml
<!-- Component-scoped tokens referencing semantic layer -->
<style name="Widget.App.Button" parent="Widget.Material3.Button">
    <item name="backgroundTint">?attr/colorPrimary</item>
    <item name="cornerSize">6dp</item>
</style>
<style name="Widget.App.Card" parent="Widget.Material3.CardView.Elevated">
    <item name="cardBackgroundColor">?attr/colorSurfaceVariant</item>
    <item name="cardCornerRadius">10dp</item>
</style>
```

*Jetpack Compose equivalent:*
```kotlin
// Layer 1: primitives in a Palette object
// Layer 2: MaterialTheme.colorScheme (semantic)
// Layer 3: Component defaults via MaterialTheme overrides or custom CompositionLocal
```

**Assess (Android-specific):** Does `colors.xml` contain only primitives, or is it a mix of primitives and semantic names? Are layouts referencing `?attr/` theme attributes or hardcoded `@color/` resources? Does `styles.xml` define component-level overrides, or are components using bare Material defaults?

**Gap findings:**
```
Layer 1 (primitives): PRESENT / PARTIAL / ABSENT
Layer 2 (semantic):   PRESENT / PARTIAL / ABSENT
Layer 3 (component):  PRESENT / PARTIAL / ABSENT

Highest-impact gap: [which missing layer would produce the most value]
Migration path: [how to add the missing layer without a full rewrite — incremental approach]
```

*Android migration path note:* The lowest-effort, highest-impact migration for Android apps is adding Layer 2 — moving from `@color/blue_500` references in layouts to `?attr/colorPrimary` references. This single change enables dark mode, Dynamic Color, and theme overlays for free. Grep for `@color/` in layout XML files to find all direct primitive references that should be `?attr/`.

---

### §DTA2. Character-Carrying Token Gaps

Tokens only carry character if the right decisions are tokenized. Assess which character-critical values are hardcoded (magic numbers) vs tokenized (intentional):

```
CHARACTER TOKEN AUDIT
  □ Background surface lightness step: tokenized or hardcoded?
  □ Primary accent OKLCH value: tokenized or hardcoded?
  □ Component border-radius: tokenized or hardcoded per component?
  □ Typography scale: using CSS custom properties or Tailwind class strings?
  □ Transition durations: tokenized or hardcoded per animation?
  □ Shadow definitions: tokenized or copy-pasted?
  □ Focus ring style: tokenized or repeated?
  □ Spacing base unit: tokenized or assumed to be 4px/8px?
```

For each hardcoded character-critical value: flag it as a token gap, provide the token definition, and show the migration from hardcoded to tokenized.

**The "find and replace" test:** If the product needed to change its accent color globally — how many files would need to change? 1 (token definition) = good architecture. 10+ = no architecture. Flag the gap and provide the refactoring path.

---

## §FINDING FORMAT

Every finding from this skill uses this template:

```
[SEVERITY] — {Title}
Dimension: §D{code} — {Name}
Finding: {Specific description — function name, CSS class, exact color value}
Why it matters: {Connected to the app's axis profile and aesthetic goal}
Recommendation: {Specific change — include exact values where applicable}
Effort: LOW / MEDIUM / HIGH
```

**Severity scale:** [CRITICAL] [HIGH] [MEDIUM] [LOW] [POLISH] — matches app-audit severity scale.

When used as a companion to app-audit, produce a **Design Aesthetic Supplement** appended to the relevant audit part (P6 — Visual Design · Polish · Design System):

```
━━━ DESIGN AESTHETIC SUPPLEMENT ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
[findings in app-audit §V format (see §FINDING FORMAT above), prefixed with §D to distinguish from §E findings]
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
```

---

## §EXEC. EXECUTION ORDER

> **Claude execution note**: Choose the path that matches the user's request. Do NOT attempt all 21 steps in one response — work through one path, presenting findings incrementally. For the general audit path, pause after step 3 (Character Brief) to confirm direction with the user. The mid-audit companion minimum (last line) is the fastest high-value path.
>
> **Claude Code**: At the start of any path, use `TodoWrite` to create a progress tracker listing each step. Mark each step `completed` as you finish it. Use `AskUserQuestion` to confirm the Character Brief (step 3) before deepening. Example:
> ```
> TodoWrite([
>   { content: "§DP0: Extract character from code", status: "in_progress", activeForm: "Extracting design character" },
>   { content: "§DP1-DP2: Analyze dimensions + Character Brief", status: "pending", activeForm: "Analyzing character dimensions" },
>   { content: "§DBI3: Anti-genericness audit (12 signals)", status: "pending", activeForm: "Auditing genericness signals" },
>   ...
> ])
> ```

### If a named source is referenced (game, show, brand, IP) — SOURCE MATERIAL PATH:

```
FIRST: §SR0 — 5-pass research (execute immediately, before anything else)
THEN:  §SR1 — Build Source Style Brief: all 5 layers (Surface → Structural →
               Cultural → Philosophical → Identity Thesis)
THEN:  §SR2 — Establish fidelity level with user (propose L3 if not specified)
THEN:  §DP0 + §DP1 → §DP2 — Character Brief (sourced from §SR1 Layer 4)
THEN:  §SR3 — Translation plan (minimum authentic set first, secondary elements after)
THEN:  §DC1–§DC5 + §DT1–§DT4 + §DI1–§DI4 — Color, type, icon using SR values
THEN:  §DCO1–§DCO6 — Component character: does every component carry the identity?
THEN:  §DSA1–§DSA5 — Surface & atmosphere aligned to source material
THEN:  §DP3 — Character deepening (tokens drawn from §SR1 minimum authentic set)
THEN:  §DST1–§DST4 — State design: every state carries the character
THEN:  §DCVW1–§DCVW3 — Copy voice: does writing match the visual identity?
THEN:  §DIL1–§DIL3 — Illustration: does it carry or break the identity?
THEN:  §DRC1–§DRC3 — Responsive: character survives all viewports
THEN:  §SR4–§SR6 — Authenticity audit + research log
```

### For a personality-focused request ("deepen the personality", "make it feel more X"):

```
FIRST: §DP0 — Character extraction (read what the design already says)
THEN:  §DP1 — Assess six personality dimensions
THEN:  §DP2 — Produce Character Brief
THEN:  §DS1 — Confirm style classification against personality
THEN:  §DBI1 — Confirm archetype alignment
THEN:  §DP3 — Full deepening protocol (all seven techniques)
THEN:  §DM5 — Motion signature (concentrated personality in motion)
THEN:  §DBI2 — Design signature specification
THEN:  §DCO1–§DCO6 — Verify every component is on-character
THEN:  §DST1–§DST4 — Verify personality holds across all states
THEN:  §DCVW1–§DCVW3 — Verify copy voice is aligned
THEN:  §DC3–§DC5 + §DT1–§DT4 — Color/type/gradient aligned to character
```

### For a general aesthetic audit (no source reference, no specific personality request):

1. **§DS1–§DS2** — Classify style; establishes the lens for everything
2. **§0 / §DP0 / §DP1** — Axis profile + Character Extraction + Character Dimensions
3. **§DP2** — Character Brief; filter for all subsequent findings
4. **§DBI1** (archetype) + **§DBI3** (genericness, all 12 signals) → high-value wins
5. **§DC1–§DC5** — Color architecture + narrative
6. **§DT1–§DT4** — Typography craft + voice + expressiveness
7. **§DCO1–§DCO6** — Component system: buttons, inputs, cards, nav, modals, toasts
8. **§DH1–§DH4** — Hierarchy + contrast as composition
9. **§DSA1–§DSA5** — Surface, atmosphere, light physics, focal vs ambient
10. **§DM1–§DM5** — Motion vocabulary + micro-interactions + motion signature
11. **§DI1–§DI4** — Iconography system + expressiveness + custom direction
12. **§DST1–§DST4** — State design: empty, loading, error, success
13. **§DCVW1–§DCVW3** — Copy voice alignment
14. **§DIL1–§DIL3** — Illustration audit + character brief
15. **§DDV1–§DDV3** — Data visualization character (if charts present)
16. **§DTA1–§DTA2** — Token architecture (if design system is present/needed)
17. **§DRC1–§DRC3** — Responsive character at all viewports
18. **§DDT1–§DDT2** — Trend calibration + trend strategy
19. **§DP3** — Character deepening; concentrating what already works
20. **§DBI2** — Design signature; forward-looking investment
21. **§DCP1–§DCP3** — Competitive positioning; strategic layer

When invoked mid-audit to supplement app-audit §E/P6: run §SR0 first if a source is named, then §DS1 → §DP0 → §DP1–§DP2 → §DC1–§DC4 → §DCO1 (buttons) → §DBI1–§DBI3 as the minimum high-value path.
