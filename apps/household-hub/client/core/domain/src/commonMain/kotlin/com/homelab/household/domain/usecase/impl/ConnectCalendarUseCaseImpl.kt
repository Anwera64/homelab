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
        val trimmedAccount = account.trim()
        // Google shows its app password in four groups with spaces; it is the same password without them.
        val cleanPassword =
            if (provider ==
                CalendarProvider.GOOGLE
            ) {
                password.filterNot { it.isWhitespace() }
            } else {
                password.trim()
            }
        val address = provider.presetServer(trimmedAccount) ?: server.trim()

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
