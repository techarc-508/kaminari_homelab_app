package com.rplab.kaminarinfty.model

import java.io.IOException

/**
 * Transport failure that keeps its real category.
 *
 * Before this type, every probe failure reached the ViewModel as a plain
 * `IOException`, so DNS, TLS and timeout problems were all shown as
 * "authentication failed" (phase 2 gate finding G-05).
 */
class NftyException(
    val category: NftyErrorCategory,
    override val message: String,
) : IOException(message)
