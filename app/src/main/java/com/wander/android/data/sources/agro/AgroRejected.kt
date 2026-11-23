package com.wander.android.data.sources.agro

import java.io.IOException

/**
 * The server answered, and refused the request: a GraphQL error rather than a network failure.
 *
 * Kept apart from a dropped connection because the two call for different words. A refusal of a
 * query's own shape — an argument or field the server has never heard of — is what an Agro older
 * than the app says, and the useful answer then is "update Agro", not "try again".
 * [serverMessage] is the server's own text, which never carries the URL or the key.
 */
class AgroRejected(val serverMessage: String) :
    IOException("Agro rejected the request: $serverMessage")
