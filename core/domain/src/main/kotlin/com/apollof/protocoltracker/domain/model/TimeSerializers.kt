package com.apollof.protocoltracker.domain.model

import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime

private abstract class IsoSerializer<T>(name: String, val parse: (String) -> T) : KSerializer<T> {
    override val descriptor = PrimitiveSerialDescriptor(name, PrimitiveKind.STRING)
    override fun serialize(encoder: Encoder, value: T) = encoder.encodeString(value.toString())
    override fun deserialize(decoder: Decoder): T = parse(decoder.decodeString())
}

object LocalDateSerializer : KSerializer<LocalDate> by object : IsoSerializer<LocalDate>("LocalDate", LocalDate::parse) {}
object LocalTimeSerializer : KSerializer<LocalTime> by object : IsoSerializer<LocalTime>("LocalTime", LocalTime::parse) {}
object InstantSerializer : KSerializer<Instant> by object : IsoSerializer<Instant>("Instant", Instant::parse) {}
