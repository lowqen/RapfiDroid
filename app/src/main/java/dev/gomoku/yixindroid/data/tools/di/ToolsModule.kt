package dev.gomoku.yixindroid.data.tools.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dev.gomoku.yixindroid.data.tools.EngineToolsRepositoryImpl
import dev.gomoku.yixindroid.domain.repository.EngineToolsRepository
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
