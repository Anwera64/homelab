package com.homelab.household.data.repository

import com.homelab.household.data.datasource.remote.`interface`.CalendarRemoteDataSource
import com.homelab.household.data.dto.CalendarCredentialCreateDto
import com.homelab.household.data.mapper.CalendarDataMapper
import com.homelab.household.domain.model.CalendarConnection
import com.homelab.household.domain.model.CalendarProvider
import com.homelab.household.domain.repository.CalendarRepository

/** Orchestration and mapping for the member's calendar connection. [remote] speaks DTOs and wire names. */
class CalendarRepositoryImpl(
    private val remote: CalendarRemoteDataSource,
) : CalendarRepository {
    override suspend fun getCalendar(): CalendarConnection? = remote.getMyCalendar()?.let(CalendarDataMapper::toDomain)

    override suspend fun connectCalendar(
        provider: CalendarProvider,
        server: String,
        account: String,
        password: String,
        calendarName: String?,
    ): CalendarConnection =
        CalendarDataMapper.toDomain(
            remote.configureCalendar(
                CalendarCredentialCreateDto(
                    provider = CalendarDataMapper.toWire(provider),
                    url = server,
                    username = account,
                    password = password,
                    calendarName = calendarName,
                ),
            ),
        )

    override suspend fun removeCalendar() = remote.deleteCalendar()

    override suspend fun startGoogleSignIn(): String = remote.startGoogleSignIn()
}
