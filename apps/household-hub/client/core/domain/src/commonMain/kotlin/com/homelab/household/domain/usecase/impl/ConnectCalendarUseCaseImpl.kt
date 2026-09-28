package com.homelab.household.domain.usecase.impl

import com.homelab.household.domain.exception.ValidationException
import com.homelab.household.domain.model.CalendarConnection
import com.homelab.household.domain.model.CalendarProvider
import com.homelab.household.domain.repository.CalendarRepository
import com.homelab.household.domain.usecase.ConnectCalendarUseCase

class ConnectCalendarUseCaseImpl(
    private val calendarRepository: CalendarRepository,
) : ConnectCalendarUseCase {
    override suspend operator fun invoke(
        provider: CalendarProvider,
        account: String,
        password: String,
        server: String,
        calendarName: String,
    ): CalendarConnection {
        if (provider.signsIn) throw ValidationException("Google calendars are connected by signing in with Google")

        val trimmedAccount = account.trim()
        val cleanPassword = password.trim()
        val address = provider.presetServer() ?: server.trim()

        if (trimmedAccount.isEmpty()) throw ValidationException("Account cannot be blank")
        if (cleanPassword.isEmpty()) throw ValidationException("Password cannot be blank")
        if (address.isEmpty()) throw ValidationException("Server cannot be blank")

        return calendarRepository.connectCalendar(
            provider = provider,
            server = address,
            account = trimmedAccount,
            password = cleanPassword,
            calendarName = calendarName.trim().ifEmpty { null },
        )
    }
}
