package com.example.hooks

import spock.lang.Specification
import spock.lang.Subject
import spock.lang.Unroll

class BranchCreationPolicySpec extends Specification {

    static final String ZERO  = '0' * 40
    static final String MAIN  = 'a' * 40
    static final String MSTR  = 'b' * 40
    static final String REL20 = 'c' * 40
    static final String REL10 = 'd' * 40
    static final String TAG23 = 'e' * 40
    static final String TAG10 = 'f' * 40
    static final String OTHER = '9' * 40

    // Default resolver — has main, two release branches, and two tags
    def resolver = Stub(RefSourceResolver) {
        resolveRef('refs/heads/main')   >> MAIN
        resolveRef('refs/heads/master') >> null
        collectRefs('branch') >> [
            'refs/heads/release/2.0': REL20,
            'refs/heads/release/1.0': REL10,
        ]
        collectRefs('tag') >> [
            'refs/tags/v2.3.0': TAG23,
            'refs/tags/v1.0.0': TAG10,
        ]
    }

    @Subject
    BranchCreationPolicy policy = new BranchCreationPolicy(resolver)

    // ── Happy path ─────────────────────────────────────────────────────────────

    def "allows branch created from tip of main"() {
        expect:
        policy.check('refs/heads/feature/my-feature', MAIN) == null
    }

    def "allows branch created from tip of master"() {
        given:
        def r = Stub(RefSourceResolver) {
            resolveRef('refs/heads/main')   >> null
            resolveRef('refs/heads/master') >> MSTR
            collectRefs(_) >> [:]
        }
        expect:
        new BranchCreationPolicy(r).check('refs/heads/feature/x', MSTR) == null
    }

    def "allows branch created from tip of latest release branch"() {
        expect:
        policy.check('refs/heads/bugfix/something', REL20) == null
    }

    def "allows branch created from tip of older release branch"() {
        expect:
        policy.check('refs/heads/bugfix/something', REL10) == null
    }

    def "allows branch created from latest tag"() {
        expect:
        policy.check('refs/heads/hotfix/patch', TAG23) == null
    }

    def "allows branch created from older tag"() {
        expect:
        policy.check('refs/heads/hotfix/patch', TAG10) == null
    }

    def "allows creation of a release branch itself from main"() {
        expect:
        policy.check('refs/heads/release/3.0', MAIN) == null
    }

    def "allows creation of a release branch itself regardless of fromHash"() {
        // Release branches are exempt — they ARE allowed sources, not consumers
        expect:
        policy.check('refs/heads/release/3.0', OTHER) == null
    }

    // ── Blocking cases ─────────────────────────────────────────────────────────

    def "blocks branch created from an arbitrary commit"() {
        expect:
        policy.check('refs/heads/feature/rogue', OTHER) != null
    }

    def "violation contains the correct branch name"() {
        when:
        def v = policy.check('refs/heads/feature/rogue', OTHER)
        then:
        v.branchName == 'feature/rogue'
    }

    def "violation contains the offending fromHash"() {
        when:
        def v = policy.check('refs/heads/feature/rogue', OTHER)
        then:
        v.fromHash == OTHER
    }

    def "violation includes main SHA in allowed sources"() {
        when:
        def v = policy.check('refs/heads/feature/rogue', OTHER)
        then:
        v.mainSha == MAIN
    }

    def "violation includes release branches in allowed sources"() {
        when:
        def v = policy.check('refs/heads/feature/rogue', OTHER)
        then:
        v.releaseBranches.containsKey('refs/heads/release/2.0')
        v.releaseBranches.containsKey('refs/heads/release/1.0')
    }

    def "violation includes tags in allowed sources"() {
        when:
        def v = policy.check('refs/heads/feature/rogue', OTHER)
        then:
        v.tags.containsKey('refs/tags/v2.3.0')
        v.tags.containsKey('refs/tags/v1.0.0')
    }

    // ── Edge cases ─────────────────────────────────────────────────────────────

    def "ignores non-branch refs (e.g. tag pushes)"() {
        expect:
        policy.check('refs/tags/v3.0', OTHER) == null
    }

    def "ignores ref changes with zero hash (not a creation)"() {
        expect:
        policy.check('refs/heads/feature/x', ZERO) == null
    }

    def "fails open (returns violation, no exception) when RefService returns nothing"() {
        given:
        def r = Stub(RefSourceResolver) {
            resolveRef(_)  >> null
            collectRefs(_) >> [:]
        }
        when:
        def v = new BranchCreationPolicy(r).check('refs/heads/feature/x', OTHER)
        then:
        noExceptionThrown()
        v != null
    }

    def "does not throw when resolver returns null for main and master"() {
        given:
        def r = Stub(RefSourceResolver) {
            resolveRef(_)  >> null
            collectRefs(_) >> [:]
        }
        when:
        def v = new BranchCreationPolicy(r).check('refs/heads/feature/x', MAIN)
        then:
        noExceptionThrown()
        v != null
    }

    // ── Sorting ────────────────────────────────────────────────────────────────

    @Unroll
    def "release branches are checked newest-first: #description"() {
        given:
        def r = Stub(RefSourceResolver) {
            resolveRef(_)         >> null
            collectRefs('branch') >> branches.collectEntries { k, v -> ["refs/heads/${k}".toString(), v] }
            collectRefs('tag')    >> [:]
        }
        // If newest-first ordering is correct, fromHash matching the newest SHA should pass
        expect:
        new BranchCreationPolicy(r).check('refs/heads/feature/x', newestSha) == null

        where:
        description                        | branches                                                                         | newestSha
        'simple semver ordering'           | ['release/2.0': REL20, 'release/1.0': REL10]                                    | REL20
        'double-digit beats single-digit'  | ['release/1.0': REL10, 'release/10.0': OTHER, 'release/2.0': REL20]             | OTHER
        'patch version ordering'           | ['release/1.0.0': REL10, 'release/1.0.1': REL20]                                | REL20
    }

    @Unroll
    def "tags are checked newest-first: #description"() {
        given:
        def r = Stub(RefSourceResolver) {
            resolveRef(_)         >> null
            collectRefs('branch') >> [:]
            collectRefs('tag')    >> tags.collectEntries { k, v -> ["refs/tags/${k}".toString(), v] }
        }
        expect:
        new BranchCreationPolicy(r).check('refs/heads/feature/x', newestSha) == null

        where:
        description               | tags                                          | newestSha
        'major version ordering'  | ['v1.0.0': TAG10, 'v2.3.0': TAG23]           | TAG23
        'patch version ordering'  | ['v1.0.0': TAG10, 'v1.0.1': TAG23]           | TAG23
        'double-digit minor'      | ['v1.9.0': TAG10, 'v1.10.0': TAG23]          | TAG23
    }

    // ── Both main and master present ───────────────────────────────────────────

    def "allows from main when both main and master exist"() {
        given:
        def r = Stub(RefSourceResolver) {
            resolveRef('refs/heads/main')   >> MAIN
            resolveRef('refs/heads/master') >> MSTR
            collectRefs(_) >> [:]
        }
        expect:
        new BranchCreationPolicy(r).check('refs/heads/feature/x', MAIN) == null
    }

    def "allows from master when both main and master exist"() {
        given:
        def r = Stub(RefSourceResolver) {
            resolveRef('refs/heads/main')   >> MAIN
            resolveRef('refs/heads/master') >> MSTR
            collectRefs(_) >> [:]
        }
        expect:
        new BranchCreationPolicy(r).check('refs/heads/feature/x', MSTR) == null
    }
}
