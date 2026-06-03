package com.example.hooks

class BranchCreationPolicy {

    static final String ZERO_HASH = '0' * 40

    private static final def RELEASE_BRANCH_PATTERN = ~/^refs\/heads\/release\/.+$/
    private static final def TAG_PATTERN             = ~/^refs\/tags\/.+$/

    private final RefSourceResolver resolver

    BranchCreationPolicy(RefSourceResolver resolver) {
        this.resolver = resolver
    }

    /**
     * Returns null if the branch creation is allowed,
     * or a PolicyViolation describing why it was blocked.
     */
    PolicyViolation check(String refId, String fromHash) {

        // Not a branch creation — nothing to check
        if (!refId.startsWith('refs/heads/'))           return null
        if (fromHash == ZERO_HASH)                      return null

        // Release branches are allowed sources themselves — don't block them
        if (refId ==~ RELEASE_BRANCH_PATTERN)           return null

        // Stage 1: main / master — cheap, no iteration needed
        def mainSha   = resolver.resolveRef('refs/heads/main')
        def masterSha = resolver.resolveRef('refs/heads/master')
        if (fromHash == mainSha || fromHash == masterSha) return null

        // Stage 2: release branches, newest first
        def releaseBranches = sortNewestFirst(
            resolver.collectRefs('branch').findAll { ref, _ -> ref ==~ RELEASE_BRANCH_PATTERN }
        )
        if (releaseBranches.any { ref, sha -> fromHash == sha }) return null

        // Stage 3: tags, newest first
        // RefService resolves annotated tags to their underlying commit SHA automatically
        def tags = sortNewestFirst(
            resolver.collectRefs('tag').findAll { ref, _ -> ref ==~ TAG_PATTERN }
        )
        if (tags.any { ref, sha -> fromHash == sha }) return null

        // Nothing matched — return a violation
        return new PolicyViolation(
            branchName      : refId - 'refs/heads/',
            fromHash        : fromHash,
            mainSha         : mainSha,
            masterSha       : masterSha,
            releaseBranches : releaseBranches,
            tags            : tags
        )
    }

    private static Map<String, String> sortNewestFirst(Map<String, String> refs) {
        refs.sort { a, b ->
            def ver = { String refId ->
                def m = refId =~ /(\d+)\.(\d+)(?:\.(\d+))?(?:\.(\d+))?/
                m ? [m[0][1], m[0][2], m[0][3] ?: '0', m[0][4] ?: '0'].collect { it as int }
                  : [-1, -1, -1, -1]
            }
            def vA = ver(a.key); def vB = ver(b.key)
            for (int i = 0; i < 4; i++) { if (vB[i] != vA[i]) return vB[i] <=> vA[i] }
            return 0
        }
    }
}
