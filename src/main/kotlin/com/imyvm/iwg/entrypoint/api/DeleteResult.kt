package com.imyvm.iwg.inter.api

/** Observable outcomes of deleting an exact canonical Region. */
enum class RegionDeleteResult {
    SUCCESS,
    PERSISTENCE_FAILED
}

/** Observable outcomes of deleting an exact canonical Scope. */
enum class ScopeDeleteResult {
    SUCCESS,
    LAST_SCOPE,
    PERSISTENCE_FAILED
}
