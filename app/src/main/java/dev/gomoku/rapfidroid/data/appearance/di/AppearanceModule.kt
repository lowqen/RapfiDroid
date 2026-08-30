package dev.gomoku.rapfidroid.data.appearance.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dev.gomoku.rapfidroid.data.appearance.AppearanceRepositoryImpl
import dev.gomoku.rapfidroid.domain.repository.AppearanceRepository
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class AppearanceModule {

    @Binds
    @Singleton
    abstract fun bindAppearanceRepository(impl: AppearanceRepositoryImpl): AppearanceRepository
}
