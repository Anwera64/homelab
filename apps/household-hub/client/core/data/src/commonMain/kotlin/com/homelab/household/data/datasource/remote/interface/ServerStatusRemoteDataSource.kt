package com.homelab.household.data.datasource.remote.`interface`

import com.homelab.household.domain.model.ServerStatus
import kotlinx.coroutines.flow.Flow

/**
 * Whether the hub is there, and a running answer to that question.
 *
 * This is the one data source that hands back a domain model rather than a DTO, and it is exempt
 * from that rule by name in `DataLayerBoundaryTest`. "Is the hub reachable?" has no failure case:
 * an unreachable hub *is* the answer, so [checkHealth] folds a refusal or a dead socket into
 * [ServerStatus.Offline] rather than throwing. A DTO here would be [ServerStatus] under another
 * name, mapped one-to-one, for no reader's benefit.
 */
interface ServerStatusRemoteDataSource {

    suspend fun checkHealth(): ServerStatus

    /**
     * Checks every [intervalSeconds] while anyone is collecting, backing off to at most a minute
     * while the hub stays down and returning to the steady interval once it answers again.
     */
    fun observeStatus(intervalSeconds: Long = 10): Flow<ServerStatus>
}
