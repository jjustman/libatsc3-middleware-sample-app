package com.nextgenbroadcast.mobile.middleware

import com.fasterxml.jackson.databind.ObjectMapper
import com.github.nmuzhichin.jsonrpc.context.ConsumerBuilder
import com.github.nmuzhichin.jsonrpc.normalizer.JacksonNormalization
import org.junit.Assert
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger

class UseConstraintTest {
    @Test
    fun test() {
        val consumer = ConsumerBuilder()
                .valueNormalizer(JacksonNormalization(ObjectMapper()))
                .build()
        Assert.assertNotNull(consumer)
        Assert.assertNotNull(consumer.context)
    }

    companion object {
        private val counter: AtomicInteger? = AtomicInteger()
    }
}