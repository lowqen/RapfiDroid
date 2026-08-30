package dev.gomoku.rapfidroid.data.tools.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dev.gomoku.rapfidroid.data.tools.EngineToolsRepositoryImpl
import dev.gomoku.rapfidroid.domain.repository.EngineToolsRepository
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class ToolsModule {

    @Binds
    @Singleton
    abstract fun bindEngineToolsRepository(
        impl: EngineToolsRepositoryImpl,
    ): EngineToolsRepository
}
