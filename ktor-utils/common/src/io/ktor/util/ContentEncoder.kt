/*
 * Copyright 2014-2023 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.util

/**
 * A request/response content encoder.
 */
public interface ContentEncoder : Encoder {
    /**
     * Encoder identifier to use in http headers.
     */
    public val name: String

    /**
     * Provides an estimation for the compressed length based on the originalLength or return null if it's impossible.
     */
    public fun predictCompressedLength(contentLength: Long): Long? = null
}

/**
 * Implementation of [ContentEncoder] using gzip algorithm
 */
public expect object GZipEncoder : ContentEncoder

/**
 * Implementation of [ContentEncoder] using deflate algorithm
 */
public expect object DeflateEncoder : ContentEncoder

/**
 * Implementation of [ContentEncoder] using identity algorithm
 */
public object IdentityEncoder : ContentEncoder, Encoder by Identity {
    override val name: String = "identity"

    override fun predictCompressedLength(contentLength: Long): Long = contentLength
}
