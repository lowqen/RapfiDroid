package dev.gomoku.rapfidroid.data.engine.di

import dev.gomoku.rapfidroid.data.engine.EngineRepositoryImpl
import dev.gomoku.rapfidroid.domain.repository.EngineRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class EngineModule {

    @Binds
    @Singleton
    abstract fun bindEngineRepository(impl: EngineRepositoryImpl): EngineRepository
}
