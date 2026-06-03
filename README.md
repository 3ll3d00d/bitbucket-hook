# Bitbucket Server – Branch Creation Policy Hook

A ScriptRunner pre-receive hook for Bitbucket Server 8.x that blocks branch
creation unless the new branch is forked from the tip of an allowed source ref.

## Allowed sources (checked in order)

1. `main` or `master` — checked first (single ref lookup, cheapest)
2. `release/*` branches — checked newest version first
3. Any tag — checked newest version first (annotated tags dereferenced automatically via `RefService`)

## Project structure

```
src/
  main/groovy/
    com/example/hooks/
      RefSourceResolver.groovy          Interface abstracting RefService
      BitbucketRefSourceResolver.groovy Real implementation (uses Bitbucket RefService)
      BranchCreationPolicy.groovy       Pure policy logic — no Bitbucket deps
      PolicyViolation.groovy            Value object returned on a blocked creation
    hook.groovy                         Thin ScriptRunner hook — wires everything together
  test/groovy/
    com/example/hooks/
      BranchCreationPolicySpec.groovy   Spock unit tests
build.gradle
```

## Running the tests

```bash
./gradlew test
```

No running Bitbucket instance needed — `RefSourceResolver` is stubbed via Spock.

## Installing the hook

1. Build the classes and add them to ScriptRunner's classpath, or inline the
   source files into your ScriptRunner script library.
2. In Bitbucket, go to **Repository Settings → Hooks**.
3. Enable **ScriptRunner – Pre-Receive Hook**.
4. Paste the contents of `src/main/groovy/hook.groovy` into the script editor.

## Customising allowed sources

Edit the patterns in `BranchCreationPolicy.groovy`:

```groovy
// Release branch naming — adjust to your convention
private static final def RELEASE_BRANCH_PATTERN = ~/^refs\/heads\/release\/.+$/

// Tag pattern — restrict to semver only if preferred
private static final def TAG_PATTERN = ~/^refs\/tags\/v\d+\.\d+\.\d+.*$/
```

## Fail-open vs fail-closed

If `RefService` is unavailable (e.g. during startup), the hook currently
**fails open** (allows the push) and logs a warning. To fail closed instead,
change `return true` to `return false` in the guard clauses at the top of
`hook.groovy`.
