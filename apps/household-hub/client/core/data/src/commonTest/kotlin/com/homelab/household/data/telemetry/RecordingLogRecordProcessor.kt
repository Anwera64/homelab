package com.homelab.household.data.telemetry

import io.opentelemetry.kotlin.ExperimentalApi
import io.opentelemetry.kotlin.context.Context
import io.opentelemetry.kotlin.export.OperationResultCode
import io.opentelemetry.kotlin.logging.data.LogRecordData
import io.opentelemetry.kotlin.logging.export.LogRecordProcessor
import io.opentelemetry.kotlin.logging.model.ReadWriteLogRecord

/**
 * Stands where the batching processor and the OTLP exporter stand in the app, and keeps every
 * record the SDK hands over. It is called on the emitting thread, so a test reads [records] the
 * line after it logs, with no clock and no network to wait for.
 */
@OptIn(ExperimentalApi::class)
class RecordingLogRecordProcessor : LogRecordProcessor {
    val records = mutableListOf<LogRecordData>()

    override fun onEmit(
        log: ReadWriteLogRecord,
        context: Context,
    ) {
        records += log.toLogRecordData()
    }

    override suspend fun forceFlush(): OperationResultCode = OperationResultCode.Success

    override suspend fun shutdown(): OperationResultCode = OperationResultCode.Success
}

/** Everything a record would put on the wire, as one string to search for what must not be there. */
@OptIn(ExperimentalApi::class)
fun LogRecordData.everythingExported(): String =
    listOf(body, eventName, severityText, attributes, resource.attributes, instrumentationScopeInfo).joinToString(" | ")
