package com.example.hooks

interface RefSourceResolver {
    String resolveRef(String refId)                 // null if not found
    Map<String, String> collectRefs(String refType) // refId -> commitSha
}
