package com.example.hooks

import com.atlassian.bitbucket.ref.*
import com.atlassian.bitbucket.repository.Repository

class BitbucketRefSourceResolver implements RefSourceResolver {

    private final RefService refService
    private final Repository repository

    BitbucketRefSourceResolver(RefService refService, Repository repository) {
        this.refService = refService
        this.repository = repository
    }

    @Override
    String resolveRef(String refId) {
        try {
            return refService.resolveRef(
                new ResolveRefRequest.Builder(repository)
                    .refId(refId)
                    .build()
            )?.latestCommit
        } catch (Exception e) {
            return null
        }
    }

    @Override
    Map<String, String> collectRefs(String refType) {
        Map<String, String> result = [:]
        def type = refType == 'tag' ? RefType.TAG : RefType.BRANCH
        refService.streamRefs(
            new StreamRefsRequest.Builder(repository)
                .refType(type)
                .build()
        ) { Ref ref -> result[ref.id] = ref.latestCommit }
        return result
    }
}
