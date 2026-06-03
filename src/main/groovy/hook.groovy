import com.atlassian.bitbucket.hook.repository.*
import com.atlassian.bitbucket.repository.*
import com.atlassian.bitbucket.ref.*
import com.atlassian.sal.api.component.ComponentLocator
import com.example.hooks.*

// ── Resolve RefService ───────────────────────────────────────────────────────
RefService refService
try {
    refService = ComponentLocator.getComponent(RefService)
} catch (Exception e) {
    hookResponse.out().println("WARN: RefService unavailable — branch source check skipped: ${e.message}")
    return true
}

if (refService == null) {
    hookResponse.out().println("WARN: RefService resolved to null — branch source check skipped")
    return true
}

def resolver = new BitbucketRefSourceResolver(refService, repository)
def policy   = new BranchCreationPolicy(resolver)

// ── Process each incoming ref change ────────────────────────────────────────
for (RefChange refChange : refChanges) {
    def violation = policy.check(refChange.ref.id, refChange.fromHash)
    if (violation == null) continue

    def sourceLines = []
    if (violation.mainSha)   sourceLines << "  [branch] main                         ${violation.mainSha.take(8)}"
    if (violation.masterSha) sourceLines << "  [branch] master                       ${violation.masterSha.take(8)}"
    violation.releaseBranches.each { ref, sha ->
        sourceLines << "  [branch] ${(ref - 'refs/heads/').padRight(30)} ${sha.take(8)}"
    }
    violation.tags.each { ref, sha ->
        sourceLines << "  [tag]    ${(ref - 'refs/tags/').padRight(30)} ${sha.take(8)}"
    }

    hookResponse.out().println("""
    ╔══════════════════════════════════════════════════════════════╗
    ║              BRANCH CREATION BLOCKED                        ║
    ╠══════════════════════════════════════════════════════════════╣
    ║  Branch  : ${violation.branchName.take(50).padRight(50)}  ║
    ║  From    : ${violation.fromHash.take(8).padRight(50)}  ║
    ╠══════════════════════════════════════════════════════════════╣
    ║  Must be created from the tip of an allowed source:         ║
    ║                                                              ║
    ${sourceLines.join('\n')}
    ║                                                              ║
    ║  Fetch the latest refs and retry.                           ║
    ╚══════════════════════════════════════════════════════════════╝
    """.stripIndent())

    return false
}

return true
